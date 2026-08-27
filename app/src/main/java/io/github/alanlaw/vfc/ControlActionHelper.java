package io.github.alanlaw.vfc;

import android.content.Context;
import android.os.Bundle;

import java.io.File;

import io.github.alanlaw.vfc.utils.LogUtil;
import io.github.alanlaw.vfc.utils.VideoManager;

public final class ControlActionHelper {
    private ControlActionHelper() {
    }

    public static boolean switchVideo(Context context, boolean next) {
        String method = next ? IpcContract.METHOD_NEXT : IpcContract.METHOD_PREV;
        try {
            Bundle result = context.getContentResolver().call(IpcContract.CONTENT_URI, method, null, null);
            if (result != null) {
                return result.getBoolean(IpcContract.EXTRA_CHANGED, false);
            }
        } catch (Throwable t) {
            LogUtil.log("【CS】ControlActionHelper provider switch failed: " + t);
        }
        return fallbackSwitchVideo(context, next);
    }

    public static int rotateVideo(Context context) {
        ConfigManager configManager = new ConfigManager();
        configManager.setContext(context);
        configManager.forceReload();
        int rotation = (configManager.getInt(ConfigManager.KEY_VIDEO_ROTATION_OFFSET, 0) + 90) % 360;
        configManager.setInt(ConfigManager.KEY_VIDEO_ROTATION_OFFSET, rotation);
        return rotation;
    }

    /** Select one exact video through the provider IPC contract. */
    public static boolean selectVideo(Context context, String videoName) {
        if (context == null || videoName == null || videoName.isEmpty()) {
            return false;
        }
        try {
            Bundle extras = new Bundle();
            extras.putString(IpcContract.EXTRA_VIDEO_NAME, videoName);
            Bundle result = context.getContentResolver().call(
                    IpcContract.CONTENT_URI, IpcContract.METHOD_SELECT, null, extras);
            return result != null && result.getBoolean(IpcContract.EXTRA_CHANGED, false);
        } catch (Throwable t) {
            LogUtil.log("【CS】ControlActionHelper provider select failed: " + t);
            return false;
        }
    }

    public static void setOverlayEnabled(Context context, boolean enabled) {
        ConfigManager configManager = new ConfigManager();
        configManager.setContext(context);
        configManager.setBoolean(ConfigManager.KEY_OVERLAY_CONTROL_ENABLED, enabled);
    }

    private static boolean fallbackSwitchVideo(Context context, boolean next) {
        ConfigManager configManager = new ConfigManager();
        configManager.setContext(context);
        File[] files = VideoManager.listVideoFiles(new File(ConfigManager.DEFAULT_CONFIG_DIR));
        if (files == null || files.length == 0) {
            return false;
        }

        String selectedVideo = configManager.getString(ConfigManager.KEY_SELECTED_VIDEO, null);
        int currentIndex = -1;
        if (selectedVideo != null) {
            for (int i = 0; i < files.length; i++) {
                if (files[i].getName().equals(selectedVideo)) {
                    currentIndex = i;
                    break;
                }
            }
        }

        int newIndex = currentIndex == -1 ? 0
                : (next ? (currentIndex + 1) % files.length : (currentIndex - 1 + files.length) % files.length);
        configManager.setString(ConfigManager.KEY_SELECTED_VIDEO, files[newIndex].getName());
        return true;
    }
}
