package io.github.alanlaw.vfc;

import java.util.Locale;

/**
 * Pure geometry model for the Dynamic v2 requested-footprint renderer.
 *
 * <p>Persistent anchors are stored in decoded-source space. The source is
 * first rotated into the VFC virtual canvas, then the requested camera
 * footprint is fitted into that canvas, and finally the viewport is zoomed
 * and clamped around the anchor. No host-window dimensions are involved.</p>
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

    /** Immutable result cached by each GL renderer until geometry becomes dirty. */
    public static final class Calculation {
        public final RenderTargetRole role;
        public final int sourceWidth;
        public final int sourceHeight;
        public final int rotationDegrees;
        public final int logicalSensorWidth;
        public final int logicalSensorHeight;
        public final int requestedWidth;
        public final int requestedHeight;
        public final float sourceAspect;
        public final float targetAspect;
        public final float sourceAnchorU;
        public final float sourceAnchorV;
        public final float logicalAnchorU;
        public final float logicalAnchorV;
        public final float zoom;
        public final float fitScale;
        public final float baseViewportWidth;
        public final float baseViewportHeight;
        public final float viewportWidth;
        public final float viewportHeight;
        public final NormalizedRect logicalCropRect;
        public final NormalizedRect sourceCropRect;
        public final boolean valid;

        private Calculation(RenderTargetRole role,
                int sourceWidth, int sourceHeight, int rotationDegrees,
                int logicalSensorWidth, int logicalSensorHeight,
                int requestedWidth, int requestedHeight,
                float sourceAspect, float targetAspect,
                float sourceAnchorU, float sourceAnchorV,
                float logicalAnchorU, float logicalAnchorV,
                float zoom, float fitScale,
                float baseViewportWidth, float baseViewportHeight,
                float viewportWidth, float viewportHeight,
                NormalizedRect logicalCropRect, NormalizedRect sourceCropRect,
                boolean valid) {
            this.role = role;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.rotationDegrees = rotationDegrees;
            this.logicalSensorWidth = logicalSensorWidth;
            this.logicalSensorHeight = logicalSensorHeight;
            this.requestedWidth = requestedWidth;
            this.requestedHeight = requestedHeight;
            this.sourceAspect = sourceAspect;
            this.targetAspect = targetAspect;
            this.sourceAnchorU = sourceAnchorU;
            this.sourceAnchorV = sourceAnchorV;
            this.logicalAnchorU = logicalAnchorU;
            this.logicalAnchorV = logicalAnchorV;
            this.zoom = zoom;
            this.fitScale = fitScale;
            this.baseViewportWidth = baseViewportWidth;
            this.baseViewportHeight = baseViewportHeight;
            this.viewportWidth = viewportWidth;
            this.viewportHeight = viewportHeight;
            this.logicalCropRect = logicalCropRect;
            this.sourceCropRect = sourceCropRect;
            this.valid = valid;
        }
    }

    /** Backward-compatible entry point: requested footprint equals raw target, zoom 1. */
    public static Calculation calculate(int sourceWidth, int sourceHeight,
            int requestedWidth, int requestedHeight, int rotationDegrees,
            float sourceAnchorU, float sourceAnchorV) {
        return calculate(sourceWidth, sourceHeight, requestedWidth, requestedHeight,
                rotationDegrees, sourceAnchorU, sourceAnchorV,
                ConfigManager.DEFAULT_VIEWPORT_ZOOM, RenderTargetRole.PREVIEW);
    }

    /** Backward-compatible entry point with an explicit consumer role. */
    public static Calculation calculate(int sourceWidth, int sourceHeight,
            int requestedWidth, int requestedHeight, int rotationDegrees,
            float sourceAnchorU, float sourceAnchorV, RenderTargetRole role) {
        return calculate(sourceWidth, sourceHeight, requestedWidth, requestedHeight,
                rotationDegrees, sourceAnchorU, sourceAnchorV,
                ConfigManager.DEFAULT_VIEWPORT_ZOOM, role);
    }

    /**
     * Calculate Dynamic v2 geometry using a requested output footprint.
     * {@code requestedWidth/Height} are not swapped for producer orientation.
     */
    public static Calculation calculate(int sourceWidth, int sourceHeight,
            int requestedWidth, int requestedHeight, int rotationDegrees,
            float sourceAnchorU, float sourceAnchorV, float zoom,
            RenderTargetRole role) {
        RenderTargetRole effectiveRole = role == null ? RenderTargetRole.PREVIEW : role;
        int normalizedRotation = VirtualSensorTransform.normalizeRotation(rotationDegrees);
        float safeSourceU = clampUnit(sourceAnchorU);
        float safeSourceV = clampUnit(sourceAnchorV);
        float[] logicalAnchor = VirtualSensorTransform.sourceToLogical(
                safeSourceU, safeSourceV, normalizedRotation);

        int logicalWidth = sourceWidth;
        int logicalHeight = sourceHeight;
        if (normalizedRotation == 90 || normalizedRotation == 270) {
            logicalWidth = sourceHeight;
            logicalHeight = sourceWidth;
        }

        boolean valid = sourceWidth > 0 && sourceHeight > 0
                && requestedWidth > 0 && requestedHeight > 0;
        if (!valid) {
            NormalizedRect full = fullRect();
            return new Calculation(effectiveRole,
                    Math.max(0, sourceWidth), Math.max(0, sourceHeight), normalizedRotation,
                    Math.max(0, logicalWidth), Math.max(0, logicalHeight),
                    Math.max(0, requestedWidth), Math.max(0, requestedHeight),
                    1.0f, 1.0f, safeSourceU, safeSourceV,
                    logicalAnchor[0], logicalAnchor[1],
                    sanitizeZoom(zoom), 1.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                    full, full, false);
        }

        float sourceAspect = logicalWidth / (float) logicalHeight;
        float targetAspect = requestedWidth / (float) requestedHeight;
        float fitScale = Math.min(1.0f, Math.min(
                logicalWidth / (float) requestedWidth,
                logicalHeight / (float) requestedHeight));
        float baseWidth = requestedWidth * fitScale;
        float baseHeight = requestedHeight * fitScale;
        float safeZoom = sanitizeZoom(zoom);
        float viewportWidth = Math.min(logicalWidth, baseWidth / safeZoom);
        float viewportHeight = Math.min(logicalHeight, baseHeight / safeZoom);
        float normalizedViewportWidth = clampUnit(viewportWidth / logicalWidth);
        float normalizedViewportHeight = clampUnit(viewportHeight / logicalHeight);
        float logicalCenterU = clampCenter(logicalAnchor[0], normalizedViewportWidth);
        float logicalCenterV = clampCenter(logicalAnchor[1], normalizedViewportHeight);
        NormalizedRect logicalCrop = rectAround(logicalCenterU, logicalCenterV,
                normalizedViewportWidth, normalizedViewportHeight);
        NormalizedRect sourceCrop = inverseRotatedBounds(logicalCrop, normalizedRotation);

        return new Calculation(effectiveRole,
                sourceWidth, sourceHeight, normalizedRotation,
                logicalWidth, logicalHeight, requestedWidth, requestedHeight,
                sourceAspect, targetAspect, safeSourceU, safeSourceV,
                logicalCenterU, logicalCenterV, safeZoom, fitScale,
                baseWidth, baseHeight, viewportWidth, viewportHeight,
                logicalCrop, sourceCrop, true);
    }

    /**
     * Build the old crop-only matrix used by FIT/CROP. Dynamic v2 uses
     * {@link #buildDynamicTextureMatrix(Calculation)} instead.
     */
    public static float[] buildTextureCropMatrix(NormalizedRect sourceCropRect) {
        NormalizedRect rect = sourceCropRect == null ? fullRect() : sourceCropRect;
        float width = Math.max(0.0f, rect.width());
        float height = Math.max(0.0f, rect.height());
        float[] matrix = new float[16];
        matrix[0] = width;
        matrix[5] = height;
        matrix[10] = 1.0f;
        matrix[12] = rect.left;
        matrix[13] = clampUnit(1.0f - rect.bottom);
        matrix[15] = 1.0f;
        return matrix;
    }

    /**
     * Build output-UV to decoded-source-UV mapping for Dynamic v2.
     *
     * <p>The matrix is intended for
     * {@code uSTMatrix * uDynamicTextureMatrix * aTextureCoord}. Its
     * coordinate convention preserves the existing full-texture identity and
     * keeps SurfaceTexture's ST matrix as the producer-side transform owner.</p>
     */
    public static float[] buildDynamicTextureMatrix(Calculation calculation) {
        float[] matrix = new float[16];
        if (calculation == null || !calculation.valid) {
            matrix[0] = 1.0f;
            matrix[5] = 1.0f;
            matrix[10] = 1.0f;
            matrix[15] = 1.0f;
            return matrix;
        }

        NormalizedRect logical = calculation.logicalCropRect;
        float width = logical.width();
        float height = logical.height();
        switch (calculation.rotationDegrees) {
            case 90:
                // source = (logicalV, 1 - logicalU)
                matrix[4] = -height;
                matrix[12] = logical.bottom;
                matrix[1] = width;
                matrix[13] = logical.left;
                break;
            case 180:
                // source = (1 - logicalU, 1 - logicalV)
                matrix[0] = -width;
                matrix[12] = 1.0f - logical.left;
                matrix[5] = -height;
                matrix[13] = logical.bottom;
                break;
            case 270:
                // source = (1 - logicalV, logicalU)
                matrix[4] = height;
                matrix[12] = 1.0f - logical.bottom;
                matrix[1] = -width;
                matrix[13] = 1.0f - logical.left;
                break;
            default:
                // source = (logicalU, logicalV), preserving the established
                // crop matrix's bottom-up texture coordinate convention.
                matrix[0] = width;
                matrix[12] = logical.left;
                matrix[5] = height;
                matrix[13] = clampUnit(1.0f - logical.bottom);
                break;
        }
        matrix[10] = 1.0f;
        matrix[15] = 1.0f;
        return matrix;
    }

    /** Clamp a logical anchor to the movable area of a normalized viewport. */
    public static float[] clampLogicalAnchor(float logicalU, float logicalV,
            float viewportWidth, float viewportHeight) {
        return new float[] {
                clampCenter(clampUnit(logicalU), clampUnit(viewportWidth)),
                clampCenter(clampUnit(logicalV), clampUnit(viewportHeight))
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

    private static float sanitizeZoom(float value) {
        return clamp(value, ConfigManager.MIN_VIEWPORT_ZOOM,
                ConfigManager.MAX_VIEWPORT_ZOOM);
    }

    private static NormalizedRect fullRect() {
        return new NormalizedRect(0.0f, 0.0f, 1.0f, 1.0f);
    }

    private static float clampUnit(float value) {
        return clamp(value, 0.0f, 1.0f);
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
