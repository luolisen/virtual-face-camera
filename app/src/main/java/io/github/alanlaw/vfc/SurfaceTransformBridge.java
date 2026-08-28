package io.github.alanlaw.vfc;

import android.view.Surface;

import io.github.alanlaw.vfc.utils.LogUtil;

/** Small API-26+ bridge for applying a producer buffer transform to a Surface. */
public final class SurfaceTransformBridge {
    private static volatile boolean loadAttempted;
    private static volatile boolean loaded;

    private SurfaceTransformBridge() {
    }

    public static boolean applyTransform(Surface surface, int transformFlags) {
        if (surface == null || !surface.isValid()
                || !Camera1PreviewTransform.isValid(transformFlags)) {
            return false;
        }
        if (!ensureLoaded()) {
            return false;
        }
        try {
            return nativeApplyTransform(surface, transformFlags);
        } catch (Throwable t) {
            LogUtil.log("[VFC][SurfaceTransform] native apply failed: " + t);
            return false;
        }
    }

    private static boolean ensureLoaded() {
        if (loadAttempted) {
            return loaded;
        }
        synchronized (SurfaceTransformBridge.class) {
            if (loadAttempted) {
                return loaded;
            }
            loadAttempted = true;
            try {
                System.loadLibrary("vfc_surface_bridge");
                loaded = true;
            } catch (Throwable t) {
                LogUtil.log("[VFC][SurfaceTransform] load bridge failed: " + t);
                loaded = false;
            }
            return loaded;
        }
    }

    private static native boolean nativeApplyTransform(Surface surface, int transformFlags);
}
