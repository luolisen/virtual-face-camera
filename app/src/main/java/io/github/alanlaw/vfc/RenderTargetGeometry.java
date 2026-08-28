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
     * Keep the raw EGL target untouched while optionally swapping only the
     * logical target used by preview aspect calculations.
     */
    public static Calculation calculate(int rawTargetWidth, int rawTargetHeight,
            HostWindowGeometry.Snapshot hostGeometry, RenderTargetRole role) {
        RenderTargetRole effectiveRole = role == null ? RenderTargetRole.PREVIEW : role;
        int hostWidth = hostGeometry == null ? 0 : hostGeometry.getWidth();
        int hostHeight = hostGeometry == null ? 0 : hostGeometry.getHeight();
        int displayRotation = hostGeometry == null
                ? HostWindowGeometry.UNKNOWN_ROTATION
                : hostGeometry.getDisplayRotation();

        boolean rawLandscape = rawTargetWidth > rawTargetHeight;
        boolean rawPortrait = rawTargetWidth < rawTargetHeight;
        boolean hostLandscape = hostWidth > hostHeight;
        boolean hostPortrait = hostWidth < hostHeight;
        boolean orientationCompensated = effectiveRole == RenderTargetRole.PREVIEW
                && rawTargetWidth > 0
                && rawTargetHeight > 0
                && hostGeometry != null
                && hostGeometry.isAvailable()
                && hostGeometry.hasKnownOrientation()
                && ((rawLandscape && hostPortrait) || (rawPortrait && hostLandscape));

        int logicalTargetWidth = rawTargetWidth;
        int logicalTargetHeight = rawTargetHeight;
        if (orientationCompensated) {
            logicalTargetWidth = rawTargetHeight;
            logicalTargetHeight = rawTargetWidth;
        }

        return new Calculation(effectiveRole,
                rawTargetWidth, rawTargetHeight,
                hostWidth, hostHeight, displayRotation,
                logicalTargetWidth, logicalTargetHeight,
                orientationCompensated);
    }
}
