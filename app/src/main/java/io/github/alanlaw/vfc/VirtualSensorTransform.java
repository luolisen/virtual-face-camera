package io.github.alanlaw.vfc;

/**
 * Pure normalized-coordinate transforms for the virtual sensor.
 *
 * <p>Coordinates use image space: U grows to the right and V grows down. The
 * mapping mirrors the existing GL path, which applies
 * {@code Matrix.rotateM(..., -rotationDegrees, ...)} to the full-screen quad.
 * Keeping both directions here prevents viewport controls from drifting when
 * the video is rotated.</p>
 */
public final class VirtualSensorTransform {
    private VirtualSensorTransform() {
    }

    public static int normalizeRotation(int rotationDegrees) {
        int normalized = rotationDegrees % 360;
        if (normalized < 0) {
            normalized += 360;
        }
        return normalized;
    }

    /** Map a decoded-source point to logical virtual-sensor space. */
    public static float[] sourceToLogical(float sourceU, float sourceV, int rotationDegrees) {
        float u = clampUnit(sourceU);
        float v = clampUnit(sourceV);
        switch (normalizeRotation(rotationDegrees)) {
            case 90:
                return new float[] { 1.0f - v, u };
            case 180:
                return new float[] { 1.0f - u, 1.0f - v };
            case 270:
                return new float[] { v, 1.0f - u };
            default:
                return new float[] { u, v };
        }
    }

    /** Map a logical virtual-sensor point back to decoded-source space. */
    public static float[] logicalToSource(float logicalU, float logicalV, int rotationDegrees) {
        float u = clampUnit(logicalU);
        float v = clampUnit(logicalV);
        switch (normalizeRotation(rotationDegrees)) {
            case 90:
                return new float[] { v, 1.0f - u };
            case 180:
                return new float[] { 1.0f - u, 1.0f - v };
            case 270:
                return new float[] { 1.0f - v, u };
            default:
                return new float[] { u, v };
        }
    }

    /** Move a logical-sensor point using the product's top-down directions. */
    public static float[] moveLogical(float logicalU, float logicalV,
            String direction, int stepPercent) {
        float u = clampUnit(logicalU);
        float v = clampUnit(logicalV);
        float delta = Math.max(0, stepPercent) / 100.0f;
        if (ConfigManager.VIEWPORT_DIRECTION_UP.equals(direction)) {
            v -= delta;
        } else if (ConfigManager.VIEWPORT_DIRECTION_DOWN.equals(direction)) {
            v += delta;
        } else if (ConfigManager.VIEWPORT_DIRECTION_LEFT.equals(direction)) {
            u -= delta;
        } else if (ConfigManager.VIEWPORT_DIRECTION_RIGHT.equals(direction)) {
            u += delta;
        }
        return new float[] { clampUnit(u), clampUnit(v) };
    }

    private static float clampUnit(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0.5f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
