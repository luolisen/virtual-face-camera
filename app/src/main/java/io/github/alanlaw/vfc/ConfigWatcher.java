package io.github.alanlaw.vfc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;

import io.github.alanlaw.vfc.utils.LogUtil;
import io.github.alanlaw.vfc.utils.VideoManager;

import java.util.Locale;

/**
 * Watches for configuration changes via ContentObserver, FileObserver, and
 * BroadcastReceiver, then notifies via {@link Callback}.
 */
public final class ConfigWatcher {

    public interface Callback {
        void onMediaSourceChanged();

        void onRenderingConfigChanged(int degrees, String aspectMode);

        /** A viewport-only change must not restart the media player. */
        void onViewportChanged();
    }

    private final Callback callback;
    private android.database.ContentObserver configObserver;
    private FileObserver configFileObserver;

    public ConfigWatcher(Callback callback) {
        this.callback = callback;
    }

    /**
     * Register all observers and receivers.
     * Must be called from a thread with a Looper (typically main thread).
     */
    public void init(final Context context) {
        if (configObserver != null)
            return; // already initialized

        LogUtil.log("【CS】初始化配置监听");
        configObserver = new android.database.ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                LogUtil.log("【CS】Provider 配置变更");
                ConfigManager config = VideoManager.getConfig();
                ConfigState oldState = ConfigState.capture(config);
                config.forceReload();
                dispatchChanges(config, oldState);
            }
        };

        boolean observerRegistered = false;
        try {
            context.getContentResolver().registerContentObserver(IpcContract.URI_CONFIG, true, configObserver);
            observerRegistered = true;
        } catch (Exception e) {
            LogUtil.log("【CS】注册 ContentObserver 失败: " + e);
        }

        // Fallback: FileObserver when Provider unavailable
        if (!observerRegistered) {
            LogUtil.log("【CS】降级到 FileObserver 监听");
            try {
                String configDir = ConfigManager.DEFAULT_CONFIG_DIR;
                configFileObserver = new FileObserver(configDir,
                        FileObserver.MODIFY | FileObserver.CREATE | FileObserver.MOVED_TO) {
                    @Override
                    public void onEvent(int event, String path) {
                        if (path != null && path.endsWith(".json")) {
                            LogUtil.log("【CS】文件变更: " + path);
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                ConfigManager config = VideoManager.getConfig();
                                ConfigState oldState = ConfigState.capture(config);
                                config.forceReload();
                                dispatchChanges(config, oldState);
                            }, 200);
                        }
                    }
                };
                configFileObserver.startWatching();
                LogUtil.log("【CS】FileObserver 已启动: " + configDir);
            } catch (Exception e) {
                LogUtil.log("【CS】FileObserver 启动失败: " + e);
            }

            // Active Config Request via broadcast
            new Handler(Looper.getMainLooper()).postDelayed(() -> VideoManager.getConfig().requestConfig(context),
                    1000);
        }

        // BroadcastReceiver for control signals & configuration updates
        registerBroadcastReceiver(context);
    }

    private void registerBroadcastReceiver(final Context context) {
        try {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    String action = intent.getAction();
                    if (IpcContract.ACTION_UPDATE_CONFIG.equals(action)) {
                        handleConfigUpdate(intent);
                    }
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(IpcContract.ACTION_UPDATE_CONFIG);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            LogUtil.log("【CS】广播接收器已注册 (RECEIVER_EXPORTED)");
        } catch (Exception e) {
            LogUtil.log("【CS】注册广播接收器失败: " + e);
        }
    }

    private void handleConfigUpdate(Intent intent) {
        ConfigManager config = VideoManager.getConfig();
        String configJson = intent.getStringExtra(IpcContract.EXTRA_CONFIG_JSON);
        if (configJson == null)
            return;

        ConfigState oldState = ConfigState.capture(config);

        config.updateConfigFromJSON(configJson);

        // Always extract video from Binder if attached
        if (intent.hasExtra(IpcContract.EXTRA_VIDEO_BUNDLE)) {
            extractVideoFromBinder(intent);
        }

        dispatchChanges(config, oldState);
    }

    private void dispatchChanges(ConfigManager config, ConfigState oldState) {
        ConfigState newState = ConfigState.capture(config);
        boolean mediaChanged = !oldState.selectedVideo.equals(newState.selectedVideo)
                || !oldState.selectedImage.equals(newState.selectedImage)
                || !oldState.replaceMode.equals(newState.replaceMode)
                || oldState.forcePrivateDir != newState.forcePrivateDir
                || !oldState.sourceType.equals(newState.sourceType)
                || !oldState.streamUrl.equals(newState.streamUrl);
        boolean renderingChanged = oldState.rotation != newState.rotation
                || !oldState.aspectMode.equals(newState.aspectMode);
        boolean viewportChanged = !oldState.viewportFingerprint.equals(
                newState.viewportFingerprint);

        if (mediaChanged) {
            VideoManager.updateVideoPath(false);
            callback.onMediaSourceChanged();
            LogUtil.log("【CS】配置更新: 媒体源变化，重启播放器");
        }
        if (renderingChanged) {
            LogUtil.log("【CS】配置更新: 渲染配置 旋转=" + newState.rotation
                    + "° 适配=" + newState.aspectMode);
            callback.onRenderingConfigChanged(newState.rotation, newState.aspectMode);
        }
        if (viewportChanged) {
            LogUtil.log("【CS】配置更新: 动态取景变化，仅更新渲染器");
            callback.onViewportChanged();
        }
        if (!mediaChanged && !renderingChanged && !viewportChanged) {
            LogUtil.log("【CS】配置更新: 无变化");
        }
    }

    private static final class ConfigState {
        final String selectedVideo;
        final String selectedImage;
        final String replaceMode;
        final boolean forcePrivateDir;
        final String sourceType;
        final String streamUrl;
        final int rotation;
        final String aspectMode;
        final String viewportFingerprint;

        private ConfigState(String selectedVideo, String selectedImage, String replaceMode,
                boolean forcePrivateDir, String sourceType, String streamUrl,
                int rotation, String aspectMode, String viewportFingerprint) {
            this.selectedVideo = selectedVideo;
            this.selectedImage = selectedImage;
            this.replaceMode = replaceMode;
            this.forcePrivateDir = forcePrivateDir;
            this.sourceType = sourceType;
            this.streamUrl = streamUrl;
            this.rotation = rotation;
            this.aspectMode = aspectMode;
            this.viewportFingerprint = viewportFingerprint;
        }

        static ConfigState capture(ConfigManager config) {
            return new ConfigState(
                    config.getString(ConfigManager.KEY_SELECTED_VIDEO, ""),
                    config.getString(ConfigManager.KEY_SELECTED_IMAGE, ""),
                    config.getString(ConfigManager.KEY_REPLACE_MODE, ConfigManager.REPLACE_MODE_VIDEO),
                    config.getBoolean(ConfigManager.KEY_FORCE_PRIVATE_DIR, false),
                    config.getString(ConfigManager.KEY_MEDIA_SOURCE_TYPE, ConfigManager.MEDIA_SOURCE_LOCAL),
                    config.getString(ConfigManager.KEY_STREAM_URL, ""),
                    config.getInt(ConfigManager.KEY_VIDEO_ROTATION_OFFSET, 0),
                    config.getString(ConfigManager.KEY_VIDEO_ASPECT_MODE,
                            ConfigManager.ASPECT_MODE_DYNAMIC),
                    captureViewportFingerprint(config));
        }

        private static String captureViewportFingerprint(ConfigManager config) {
            String presetId = config.getString(
                    ConfigManager.KEY_ACTIVE_BINDING_PRESET_ID, "");
            String shortcutKey = config.getString(
                    ConfigManager.KEY_ACTIVE_BINDING_SHORTCUT, "");
            ConfigManager.Viewport viewport = config.getPresetShortcutViewport(
                    presetId, shortcutKey);
            return String.format(Locale.US, "%s|%s|%.6f|%.6f|%d",
                    presetId, shortcutKey,
                    viewport.getAnchorU(), viewport.getAnchorV(),
                    config.getViewportMoveStepPercent());
        }
    }

    private void extractVideoFromBinder(Intent intent) {
        android.os.Bundle bundle = intent.getBundleExtra(IpcContract.EXTRA_VIDEO_BUNDLE);
        if (bundle == null) {
            return;
        }
        android.os.IBinder binder = bundle.getBinder(IpcContract.EXTRA_VIDEO_BINDER);
        if (binder == null) {
            LogUtil.log("【CS】video_binder 为 null");
            return;
        }
        android.os.Parcel data = android.os.Parcel.obtain();
        android.os.Parcel reply = android.os.Parcel.obtain();
        try {
            binder.transact(1, data, reply, 0);
            reply.readException();
            int hasFd = reply.readInt();
            if (hasFd != 0) {
                android.os.ParcelFileDescriptor pfd = android.os.ParcelFileDescriptor.CREATOR.createFromParcel(reply);
                if (pfd != null) {
                    LogUtil.log("【CS】Binder 视频 FD 已获取，拷贝到私有目录");
                    VideoManager.copyToPrivateDir(pfd);
                    pfd.close();
                } else {
                    LogUtil.log("【CS】Binder PFD 为 null");
                }
            }
        } catch (Exception e) {
            LogUtil.log("【CS】Binder 获取 FD 失败: " + e);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}
