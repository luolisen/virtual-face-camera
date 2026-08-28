package io.github.alanlaw.vfc;

import java.util.Objects;

/**
 * Immutable renderer input snapshot. A GL frame reads one instance and does
 * not mix values from multiple configuration updates.
 */
public final class RendererState {
    public final long generation;
    public final int sourceWidth;
    public final int sourceHeight;
    public final int rotationDegrees;
    public final String aspectMode;
    public final float anchorU;
    public final float anchorV;
    public final float zoom;
    public final int requestedWidth;
    public final int requestedHeight;
    public final int producerTransformFlags;
    public final RenderTargetRole role;
    public final String activePresetId;
    public final String activeShortcutKey;
    public final int viewportMoveStepPercent;
    public final HostWindowGeometry.Snapshot hostWindowGeometry;

    private RendererState(long generation,
            int sourceWidth, int sourceHeight, int rotationDegrees,
            String aspectMode, float anchorU, float anchorV, float zoom,
            int requestedWidth, int requestedHeight, int producerTransformFlags,
            RenderTargetRole role, String activePresetId, String activeShortcutKey,
            int viewportMoveStepPercent, HostWindowGeometry.Snapshot hostWindowGeometry) {
        this.generation = generation;
        this.sourceWidth = Math.max(0, sourceWidth);
        this.sourceHeight = Math.max(0, sourceHeight);
        this.rotationDegrees = normalizeRotation(rotationDegrees);
        this.aspectMode = normalizeAspectMode(aspectMode);
        this.anchorU = clampUnit(anchorU, 0.5f);
        this.anchorV = clampUnit(anchorV, 0.5f);
        this.zoom = clamp(zoom, ConfigManager.MIN_VIEWPORT_ZOOM,
                ConfigManager.MAX_VIEWPORT_ZOOM);
        this.requestedWidth = Math.max(0, requestedWidth);
        this.requestedHeight = Math.max(0, requestedHeight);
        this.producerTransformFlags = producerTransformFlags;
        this.role = role == null ? RenderTargetRole.PREVIEW : role;
        this.activePresetId = activePresetId == null ? "" : activePresetId;
        this.activeShortcutKey = activeShortcutKey == null ? "" : activeShortcutKey;
        this.viewportMoveStepPercent = Math.max(ConfigManager.MIN_VIEWPORT_MOVE_STEP_PERCENT,
                Math.min(ConfigManager.MAX_VIEWPORT_MOVE_STEP_PERCENT, viewportMoveStepPercent));
        this.hostWindowGeometry = hostWindowGeometry == null
                ? HostWindowGeometry.Snapshot.unavailable() : hostWindowGeometry;
    }

    public static RendererState initial(RenderTargetRole role) {
        return new RendererState(0L, 0, 0, 0, ConfigManager.ASPECT_MODE_DYNAMIC,
                0.5f, 0.5f, ConfigManager.DEFAULT_VIEWPORT_ZOOM,
                0, 0, Camera1PreviewTransform.IDENTITY,
                role, "", "", ConfigManager.DEFAULT_VIEWPORT_MOVE_STEP_PERCENT,
                HostWindowGeometry.Snapshot.unavailable());
    }

    public RendererState withRotation(int degrees) {
        return next(sourceWidth, sourceHeight, degrees, aspectMode,
                anchorU, anchorV, zoom, requestedWidth, requestedHeight,
                producerTransformFlags, role, activePresetId, activeShortcutKey,
                viewportMoveStepPercent, hostWindowGeometry);
    }

    public RendererState withSourceSize(int width, int height) {
        return next(width, height, rotationDegrees, aspectMode,
                anchorU, anchorV, zoom, requestedWidth, requestedHeight,
                producerTransformFlags, role, activePresetId, activeShortcutKey,
                viewportMoveStepPercent, hostWindowGeometry);
    }

    public RendererState withAspectMode(String mode) {
        return next(sourceWidth, sourceHeight, rotationDegrees, mode,
                anchorU, anchorV, zoom, requestedWidth, requestedHeight,
                producerTransformFlags, role, activePresetId, activeShortcutKey,
                viewportMoveStepPercent, hostWindowGeometry);
    }

    public RendererState withHostWindowGeometry(HostWindowGeometry.Snapshot geometry) {
        return next(sourceWidth, sourceHeight, rotationDegrees, aspectMode,
                anchorU, anchorV, zoom, requestedWidth, requestedHeight,
                producerTransformFlags, role, activePresetId, activeShortcutKey,
                viewportMoveStepPercent, geometry);
    }

    public RendererState withRequestedFootprint(int width, int height) {
        return next(sourceWidth, sourceHeight, rotationDegrees, aspectMode,
                anchorU, anchorV, zoom,
                width > 0 && height > 0 ? width : 0,
                width > 0 && height > 0 ? height : 0,
                producerTransformFlags, role, activePresetId, activeShortcutKey,
                viewportMoveStepPercent, hostWindowGeometry);
    }

