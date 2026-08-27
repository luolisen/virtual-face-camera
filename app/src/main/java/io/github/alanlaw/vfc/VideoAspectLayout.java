package io.github.alanlaw.vfc;

/**
 * Calculates the vertex scale needed to render a video without geometric stretching.
 * The returned scale is applied to a full-screen quad in clip space.
 */
public final class VideoAspectLayout {
    private VideoAspectLayout() {
    }

    public static final class Layout {
        public final int effectiveSourceWidth;
        public final int effectiveSourceHeight;
        public final float sourceAspect;
        public final float targetAspect;
        public final float scaleX;
        public final float scaleY;

        private Layout(int effectiveSourceWidth, int effectiveSourceHeight,
                float sourceAspect, float targetAspect, float scaleX, float scaleY) {
            this.effectiveSourceWidth = effectiveSourceWidth;
            this.effectiveSourceHeight = effectiveSourceHeight;
            this.sourceAspect = sourceAspect;
            this.targetAspect = targetAspect;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
        }
    }

    public static Layout calculate(int sourceWidth, int sourceHeight,
            int targetWidth, int targetHeight, int rotationDegrees, String aspectMode) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return new Layout(Math.max(0, sourceWidth), Math.max(0, sourceHeight),
                    1.0f, 1.0f, 1.0f, 1.0f);
        }

        int normalizedRotation = ((rotationDegrees % 360) + 360) % 360;
        boolean swap = normalizedRotation == 90 || normalizedRotation == 270;
        int effectiveWidth = swap ? sourceHeight : sourceWidth;
        int effectiveHeight = swap ? sourceWidth : sourceHeight;
        float sourceAspect = effectiveWidth / (float) effectiveHeight;
        float targetAspect = targetWidth / (float) targetHeight;

        boolean crop = ConfigManager.ASPECT_MODE_CROP.equals(aspectMode);
        float scaleX;
        float scaleY;
        if (crop) {
            if (sourceAspect > targetAspect) {
                scaleX = sourceAspect / targetAspect;
                scaleY = 1.0f;
            } else {
                scaleX = 1.0f;
                scaleY = targetAspect / sourceAspect;
            }
        } else {
            if (sourceAspect > targetAspect) {
                scaleX = 1.0f;
                scaleY = targetAspect / sourceAspect;
            } else {
                scaleX = sourceAspect / targetAspect;
                scaleY = 1.0f;
            }
        }

        return new Layout(effectiveWidth, effectiveHeight, sourceAspect, targetAspect, scaleX, scaleY);
    }
}
