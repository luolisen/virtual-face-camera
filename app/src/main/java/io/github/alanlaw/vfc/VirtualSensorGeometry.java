package io.github.alanlaw.vfc;

import java.util.Locale;

/**
 * Pure geometry model for Dynamic virtual-sensor rendering.
 *
 * <p>The model deliberately knows only decoded source dimensions and the raw
 * EGL target dimensions. Host Activity/window dimensions are not inputs and
 * therefore cannot change Camera buffer geometry.</p>
 */
public final class VirtualSensorGeometry {
    private static final float CENTER = 0.5f;

    private VirtualSensorGeometry() {
    }

    /** A normalized top-left image-space rectangle. */
    public static final class NormalizedRect {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        public NormalizedRect(float left, float top, float right, float bottom) {
            this.left = clampUnit(left);
            this.top = clampUnit(top);
            this.right = clampUnit(right);
            this.bottom = clampUnit(bottom);
        }

        public float width() {
            return Math.max(0.0f, right - left);
        }

        public float height() {
            return Math.max(0.0f, bottom - top);
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "[%.5f,%.5f,%.5f,%.5f]",
                    left, top, right, bottom);
        }
    }

    /** Immutable result shared by GL renderers and unit tests. */
    public static final class Calculation {
        public final RenderTargetRole role;
        public final int sourceWidth;
        public final int sourceHeight;
        public final int rotationDegrees;
        public final int logicalSensorWidth;
        public final int logicalSensorHeight;
        public final int rawTargetWidth;
        public final int rawTargetHeight;
        public final float sourceAspect;
        public final float targetAspect;
        public final float sourceAnchorU;
        public final float sourceAnchorV;
        public final float logicalAnchorU;
        public final float logicalAnchorV;
        public final NormalizedRect logicalCropRect;
        public final NormalizedRect sourceCropRect;
        public final boolean valid;

        private Calculation(RenderTargetRole role,
                int sourceWidth, int sourceHeight, int rotationDegrees,
                int logicalSensorWidth, int logicalSensorHeight,
                int rawTargetWidth, int rawTargetHeight,
                float sourceAspect, float targetAspect,
                float sourceAnchorU, float sourceAnchorV,
                float logicalAnchorU, float logicalAnchorV,
                NormalizedRect logicalCropRect, NormalizedRect sourceCropRect,
                boolean valid) {
            this.role = role;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.rotationDegrees = rotationDegrees;
            this.logicalSensorWidth = logicalSensorWidth;
            this.logicalSensorHeight = logicalSensorHeight;
            this.rawTargetWidth = rawTargetWidth;
            this.rawTargetHeight = rawTargetHeight;
            this.sourceAspect = sourceAspect;
            this.targetAspect = targetAspect;
            this.sourceAnchorU = sourceAnchorU;
            this.sourceAnchorV = sourceAnchorV;
            this.logicalAnchorU = logicalAnchorU;
            this.logicalAnchorV = logicalAnchorV;
            this.logicalCropRect = logicalCropRect;
            this.sourceCropRect = sourceCropRect;
            this.valid = valid;
        }
    }

    /** Calculate a target-aspect crop in logical sensor space. */
    public static Calculation calculate(int sourceWidth, int sourceHeight,
            int rawTargetWidth, int rawTargetHeight, int rotationDegrees,
            float sourceAnchorU, float sourceAnchorV) {
        return calculate(sourceWidth, sourceHeight, rawTargetWidth, rawTargetHeight,
                rotationDegrees, sourceAnchorU, sourceAnchorV, RenderTargetRole.PREVIEW);
    }

    public static Calculation calculate(int sourceWidth, int sourceHeight,
            int rawTargetWidth, int rawTargetHeight, int rotationDegrees,
            float sourceAnchorU, float sourceAnchorV, RenderTargetRole role) {
        RenderTargetRole effectiveRole = role == null ? RenderTargetRole.PREVIEW : role;
        int normalizedRotation = VirtualSensorTransform.normalizeRotation(rotationDegrees);
        float safeSourceU = clampUnit(sourceAnchorU);
        float safeSourceV = clampUnit(sourceAnchorV);
        float[] logicalAnchor = VirtualSensorTransform.sourceToLogical(
                safeSourceU, safeSourceV, normalizedRotation);
        boolean valid = sourceWidth > 0 && sourceHeight > 0
                && rawTargetWidth > 0 && rawTargetHeight > 0;

        int logicalWidth = sourceWidth;
        int logicalHeight = sourceHeight;
        if (normalizedRotation == 90 || normalizedRotation == 270) {
            logicalWidth = sourceHeight;
            logicalHeight = sourceWidth;
        }

        if (!valid) {
            NormalizedRect full = fullRect();
            return new Calculation(effectiveRole,
                    Math.max(0, sourceWidth), Math.max(0, sourceHeight), normalizedRotation,
                    Math.max(0, logicalWidth), Math.max(0, logicalHeight),
                    Math.max(0, rawTargetWidth), Math.max(0, rawTargetHeight),
                    1.0f, 1.0f, safeSourceU, safeSourceV,
                    logicalAnchor[0], logicalAnchor[1], full, full, false);
        }

        float sourceAspect = logicalWidth / (float) logicalHeight;
        float targetAspect = rawTargetWidth / (float) rawTargetHeight;
        float cropWidth = 1.0f;
        float cropHeight = 1.0f;
        if (sourceAspect > targetAspect) {
            cropWidth = targetAspect / sourceAspect;
        } else if (sourceAspect < targetAspect) {
            cropHeight = sourceAspect / targetAspect;
        }
        cropWidth = clampUnit(cropWidth);
        cropHeight = clampUnit(cropHeight);

        float logicalCenterU = clampCenter(logicalAnchor[0], cropWidth);
        float logicalCenterV = clampCenter(logicalAnchor[1], cropHeight);
        NormalizedRect logicalCrop = rectAround(logicalCenterU, logicalCenterV,
                cropWidth, cropHeight);
        NormalizedRect sourceCrop = inverseRotatedBounds(logicalCrop, normalizedRotation);

        return new Calculation(effectiveRole,
                sourceWidth, sourceHeight, normalizedRotation,
                logicalWidth, logicalHeight, rawTargetWidth, rawTargetHeight,
                sourceAspect, targetAspect, safeSourceU, safeSourceV,
                logicalCenterU, logicalCenterV, logicalCrop, sourceCrop, true);
    }

    /**
     * Build a source-space crop matrix. The shader applies it as
     * {@code uSTMatrix * uCropMatrix * aTextureCoord}, so the crop is applied
     * before SurfaceTexture's existing buffer transform and the latter remains
     * the sole owner of producer-side orientation/crop metadata.
     */
    public static float[] buildTextureCropMatrix(NormalizedRect sourceCropRect) {
        NormalizedRect rect = sourceCropRect == null ? fullRect() : sourceCropRect;
        float width = Math.max(0.0f, rect.width());
        float height = Math.max(0.0f, rect.height());
        // OpenGL texture V grows upward while model rectangles use top-down V.
        float textureBottom = clampUnit(1.0f - rect.bottom);
        float[] matrix = new float[16];
        matrix[0] = width;
        matrix[5] = height;
        matrix[10] = 1.0f;
        matrix[12] = rect.left;
        matrix[13] = textureBottom;
        matrix[15] = 1.0f;
        return matrix;
    }

    /** Clamp a logical anchor to the crop rectangle's movable area. */
    public static float[] clampLogicalAnchor(float logicalU, float logicalV,
            float cropWidth, float cropHeight) {
        float safeWidth = clampUnit(cropWidth);
        float safeHeight = clampUnit(cropHeight);
        return new float[] {
                clampCenter(clampUnit(logicalU), safeWidth),
                clampCenter(clampUnit(logicalV), safeHeight)
        };
    }

    private static NormalizedRect inverseRotatedBounds(NormalizedRect logicalRect, int rotation) {
        float[][] corners = {
                VirtualSensorTransform.logicalToSource(logicalRect.left, logicalRect.top, rotation),
                VirtualSensorTransform.logicalToSource(logicalRect.right, logicalRect.top, rotation),
                VirtualSensorTransform.logicalToSource(logicalRect.left, logicalRect.bottom, rotation),
                VirtualSensorTransform.logicalToSource(logicalRect.right, logicalRect.bottom, rotation)
        };
        float left = 1.0f;
        float top = 1.0f;
        float right = 0.0f;
        float bottom = 0.0f;
        for (float[] corner : corners) {
            left = Math.min(left, corner[0]);
            top = Math.min(top, corner[1]);
            right = Math.max(right, corner[0]);
            bottom = Math.max(bottom, corner[1]);
        }
        return new NormalizedRect(left, top, right, bottom);
    }

    private static NormalizedRect rectAround(float centerU, float centerV,
            float width, float height) {
        return new NormalizedRect(
                centerU - width / 2.0f,
                centerV - height / 2.0f,
                centerU + width / 2.0f,
                centerV + height / 2.0f);
    }

    private static float clampCenter(float value, float extent) {
        if (extent >= 1.0f) {
            return CENTER;
        }
        float half = Math.max(0.0f, extent / 2.0f);
        return clamp(value, half, 1.0f - half);
    }

    private static NormalizedRect fullRect() {
        return new NormalizedRect(0.0f, 0.0f, 1.0f, 1.0f);
    }

    private static float clampUnit(float value) {
        return clamp(value, 0.0f, 1.0f);
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return CENTER;
        }
        return Math.max(min, Math.min(max, value));
    }
}
