package io.github.alanlaw.vfc;

import android.content.Context;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Surface;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.alanlaw.vfc.utils.LogUtil;

/**
 * Per-process registry for real Camera1 instances and their preview contract.
 * The map is identity-based because Camera instances can be reused by a host
 * app and camera ids alone are not sufficient to identify a session.
 */
public final class Camera1SessionRegistry {
    public static final int UNKNOWN_CAMERA_ID = -1;
    public static final int UNKNOWN_FACING = -1;
    public static final int UNKNOWN_SENSOR_ORIENTATION = -1;

    private static final Object LOCK = new Object();
    private static final Map<Camera, MutableState> SESSIONS = new IdentityHashMap<>();
    private static final ExecutorService REPORT_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "VFC-CameraRuntimeReport");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile Context context;
    // Seed generations with wall-clock time so a newly created target process
    // cannot be mistaken for an older session whose state is still cached by
    // the host provider.
    private static long nextGeneration = System.currentTimeMillis();

    private Camera1SessionRegistry() {
    }

    public static void setContext(Context newContext) {
        context = newContext == null ? null : newContext.getApplicationContext();
        if (context == null) {
            return;
        }
        synchronized (LOCK) {
            for (MutableState state : SESSIONS.values()) {
                reportAsync(snapshotLocked(state));
            }
        }
    }

    public static Snapshot registerOpened(Camera camera, int cameraId) {
        if (camera == null) {
            return null;
        }
        int facing = UNKNOWN_FACING;
        int sensorOrientation = UNKNOWN_SENSOR_ORIENTATION;
        if (cameraId >= 0) {
            try {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(cameraId, info);
                facing = info.facing;
                sensorOrientation = info.orientation;
            } catch (Throwable t) {
                LogUtil.log("[VFC][Camera1Session] Camera.getCameraInfo 失败: " + t);
            }
        }
        synchronized (LOCK) {
            MutableState state = new MutableState(camera, cameraId, facing, sensorOrientation,
                    ++nextGeneration);
            SESSIONS.put(camera, state);
            Snapshot snapshot = snapshotLocked(state);
            logState(snapshot);
            reportAsync(snapshot);
            return snapshot;
        }
    }

    public static int findFirstBackCameraId() {
        try {
            int count = Camera.getNumberOfCameras();
            for (int cameraId = 0; cameraId < count; cameraId++) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(cameraId, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    return cameraId;
                }
            }
        } catch (Throwable t) {
            LogUtil.log("[VFC][Camera1Session] 解析 Camera.open() 默认 id 失败: " + t);
        }
        return UNKNOWN_CAMERA_ID;
    }

    public static Snapshot ensure(Camera camera) {
        if (camera == null) {
            return null;
        }
        synchronized (LOCK) {
            MutableState state = SESSIONS.get(camera);
            if (state == null) {
                state = new MutableState(camera, UNKNOWN_CAMERA_ID, UNKNOWN_FACING,
                        UNKNOWN_SENSOR_ORIENTATION, ++nextGeneration);
                SESSIONS.put(camera, state);
                reportAsync(snapshotLocked(state));
            }
            return snapshotLocked(state);
        }
    }

    public static Snapshot updateDisplayOrientation(Camera camera, int degrees) {
        synchronized (LOCK) {
            MutableState state = ensureLocked(camera);
            if (state == null) {
                return null;
            }
            state.displayOrientationDegrees = degrees;
            int transform = Camera1PreviewTransform.forDisplayOrientation(state.facing, degrees);
            state.previewTransformFlags = transform == Camera1PreviewTransform.INVALID
                    ? Camera1PreviewTransform.IDENTITY : transform;
            Snapshot snapshot = snapshotLocked(state);
            logState(snapshot);
            reportAsync(snapshot);
            return snapshot;
        }
    }

    public static Snapshot setPreviewTarget(Camera camera, Surface surface) {
        synchronized (LOCK) {
            MutableState state = ensureLocked(camera);
            if (state == null) {
                return null;
            }
            state.previewTarget = surface;
            Snapshot snapshot = snapshotLocked(state);
            logState(snapshot);
            reportAsync(snapshot);
            return snapshot;
        }
    }

    public static Snapshot updatePreviewSize(Camera camera, int width, int height) {
        synchronized (LOCK) {
            MutableState state = ensureLocked(camera);
            if (state == null) {
                return null;
            }
            if (width > 0 && height > 0) {
                state.previewWidth = width;
                state.previewHeight = height;
            }
            Snapshot snapshot = snapshotLocked(state);
            logState(snapshot);
            reportAsync(snapshot);
            return snapshot;
        }
    }

    public static Snapshot setPreviewActive(Camera camera, boolean active) {
        synchronized (LOCK) {
            MutableState state = ensureLocked(camera);
            if (state == null) {
                return null;
            }
            state.previewActive = active;
            Snapshot snapshot = snapshotLocked(state);
            logState(snapshot);
            reportAsync(snapshot);
            return snapshot;
        }
    }

    /** Apply the current producer transform to the registered original preview target. */
    public static boolean applyCurrentPreviewTransform(Camera camera) {
        Snapshot snapshot;
        synchronized (LOCK) {
            MutableState state = ensureLocked(camera);
            if (state == null || state.previewTarget == null) {
                return false;
            }
            snapshot = snapshotLocked(state);
        }
        boolean applied = SurfaceTransformBridge.applyTransform(
                snapshot.previewTarget, snapshot.previewTransformFlags);
        LogUtil.log("[VFC][Camera1Transform] cameraId=" + snapshot.cameraId
                + " facing=" + facingName(snapshot.facing)
                + " displayOrientation=" + snapshot.displayOrientationDegrees
                + " transform=0x" + Integer.toHexString(snapshot.previewTransformFlags)
                + " preview=" + snapshot.previewWidth + "x" + snapshot.previewHeight
                + " surface=" + snapshot.previewTarget + " applyResult=" + applied);
        return applied;
    }

    /** Restore identity on the original target and remove the released Camera identity. */
    public static void release(Camera camera) {
        Snapshot snapshot = null;
        synchronized (LOCK) {
            MutableState state = SESSIONS.get(camera);
            if (state != null) {
                snapshot = snapshotLocked(state);
            }
        }
        if (snapshot != null && snapshot.previewTarget != null) {
            SurfaceTransformBridge.applyTransform(snapshot.previewTarget,
                    Camera1PreviewTransform.IDENTITY);
        }
        synchronized (LOCK) {
            MutableState state = SESSIONS.remove(camera);
            if (state != null) {
                state.previewActive = false;
                Snapshot inactive = snapshotLocked(state);
                logState(inactive);
                reportAsync(inactive);
            }
        }
    }

    public static Snapshot get(Camera camera) {
        synchronized (LOCK) {
            MutableState state = SESSIONS.get(camera);
            return state == null ? null : snapshotLocked(state);
        }
    }

    /** Return the newest active Camera1 session in this injected process. */
    public static Snapshot getLatestActive() {
        synchronized (LOCK) {
            MutableState latest = null;
            for (MutableState state : SESSIONS.values()) {
                if (!state.previewActive || (latest != null && state.generation <= latest.generation)) {
                    continue;
                }
                latest = state;
            }
            return latest == null ? null : snapshotLocked(latest);
        }
    }

    public static void clearForTests() {
        synchronized (LOCK) {
            SESSIONS.clear();
            nextGeneration = System.currentTimeMillis();
        }
    }

    private static MutableState ensureLocked(Camera camera) {
        if (camera == null) {
            return null;
        }
        MutableState state = SESSIONS.get(camera);
        if (state == null) {
            state = new MutableState(camera, UNKNOWN_CAMERA_ID, UNKNOWN_FACING,
                    UNKNOWN_SENSOR_ORIENTATION, ++nextGeneration);
            SESSIONS.put(camera, state);
        }
        return state;
    }

    private static Snapshot snapshotLocked(MutableState state) {
        return new Snapshot(state.camera, state.cameraId, state.facing, state.sensorOrientation,
                state.displayOrientationDegrees, state.previewTransformFlags,
                state.previewWidth, state.previewHeight, state.previewActive,
                state.generation, state.previewTarget);
    }

    private static void logState(Snapshot state) {
        if (state == null) {
            return;
        }
        LogUtil.log("[VFC][Camera1Session] cameraId=" + state.cameraId
                + " facing=" + facingName(state.facing)
                + " sensorOrientation=" + state.sensorOrientation
                + " displayOrientation=" + state.displayOrientationDegrees
                + " previewTransform=0x" + Integer.toHexString(state.previewTransformFlags)
                + " preview=" + state.previewWidth + "x" + state.previewHeight
                + " active=" + state.previewActive + " generation=" + state.generation);
    }

    private static String facingName(int facing) {
        if (facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return "FRONT";
        }
        if (facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
            return "BACK";
        }
        return "UNKNOWN";
    }

    private static void reportAsync(Snapshot state) {
        Context currentContext = context;
        if (currentContext == null || state == null) {
            return;
        }
        REPORT_EXECUTOR.execute(() -> {
            try {
                Bundle extras = new Bundle();
                extras.putString(IpcContract.EXTRA_HOST_PACKAGE, currentContext.getPackageName());
                extras.putString(IpcContract.EXTRA_CAMERA_API, "camera1");
                extras.putInt(IpcContract.EXTRA_CAMERA_ID, state.cameraId);
                extras.putInt(IpcContract.EXTRA_CAMERA_FACING, state.facing);
                extras.putInt(IpcContract.EXTRA_SENSOR_ORIENTATION, state.sensorOrientation);
                extras.putInt(IpcContract.EXTRA_DISPLAY_ORIENTATION, state.displayOrientationDegrees);
                extras.putInt(IpcContract.EXTRA_PREVIEW_TRANSFORM_FLAGS, state.previewTransformFlags);
                extras.putInt(IpcContract.EXTRA_PREVIEW_WIDTH, state.previewWidth);
                extras.putInt(IpcContract.EXTRA_PREVIEW_HEIGHT, state.previewHeight);
                extras.putBoolean(IpcContract.EXTRA_PREVIEW_ACTIVE, state.previewActive);
                extras.putLong(IpcContract.EXTRA_GENERATION, state.generation);
                extras.putLong(IpcContract.EXTRA_TIMESTAMP, System.currentTimeMillis());
                currentContext.getContentResolver().call(
                        IpcContract.CONTENT_URI,
                        IpcContract.METHOD_REPORT_CAMERA_RUNTIME,
                        null,
                        extras);
            } catch (Throwable t) {
                LogUtil.log("[VFC][Camera1Session] runtime state report failed: " + t);
            }
        });
    }

    private static final class MutableState {
        final Camera camera;
        final int cameraId;
        final int facing;
        final int sensorOrientation;
        final long generation;
        int displayOrientationDegrees;
        int previewTransformFlags = Camera1PreviewTransform.IDENTITY;
        int previewWidth;
        int previewHeight;
        boolean previewActive;
        Surface previewTarget;

        MutableState(Camera camera, int cameraId, int facing, int sensorOrientation,
                long generation) {
            this.camera = camera;
            this.cameraId = cameraId;
            this.facing = facing;
            this.sensorOrientation = sensorOrientation;
            this.generation = generation;
        }
    }

    public static final class Snapshot {
        private final Camera camera;
        private final int cameraId;
        private final int facing;
        private final int sensorOrientation;
        private final int displayOrientationDegrees;
        private final int previewTransformFlags;
        private final int previewWidth;
        private final int previewHeight;
        private final boolean previewActive;
        private final long generation;
        private final Surface previewTarget;

        private Snapshot(Camera camera, int cameraId, int facing, int sensorOrientation,
                int displayOrientationDegrees, int previewTransformFlags,
                int previewWidth, int previewHeight, boolean previewActive,
                long generation, Surface previewTarget) {
            this.camera = camera;
            this.cameraId = cameraId;
            this.facing = facing;
            this.sensorOrientation = sensorOrientation;
            this.displayOrientationDegrees = displayOrientationDegrees;
            this.previewTransformFlags = previewTransformFlags;
            this.previewWidth = previewWidth;
            this.previewHeight = previewHeight;
            this.previewActive = previewActive;
            this.generation = generation;
            this.previewTarget = previewTarget;
        }

        public Camera getCamera() {
            return camera;
        }

        public int getCameraId() {
            return cameraId;
        }

        public int getFacing() {
            return facing;
        }

        public int getSensorOrientation() {
            return sensorOrientation;
        }

        public int getDisplayOrientationDegrees() {
            return displayOrientationDegrees;
        }

        public int getPreviewTransformFlags() {
            return previewTransformFlags;
        }

        public int getPreviewWidth() {
            return previewWidth;
        }

        public int getPreviewHeight() {
            return previewHeight;
        }

        public boolean isPreviewActive() {
            return previewActive;
        }

        public long getGeneration() {
            return generation;
        }

        public Surface getPreviewTarget() {
            return previewTarget;
        }
    }
}
