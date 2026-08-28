package io.github.alanlaw.vfc;

/** Pure geometry decision for mapping a raw EGL target to aspect math space. */
public final class RenderTargetGeometry {
    public static final class Calculation {
        public final RenderTargetRole role;
        public final int rawTargetWidth;
        public final int rawTargetHeight;
        public final int hostWidth;
        public final int hostHeight;
        public final int displayRotation;
        public final int logicalTargetWidth;
        public final int logicalTargetHeight;
        public final boolean orientationCompensated;

        private Calculation(RenderTargetRole role,
                int rawTargetWidth, int rawTargetHeight,
                int hostWidth, int hostHeight, int displayRotation,
                int logicalTargetWidth, int logicalTargetHeight,
                boolean orientationCompensated) {
            this.role = role;
            this.rawTargetWidth = rawTargetWidth;
            this.rawTargetHeight = rawTargetHeight;
            this.hostWidth = hostWidth;
            this.hostHeight = hostHeight;
            this.displayRotation = displayRotation;
            this.logicalTargetWidth = logicalTargetWidth;
            this.logicalTargetHeight = logicalTargetHeight;
            this.orientationCompensated = orientationCompensated;
        }
    }

    private RenderTargetGeometry() {
    }

    /**
     * Keep the raw EGL target as the only rendering target. Host window
     * geometry is retained in the result strictly for diagnostics; it must
     * never rewrite a Camera buffer's aspect.
     */
    public static Calculation calculate(int rawTargetWidth, int rawTargetHeight,
            HostWindowGeometry.Snapshot hostGeometry, RenderTargetRole role) {
        RenderTargetRole effectiveRole = role == null ? RenderTargetRole.PREVIEW : role;
        int hostWidth = hostGeometry == null ? 0 : hostGeometry.getWidth();
        int hostHeight = hostGeometry == null ? 0 : hostGeometry.getHeight();
        int displayRotation = hostGeometry == null
                ? HostWindowGeometry.UNKNOWN_ROTATION
                : hostGeometry.getDisplayRotation();

        // Do not infer Camera Surface orientation from Activity/window
        // orientation. The host may apply its own later transform (for
        // example, a portrait UI backed by a 1600x728 buffer).
        boolean orientationCompensated = false;
        int logicalTargetWidth = rawTargetWidth;
        int logicalTargetHeight = rawTargetHeight;

        return new Calculation(effectiveRole,
                rawTargetWidth, rawTargetHeight,
                hostWidth, hostHeight, displayRotation,
                logicalTargetWidth, logicalTargetHeight,
                orientationCompensated);
    }
}
