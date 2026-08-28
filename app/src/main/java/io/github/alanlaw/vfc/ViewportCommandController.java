package io.github.alanlaw.vfc;

/**
 * Pure runtime viewport-command interpreter. The target process supplies its
 * actual Camera1 producer transform and requested preview footprint; the app
 * process never guesses either value.
 */
public final class ViewportCommandController {
    public static final class Result {
        private final ConfigManager.Viewport viewport;
        private final boolean changed;

        private Result(ConfigManager.Viewport viewport, boolean changed) {
            this.viewport = viewport;
            this.changed = changed;
        }

        public ConfigManager.Viewport getViewport() {
            return viewport;
        }

        public boolean isChanged() {
            return changed;
        }
    }

    private ViewportCommandController() {
    }

    public static Result apply(ConfigManager.Viewport current,
            String command, int vfcRotationDegrees, int previewTransformFlags,
            int sourceWidth, int sourceHeight,
            int requestedWidth, int requestedHeight, int stepPercent) {
        ConfigManager.Viewport safeCurrent = current == null
                ? new ConfigManager.Viewport(0.5f, 0.5f)
                : current;
        if (IpcContract.VIEWPORT_COMMAND_RESET.equals(command)) {
            return result(new ConfigManager.Viewport(0.5f, 0.5f,
                    ConfigManager.DEFAULT_VIEWPORT_ZOOM), safeCurrent);
        }
        if (IpcContract.VIEWPORT_COMMAND_ZOOM_IN.equals(command)) {
            float zoom = Math.min(ConfigManager.MAX_VIEWPORT_ZOOM,
                    safeCurrent.getZoom() * ConfigManager.VIEWPORT_ZOOM_FACTOR);
            return result(new ConfigManager.Viewport(safeCurrent.getAnchorU(),
                    safeCurrent.getAnchorV(), zoom), safeCurrent);
        }
        if (IpcContract.VIEWPORT_COMMAND_ZOOM_OUT.equals(command)) {
            float zoom = Math.max(ConfigManager.MIN_VIEWPORT_ZOOM,
                    safeCurrent.getZoom() / ConfigManager.VIEWPORT_ZOOM_FACTOR);
            return result(new ConfigManager.Viewport(safeCurrent.getAnchorU(),
                    safeCurrent.getAnchorV(), zoom), safeCurrent);
        }
        if (!isDirection(command)) {
            return new Result(safeCurrent, false);
        }

        int safeStep = Math.max(ConfigManager.MIN_VIEWPORT_MOVE_STEP_PERCENT,
                Math.min(ConfigManager.MAX_VIEWPORT_MOVE_STEP_PERCENT, stepPercent));
        float[] logical = VirtualSensorTransform.sourceToLogical(
                safeCurrent.getAnchorU(), safeCurrent.getAnchorV(), vfcRotationDegrees);
        VirtualSensorGeometry.Calculation geometry = VirtualSensorGeometry.calculate(
                sourceWidth, sourceHeight, requestedWidth, requestedHeight,
                vfcRotationDegrees, safeCurrent.getAnchorU(), safeCurrent.getAnchorV(),
                safeCurrent.getZoom(), RenderTargetRole.PREVIEW);
        if (geometry.valid) {
            logical[0] = geometry.logicalAnchorU;
            logical[1] = geometry.logicalAnchorV;
        }

        float delta = safeStep / 100.0f;
        float displayDeltaU = 0.0f;
        float displayDeltaV = 0.0f;
        if (IpcContract.VIEWPORT_COMMAND_UP.equals(command)) {
            displayDeltaV = -delta;
        } else if (IpcContract.VIEWPORT_COMMAND_DOWN.equals(command)) {
            displayDeltaV = delta;
        } else if (IpcContract.VIEWPORT_COMMAND_LEFT.equals(command)) {
            displayDeltaU = -delta;
        } else if (IpcContract.VIEWPORT_COMMAND_RIGHT.equals(command)) {
            displayDeltaU = delta;
        }
        // Arrow commands are vectors. Transform the delta directly so edge
        // clamping of an absolute point cannot change its direction.
        float[] logicalDelta = Camera1PreviewTransform.inverseDelta(
                displayDeltaU, displayDeltaV, previewTransformFlags);
        float[] movedLogical = {
                logical[0] + logicalDelta[0],
                logical[1] + logicalDelta[1]
        };
        if (geometry.valid) {
            float normalizedViewportWidth = geometry.viewportWidth
                    / (float) geometry.logicalSensorWidth;
            float normalizedViewportHeight = geometry.viewportHeight
                    / (float) geometry.logicalSensorHeight;
            float[] clamped = VirtualSensorGeometry.clampLogicalAnchor(
                    movedLogical[0], movedLogical[1],
                    normalizedViewportWidth, normalizedViewportHeight);
            movedLogical[0] = clamped[0];
            movedLogical[1] = clamped[1];
        }

        float[] source = VirtualSensorTransform.logicalToSource(
                movedLogical[0], movedLogical[1], vfcRotationDegrees);
        return result(new ConfigManager.Viewport(source[0], source[1], safeCurrent.getZoom()), safeCurrent);
    }

    private static Result result(ConfigManager.Viewport next, ConfigManager.Viewport current) {
        return new Result(next, !next.equals(current));
    }

    private static boolean isDirection(String command) {
        return IpcContract.VIEWPORT_COMMAND_UP.equals(command)
                || IpcContract.VIEWPORT_COMMAND_DOWN.equals(command)
                || IpcContract.VIEWPORT_COMMAND_LEFT.equals(command)
                || IpcContract.VIEWPORT_COMMAND_RIGHT.equals(command);
    }
}
