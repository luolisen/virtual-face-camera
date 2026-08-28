package io.github.alanlaw.vfc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Camera1PreviewTransformTest {
    private static final float EPSILON = 0.0001f;

    @Test
    public void backCameraMatchesAospRotationFlags() {
        assertEquals(Camera1PreviewTransform.IDENTITY,
                Camera1PreviewTransform.forDisplayOrientation(
                        android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK, 0));
        assertEquals(Camera1PreviewTransform.ROT90,
                Camera1PreviewTransform.forDisplayOrientation(
                        android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK, 90));
        assertEquals(Camera1PreviewTransform.ROT180,
                Camera1PreviewTransform.forDisplayOrientation(
                        android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK, 180));
        assertEquals(Camera1PreviewTransform.ROT270,
                Camera1PreviewTransform.forDisplayOrientation(
                        android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK, 270));
    }

    @Test
    public void frontCameraMatchesAospMirrorAndRotationFlags() {
        assertEquals(Camera1PreviewTransform.FLIP_H,
                Camera1PreviewTransform.forDisplayOrientation(
                        android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT, 0));
        assertEquals(Camera1PreviewTransform.FLIP_H | Camera1PreviewTransform.ROT90,
                Camera1PreviewTransform.forDisplayOrientation(
                        android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT, 90));
        assertEquals(Camera1PreviewTransform.FLIP_V,
                Camera1PreviewTransform.forDisplayOrientation(
                        android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT, 180));
        assertEquals(Camera1PreviewTransform.FLIP_V | Camera1PreviewTransform.ROT90,
                Camera1PreviewTransform.forDisplayOrientation(
                        android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT, 270));
    }

    @Test
    public void invalidFlagsAndDisplayAnglesAreRejected() {
        assertEquals(Camera1PreviewTransform.INVALID,
                Camera1PreviewTransform.forDisplayOrientation(
                        android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK, 45));
        assertFalse(Camera1PreviewTransform.isValid(0x08));
        assertTrue(Camera1PreviewTransform.isValid(Camera1PreviewTransform.ROT270));
    }

    @Test
    public void forwardAndInverseTransformsRoundTrip() {
        int[] flags = {
                Camera1PreviewTransform.IDENTITY,
                Camera1PreviewTransform.FLIP_H,
                Camera1PreviewTransform.FLIP_V,
                Camera1PreviewTransform.ROT90,
                Camera1PreviewTransform.ROT180,
                Camera1PreviewTransform.ROT270,
                Camera1PreviewTransform.FLIP_H | Camera1PreviewTransform.ROT90,
                Camera1PreviewTransform.FLIP_V | Camera1PreviewTransform.ROT90
        };
        for (int flag : flags) {
            float[] displayed = Camera1PreviewTransform.forwardPoint(0.23f, 0.71f, flag);
            float[] source = Camera1PreviewTransform.inversePoint(
                    displayed[0], displayed[1], flag);
            assertEquals(0.23f, source[0], EPSILON);
            assertEquals(0.71f, source[1], EPSILON);
        }
    }

    @Test
    public void inverseDeltaFollowsProducerRotation() {
        float[] sourceDelta = Camera1PreviewTransform.inverseDelta(
                0.1f, 0.0f, Camera1PreviewTransform.ROT90);

        assertEquals(0.0f, sourceDelta[0], EPSILON);
        assertEquals(-0.1f, sourceDelta[1], EPSILON);
    }
}
