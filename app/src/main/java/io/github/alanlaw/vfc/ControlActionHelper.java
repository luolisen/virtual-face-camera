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

    /** Select the binding from one exact preset/shortcut pair through IPC. */
    public static boolean selectPresetShortcut(Context context, String presetId,
            String shortcutKey) {
        if (context == null || presetId == null || presetId.isEmpty()
                || shortcutKey == null || shortcutKey.isEmpty()) {
            return false;
        }
        try {
            Bundle extras = new Bundle();
            extras.putString(IpcContract.EXTRA_PRESET_ID, presetId);
            extras.putString(IpcContract.EXTRA_SHORTCUT_KEY, shortcutKey);
            Bundle result = context.getContentResolver().call(
                    IpcContract.CONTENT_URI,
                    IpcContract.METHOD_SELECT_PRESET_SHORTCUT,
                    null,
                    extras);
            return result != null && result.getBoolean(IpcContract.EXTRA_CHANGED, false);
        } catch (Throwable t) {
            LogUtil.log("【CS】ControlActionHelper preset shortcut select failed: " + t);
            return false;
        }
    }

    /** Move the currently active preset binding's dynamic viewport. */
    public static boolean moveViewport(Context context, String direction) {
        if (context == null || direction == null || direction.isEmpty()) {
            return false;
        }
        return enqueueViewportCommand(context, direction);
    }

    /** Reset the currently active preset binding's dynamic viewport to center. */
    public static boolean resetViewport(Context context) {
        return enqueueViewportCommand(context, IpcContract.VIEWPORT_COMMAND_RESET);
    }

    public static boolean zoomInViewport(Context context) {
        return enqueueViewportCommand(context, IpcContract.VIEWPORT_COMMAND_ZOOM_IN);
    }

    public static boolean zoomOutViewport(Context context) {
        return enqueueViewportCommand(context, IpcContract.VIEWPORT_COMMAND_ZOOM_OUT);
    }

    /** Send a runtime command; only the process owning the active camera interprets it. */
    public static boolean enqueueViewportCommand(Context context, String command) {
        if (context == null || command == null || command.isEmpty()) {
            return false;
        }
        try {
            Bundle extras = new Bundle();
            extras.putString(IpcContract.EXTRA_VIEWPORT_COMMAND, normalizeViewportCommand(command));
            Bundle result = context.getContentResolver().call(
                    IpcContract.CONTENT_URI,
                    IpcContract.METHOD_ENQUEUE_VIEWPORT_COMMAND,
                    null,
                    extras);
            return result != null && result.getBoolean(IpcContract.EXTRA_CHANGED, false);
        } catch (Throwable t) {
            LogUtil.log("【CS】ControlActionHelper viewport command failed: " + t);
            return false;
        }
    }

    /** Return whether the provider currently knows an active target Camera1 session. */
    public static boolean hasActiveCamera(Context context) {
        if (context == null) {
            return false;
        }
        try {
            Bundle result = context.getContentResolver().call(
                    IpcContract.CONTENT_URI,
                    IpcContract.METHOD_HAS_ACTIVE_CAMERA,
                    null,
                    null);
            return result != null && result.getBoolean(IpcContract.EXTRA_CHANGED, false);
        } catch (Throwable t) {
            LogUtil.log("【CS】ControlActionHelper active camera query failed: " + t);
            return false;
        }
    }

    private static String normalizeViewportCommand(String command) {
        if (ConfigManager.VIEWPORT_DIRECTION_UP.equals(command)) {
            return IpcContract.VIEWPORT_COMMAND_UP;
        }
        if (ConfigManager.VIEWPORT_DIRECTION_DOWN.equals(command)) {
            return IpcContract.VIEWPORT_COMMAND_DOWN;
        }
        if (ConfigManager.VIEWPORT_DIRECTION_LEFT.equals(command)) {
            return IpcContract.VIEWPORT_COMMAND_LEFT;
        }
        if (ConfigManager.VIEWPORT_DIRECTION_RIGHT.equals(command)) {
            return IpcContract.VIEWPORT_COMMAND_RIGHT;
        }
        return command;
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
        return configManager.setSelectedVideoAndClearActive(files[newIndex].getName());
    }
}
