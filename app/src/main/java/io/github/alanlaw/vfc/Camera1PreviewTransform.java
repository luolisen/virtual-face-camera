package io.github.alanlaw.vfc;

/**
 * Pure Camera1 preview-buffer transform helpers.
 *
 * <p>The flag values and front-camera mapping mirror the AOSP
 * {@code CameraClient::getOrientation(int, bool)} contract. Coordinates are
 * normalized image coordinates with U to the right and V down. The native
 * window applies horizontal/vertical mirroring first, followed by the
 * clockwise 90 degree transform when the rotation bit is present.</p>
 */
public final class Camera1PreviewTransform {
    public static final int IDENTITY = 0x00;
    public static final int FLIP_H = 0x01;
    public static final int FLIP_V = 0x02;
    public static final int ROT90 = 0x04;
    public static final int ROT180 = FLIP_H | FLIP_V;
    public static final int ROT270 = ROT180 | ROT90;
    public static final int INVALID = -1;
    private static final int VALID_MASK = FLIP_H | FLIP_V | ROT90;

    private Camera1PreviewTransform() {
    }

    /** Return the exact AOSP transform for a Camera1 facing and display angle. */
    public static int forDisplayOrientation(int facing, int degrees) {
        if (degrees != 0 && degrees != 90 && degrees != 180 && degrees != 270) {
            return INVALID;
        }
        boolean front = facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT;
        if (!front) {
            switch (degrees) {
                case 90:
                    return ROT90;
                case 180:
                    return ROT180;
                case 270:
                    return ROT270;
                default:
                    return IDENTITY;
            }
        }

        // AOSP getOrientation(degrees, mirror):
        // 0 -> FLIP_H, 90 -> FLIP_H|ROT90,
        // 180 -> FLIP_V, 270 -> FLIP_V|ROT90.
        switch (degrees) {
            case 90:
                return FLIP_H | ROT90;
            case 180:
                return FLIP_V;
            case 270:
                return FLIP_V | ROT90;
            default:
                return FLIP_H;
        }
    }

    public static boolean isValid(int flags) {
        return flags >= 0 && (flags & ~VALID_MASK) == 0;
    }

    /** Map a source point through the forward display transform. */
    public static float[] forwardPoint(float u, float v, int flags) {
        float x = clampUnit(u);
        float y = clampUnit(v);
        if (!isValid(flags)) {
            return new float[] { x, y };
        }
        if ((flags & FLIP_H) != 0) {
            x = 1.0f - x;
        }
        if ((flags & FLIP_V) != 0) {
            y = 1.0f - y;
        }
        if ((flags & ROT90) != 0) {
            float rotatedX = 1.0f - y;
            y = x;
            x = rotatedX;
        }
        return new float[] { x, y };
    }

    /** Map a displayed point back to the source point. */
    public static float[] inversePoint(float u, float v, int flags) {
        float x = clampUnit(u);
        float y = clampUnit(v);
        if (!isValid(flags)) {
            return new float[] { x, y };
        }
        if ((flags & ROT90) != 0) {
            float sourceBeforeRotationX = y;
            float sourceBeforeRotationY = 1.0f - x;
            x = sourceBeforeRotationX;
            y = sourceBeforeRotationY;
        }
        if ((flags & FLIP_H) != 0) {
            x = 1.0f - x;
        }
        if ((flags & FLIP_V) != 0) {
            y = 1.0f - y;
        }
        return new float[] { x, y };
    }

    /** Map a display-space delta into source-space delta. */
    public static float[] inverseDelta(float deltaU, float deltaV, int flags) {
        if (!isValid(flags)) {
            return new float[] { deltaU, deltaV };
        }
        float sourceU = deltaU;
        float sourceV = deltaV;
        if ((flags & ROT90) != 0) {
            sourceU = deltaV;
            sourceV = -deltaU;
        }
        if ((flags & FLIP_H) != 0) {
            sourceU = -sourceU;
        }
        if ((flags & FLIP_V) != 0) {
            sourceV = -sourceV;
        }
        return new float[] { sourceU, sourceV };
    }

    /** Map a source-space delta into displayed delta. */
    public static float[] forwardDelta(float deltaU, float deltaV, int flags) {
        if (!isValid(flags)) {
            return new float[] { deltaU, deltaV };
        }
        float displayedU = deltaU;
        float displayedV = deltaV;
        if ((flags & FLIP_H) != 0) {
            displayedU = -displayedU;
        }
        if ((flags & FLIP_V) != 0) {
            displayedV = -displayedV;
        }
        if ((flags & ROT90) != 0) {
            float rotatedU = -displayedV;
            displayedV = displayedU;
            displayedU = rotatedU;
        }
        return new float[] { displayedU, displayedV };
    }

    private static float clampUnit(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0.5f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
