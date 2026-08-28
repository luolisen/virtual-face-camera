package io.github.alanlaw.vfc;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import java.util.Objects;

/**
 * Stores the latest resumed host Activity window geometry for preview aspect
 * calculations. The values are observations from the host window; no device
 * resolution or package-specific assumptions are used.
 */
public final class HostWindowGeometry {
    public static final int UNKNOWN_ROTATION = -1;

    /** Immutable snapshot that can safely be shared with a renderer thread. */
    public static final class Snapshot {
        private final int width;
        private final int height;
        private final int displayRotation;

        public Snapshot(int width, int height, int displayRotation) {
            this.width = width;
            this.height = height;
            this.displayRotation = displayRotation;
        }

        public static Snapshot unavailable() {
            return new Snapshot(0, 0, UNKNOWN_ROTATION);
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int getDisplayRotation() {
            return displayRotation;
        }

        public boolean isAvailable() {
            return width > 0 && height > 0;
        }

        public boolean hasKnownOrientation() {
            return isAvailable() && width != height;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Snapshot)) {
                return false;
            }
            Snapshot that = (Snapshot) other;
            return width == that.width
                    && height == that.height
                    && displayRotation == that.displayRotation;
        }

        @Override
        public int hashCode() {
            return Objects.hash(width, height, displayRotation);
        }
    }

    private volatile Snapshot snapshot = Snapshot.unavailable();

    public Snapshot getSnapshot() {
        return snapshot;
    }

    /** Read and publish the current window geometry for a resumed Activity. */
    public Snapshot updateFromActivity(Activity activity) {
        Snapshot next = snapshotForActivity(activity);
        snapshot = next;
        return next;
    }

    /**
     * Read window bounds using WindowMetrics on Android 11+, with a display
     * metrics fallback for older releases or transient framework failures.
     */
    public static Snapshot snapshotForActivity(Activity activity) {
        if (activity == null) {
            return Snapshot.unavailable();
        }

        int width = 0;
        int height = 0;
        int rotation = UNKNOWN_ROTATION;
        try {
            WindowManager windowManager = activity.getWindowManager();
            if (windowManager == null) {
                return Snapshot.unavailable();
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    Rect bounds = windowManager.getCurrentWindowMetrics().getBounds();
                    if (bounds != null) {
                        width = bounds.width();
                        height = bounds.height();
                    }
                } catch (Throwable ignored) {
                    // Fall through to the display metrics fallback below.
                }
            }

            Display display = windowManager.getDefaultDisplay();
            if (display != null) {
                try {
                    rotation = display.getRotation();
                } catch (Throwable ignored) {
                    rotation = UNKNOWN_ROTATION;
                }
            }

            if (width <= 0 || height <= 0) {
                DisplayMetrics metrics = new DisplayMetrics();
                if (display != null) {
                    display.getMetrics(metrics);
                    width = metrics.widthPixels;
                    height = metrics.heightPixels;
                }
            }
        } catch (Throwable ignored) {
            return Snapshot.unavailable();
        }

        return new Snapshot(width, height, rotation);
    }
}
