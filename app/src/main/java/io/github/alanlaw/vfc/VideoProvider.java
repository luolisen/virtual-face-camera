package io.github.alanlaw.vfc;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.github.alanlaw.vfc.utils.VideoManager;

public class VideoProvider extends ContentProvider {
    private ConfigManager configManager;
    private final Object runtimeLock = new Object();
    private final Map<String, CameraRuntimeState> runtimeStates = new HashMap<>();
    private final Map<String, ArrayDeque<ViewportCommand>> pendingViewportCommands = new HashMap<>();
    private long nextViewportCommandSequence;

    private boolean isCallerAllowed() {
        android.content.Context context = getContext();
        if (context == null) {
            return false;
        }

        int callingUid = Binder.getCallingUid();
        if (callingUid == android.os.Process.myUid()) {
            return true;
        }

        String[] packages = context.getPackageManager().getPackagesForUid(callingUid);
        if (packages == null || packages.length == 0) {
            Log.w("VideoProvider", "Rejecting call with empty package list for uid=" + callingUid);
            return false;
        }

        Set<String> targetPackages = configManager.getTargetPackages();
        if (targetPackages.isEmpty()) {
            return true;
        }

        Set<String> allowedPackages = new HashSet<>(targetPackages);
        allowedPackages.add(context.getPackageName());
        for (String pkg : packages) {
            if (allowedPackages.contains(pkg)) {
                return true;
            }
        }

        Log.w("VideoProvider", "Rejecting caller packages=" + java.util.Arrays.toString(packages));
        return false;
    }

