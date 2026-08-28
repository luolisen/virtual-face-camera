package io.github.alanlaw.vfc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VirtualSensorTransformTest {
    private static final float EPSILON = 0.0001f;

    @Test
    public void sourceAndLogicalTransformsAreInversesForAllRotations() {
        float sourceU = 0.23f;
        float sourceV = 0.71f;
        for (int rotation : new int[] { 0, 90, 180, 270 }) {
            float[] logical = VirtualSensorTransform.sourceToLogical(
                    sourceU, sourceV, rotation);
            float[] source = VirtualSensorTransform.logicalToSource(
                    logical[0], logical[1], rotation);
            assertEquals(sourceU, source[0], EPSILON);
            assertEquals(sourceV, source[1], EPSILON);
        }
    }

    @Test
    public void joystickDirectionsStayInLogicalSpaceForAllRotations() {
        float sourceU = 0.37f;
        float sourceV = 0.62f;
        for (int rotation : new int[] { 0, 90, 180, 270 }) {
            float[] logical = VirtualSensorTransform.sourceToLogical(
                    sourceU, sourceV, rotation);
            float[] up = VirtualSensorTransform.moveLogical(
                    logical[0], logical[1], ConfigManager.VIEWPORT_DIRECTION_UP, 5);
            float[] down = VirtualSensorTransform.moveLogical(
                    logical[0], logical[1], ConfigManager.VIEWPORT_DIRECTION_DOWN, 5);
            float[] left = VirtualSensorTransform.moveLogical(
                    logical[0], logical[1], ConfigManager.VIEWPORT_DIRECTION_LEFT, 5);
            float[] right = VirtualSensorTransform.moveLogical(
                    logical[0], logical[1], ConfigManager.VIEWPORT_DIRECTION_RIGHT, 5);
            assertEquals(logical[0], up[0], EPSILON);
            assertTrue(up[1] < logical[1]);
            assertEquals(logical[0], down[0], EPSILON);
            assertTrue(down[1] > logical[1]);
            assertTrue(left[0] < logical[0]);
            assertEquals(logical[1], left[1], EPSILON);
            assertTrue(right[0] > logical[0]);
            assertEquals(logical[1], right[1], EPSILON);

            for (String direction : new String[] {
                    ConfigManager.VIEWPORT_DIRECTION_UP,
                    ConfigManager.VIEWPORT_DIRECTION_DOWN,
                    ConfigManager.VIEWPORT_DIRECTION_LEFT,
                    ConfigManager.VIEWPORT_DIRECTION_RIGHT
            }) {
                float[] moved = VirtualSensorTransform.moveLogical(
                        logical[0], logical[1], direction, 5);
                float[] roundTrip = VirtualSensorTransform.logicalToSource(
                        moved[0], moved[1], rotation);
                float[] movedLogical = VirtualSensorTransform.sourceToLogical(
                        roundTrip[0], roundTrip[1], rotation);
                assertEquals(moved[0], movedLogical[0], EPSILON);
                assertEquals(moved[1], movedLogical[1], EPSILON);
            }
        }
    }

    @Test
    public void logicalMovementClampsAtTheSensorEdges() {
        float[] topLeft = VirtualSensorTransform.moveLogical(
                0.01f, 0.01f, ConfigManager.VIEWPORT_DIRECTION_UP, 20);
        assertEquals(0.01f, topLeft[0], EPSILON);
        assertEquals(0.0f, topLeft[1], EPSILON);

        float[] bottomRight = VirtualSensorTransform.moveLogical(
                0.99f, 0.99f, ConfigManager.VIEWPORT_DIRECTION_RIGHT, 20);
        assertEquals(1.0f, bottomRight[0], EPSILON);
        assertEquals(0.99f, bottomRight[1], EPSILON);
    }
}