    public RendererState withProducerTransformFlags(int flags) {
        return next(sourceWidth, sourceHeight, rotationDegrees, aspectMode,
                anchorU, anchorV, zoom, requestedWidth, requestedHeight,
                flags, role, activePresetId, activeShortcutKey,
                viewportMoveStepPercent, hostWindowGeometry);
    }

    public RendererState withViewportState(ConfigManager.ActiveBinding binding, int stepPercent) {
        if (binding == null || binding.getViewport() == null) {
            return next(sourceWidth, sourceHeight, rotationDegrees, aspectMode,
                    0.5f, 0.5f, ConfigManager.DEFAULT_VIEWPORT_ZOOM,
                    requestedWidth, requestedHeight, producerTransformFlags, role,
                    "", "", stepPercent, hostWindowGeometry);
        }
        ConfigManager.Viewport viewport = binding.getViewport();
        return next(sourceWidth, sourceHeight, rotationDegrees, aspectMode,
                viewport.getAnchorU(), viewport.getAnchorV(), viewport.getZoom(),
                requestedWidth, requestedHeight, producerTransformFlags, role,
                binding.getPresetId(), binding.getShortcutKey(), stepPercent,
                hostWindowGeometry);
    }

    /** Apply all live rendering fields as one immutable snapshot. */
    public RendererState withRenderConfiguration(int degrees, String mode,
            ConfigManager.ActiveBinding binding, int stepPercent,
            HostWindowGeometry.Snapshot geometry) {
        float nextAnchorU = 0.5f;
        float nextAnchorV = 0.5f;
        float nextZoom = ConfigManager.DEFAULT_VIEWPORT_ZOOM;
        String nextPresetId = "";
        String nextShortcutKey = "";
        if (binding != null && binding.getViewport() != null) {
            ConfigManager.Viewport viewport = binding.getViewport();
            nextAnchorU = viewport.getAnchorU();
            nextAnchorV = viewport.getAnchorV();
            nextZoom = viewport.getZoom();
            nextPresetId = binding.getPresetId();
            nextShortcutKey = binding.getShortcutKey();
        }
        return next(sourceWidth, sourceHeight, degrees, mode,
                nextAnchorU, nextAnchorV, nextZoom,
                requestedWidth, requestedHeight, producerTransformFlags, role,
                nextPresetId, nextShortcutKey, stepPercent, geometry);
    }

    private RendererState next(int nextSourceWidth, int nextSourceHeight,
            int nextRotation, String nextAspectMode,
            float nextAnchorU, float nextAnchorV, float nextZoom,
            int nextRequestedWidth, int nextRequestedHeight,
            int nextProducerTransformFlags, RenderTargetRole nextRole,
            String nextPresetId, String nextShortcutKey, int nextStepPercent,
            HostWindowGeometry.Snapshot nextHostGeometry) {
        RendererState candidate = new RendererState(generation + 1L,
                nextSourceWidth, nextSourceHeight, nextRotation, nextAspectMode,
                nextAnchorU, nextAnchorV, nextZoom,
                nextRequestedWidth, nextRequestedHeight,
                nextProducerTransformFlags, nextRole,
                nextPresetId, nextShortcutKey, nextStepPercent, nextHostGeometry);
        return sameValues(candidate) ? this : candidate;
    }

    private boolean sameValues(RendererState other) {
        return sourceWidth == other.sourceWidth
                && sourceHeight == other.sourceHeight
                && rotationDegrees == other.rotationDegrees
                && Objects.equals(aspectMode, other.aspectMode)
                && Float.compare(anchorU, other.anchorU) == 0
                && Float.compare(anchorV, other.anchorV) == 0
                && Float.compare(zoom, other.zoom) == 0
                && requestedWidth == other.requestedWidth
                && requestedHeight == other.requestedHeight
                && producerTransformFlags == other.producerTransformFlags
                && role == other.role
                && Objects.equals(activePresetId, other.activePresetId)
                && Objects.equals(activeShortcutKey, other.activeShortcutKey)
                && viewportMoveStepPercent == other.viewportMoveStepPercent
                && Objects.equals(hostWindowGeometry, other.hostWindowGeometry);
    }

    private static int normalizeRotation(int degrees) {
        return ((degrees % 360) + 360) % 360;
    }

    private static String normalizeAspectMode(String mode) {
        if (ConfigManager.ASPECT_MODE_FIT.equals(mode)) {
            return ConfigManager.ASPECT_MODE_FIT;
        }
        if (ConfigManager.ASPECT_MODE_CROP.equals(mode)) {
            return ConfigManager.ASPECT_MODE_CROP;
        }
        return ConfigManager.ASPECT_MODE_DYNAMIC;
    }

    private static float clampUnit(float value, float fallback) {
        return clamp(Float.isFinite(value) ? value : fallback, 0.0f, 1.0f);
    }

    private static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
