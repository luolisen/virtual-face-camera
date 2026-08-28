package io.github.alanlaw.vfc;

import android.os.Environment;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ConfigManager {
    public static final String CONFIG_FILE_NAME = "cs_config.json";
    public static final String DEFAULT_CONFIG_DIR;
    static {
        String path;
        try {
            path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/";
        } catch (Throwable e) {
            path = "/sdcard/DCIM/Camera1/";
        }
        DEFAULT_CONFIG_DIR = path;
    }

    // Config Keys
    public static final String KEY_DISABLE_MODULE = "disable_module";
    public static final String KEY_PLAY_VIDEO_SOUND = "play_video_sound";
    public static final String KEY_FORCE_PRIVATE_DIR = "force_private_dir";
    public static final String KEY_DISABLE_TOAST = "disable_toast";
    public static final String KEY_ENABLE_RANDOM_PLAY = "enable_random_play";
    public static final String KEY_TARGET_PACKAGES = "target_packages";
    public static final String KEY_SELECTED_VIDEO = "selected_video";
    public static final String KEY_ORIGINAL_VIDEO_NAME = "original_video_name";
    public static final String KEY_SELECTED_IMAGE = "selected_image";
    public static final String KEY_REPLACE_MODE = "replace_mode";
    public static final String KEY_ENABLE_MIC_HOOK = "enable_mic_hook";
    public static final String KEY_MIC_HOOK_MODE = "mic_hook_mode"; // "mute" | "replace" | "video_sync"
    public static final String KEY_SELECTED_AUDIO = "selected_audio"; // 音频文件名
    public static final String KEY_NOTIFICATION_CONTROL_ENABLED = "notification_control_enabled";
    public static final String KEY_OVERLAY_CONTROL_ENABLED = "overlay_control_enabled";
    public static final String MIC_MODE_MUTE = "mute";
    public static final String MIC_MODE_REPLACE = "replace";
    public static final String MIC_MODE_VIDEO_SYNC = "video_sync";
    public static final String REPLACE_MODE_VIDEO = "video";
    public static final String REPLACE_MODE_IMAGE = "image";
    public static final String KEY_VIDEO_ROTATION_OFFSET = "video_rotation_offset"; // 视频旋转偏移角度
    public static final String KEY_VIDEO_ASPECT_MODE = "video_aspect_mode";
    public static final String ASPECT_MODE_FIT = "fit";
    public static final String ASPECT_MODE_CROP = "crop";
    public static final String ASPECT_MODE_DYNAMIC = "dynamic";
    public static final String KEY_VIEWPORT_MOVE_STEP_PERCENT = "viewport_move_step_percent";
    public static final int DEFAULT_VIEWPORT_MOVE_STEP_PERCENT = 5;
    public static final int MIN_VIEWPORT_MOVE_STEP_PERCENT = 1;
    public static final int MAX_VIEWPORT_MOVE_STEP_PERCENT = 20;
    public static final String KEY_ACTIVE_BINDING_PRESET_ID = "active_binding_preset_id";
    public static final String KEY_ACTIVE_BINDING_SHORTCUT = "active_binding_shortcut";
    public static final String KEY_ENABLE_PHOTO_FAKE = "enable_photo_fake"; // 启用拍照替换 (动态防御)
    public static final String KEY_ENABLE_WHATSAPP_CAMERA2_COMPAT = "enable_whatsapp_camera2_compat";

    // Overlay shortcut bindings. Each key stores a video filename, or an empty string when unbound.
    public static final String KEY_SHORTCUT_DOT_VIDEO = "shortcut_dot_video";
    public static final String KEY_SHORTCUT_LEFT_VIDEO = "shortcut_left_video";
    public static final String KEY_SHORTCUT_RIGHT_VIDEO = "shortcut_right_video";
    public static final String KEY_SHORTCUT_OPEN_VIDEO = "shortcut_open_video";
    public static final String KEY_SHORTCUT_BLINK_VIDEO = "shortcut_blink_video";

    // Presets are the primary five-key binding model from v0.2 onward. The
    // legacy keys above are kept only so an existing v0.1 configuration can be
    // migrated without asking the user to bind the videos again.
    public static final String KEY_SHORTCUT_PRESETS = "shortcut_presets";
    public static final String KEY_CURRENT_PRESET_ID = "current_preset_id";
    public static final String KEY_NEXT_PRESET_NUMBER = "next_preset_number";
    public static final String PRESET_ID_FIELD = "id";
    public static final String PRESET_NAME_FIELD = "name";
    public static final String PRESET_BINDINGS_FIELD = "bindings";
    public static final String PRESET_VIEWPORTS_FIELD = "viewports";
    public static final String VIEWPORT_ANCHOR_U_FIELD = "anchor_u";
    public static final String VIEWPORT_ANCHOR_V_FIELD = "anchor_v";
    public static final String VIEWPORT_ZOOM_FIELD = "zoom";
    public static final float DEFAULT_VIEWPORT_ZOOM = 1.0f;
    public static final float MIN_VIEWPORT_ZOOM = 1.0f;
    public static final float MAX_VIEWPORT_ZOOM = 4.0f;
    public static final float VIEWPORT_ZOOM_FACTOR = 1.10f;
    public static final String PRESET_SHORTCUT_DOT = "dot";
    public static final String PRESET_SHORTCUT_LEFT = "left";
    public static final String PRESET_SHORTCUT_RIGHT = "right";
    public static final String PRESET_SHORTCUT_OPEN = "open";
    public static final String PRESET_SHORTCUT_BLINK = "blink";
    public static final String VIEWPORT_DIRECTION_UP = "up";
    public static final String VIEWPORT_DIRECTION_DOWN = "down";
    public static final String VIEWPORT_DIRECTION_LEFT = "left";
    public static final String VIEWPORT_DIRECTION_RIGHT = "right";
    private static final String[] PRESET_SHORTCUT_KEYS = {
            PRESET_SHORTCUT_DOT,
            PRESET_SHORTCUT_LEFT,
            PRESET_SHORTCUT_RIGHT,
            PRESET_SHORTCUT_OPEN,
            PRESET_SHORTCUT_BLINK
    };
    private static final String[] PRESET_DEFAULT_NAME_DIGITS = {
            "零", "一", "二", "三", "四", "五", "六", "七", "八", "九"
    };

    // Stream media source keys
    public static final String KEY_MEDIA_SOURCE_TYPE = "media_source_type";       // "local" | "stream"
    public static final String KEY_STREAM_URL = "stream_url";                     // rtsp://... etc.
    public static final String KEY_INJECTION_MODE = "injection_mode";
    public static final String INJECTION_MODE_LSPOSED = "lsposed";
    public static final String INJECTION_MODE_CAMSERVER = "cameraserver";
    public static final String KEY_STREAM_AUTO_RECONNECT = "stream_auto_reconnect";
    public static final String KEY_STREAM_LOCAL_FALLBACK = "stream_enable_local_fallback";
    public static final String KEY_STREAM_TRANSPORT_HINT = "stream_transport_hint"; // "auto" | "tcp" | "udp"
    public static final String KEY_STREAM_TIMEOUT_MS = "stream_timeout_ms";
    public static final String MEDIA_SOURCE_LOCAL = "local";
    public static final String MEDIA_SOURCE_STREAM = "stream";

    // Broadcast Actions
    public static final String ACTION_UPDATE_CONFIG = IpcContract.ACTION_UPDATE_CONFIG;
    public static final String ACTION_REQUEST_CONFIG = IpcContract.ACTION_REQUEST_CONFIG;
    public static final String EXTRA_CONFIG_JSON = IpcContract.EXTRA_CONFIG_JSON;

    // Fallback switch
    public static boolean ENABLE_LEGACY_FILE_ACCESS = true;

    private final AtomicReference<JSONObject> configData = new AtomicReference<>(new JSONObject());
    private volatile long lastLoadedTime = 0;
    private volatile android.content.Context context; // Context for remote loading
    private volatile boolean skipProviderReload = false;
    private final Object configWriteLock = new Object();

    /** Immutable view of one preset and its fixed five shortcut bindings. */
    public static final class ShortcutPreset {
        private final String id;
        private final String name;
        private final Map<String, String> bindings;

        private ShortcutPreset(String id, String name, Map<String, String> bindings) {
            this.id = id;
            this.name = name;
            this.bindings = Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Map<String, String> getBindings() {
            return bindings;
        }

        public String getVideoName(String shortcutKey) {
            String value = bindings.get(shortcutKey);
            return value == null ? "" : value;
        }
    }

    /** Immutable normalized source-space anchor for one preset shortcut. */
    public static final class Viewport {
        private final float anchorU;
        private final float anchorV;
        private final float zoom;

        public Viewport(float anchorU, float anchorV) {
            this(anchorU, anchorV, DEFAULT_VIEWPORT_ZOOM);
        }

        public Viewport(float anchorU, float anchorV, float zoom) {
            this.anchorU = clampUnit(anchorU, 0.5f);
            this.anchorV = clampUnit(anchorV, 0.5f);
            this.zoom = clamp(zoom, MIN_VIEWPORT_ZOOM, MAX_VIEWPORT_ZOOM);
        }

        public float getAnchorU() {
            return anchorU;
        }

        public float getAnchorV() {
            return anchorV;
        }

        public float getZoom() {
            return zoom;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Viewport)) {
                return false;
            }
            Viewport that = (Viewport) other;
            return Float.compare(anchorU, that.anchorU) == 0
                    && Float.compare(anchorV, that.anchorV) == 0
                    && Float.compare(zoom, that.zoom) == 0;
        }

        @Override
        public int hashCode() {
            int result = 31 * Float.floatToIntBits(anchorU) + Float.floatToIntBits(anchorV);
            return 31 * result + Float.floatToIntBits(zoom);
        }
    }

    /** The binding currently selected by a shortcut in the floating overlay. */
    public static final class ActiveBinding {
        private final String presetId;
        private final String shortcutKey;
        private final String videoName;
        private final Viewport viewport;

        private ActiveBinding(String presetId, String shortcutKey,
                String videoName, Viewport viewport) {
            this.presetId = presetId;
            this.shortcutKey = shortcutKey;
            this.videoName = videoName;
            this.viewport = viewport;
        }

        public String getPresetId() {
            return presetId;
        }

        public String getShortcutKey() {
            return shortcutKey;
        }

        public String getVideoName() {
            return videoName;
        }

        public Viewport getViewport() {
            return viewport;
        }
    }

    public ConfigManager() {
        this(true);
    }

    public ConfigManager(boolean initReload) {
        if (initReload) {
            reload();
        }
    }

    public void setSkipProviderReload(boolean skip) {
        this.skipProviderReload = skip;
    }

    public void setContext(android.content.Context context) {
        this.context = context;
        reload(); // Reload with context
    }

    public JSONObject getConfigData() {
        return copyConfig(getConfigSnapshot());
    }

    private final AtomicLong lastReloadTime = new AtomicLong(0);
    private static final long MIN_RELOAD_INTERVAL_MS = 1000; // 1 second debounce

    private interface ConfigMutation {
        void apply(JSONObject config) throws JSONException;
    }

    private JSONObject getConfigSnapshot() {
        JSONObject snapshot = configData.get();
        return snapshot != null ? snapshot : new JSONObject();
    }

    private static JSONObject copyConfig(JSONObject source) {
        if (source == null) {
            return new JSONObject();
        }
        try {
            return new JSONObject(source.toString());
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private void setConfigSnapshot(JSONObject snapshot) {
        configData.set(snapshot != null ? snapshot : new JSONObject());
    }

    private void updateConfigAndSave(ConfigMutation mutation) {
        synchronized (configWriteLock) {
            try {
                JSONObject updated = copyConfig(getConfigSnapshot());
                mutation.apply(updated);
                setConfigSnapshot(updated);
                save(updated);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public void reload() {
        long now = System.currentTimeMillis();
        while (true) {
            long last = lastReloadTime.get();
            if (now - last < MIN_RELOAD_INTERVAL_MS) {
                return;
            }
            if (lastReloadTime.compareAndSet(last, now)) {
                break;
            }
        }

        boolean providerSuccess = false;
        if (context != null && !skipProviderReload) {
            providerSuccess = reloadFromProvider();
        }

        if (!providerSuccess && ENABLE_LEGACY_FILE_ACCESS) {
            reloadFromFile();
        }
    }

    /**
     * 强制重新加载配置，忽略防抖时间限制和文件修改时间检查。
     * 用于 ContentObserver.onChange() 等需要立即读取最新配置的场景。
     */
    public void forceReload() {
        lastReloadTime.set(0); // 重置防抖
        lastLoadedTime = 0; // 重置文件时间戳，强制重读文件
        reload();
    }

    private boolean reloadFromProvider() {
        android.net.Uri uri = IpcContract.URI_CONFIG;
        try (android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null) {
                JSONObject newConfig = new JSONObject();
                while (cursor.moveToNext()) {
                    String key = cursor.getString(0);
                    String valueStr = cursor.getString(1);
                    String type = cursor.getString(2);

                    try {
                        if ("boolean".equals(type)) {
                            newConfig.put(key, Boolean.parseBoolean(valueStr));
                        } else if ("int".equals(type)) {
                            newConfig.put(key, Integer.parseInt(valueStr));
                        } else if ("long".equals(type)) {
                            newConfig.put(key, Long.parseLong(valueStr));
                        } else if ("json_array".equals(type)) {
                            newConfig.put(key, new JSONArray(valueStr));
                        } else {
                            newConfig.put(key, valueStr);
                        }
                    } catch (Exception e) {
                        newConfig.put(key, valueStr);
                    }
                }

                if (newConfig.length() > 0) {
                    setConfigSnapshot(newConfig);
                    io.github.alanlaw.vfc.utils.LogUtil.log("【CS】配置已通过 Provider 加载 (" + newConfig.length() + " keys)");
                    return true;
                } else {
                    io.github.alanlaw.vfc.utils.LogUtil
                            .log("【CS】Provider Cursor 为空 (0 行), 降级到文件读取");
                }
            } else {
                io.github.alanlaw.vfc.utils.LogUtil.log("【CS】Provider Cursor 为空, 降级到文件读取");
            }
        } catch (Exception e) {
            io.github.alanlaw.vfc.utils.LogUtil.log("【CS】配置 Provider 错误: " + e);
        }
        return false;
    }

    /**
     * Request config from host app via broadcast.
     * Useful for cold start of target app when provider/file is inaccessible.
     */
    public void requestConfig(Context context) {
        try {
            android.content.Intent intent = new android.content.Intent(IpcContract.ACTION_REQUEST_CONFIG);
            intent.setPackage("io.github.alanlaw.vfc"); // Explicit intent to wake up host receiver
            intent.putExtra(IpcContract.EXTRA_REQUESTER_PACKAGE, context.getPackageName());
            context.sendBroadcast(intent);
            io.github.alanlaw.vfc.utils.LogUtil.log("【CS】已发送配置请求广播 config request broadcast sent");
        } catch (Exception e) {
            io.github.alanlaw.vfc.utils.LogUtil.log("【CS】发送配置请求广播失败: " + e);
        }
    }

    /**
     * Send current config via broadcast.
     */
    public void sendConfigBroadcast(Context context) {
        sendConfigBroadcast(context, null);
    }

    public void sendConfigBroadcast(Context context, String explicitTargetPackage) {
        Set<String> targetPackages = new HashSet<>();
        if (explicitTargetPackage != null && !explicitTargetPackage.isEmpty()) {
            targetPackages.add(explicitTargetPackage);
        } else {
            targetPackages.addAll(getTargetPackages());
        }

        if (targetPackages.isEmpty()) {
            sendConfigBroadcastInternal(context, null);
            return;
        }

        for (String targetPackage : targetPackages) {
            sendConfigBroadcastInternal(context, targetPackage);
        }
    }

    private void sendConfigBroadcastInternal(Context context, String targetPackage) {
        try {
            android.content.Intent intent = new android.content.Intent(IpcContract.ACTION_UPDATE_CONFIG);
            if (targetPackage != null && !targetPackage.isEmpty()) {
                intent.setPackage(targetPackage);
            }
            intent.putExtra(IpcContract.EXTRA_CONFIG_JSON, getConfigSnapshot().toString());

            String videoName = getString(KEY_SELECTED_VIDEO, "Cam.mp4");
            File videoFile = null;
            if (videoName != null && !videoName.isEmpty()) {
                videoFile = new File(DEFAULT_CONFIG_DIR, videoName);
            }
            if (videoFile == null || !videoFile.exists()) {
                File[] files = new File(DEFAULT_CONFIG_DIR)
                        .listFiles((dir, name) -> name.toLowerCase().endsWith(".mp4"));
                if (files != null && files.length > 0) {
                    videoFile = files[0];
                }
            }
            if (videoFile != null && !videoFile.exists()) {
                videoFile = new File(DEFAULT_CONFIG_DIR, "Cam.mp4");
            }
            if (videoFile != null && videoFile.exists()) {
                try {
                    final File finalVideoFile = videoFile;
                    android.os.Bundle bundle = new android.os.Bundle();
                    bundle.putBinder(IpcContract.EXTRA_VIDEO_BINDER, new android.os.Binder() {
                        @Override
                        protected boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply,
                                int flags) throws android.os.RemoteException {
                            if (code == 1) { // 1 = Get FD
                                reply.writeNoException();
                                try {
                                    android.os.ParcelFileDescriptor pfd = android.os.ParcelFileDescriptor
                                            .open(finalVideoFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
                                    reply.writeInt(1);
                                    pfd.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
                                } catch (Exception e) {
                                    io.github.alanlaw.vfc.utils.LogUtil.log("【CS】Binder PFD 失败: " + e);
                                    reply.writeInt(0);
                                }
                                return true;
                            }
                            return super.onTransact(code, data, reply, flags);
                        }
                    });
                    intent.putExtra(IpcContract.EXTRA_VIDEO_BUNDLE, bundle);
                } catch (Exception e) {
                    io.github.alanlaw.vfc.utils.LogUtil.log("【CS】广播附加 video_bundle 失败: " + e);
                }
            }

            context.sendBroadcast(intent);
            if (targetPackage != null && !targetPackage.isEmpty()) {
                io.github.alanlaw.vfc.utils.LogUtil.log("【CS】配置广播已发送到: " + targetPackage);
            } else {
                io.github.alanlaw.vfc.utils.LogUtil.log("【CS】配置广播已发送");
            }
        } catch (Exception e) {
            io.github.alanlaw.vfc.utils.LogUtil.log("【CS】广播配置失败: " + e);
        }
    }

    private void reloadFromFile() {
        File configFile = new File(DEFAULT_CONFIG_DIR, CONFIG_FILE_NAME);
        if (configFile.exists()) {
            long fileModTime = configFile.lastModified();
            // fileModTime==0 means we couldn't get modification time (external storage
            // restriction).
            // When lastLoadedTime==0 (forceReload triggered), always read regardless of
            // timestamp.
            boolean shouldRead = (lastLoadedTime == 0) || (fileModTime > 0 && fileModTime > lastLoadedTime);
            if (shouldRead) {
                try {
                    StringBuilder stringBuilder = new StringBuilder();
                    try (BufferedReader bufferedReader = new BufferedReader(
                            new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = bufferedReader.readLine()) != null) {
                            stringBuilder.append(line);
                        }
                    }
                    setConfigSnapshot(new JSONObject(stringBuilder.toString()));
                    lastLoadedTime = (fileModTime > 0) ? fileModTime : System.currentTimeMillis();
                    io.github.alanlaw.vfc.utils.LogUtil
                            .log("【CS】配置已从文件加载: " + configFile.getName());
                } catch (Exception e) {
                    io.github.alanlaw.vfc.utils.LogUtil.log("【CS】Config file read error: " + e);
                    setConfigSnapshot(getConfigSnapshot());
                }
            } else {
                // Config file unchanged, skip read
            }
        } else {
            io.github.alanlaw.vfc.utils.LogUtil.log("【CS】Config file not found: " + configFile.getAbsolutePath());
            setConfigSnapshot(getConfigSnapshot());
        }
    }

    public boolean getBoolean(String key, boolean defValue) {
        return getConfigSnapshot().optBoolean(key, defValue);
    }

    public int getInt(String key, int defValue) {
        return getConfigSnapshot().optInt(key, defValue);
    }

    public void setInt(String key, int value) {
        updateConfigAndSave(config -> config.put(key, value));
    }

    public void setBoolean(String key, boolean value) {
        updateConfigAndSave(config -> config.put(key, value));
    }

    public Set<String> getTargetPackages() {
        Set<String> packages = new HashSet<>();
        JSONArray jsonArray = getConfigSnapshot().optJSONArray(KEY_TARGET_PACKAGES);
        if (jsonArray != null) {
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    packages.add(jsonArray.getString(i));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return packages;
    }

    public void setTargetPackages(Set<String> packages) {
        JSONArray jsonArray = new JSONArray();
        for (String pkg : packages) {
            jsonArray.put(pkg);
        }
        updateConfigAndSave(config -> config.put(KEY_TARGET_PACKAGES, jsonArray));
    }

    public void addTargetPackage(String pkg) {
        Set<String> packages = getTargetPackages();
        packages.add(pkg);
        setTargetPackages(packages);
    }

    public void removeTargetPackage(String pkg) {
        Set<String> packages = getTargetPackages();
        packages.remove(pkg);
        setTargetPackages(packages);
    }

    public long getLong(String key, long defValue) {
        return getConfigSnapshot().optLong(key, defValue);
    }

    public void setLong(String key, long value) {
        updateConfigAndSave(config -> config.put(key, value));
    }

    public String getString(String key, String defValue) {
        return getConfigSnapshot().optString(key, defValue);
    }

    public void setString(String key, String value) {
        updateConfigAndSave(config -> config.put(key, value));
    }

    public String getShortcutVideo(String key) {
        if (!isShortcutVideoKey(key)) {
            return "";
        }
        return getString(key, "");
    }

    public void setShortcutVideo(String key, String videoName) {
        if (!isShortcutVideoKey(key)) {
            throw new IllegalArgumentException("Unknown shortcut binding key: " + key);
        }
        setString(key, videoName == null ? "" : videoName);
    }

    /** Return the fixed shortcut order used by both the app and the overlay. */
    public static String[] getPresetShortcutKeys() {
        return PRESET_SHORTCUT_KEYS.clone();
    }

    public static boolean isPresetShortcutKey(String key) {
        if (key == null) {
            return false;
        }
        for (String shortcutKey : PRESET_SHORTCUT_KEYS) {
            if (shortcutKey.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** Read all well-formed presets in their persisted creation order. */
    public List<ShortcutPreset> listPresets() {
        return parsePresets(getConfigSnapshot());
    }

    public ShortcutPreset getPreset(String presetId) {
        if (presetId == null || presetId.trim().isEmpty()) {
            return null;
        }
        JSONArray presets = getConfigSnapshot().optJSONArray(KEY_SHORTCUT_PRESETS);
        if (presets == null) {
            return null;
        }
        for (int i = 0; i < presets.length(); i++) {
            JSONObject preset = presets.optJSONObject(i);
            if (preset == null || !presetId.equals(preset.optString(PRESET_ID_FIELD, ""))) {
                continue;
            }
            return parsePreset(preset);
        }
        return null;
    }

    public ShortcutPreset getCurrentPreset() {
        return getPreset(getString(KEY_CURRENT_PRESET_ID, ""));
    }

    /**
     * Create one fixed five-slot preset. The first created preset becomes the
     * current preset; later creations do not change the current selection.
     */
    public ShortcutPreset createPreset() {
        synchronized (configWriteLock) {
            JSONObject updated = copyConfig(getConfigSnapshot());
            JSONArray presets = updated.optJSONArray(KEY_SHORTCUT_PRESETS);
            if (presets == null) {
                presets = new JSONArray();
            }

            int number = nextAvailablePresetNumber(updated, presets);
            String presetId = UUID.randomUUID().toString();
            JSONObject preset = new JSONObject();
            try {
                preset.put(PRESET_ID_FIELD, presetId);
                preset.put(PRESET_NAME_FIELD, defaultPresetName(number));
                preset.put(PRESET_BINDINGS_FIELD, emptyPresetBindings());
                preset.put(PRESET_VIEWPORTS_FIELD, emptyPresetViewports());
                presets.put(preset);
                updated.put(KEY_SHORTCUT_PRESETS, presets);
                updated.put(KEY_NEXT_PRESET_NUMBER, number + 1);

                if (getPresetIndex(presets, updated.optString(KEY_CURRENT_PRESET_ID, "")) < 0) {
                    updated.put(KEY_CURRENT_PRESET_ID, presetId);
                }
                setConfigSnapshot(updated);
                save(updated);
                return parsePreset(preset);
            } catch (JSONException e) {
                return null;
            }
        }
    }

    public boolean renamePreset(String presetId, String name) {
        String normalizedName = name == null ? "" : name.trim();
        if (presetId == null || presetId.trim().isEmpty() || normalizedName.isEmpty()) {
            return false;
        }
        synchronized (configWriteLock) {
            JSONObject updated = copyConfig(getConfigSnapshot());
            JSONArray presets = updated.optJSONArray(KEY_SHORTCUT_PRESETS);
            int index = getPresetIndex(presets, presetId);
            JSONObject preset = presets == null ? null : presets.optJSONObject(index);
            if (preset == null) {
                return false;
            }
            try {
                preset.put(PRESET_NAME_FIELD, normalizedName);
                updated.put(KEY_SHORTCUT_PRESETS, presets);
                setConfigSnapshot(updated);
                save(updated);
                return true;
            } catch (JSONException e) {
                return false;
            }
        }
    }

    /**
     * Delete a preset without deleting any media asset. If the current preset
     * is deleted, the first remaining preset becomes current; no replacement
     * preset is created when the list becomes empty.
     */
    public boolean deletePreset(String presetId) {
        if (presetId == null || presetId.trim().isEmpty()) {
            return false;
        }
        synchronized (configWriteLock) {
            JSONObject updated = copyConfig(getConfigSnapshot());
            JSONArray presets = updated.optJSONArray(KEY_SHORTCUT_PRESETS);
            int index = getPresetIndex(presets, presetId);
            if (presets == null || index < 0) {
                return false;
            }

            boolean deletingCurrent = presetId.equals(updated.optString(KEY_CURRENT_PRESET_ID, ""));
            presets.remove(index);
            try {
                if (deletingCurrent) {
                    if (presets.length() > 0) {
                        JSONObject first = presets.optJSONObject(0);
                        String firstId = first == null ? "" : first.optString(PRESET_ID_FIELD, "");
                        if (firstId.isEmpty()) {
                            updated.remove(KEY_CURRENT_PRESET_ID);
                        } else {
                            updated.put(KEY_CURRENT_PRESET_ID, firstId);
                        }
                    } else {
                        updated.remove(KEY_CURRENT_PRESET_ID);
                    }
                }
                if (presetId.equals(updated.optString(KEY_ACTIVE_BINDING_PRESET_ID, ""))) {
                    updated.remove(KEY_ACTIVE_BINDING_PRESET_ID);
                    updated.remove(KEY_ACTIVE_BINDING_SHORTCUT);
                }
                updated.put(KEY_SHORTCUT_PRESETS, presets);
                setConfigSnapshot(updated);
                save(updated);
                return true;
            } catch (JSONException e) {
                return false;
            }
        }
    }

    /** Change only the current preset ID; selected_video is intentionally untouched. */
    public boolean setCurrentPreset(String presetId) {
        if (presetId == null || presetId.trim().isEmpty()) {
            return false;
        }
        synchronized (configWriteLock) {
            JSONObject updated = copyConfig(getConfigSnapshot());
            JSONArray presets = updated.optJSONArray(KEY_SHORTCUT_PRESETS);
            if (getPresetIndex(presets, presetId) < 0) {
                return false;
            }
            if (presetId.equals(updated.optString(KEY_CURRENT_PRESET_ID, ""))) {
                return true;
            }
            try {
                updated.put(KEY_CURRENT_PRESET_ID, presetId);
                setConfigSnapshot(updated);
                save(updated);
                return true;
            } catch (JSONException e) {
                return false;
            }
        }
    }

    public boolean bindPresetShortcut(String presetId, String shortcutKey, String videoName) {
        if (!isPresetShortcutKey(shortcutKey) || !isValidPresetVideoName(videoName)) {
            return false;
        }
        return updatePresetBinding(presetId, shortcutKey, videoName);
    }

    public boolean unbindPresetShortcut(String presetId, String shortcutKey) {
        if (!isPresetShortcutKey(shortcutKey)) {
            return false;
        }
        return updatePresetBinding(presetId, shortcutKey, "");
    }

    public String getCurrentPresetShortcutVideo(String shortcutKey) {
        if (!isPresetShortcutKey(shortcutKey)) {
            return "";
        }
        ShortcutPreset preset = getCurrentPreset();
        return preset == null ? "" : preset.getVideoName(shortcutKey);
    }

    /** Return one shortcut's viewport, defaulting safely to the center. */
    public Viewport getPresetShortcutViewport(String presetId, String shortcutKey) {
        if (presetId == null || presetId.trim().isEmpty() || !isPresetShortcutKey(shortcutKey)) {
            return centeredViewport();
        }
        return getViewportFromConfig(getConfigSnapshot(), presetId, shortcutKey);
    }

    public Viewport getCurrentPresetShortcutViewport(String shortcutKey) {
        return getPresetShortcutViewport(getString(KEY_CURRENT_PRESET_ID, ""), shortcutKey);
    }

    /**
     * Return the active preset binding and its source-space anchor. An active
     * binding is intentionally independent from current_preset_id.
     */
    public ActiveBinding getActiveBinding() {
        JSONObject config = getConfigSnapshot();
        String presetId = config.optString(KEY_ACTIVE_BINDING_PRESET_ID, "").trim();
        String shortcutKey = config.optString(KEY_ACTIVE_BINDING_SHORTCUT, "").trim();
        if (presetId.isEmpty() || !isPresetShortcutKey(shortcutKey)) {
            return null;
        }
        ShortcutPreset preset = getPresetFromConfig(config, presetId);
        if (preset == null) {
            return null;
        }
        String videoName = preset.getVideoName(shortcutKey);
        if (!isValidPresetVideoName(videoName)) {
            return null;
        }
        return new ActiveBinding(presetId, shortcutKey, videoName,
                getViewportFromConfig(config, presetId, shortcutKey));
    }

    /**
     * Atomically select a preset shortcut and update selected_video plus the
     * active binding identity in one config snapshot.
     */
    public boolean selectPresetShortcut(String presetId, String shortcutKey, String expectedVideoName) {
        if (presetId == null || presetId.trim().isEmpty()
                || !isPresetShortcutKey(shortcutKey)
                || !isValidPresetVideoName(expectedVideoName)) {
            return false;
        }
        synchronized (configWriteLock) {
            JSONObject updated = copyConfig(getConfigSnapshot());
            ShortcutPreset preset = getPresetFromConfig(updated, presetId);
            if (preset == null || !expectedVideoName.equals(preset.getVideoName(shortcutKey))) {
                return false;
            }
            boolean changed = !expectedVideoName.equals(
                    updated.optString(KEY_SELECTED_VIDEO, ""))
                    || !presetId.equals(updated.optString(KEY_ACTIVE_BINDING_PRESET_ID, ""))
                    || !shortcutKey.equals(updated.optString(KEY_ACTIVE_BINDING_SHORTCUT, ""));
            if (!changed) {
                return false;
            }
            try {
                updated.put(KEY_SELECTED_VIDEO, expectedVideoName);
                updated.put(KEY_ACTIVE_BINDING_PRESET_ID, presetId);
                updated.put(KEY_ACTIVE_BINDING_SHORTCUT, shortcutKey);
                setConfigSnapshot(updated);
                save(updated);
                return true;
            } catch (JSONException e) {
                return false;
            }
        }
    }

    /** Set selected_video for direct/random controls and clear shortcut identity atomically. */
    public boolean setSelectedVideoAndClearActive(String videoName) {
        if (!isValidPresetVideoName(videoName)) {
            return false;
        }
        synchronized (configWriteLock) {
            JSONObject updated = copyConfig(getConfigSnapshot());
            boolean changed = !videoName.equals(updated.optString(KEY_SELECTED_VIDEO, ""))
                    || updated.has(KEY_ACTIVE_BINDING_PRESET_ID)
                    || updated.has(KEY_ACTIVE_BINDING_SHORTCUT);
            if (!changed) {
                return false;
            }
            try {
                updated.put(KEY_SELECTED_VIDEO, videoName);
                updated.remove(KEY_ACTIVE_BINDING_PRESET_ID);
                updated.remove(KEY_ACTIVE_BINDING_SHORTCUT);
                setConfigSnapshot(updated);
                save(updated);
                return true;
            } catch (JSONException e) {
                return false;
            }
        }
    }

    public boolean clearActiveBinding() {
        synchronized (configWriteLock) {
            JSONObject updated = copyConfig(getConfigSnapshot());
            if (!updated.has(KEY_ACTIVE_BINDING_PRESET_ID)
                    && !updated.has(KEY_ACTIVE_BINDING_SHORTCUT)) {
                return false;
            }
            updated.remove(KEY_ACTIVE_BINDING_PRESET_ID);
            updated.remove(KEY_ACTIVE_BINDING_SHORTCUT);
            setConfigSnapshot(updated);
            save(updated);
            return true;
        }
    }

    /** Return the configured joystick step constrained to the product range. */
    public int getViewportMoveStepPercent() {
        return clamp(getInt(KEY_VIEWPORT_MOVE_STEP_PERCENT,
                DEFAULT_VIEWPORT_MOVE_STEP_PERCENT),
                MIN_VIEWPORT_MOVE_STEP_PERCENT, MAX_VIEWPORT_MOVE_STEP_PERCENT);
    }

    public void setViewportMoveStepPercent(int percent) {
        setInt(KEY_VIEWPORT_MOVE_STEP_PERCENT,
                clamp(percent, MIN_VIEWPORT_MOVE_STEP_PERCENT, MAX_VIEWPORT_MOVE_STEP_PERCENT));
    }

    /**
     * Move the active binding in logical sensor space, then persist the
     * corresponding decoded-source anchor. Rendering performs the final
     * target-aspect clamp for each actual output surface.
     */
    public boolean moveActiveBindingViewport(String direction, int stepPercent) {
        if (!isViewportDirection(direction)) {
            return false;
        }
        int step = clamp(stepPercent, MIN_VIEWPORT_MOVE_STEP_PERCENT,
                MAX_VIEWPORT_MOVE_STEP_PERCENT);
        synchronized (configWriteLock) {
            JSONObject updated = copyConfig(getConfigSnapshot());
            if (!ASPECT_MODE_DYNAMIC.equals(updated.optString(KEY_VIDEO_ASPECT_MODE,
                    ASPECT_MODE_DYNAMIC))) {
                return false;
            }
            String presetId = updated.optString(KEY_ACTIVE_BINDING_PRESET_ID, "").trim();
            String shortcutKey = updated.optString(KEY_ACTIVE_BINDING_SHORTCUT, "").trim();
            if (presetId.isEmpty() || !isPresetShortcutKey(shortcutKey)) {
                return false;
            }
            ShortcutPreset preset = getPresetFromConfig(updated, presetId);
            if (preset == null || preset.getVideoName(shortcutKey).isEmpty()) {
                return false;
            }
            Viewport viewport = getViewportFromConfig(updated, presetId, shortcutKey);
            int rotation = normalizeRotation(updated.optInt(KEY_VIDEO_ROTATION_OFFSET, 0));
            float[] logical = VirtualSensorTransform.sourceToLogical(
                    viewport.getAnchorU(), viewport.getAnchorV(), rotation);
            logical = VirtualSensorTransform.moveLogical(logical[0], logical[1], direction, step);
            float[] source = VirtualSensorTransform.logicalToSource(
                    logical[0], logical[1], rotation);
            return updateViewportInSnapshot(updated, presetId, shortcutKey,
                    new Viewport(source[0], source[1], viewport.getZoom()));
        }
    }

    public boolean resetActiveBindingViewport() {
        synchronized (configWriteLock) {
            JSONObject updated = copyConfig(getConfigSnapshot());
            if (!ASPECT_MODE_DYNAMIC.equals(updated.optString(KEY_VIDEO_ASPECT_MODE,
                    ASPECT_MODE_DYNAMIC))) {
                return false;
            }
            String presetId = updated.optString(KEY_ACTIVE_BINDING_PRESET_ID, "").trim();
            String shortcutKey = updated.optString(KEY_ACTIVE_BINDING_SHORTCUT, "").trim();
            if (presetId.isEmpty() || !isPresetShortcutKey(shortcutKey)) {
                return false;
            }
            return updateViewportInSnapshot(updated, presetId, shortcutKey, centeredViewport());
        }
    }

    /**
     * Persist a viewport calculated by the target camera process. The caller
     * supplies the already clamped value so the provider remains the single
     * atomic writer for the active preset/shortcut binding.
     */
    public boolean setActiveBindingViewport(Viewport viewport) {
        synchronized (configWriteLock) {
            JSONObject updated = copyConfig(getConfigSnapshot());
            String presetId = updated.optString(KEY_ACTIVE_BINDING_PRESET_ID, "").trim();
            String shortcutKey = updated.optString(KEY_ACTIVE_BINDING_SHORTCUT, "").trim();
            if (presetId.isEmpty() || !isPresetShortcutKey(shortcutKey)) {
                return false;
            }
            return updateViewportInSnapshot(updated, presetId, shortcutKey,
                    viewport == null ? centeredViewport() : viewport);
        }
    }

    /**
     * Migrate the v0.1 global five-key bindings exactly once. An existing
     * shortcut_presets key, including an empty array, is authoritative.
     */
    public boolean migrateLegacyShortcutBindingsIfNeeded() {
        synchronized (configWriteLock) {
            JSONObject current = getConfigSnapshot();
            if (current.has(KEY_SHORTCUT_PRESETS)) {
                return false;
            }

            String[] legacyValues = new String[] {
                    current.optString(KEY_SHORTCUT_DOT_VIDEO, ""),
                    current.optString(KEY_SHORTCUT_LEFT_VIDEO, ""),
                    current.optString(KEY_SHORTCUT_RIGHT_VIDEO, ""),
                    current.optString(KEY_SHORTCUT_OPEN_VIDEO, ""),
                    current.optString(KEY_SHORTCUT_BLINK_VIDEO, "")
            };
            boolean hasValidBinding = false;
            for (String value : legacyValues) {
                if (isValidPresetVideoName(value)) {
                    hasValidBinding = true;
                    break;
                }
            }
            if (!hasValidBinding) {
                return false;
            }

            JSONObject updated = copyConfig(current);
            JSONObject preset = new JSONObject();
            try {
                String presetId = UUID.randomUUID().toString();
                preset.put(PRESET_ID_FIELD, presetId);
                preset.put(PRESET_NAME_FIELD, defaultPresetName(1));
                JSONObject bindings = emptyPresetBindings();
                for (int i = 0; i < PRESET_SHORTCUT_KEYS.length; i++) {
                    String value = isValidPresetVideoName(legacyValues[i]) ? legacyValues[i] : "";
                    bindings.put(PRESET_SHORTCUT_KEYS[i], value);
                }
                preset.put(PRESET_BINDINGS_FIELD, bindings);
                preset.put(PRESET_VIEWPORTS_FIELD, emptyPresetViewports());
                JSONArray presets = new JSONArray();
                presets.put(preset);
                updated.put(KEY_SHORTCUT_PRESETS, presets);
                updated.put(KEY_CURRENT_PRESET_ID, presetId);
                updated.put(KEY_NEXT_PRESET_NUMBER, 2);
                setConfigSnapshot(updated);
                save(updated);
                return true;
            } catch (JSONException e) {
                return false;
            }
        }
    }

    /** Force audio-related runtime flags off for the v0.2 configuration. */
    public boolean enforceAudioFeaturesDisabled() {
        synchronized (configWriteLock) {
            JSONObject current = getConfigSnapshot();
            boolean changed = current.optBoolean(KEY_PLAY_VIDEO_SOUND, false)
                    || current.optBoolean(KEY_ENABLE_MIC_HOOK, false)
                    || !MIC_MODE_MUTE.equals(current.optString(KEY_MIC_HOOK_MODE, MIC_MODE_MUTE));
            if (!changed) {
                return false;
            }
            JSONObject updated = copyConfig(current);
            try {
                updated.put(KEY_PLAY_VIDEO_SOUND, false);
                updated.put(KEY_ENABLE_MIC_HOOK, false);
                updated.put(KEY_MIC_HOOK_MODE, MIC_MODE_MUTE);
                setConfigSnapshot(updated);
                save(updated);
                return true;
            } catch (JSONException e) {
                return false;
            }
        }
    }

    /** Force the removed notification-control feature off for upgraded configs. */
    public boolean enforceNotificationControlDisabled() {
        synchronized (configWriteLock) {
            JSONObject current = getConfigSnapshot();
            if (!current.optBoolean(KEY_NOTIFICATION_CONTROL_ENABLED, false)) {
                return false;
            }
            JSONObject updated = copyConfig(current);
            try {
                updated.put(KEY_NOTIFICATION_CONTROL_ENABLED, false);
                setConfigSnapshot(updated);
                save(updated);
                return true;
            } catch (JSONException e) {
                return false;
            }
        }
    }

    /** Apply all v0.2 one-time compatibility changes through one public entry point. */
    public boolean migrateV02Configuration() {
        boolean presetMigrated = migrateLegacyShortcutBindingsIfNeeded();
        boolean audioChanged = enforceAudioFeaturesDisabled();
        boolean notificationChanged = enforceNotificationControlDisabled();
        boolean viewportMigrated = migratePresetViewportsIfNeeded();
        boolean aspectMigrated = normalizeAspectModeForV022();
        boolean stepMigrated = getInt(KEY_VIEWPORT_MOVE_STEP_PERCENT,
                DEFAULT_VIEWPORT_MOVE_STEP_PERCENT) != getViewportMoveStepPercent();
        if (stepMigrated) {
            setViewportMoveStepPercent(getViewportMoveStepPercent());
        }
        return presetMigrated || audioChanged || notificationChanged
                || viewportMigrated || aspectMigrated || stepMigrated;
    }

    private boolean updatePresetBinding(String presetId, String shortcutKey, String videoName) {
        if (presetId == null || presetId.trim().isEmpty()) {
            return false;
        }
        synchronized (configWriteLock) {
            JSONObject updated = copyConfig(getConfigSnapshot());
            JSONArray presets = updated.optJSONArray(KEY_SHORTCUT_PRESETS);
            int index = getPresetIndex(presets, presetId);
            JSONObject preset = presets == null ? null : presets.optJSONObject(index);
            JSONObject bindings = preset == null ? null : preset.optJSONObject(PRESET_BINDINGS_FIELD);
            if (preset == null || bindings == null) {
                return false;
            }
            try {
                boolean bindingChanged = !videoName.equals(bindings.optString(shortcutKey, ""));
                bindings.put(shortcutKey, videoName);
                preset.put(PRESET_BINDINGS_FIELD, bindings);
                JSONObject viewports = ensurePresetViewports(preset);
                Viewport oldViewport = getViewportFromConfig(updated, presetId, shortcutKey);
                boolean viewportChanged = !centeredViewport().equals(oldViewport);
                viewports.put(shortcutKey, viewportJson(centeredViewport()));
                preset.put(PRESET_VIEWPORTS_FIELD, viewports);
                // Rebinding is intentionally independent from playback state.
                // The selected video and active binding identity must remain
                // untouched until the user explicitly selects another slot.
                if (!bindingChanged && !viewportChanged) {
                    return true;
                }
                presets.put(index, preset);
                updated.put(KEY_SHORTCUT_PRESETS, presets);
                setConfigSnapshot(updated);
                save(updated);
                return true;
            } catch (JSONException e) {
                return false;
            }
        }
    }

    private static List<ShortcutPreset> parsePresets(JSONObject config) {
        JSONArray presets = config == null ? null : config.optJSONArray(KEY_SHORTCUT_PRESETS);
        if (presets == null) {
            return Collections.emptyList();
        }
        List<ShortcutPreset> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < presets.length(); i++) {
            JSONObject preset = presets.optJSONObject(i);
            ShortcutPreset parsed = parsePreset(preset);
            if (parsed != null && ids.add(parsed.getId())) {
                result.add(parsed);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static ShortcutPreset parsePreset(JSONObject preset) {
        if (preset == null) {
            return null;
        }
        String id = preset.optString(PRESET_ID_FIELD, "").trim();
        String name = preset.optString(PRESET_NAME_FIELD, "").trim();
        if (id.isEmpty() || name.isEmpty()) {
            return null;
        }
        JSONObject jsonBindings = preset.optJSONObject(PRESET_BINDINGS_FIELD);
        Map<String, String> bindings = new LinkedHashMap<>();
        for (String shortcutKey : PRESET_SHORTCUT_KEYS) {
            String value = jsonBindings == null ? "" : jsonBindings.optString(shortcutKey, "");
            bindings.put(shortcutKey, isValidPresetVideoName(value) ? value : "");
        }
        return new ShortcutPreset(id, name, bindings);
    }

    private static JSONObject emptyPresetBindings() throws JSONException {
        JSONObject bindings = new JSONObject();
        for (String shortcutKey : PRESET_SHORTCUT_KEYS) {
            bindings.put(shortcutKey, "");
        }
        return bindings;
    }

    private static JSONObject emptyPresetViewports() throws JSONException {
        JSONObject viewports = new JSONObject();
        for (String shortcutKey : PRESET_SHORTCUT_KEYS) {
            viewports.put(shortcutKey, viewportJson(centeredViewport()));
        }
        return viewports;
    }

    private static JSONObject ensurePresetViewports(JSONObject preset) throws JSONException {
        JSONObject existing = preset == null
                ? null : preset.optJSONObject(PRESET_VIEWPORTS_FIELD);
        JSONObject viewports = existing == null ? new JSONObject() : existing;
        for (String shortcutKey : PRESET_SHORTCUT_KEYS) {
            JSONObject viewport = viewports.optJSONObject(shortcutKey);
            if (viewport == null) {
                viewports.put(shortcutKey, viewportJson(centeredViewport()));
            }
        }
        return viewports;
    }

    private static JSONObject viewportJson(Viewport viewport) throws JSONException {
        JSONObject value = new JSONObject();
        value.put(VIEWPORT_ANCHOR_U_FIELD, viewport.getAnchorU());
        value.put(VIEWPORT_ANCHOR_V_FIELD, viewport.getAnchorV());
        value.put(VIEWPORT_ZOOM_FIELD, viewport.getZoom());
        return value;
    }

    private static Viewport centeredViewport() {
        return new Viewport(0.5f, 0.5f);
    }

    private static float clampUnit(float value, float fallback) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return fallback;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int normalizeRotation(int degrees) {
        return ((degrees % 360) + 360) % 360;
    }

    private static boolean isViewportDirection(String direction) {
        return VIEWPORT_DIRECTION_UP.equals(direction)
                || VIEWPORT_DIRECTION_DOWN.equals(direction)
                || VIEWPORT_DIRECTION_LEFT.equals(direction)
                || VIEWPORT_DIRECTION_RIGHT.equals(direction);
    }

    private static Viewport getViewportFromConfig(JSONObject config, String presetId,
            String shortcutKey) {
        ShortcutPreset preset = getPresetFromConfig(config, presetId);
        if (preset == null || !isPresetShortcutKey(shortcutKey)) {
            return centeredViewport();
        }
        JSONArray presets = config == null ? null : config.optJSONArray(KEY_SHORTCUT_PRESETS);
        int index = getPresetIndex(presets, presetId);
        JSONObject rawPreset = presets == null ? null : presets.optJSONObject(index);
        JSONObject viewports = rawPreset == null
                ? null : rawPreset.optJSONObject(PRESET_VIEWPORTS_FIELD);
        JSONObject rawViewport = viewports == null ? null : viewports.optJSONObject(shortcutKey);
        if (rawViewport == null) {
            return centeredViewport();
        }
        return new Viewport(
                (float) rawViewport.optDouble(VIEWPORT_ANCHOR_U_FIELD, 0.5d),
                (float) rawViewport.optDouble(VIEWPORT_ANCHOR_V_FIELD, 0.5d),
                (float) rawViewport.optDouble(VIEWPORT_ZOOM_FIELD, DEFAULT_VIEWPORT_ZOOM));
    }

    private static ShortcutPreset getPresetFromConfig(JSONObject config, String presetId) {
        if (config == null || presetId == null || presetId.trim().isEmpty()) {
            return null;
        }
        JSONArray presets = config.optJSONArray(KEY_SHORTCUT_PRESETS);
        int index = getPresetIndex(presets, presetId);
        JSONObject preset = presets == null ? null : presets.optJSONObject(index);
        return parsePreset(preset);
    }

    private boolean updateViewportInSnapshot(JSONObject updated, String presetId,
            String shortcutKey, Viewport viewport) {
        JSONArray presets = updated.optJSONArray(KEY_SHORTCUT_PRESETS);
        int index = getPresetIndex(presets, presetId);
        JSONObject preset = presets == null ? null : presets.optJSONObject(index);
        if (preset == null || !isPresetShortcutKey(shortcutKey)) {
            return false;
        }
        try {
            JSONObject viewports = ensurePresetViewports(preset);
            JSONObject old = viewports.optJSONObject(shortcutKey);
            Viewport safeViewport = viewport == null ? centeredViewport() : viewport;
            Viewport oldViewport = old == null ? centeredViewport() : new Viewport(
                    (float) old.optDouble(VIEWPORT_ANCHOR_U_FIELD, 0.5d),
                    (float) old.optDouble(VIEWPORT_ANCHOR_V_FIELD, 0.5d),
                    (float) old.optDouble(VIEWPORT_ZOOM_FIELD, DEFAULT_VIEWPORT_ZOOM));
            if (oldViewport.equals(safeViewport)) {
                return false;
            }
            viewports.put(shortcutKey, viewportJson(safeViewport));
            preset.put(PRESET_VIEWPORTS_FIELD, viewports);
            presets.put(index, preset);
            updated.put(KEY_SHORTCUT_PRESETS, presets);
            setConfigSnapshot(updated);
            save(updated);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    /** Add centered viewport objects to old presets without changing bindings. */
    private boolean migratePresetViewportsIfNeeded() {
        synchronized (configWriteLock) {
            JSONObject current = getConfigSnapshot();
            JSONArray currentPresets = current.optJSONArray(KEY_SHORTCUT_PRESETS);
            if (currentPresets == null || currentPresets.length() == 0) {
                return false;
            }
            JSONObject updated = copyConfig(current);
            JSONArray presets = updated.optJSONArray(KEY_SHORTCUT_PRESETS);
            boolean changed = false;
            try {
                for (int i = 0; i < presets.length(); i++) {
                    JSONObject preset = presets.optJSONObject(i);
                    if (preset == null) {
                        continue;
                    }
                    JSONObject viewports = preset.optJSONObject(PRESET_VIEWPORTS_FIELD);
                    if (viewports == null) {
                        preset.put(PRESET_VIEWPORTS_FIELD, emptyPresetViewports());
                        changed = true;
                        continue;
                    }
                    for (String shortcutKey : PRESET_SHORTCUT_KEYS) {
                        JSONObject viewport = viewports.optJSONObject(shortcutKey);
                        if (viewport == null) {
                            viewports.put(shortcutKey, viewportJson(centeredViewport()));
                            changed = true;
                        } else if (!viewport.has(VIEWPORT_ZOOM_FIELD)) {
                            viewport.put(VIEWPORT_ZOOM_FIELD, DEFAULT_VIEWPORT_ZOOM);
                            changed = true;
                        }
                    }
                }
                if (!changed) {
                    return false;
                }
                updated.put(KEY_SHORTCUT_PRESETS, presets);
                setConfigSnapshot(updated);
                save(updated);
                return true;
            } catch (JSONException e) {
                return false;
            }
        }
    }

    /** New installations use Dynamic; valid legacy FIT/CROP values remain unchanged. */
    private boolean normalizeAspectModeForV022() {
        synchronized (configWriteLock) {
            JSONObject current = getConfigSnapshot();
            String existing = current.optString(KEY_VIDEO_ASPECT_MODE, "");
            if (ASPECT_MODE_FIT.equals(existing) || ASPECT_MODE_CROP.equals(existing)
                    || ASPECT_MODE_DYNAMIC.equals(existing)) {
                return false;
            }
            JSONObject updated = copyConfig(current);
            try {
                updated.put(KEY_VIDEO_ASPECT_MODE, ASPECT_MODE_DYNAMIC);
                setConfigSnapshot(updated);
                save(updated);
                return true;
            } catch (JSONException e) {
                return false;
            }
        }
    }

    private static int getPresetIndex(JSONArray presets, String presetId) {
        if (presets == null || presetId == null || presetId.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < presets.length(); i++) {
            JSONObject preset = presets.optJSONObject(i);
            if (preset != null && presetId.equals(preset.optString(PRESET_ID_FIELD, ""))) {
                return i;
            }
        }
        return -1;
    }

    private static int nextAvailablePresetNumber(JSONObject config, JSONArray presets) {
        int number = config == null ? 1 : config.optInt(KEY_NEXT_PRESET_NUMBER, 1);
        if (number < 1) {
            number = 1;
        }
        while (containsPresetName(presets, defaultPresetName(number))) {
            number++;
        }
        return number;
    }

    private static boolean containsPresetName(JSONArray presets, String name) {
        if (presets == null) {
            return false;
        }
        for (int i = 0; i < presets.length(); i++) {
            JSONObject preset = presets.optJSONObject(i);
            if (preset != null && name.equals(preset.optString(PRESET_NAME_FIELD, ""))) {
                return true;
            }
        }
        return false;
    }

    private static String defaultPresetName(int number) {
        if (number <= 0) {
            return "预设一";
        }
        if (number < 10) {
            return "预设" + PRESET_DEFAULT_NAME_DIGITS[number];
        }
        if (number < 20) {
            return "预设十" + (number == 10 ? "" : PRESET_DEFAULT_NAME_DIGITS[number - 10]);
        }
        if (number < 100) {
            int tens = number / 10;
            int ones = number % 10;
            return "预设" + PRESET_DEFAULT_NAME_DIGITS[tens] + "十"
                    + (ones == 0 ? "" : PRESET_DEFAULT_NAME_DIGITS[ones]);
        }
        return "预设" + number;
    }

    static boolean isValidPresetVideoName(String videoName) {
        if (videoName == null || videoName.isEmpty()
                || ".".equals(videoName) || "..".equals(videoName)
                || videoName.indexOf('/') >= 0 || videoName.indexOf('\\') >= 0) {
            return false;
        }
        File nameOnly = new File(videoName);
        return !nameOnly.isAbsolute() && videoName.equals(nameOnly.getName());
    }

    public static boolean isShortcutVideoKey(String key) {
        return KEY_SHORTCUT_DOT_VIDEO.equals(key)
                || KEY_SHORTCUT_LEFT_VIDEO.equals(key)
                || KEY_SHORTCUT_RIGHT_VIDEO.equals(key)
                || KEY_SHORTCUT_OPEN_VIDEO.equals(key)
                || KEY_SHORTCUT_BLINK_VIDEO.equals(key);
    }

    private void save() {
        save(getConfigSnapshot());
    }

    private void save(JSONObject snapshot) {
        File dir = new File(DEFAULT_CONFIG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File configFile = new File(dir, CONFIG_FILE_NAME);
        try {
            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                fos.write(snapshot.toString(4).getBytes(StandardCharsets.UTF_8));
            }

            // Set world-readable so hook processes (inside target apps) can read
            // the config file via direct path when ContentProvider is unavailable.
            try {
                configFile.setReadable(true, false);
                configFile.setWritable(true, true); // Keep write restricted to owner
                // Also chmod parents so directory is traversable
                dir.setExecutable(true, false);
                dir.setReadable(true, false);
            } catch (Exception ignored) {
                // Best-effort
            }

            // Notify ContentObserver and broadcast changes
            if (context != null) {
                try {
                    context.getContentResolver().notifyChange(IpcContract.URI_CONFIG, null);
                } catch (Exception ignored) {
                }
                sendConfigBroadcast(context);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Migration logic
    public boolean migrateIfNeeded() {
        boolean migrated = false;
        File dir = new File(DEFAULT_CONFIG_DIR);

        // Map old files to new keys
        String[][] fileToKey = {
                { "disable.jpg", KEY_DISABLE_MODULE },
                { "no-silent.jpg", KEY_PLAY_VIDEO_SOUND },
                { "private_dir.jpg", KEY_FORCE_PRIVATE_DIR },
                { "no_toast.jpg", KEY_DISABLE_TOAST }
        };

        for (String[] map : fileToKey) {
            File oldFile = new File(dir, map[0]);
            if (oldFile.exists()) {
                setBoolean(map[1], true);
                oldFile.delete();
                migrated = true;
            }
        }

        return migrated;
    }

    public void resetToDefault() {
        synchronized (configWriteLock) {
            JSONObject updated = new JSONObject();
            setConfigSnapshot(updated);
            save(updated);
        }
    }

    public String exportConfig() {
        return getConfigSnapshot().toString();
    }

    public void importConfig(String json) throws JSONException {
        synchronized (configWriteLock) {
            JSONObject updated = new JSONObject(json);
            setConfigSnapshot(updated);
            save(updated);
        }
    }

    /**
     * Parse config from JSON string and update memory cache.
     * Does NOT save to file to avoid EACCES errors in target app.
     */
    public void updateConfigFromJSON(String json) {
        try {
            JSONObject updated = new JSONObject(json);
            setConfigSnapshot(updated);
            // Update timestamps to prevent reloadFromFile from overwriting
            long now = System.currentTimeMillis();
            lastLoadedTime = now;
            lastReloadTime.set(now);
            io.github.alanlaw.vfc.utils.LogUtil.log("【CS】已通过广播更新内存配置");
        } catch (JSONException e) {
            io.github.alanlaw.vfc.utils.LogUtil.log("【CS】解析广播配置失败: " + e);
        }
    }
}