    @Override
    public boolean onCreate() {
        // Init VideoManager config
        VideoManager.setContext(getContext());
        // Use constructor with false to avoid immediate reload via provider
        // (recursion/not ready)
        configManager = new ConfigManager(false);
        configManager.setSkipProviderReload(true);
        if (getContext() != null) {
            configManager.setContext(getContext());
        }
        // Manually reload from file now that config is set up
        configManager.reload();
        configManager.migrateV02Configuration();

        // Sync VideoManager's internal config manager too if needed,
        // but VideoManager.getConfig() creates its own instance.
        // Important: Set VideoManager context for path operations
        VideoManager.setContext(getContext());
        // Also set the provider-side ConfigManager instance into VideoManager to avoid
        // double loading
        VideoManager.setConfigManager(configManager);

        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (!isCallerAllowed()) {
            return null;
        }
        String lastPathSegment = uri.getLastPathSegment();
        if (IpcContract.PATH_CONFIG.equals(lastPathSegment)) {
            configManager.reload();
            // If configData is still empty after reload, try force reload once more
            org.json.JSONObject data = configManager.getConfigData();
            if (data == null || data.length() == 0) {
                configManager.forceReload();
                data = configManager.getConfigData();
            }
            android.database.MatrixCursor cursor = new android.database.MatrixCursor(
                    new String[] { "key", "value", "type" });
            if (data != null) {
                java.util.Iterator<String> keys = data.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object value = data.opt(key);
                    String type = "string";
                    if (value instanceof Boolean)
                        type = "boolean";
                    else if (value instanceof Integer)
                        type = "int";
                    else if (value instanceof Long)
                        type = "long";
                    else if (value instanceof org.json.JSONArray)
                        type = "json_array";

                    cursor.addRow(new Object[] { key, String.valueOf(value), type });
                }
            }
            Log.d("VideoProvider", "query /config: returning " + cursor.getCount() + " rows");
            return cursor;
        }
        io.github.alanlaw.vfc.utils.LogUtil
                .log("【CS】VideoProvider.query 返回 null, URI: " + uri.toString() + ", Seg: " + lastPathSegment);
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "video/mp4";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!isCallerAllowed()) {
            throw new FileNotFoundException("Caller not allowed");
        }
        configManager.reload();

        String lastSeg = uri.getLastPathSegment();

        // Handle audio file request
        if (IpcContract.PATH_AUDIO.equals(lastSeg)) {
            return openAudioFile();
        }

        // Random play is handled ONLY via call("random"), not on every openFile access.
        // This prevents the video from constantly switching during playback.

        // 1. Try to get the selected video name
        String videoName = configManager.getString(ConfigManager.KEY_SELECTED_VIDEO, null);

        File videoDir = new File(ConfigManager.DEFAULT_CONFIG_DIR);
        File videoFile = null;

        Log.d("VideoProvider", "openFile: selectedVideo=" + videoName + ", videoDir=" + videoDir.getAbsolutePath()
                + " exists=" + videoDir.exists());

        if (isVideoInLibrary(videoDir, videoName)) {
            videoFile = new File(videoDir, videoName);
            Log.d("VideoProvider", "openFile: trying " + videoFile.getAbsolutePath() + " exists=" + videoFile.exists()
                    + " canRead=" + videoFile.canRead());
        }

        // 2. If not found or invalid, fallback to cam.mp4
        if (videoFile == null || !videoFile.exists() || videoFile.isDirectory()) {
            videoFile = new File(videoDir, "Cam.mp4");
            Log.d("VideoProvider", "openFile: fallback to Cam.mp4 exists=" + videoFile.exists());
        }

        // 3. If still not found, try to find *any* mp4
        if (!videoFile.exists() || videoFile.isDirectory()) {
            File[] files = videoDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp4"));
            Log.d("VideoProvider", "openFile: listFiles returned " + (files == null ? "null" : files.length));
            if (files != null && files.length > 0) {
                videoFile = files[0];
            }
        }

        if (videoFile == null || !videoFile.exists()) {
            Log.e("VideoProvider", "No video file found in " + videoDir.getAbsolutePath());
            throw new FileNotFoundException("No video file found in " + videoDir.getAbsolutePath());
        }

        Log.d("VideoProvider", "openFile: opening " + videoFile.getAbsolutePath()
                + " size=" + videoFile.length()
                + " canRead=" + videoFile.canRead());
        try {
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(videoFile, ParcelFileDescriptor.MODE_READ_ONLY);
            Log.d("VideoProvider", "openFile: PFD opened successfully");
            return pfd;
        } catch (Exception e) {
            Log.e("VideoProvider", "openFile: PFD open FAILED: " + e.getMessage());
            throw new FileNotFoundException(
                    "Cannot open video file: " + videoFile.getAbsolutePath() + " - " + e.getMessage());
        }
    }

    /**
     * 打开音频文件并返回 PFD。
     * 查找逻辑：selected_audio → Mic.mp3 → 目录中任意音频文件。
     */
    private ParcelFileDescriptor openAudioFile() throws FileNotFoundException {
        File audioDir = new File(ConfigManager.DEFAULT_CONFIG_DIR);
        String selectedAudio = configManager.getString(ConfigManager.KEY_SELECTED_AUDIO, null);

        File audioFile = null;

        // 1. 使用配置中选中的音频
        if (selectedAudio != null && !selectedAudio.isEmpty()) {
            audioFile = new File(audioDir, selectedAudio);
            Log.d("VideoProvider", "openAudioFile: trying selected=" + audioFile.getAbsolutePath()
                    + " exists=" + audioFile.exists());
        }

        // 2. 降级到 Mic.mp3
        if (audioFile == null || !audioFile.exists()) {
            audioFile = new File(audioDir, "Mic.mp3");
            Log.d("VideoProvider", "openAudioFile: fallback to Mic.mp3 exists=" + audioFile.exists());
        }

        // 3. 扫描目录中任意音频文件
        if (!audioFile.exists()) {
            File[] files = audioDir.listFiles((dir, name) -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".mp3") || lower.endsWith(".wav")
                        || lower.endsWith(".aac") || lower.endsWith(".m4a")
                        || lower.endsWith(".ogg") || lower.endsWith(".flac");
            });
            if (files != null && files.length > 0) {
                audioFile = files[0];
                Log.d("VideoProvider", "openAudioFile: found audio file=" + audioFile.getName());
            }
        }

        if (audioFile == null || !audioFile.exists()) {
            Log.e("VideoProvider", "No audio file found in " + audioDir.getAbsolutePath());
            throw new FileNotFoundException("No audio file found in " + audioDir.getAbsolutePath());
        }

        Log.d("VideoProvider", "openAudioFile: opening " + audioFile.getAbsolutePath());
        try {
            return ParcelFileDescriptor.open(audioFile, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (Exception e) {
            throw new FileNotFoundException(
                    "Cannot open audio file: " + audioFile.getAbsolutePath() + " - " + e.getMessage());
        }
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!isCallerAllowed()) {
            Bundle denied = new Bundle();
            denied.putBoolean(IpcContract.EXTRA_CHANGED, false);
            return denied;
        }
        if (isRuntimeMethod(method)) {
            return handleRuntimeCall(method, extras);
        }
        configManager.reload();
        boolean changed = false;

        try {
            if (IpcContract.METHOD_NEXT.equals(method)) {
                changed = switchVideo(true);
            } else if (IpcContract.METHOD_PREV.equals(method)) {
                changed = switchVideo(false);
            } else if (IpcContract.METHOD_RANDOM.equals(method)) {
                changed = pickRandomVideo();
            } else if (IpcContract.METHOD_SELECT.equals(method)) {
                String videoName = extras == null
                        ? null
                        : extras.getString(IpcContract.EXTRA_VIDEO_NAME);
                changed = selectVideo(videoName);
            } else if (IpcContract.METHOD_SELECT_PRESET_SHORTCUT.equals(method)) {
                String presetId = extras == null
                        ? null
                        : extras.getString(IpcContract.EXTRA_PRESET_ID);
                String shortcutKey = extras == null
                        ? null
                        : extras.getString(IpcContract.EXTRA_SHORTCUT_KEY);
                changed = selectPresetShortcut(presetId, shortcutKey);
            } else if (IpcContract.METHOD_MOVE_VIEWPORT.equals(method)) {
                String direction = extras == null
                        ? null
                        : extras.getString(IpcContract.EXTRA_VIEWPORT_DIRECTION);
                changed = moveViewport(direction);
            } else if (IpcContract.METHOD_RESET_VIEWPORT.equals(method)) {
                changed = resetViewport();
            }

            if (changed) {
                // Notify both video and config URIs to ensure listeners are updated
                getContext().getContentResolver().notifyChange(IpcContract.URI_VIDEO, null);
                getContext().getContentResolver().notifyChange(IpcContract.URI_CONFIG, null);
            }
        } catch (Exception e) {
            Log.e("VideoProvider", "Error in call method: " + method, e);
        }

        Bundle result = new Bundle();
        result.putBoolean(IpcContract.EXTRA_CHANGED, changed);
        return result;
    }

    private boolean isRuntimeMethod(String method) {
        return IpcContract.METHOD_REPORT_CAMERA_RUNTIME.equals(method)
                || IpcContract.METHOD_ENQUEUE_VIEWPORT_COMMAND.equals(method)
                || IpcContract.METHOD_GET_PENDING_VIEWPORT_COMMAND.equals(method)
                || IpcContract.METHOD_ACK_VIEWPORT_COMMAND.equals(method)
                || IpcContract.METHOD_HAS_ACTIVE_CAMERA.equals(method);
    }

    private Bundle handleRuntimeCall(String method, Bundle extras) {
        if (IpcContract.METHOD_REPORT_CAMERA_RUNTIME.equals(method)) {
            return reportCameraRuntime(extras);
        }
        if (IpcContract.METHOD_ENQUEUE_VIEWPORT_COMMAND.equals(method)) {
            return enqueueViewportCommand(extras);
        }
        if (IpcContract.METHOD_GET_PENDING_VIEWPORT_COMMAND.equals(method)) {
            return getPendingViewportCommand(extras);
        }
        if (IpcContract.METHOD_HAS_ACTIVE_CAMERA.equals(method)) {
            return hasActiveCamera();
        }
        return ackViewportCommand(extras);
    }

    private Bundle hasActiveCamera() {
        Bundle result = new Bundle();
        boolean active = false;
        synchronized (runtimeLock) {
            for (CameraRuntimeState state : runtimeStates.values()) {
                if (state.active) {
                    active = true;
                    break;
                }
            }
        }
        result.putBoolean(IpcContract.EXTRA_CHANGED, active);
        return result;
    }

    private Bundle reportCameraRuntime(Bundle extras) {
        Bundle result = new Bundle();
        if (extras == null) {
            result.putBoolean(IpcContract.EXTRA_CHANGED, false);
            return result;
        }
        String hostPackage = extras.getString(IpcContract.EXTRA_HOST_PACKAGE, "").trim();
        if (!isCallingPackage(hostPackage)) {
            result.putBoolean(IpcContract.EXTRA_CHANGED, false);
            return result;
        }
        CameraRuntimeState next = new CameraRuntimeState(
                hostPackage,
                extras.getString(IpcContract.EXTRA_CAMERA_API, ""),
                extras.getInt(IpcContract.EXTRA_CAMERA_ID, -1),
                extras.getInt(IpcContract.EXTRA_CAMERA_FACING, -1),
                extras.getInt(IpcContract.EXTRA_SENSOR_ORIENTATION, -1),
                extras.getInt(IpcContract.EXTRA_DISPLAY_ORIENTATION, 0),
                extras.getInt(IpcContract.EXTRA_PREVIEW_TRANSFORM_FLAGS, 0),
                extras.getInt(IpcContract.EXTRA_PREVIEW_WIDTH, 0),
                extras.getInt(IpcContract.EXTRA_PREVIEW_HEIGHT, 0),
                extras.getBoolean(IpcContract.EXTRA_PREVIEW_ACTIVE, false),
                extras.getLong(IpcContract.EXTRA_GENERATION, 0L),
                extras.getLong(IpcContract.EXTRA_TIMESTAMP, System.currentTimeMillis()));
        synchronized (runtimeLock) {
            CameraRuntimeState previous = runtimeStates.get(hostPackage);
            if (previous == null || next.generation >= previous.generation) {
                runtimeStates.put(hostPackage, next);
                if (!next.active) {
                    pendingViewportCommands.remove(hostPackage);
                }
            }
        }
        result.putBoolean(IpcContract.EXTRA_CHANGED, true);
        return result;
    }

    private Bundle enqueueViewportCommand(Bundle extras) {
        Bundle result = new Bundle();
        String command = extras == null ? ""
                : extras.getString(IpcContract.EXTRA_VIEWPORT_COMMAND, "").trim();
        if (!isViewportCommand(command)) {
            result.putBoolean(IpcContract.EXTRA_CHANGED, false);
            return result;
        }

        CameraRuntimeState target = null;
        synchronized (runtimeLock) {
            for (CameraRuntimeState state : runtimeStates.values()) {
                if (!state.active || (target != null && state.generation <= target.generation)) {
                    continue;
                }
                target = state;
            }
            if (target != null) {
                long sequence = ++nextViewportCommandSequence;
                ArrayDeque<ViewportCommand> queue = pendingViewportCommands.get(target.hostPackage);
                if (queue == null) {
                    queue = new ArrayDeque<>();
                    pendingViewportCommands.put(target.hostPackage, queue);
                }
                queue.addLast(new ViewportCommand(sequence, target.hostPackage, command));
                result.putBoolean(IpcContract.EXTRA_CHANGED, true);
                result.putLong(IpcContract.EXTRA_COMMAND_SEQ, sequence);
                result.putString(IpcContract.EXTRA_TARGET_PACKAGE, target.hostPackage);
            } else {
                result.putBoolean(IpcContract.EXTRA_CHANGED, false);
            }
        }
        if (result.getBoolean(IpcContract.EXTRA_CHANGED, false)) {
            getContext().getContentResolver().notifyChange(IpcContract.URI_RUNTIME_COMMAND, null);
        }
        return result;
    }

    private Bundle getPendingViewportCommand(Bundle extras) {
        Bundle result = new Bundle();
        String hostPackage = extras == null ? ""
                : extras.getString(IpcContract.EXTRA_HOST_PACKAGE, "").trim();
        long lastSequence = extras == null ? 0L
                : extras.getLong(IpcContract.EXTRA_LAST_COMMAND_SEQ, 0L);
        if (!isCallingPackage(hostPackage)) {
            result.putBoolean(IpcContract.EXTRA_CHANGED, false);
            return result;
        }
        synchronized (runtimeLock) {
            ArrayDeque<ViewportCommand> queue = pendingViewportCommands.get(hostPackage);
            if (queue != null) {
                while (!queue.isEmpty() && queue.peekFirst().sequence <= lastSequence) {
                    queue.removeFirst();
                }
                if (queue.isEmpty()) {
                    pendingViewportCommands.remove(hostPackage);
                    queue = null;
                }
            }
            ViewportCommand command = queue == null ? null : queue.peekFirst();
            if (command == null) {
                result.putBoolean(IpcContract.EXTRA_CHANGED, false);
                return result;
            }
            result.putBoolean(IpcContract.EXTRA_CHANGED, true);
            result.putLong(IpcContract.EXTRA_COMMAND_SEQ, command.sequence);
            result.putString(IpcContract.EXTRA_TARGET_PACKAGE, command.targetPackage);
            result.putString(IpcContract.EXTRA_VIEWPORT_COMMAND, command.command);
            return result;
        }
    }

    private Bundle ackViewportCommand(Bundle extras) {
        Bundle result = new Bundle();
        String hostPackage = extras == null ? ""
                : extras.getString(IpcContract.EXTRA_HOST_PACKAGE, "").trim();
        long sequence = extras == null ? 0L
                : extras.getLong(IpcContract.EXTRA_COMMAND_SEQ, 0L);
        boolean removed = false;
        if (isCallingPackage(hostPackage) && sequence > 0L) {
            synchronized (runtimeLock) {
                ArrayDeque<ViewportCommand> queue = pendingViewportCommands.get(hostPackage);
                if (queue != null) {
                    ViewportCommand first = queue.peekFirst();
                    if (first != null && first.sequence == sequence) {
                        queue.removeFirst();
                        removed = true;
                    }
                    if (queue.isEmpty()) {
                        pendingViewportCommands.remove(hostPackage);
                    }
                }
            }
        }
        result.putBoolean(IpcContract.EXTRA_CHANGED, removed);
        return result;
    }

    private boolean isCallingPackage(String packageName) {
        if (packageName == null || packageName.isEmpty() || getContext() == null) {
            return false;
        }
        String[] packages = getContext().getPackageManager()
                .getPackagesForUid(Binder.getCallingUid());
        if (packages == null) {
            return false;
        }
        for (String candidate : packages) {
            if (packageName.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isViewportCommand(String command) {
        return IpcContract.VIEWPORT_COMMAND_UP.equals(command)
                || IpcContract.VIEWPORT_COMMAND_DOWN.equals(command)
                || IpcContract.VIEWPORT_COMMAND_LEFT.equals(command)
                || IpcContract.VIEWPORT_COMMAND_RIGHT.equals(command)
                || IpcContract.VIEWPORT_COMMAND_ZOOM_IN.equals(command)
                || IpcContract.VIEWPORT_COMMAND_ZOOM_OUT.equals(command)
                || IpcContract.VIEWPORT_COMMAND_RESET.equals(command);
    }

    private static final class ViewportCommand {
        final long sequence;
        final String targetPackage;
        final String command;

        ViewportCommand(long sequence, String targetPackage, String command) {
            this.sequence = sequence;
            this.targetPackage = targetPackage;
            this.command = command;
        }
    }

    private static final class CameraRuntimeState {
        final String hostPackage;
        final String cameraApi;
        final int cameraId;
        final int facing;
        final int sensorOrientation;
        final int displayOrientation;
        final int previewTransformFlags;
        final int previewWidth;
        final int previewHeight;
        final boolean active;
        final long generation;
        final long timestamp;

        CameraRuntimeState(String hostPackage, String cameraApi, int cameraId, int facing,
                int sensorOrientation, int displayOrientation, int previewTransformFlags,
                int previewWidth, int previewHeight, boolean active, long generation,
                long timestamp) {
            this.hostPackage = hostPackage;
            this.cameraApi = cameraApi;
            this.cameraId = cameraId;
            this.facing = facing;
            this.sensorOrientation = sensorOrientation;
            this.displayOrientation = displayOrientation;
            this.previewTransformFlags = previewTransformFlags;
            this.previewWidth = previewWidth;
            this.previewHeight = previewHeight;
            this.active = active;
            this.generation = generation;
            this.timestamp = timestamp;
        }
    }

    /**
     * Accept only a plain filename. This prevents absolute paths, separators,
     * dot segments, and other path traversal attempts before filesystem access.
     */
    static boolean isSafeVideoName(String videoName) {
        if (videoName == null || videoName.isEmpty()
                || ".".equals(videoName) || "..".equals(videoName)) {
            return false;
        }
        if (videoName.indexOf('/') >= 0 || videoName.indexOf('\\') >= 0) {
            return false;
        }
        File nameOnly = new File(videoName);
        return !nameOnly.isAbsolute() && videoName.equals(nameOnly.getName());
    }

    /**
     * Verify that the exact filename is a regular video returned by the same
     * VideoManager media-library scan used by the rest of the app.
     */
    static boolean isVideoInLibrary(File videoDir, String videoName) {
        if (videoDir == null || !isSafeVideoName(videoName)) {
            return false;
        }
        try {
            File canonicalDir = videoDir.getCanonicalFile();
            File candidate = new File(canonicalDir, videoName).getCanonicalFile();
            if (!canonicalDir.equals(candidate.getParentFile()) || !candidate.isFile()) {
                return false;
            }
            File[] files = VideoManager.listVideoFiles(canonicalDir);
            if (files == null) {
                return false;
            }
            for (File file : files) {
                if (videoName.equals(file.getName())
                        && candidate.equals(file.getCanonicalFile())) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.w("VideoProvider", "Video filename validation failed", e);
        }
        return false;
    }

    private boolean selectVideo(String videoName) {
        File videoDir = new File(ConfigManager.DEFAULT_CONFIG_DIR);
        if (!isVideoInLibrary(videoDir, videoName)) {
            Log.w("VideoProvider", "Rejecting invalid or unavailable video selection: " + videoName);
            return false;
        }
        return configManager.setSelectedVideoAndClearActive(videoName);
    }

    private boolean selectPresetShortcut(String presetId, String shortcutKey) {
        if (presetId == null || shortcutKey == null
                || !ConfigManager.isPresetShortcutKey(shortcutKey)) {
            return false;
        }
        ConfigManager.ShortcutPreset preset = configManager.getPreset(presetId);
        if (preset == null) {
            return false;
        }
        String videoName = preset.getVideoName(shortcutKey);
        File videoDir = new File(ConfigManager.DEFAULT_CONFIG_DIR);
        if (!isVideoInLibrary(videoDir, videoName)) {
            Log.w("VideoProvider", "Rejecting unavailable preset shortcut video: " + videoName);
            return false;
        }
        return configManager.selectPresetShortcut(presetId, shortcutKey, videoName);
    }

    private boolean moveViewport(String direction) {
        if (!ConfigManager.ASPECT_MODE_DYNAMIC.equals(
                configManager.getString(ConfigManager.KEY_VIDEO_ASPECT_MODE,
                        ConfigManager.ASPECT_MODE_DYNAMIC))) {
            return false;
        }
        return configManager.moveActiveBindingViewport(
                direction, configManager.getViewportMoveStepPercent());
    }

    private boolean resetViewport() {
        if (!ConfigManager.ASPECT_MODE_DYNAMIC.equals(
                configManager.getString(ConfigManager.KEY_VIDEO_ASPECT_MODE,
                        ConfigManager.ASPECT_MODE_DYNAMIC))) {
            return false;
        }
        return configManager.resetActiveBindingViewport();
    }

    private boolean switchVideo(boolean next) {
        if (configManager.getBoolean(ConfigManager.KEY_ENABLE_RANDOM_PLAY, false)) {
            return pickRandomVideo();
        }

        File dir = new File(ConfigManager.DEFAULT_CONFIG_DIR);
        File[] files = VideoManager.listVideoFiles(dir);

        if (files == null || files.length == 0)
            return false;

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

        int newIndex = (currentIndex == -1) ? 0
                : (next ? (currentIndex + 1) % files.length : (currentIndex - 1 + files.length) % files.length);

        String newVideoName = files[newIndex].getName();
        return configManager.setSelectedVideoAndClearActive(newVideoName);
    }

    private boolean pickRandomVideo() {
        // Directly pick a random video and store in config.
        // Do NOT call VideoManager.updateVideoPath() to avoid IPC recursion.
        File dir = new File(ConfigManager.DEFAULT_CONFIG_DIR);
        if (!dir.exists() || !dir.isDirectory())
            return false;

        File[] files = VideoManager.listVideoFiles(dir);

        if (files == null || files.length == 0)
            return false;

        int index = java.util.concurrent.ThreadLocalRandom.current().nextInt(files.length);
        return configManager.setSelectedVideoAndClearActive(files[index].getName());
    }
}
