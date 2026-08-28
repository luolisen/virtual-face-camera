package io.github.alanlaw.vfc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ViewportCommandControllerTest {
    private static final float EPSILON = 0.0001f;

    private static ConfigManager.Viewport viewport(float u, float v, float zoom) {
        return new ConfigManager.Viewport(u, v, zoom);
    }

    @Test
    public void resetReturnsCenterAndDefaultZoom() {
        ViewportCommandController.Result result = ViewportCommandController.apply(
                viewport(0.1f, 0.9f, 3.0f), IpcContract.VIEWPORT_COMMAND_RESET,
                0, Camera1PreviewTransform.IDENTITY,
                1920, 1080, 640, 480, 5);

        assertTrue(result.isChanged());
        assertEquals(0.5f, result.getViewport().getAnchorU(), EPSILON);
        assertEquals(0.5f, result.getViewport().getAnchorV(), EPSILON);
        assertEquals(1.0f, result.getViewport().getZoom(), EPSILON);
    }

    @Test
    public void zoomCommandsChangeOnlyZoomAndStayWithinRuntimeBounds() {
        ConfigManager.Viewport current = viewport(0.2f, 0.8f, 1.0f);
        ViewportCommandController.Result zoomIn = ViewportCommandController.apply(
                current, IpcContract.VIEWPORT_COMMAND_ZOOM_IN, 0,
                Camera1PreviewTransform.IDENTITY,
                1920, 1080, 640, 480, 5);
        ViewportCommandController.Result zoomOut = ViewportCommandController.apply(
                viewport(0.2f, 0.8f, 4.0f), IpcContract.VIEWPORT_COMMAND_ZOOM_OUT, 0,
                Camera1PreviewTransform.IDENTITY,
                1920, 1080, 640, 480, 5);

        assertEquals(0.2f, zoomIn.getViewport().getAnchorU(), EPSILON);
        assertEquals(0.8f, zoomIn.getViewport().getAnchorV(), EPSILON);
        assertEquals(1.1f, zoomIn.getViewport().getZoom(), EPSILON);
        assertEquals(4.0f / 1.1f, zoomOut.getViewport().getZoom(), EPSILON);
    }

    @Test
    public void arrowCommandUsesDisplayedDeltaThroughRotatedProducer() {
        ViewportCommandController.Result result = ViewportCommandController.apply(
                viewport(0.5f, 0.5f, 2.0f), IpcContract.VIEWPORT_COMMAND_RIGHT,
                0, Camera1PreviewTransform.ROT90,
                1920, 1080, 640, 480, 10);

        assertTrue(result.isChanged());
        assertEquals(0.5f, result.getViewport().getAnchorU(), EPSILON);
        assertEquals(0.4f, result.getViewport().getAnchorV(), EPSILON);
    }

    @Test
    public void frontCameraHorizontalMirrorKeepsLeftArrowOnDisplayedLeft() {
        ViewportCommandController.Result result = ViewportCommandController.apply(
                viewport(0.5f, 0.5f, 1.0f), IpcContract.VIEWPORT_COMMAND_LEFT,
                0, Camera1PreviewTransform.FLIP_H,
                1920, 1080, 640, 480, 10);

        assertTrue(result.isChanged());
        // The decoded-source anchor moves right so the producer mirror makes
        // the displayed footprint move left.
        assertEquals(0.6f, result.getViewport().getAnchorU(), EPSILON);
        assertEquals(0.5f, result.getViewport().getAnchorV(), EPSILON);
        float[] displayed = Camera1PreviewTransform.forwardPoint(
                result.getViewport().getAnchorU(), result.getViewport().getAnchorV(),
                Camera1PreviewTransform.FLIP_H);
        assertEquals(0.4f, displayed[0], EPSILON);
    }

    @Test
    public void arrowCommandAlsoAccountsForVfcRotation() {
        ViewportCommandController.Result result = ViewportCommandController.apply(
                viewport(0.5f, 0.5f, 1.0f), IpcContract.VIEWPORT_COMMAND_RIGHT,
                90, Camera1PreviewTransform.IDENTITY,
                1080, 1920, 640, 480, 10);

        assertTrue(result.isChanged());
        assertEquals(0.5f, result.getViewport().getAnchorU(), EPSILON);
        assertEquals(0.4f, result.getViewport().getAnchorV(), EPSILON);
    }

    @Test
    public void arrowCommandClampsToTheEffectiveViewport() {
        ViewportCommandController.Result result = ViewportCommandController.apply(
                viewport(0.90625f, 0.5f, 4.0f), IpcContract.VIEWPORT_COMMAND_RIGHT,
                0, Camera1PreviewTransform.IDENTITY,
                1920, 1080, 640, 480, 10);

        assertFalse(result.isChanged());
        assertEquals(0.90625f, result.getViewport().getAnchorU(), EPSILON);
        assertEquals(0.5f, result.getViewport().getAnchorV(), EPSILON);
        assertEquals(4.0f, result.getViewport().getZoom(), EPSILON);
    }

    @Test
    public void allVfcAndProducerTransformsPreserveDisplayDirection() {
        int[] rotations = { 0, 90, 180, 270 };
        int[] transforms = {
                Camera1PreviewTransform.IDENTITY,
                Camera1PreviewTransform.FLIP_H,
                Camera1PreviewTransform.FLIP_V,
                Camera1PreviewTransform.ROT90,
                Camera1PreviewTransform.ROT180,
                Camera1PreviewTransform.ROT270,
                Camera1PreviewTransform.FLIP_H | Camera1PreviewTransform.ROT90,
                Camera1PreviewTransform.FLIP_V | Camera1PreviewTransform.ROT90
        };
        String[] commands = {
                IpcContract.VIEWPORT_COMMAND_UP,
                IpcContract.VIEWPORT_COMMAND_DOWN,
                IpcContract.VIEWPORT_COMMAND_LEFT,
                IpcContract.VIEWPORT_COMMAND_RIGHT
        };
        for (int rotation : rotations) {
            for (int transform : transforms) {
                for (String command : commands) {
                    ViewportCommandController.Result result = ViewportCommandController.apply(
                            viewport(0.5f, 0.5f, 2.0f), command, rotation, transform,
                            1920, 1080, 1920, 1080, 5);
                    float displayDeltaU = IpcContract.VIEWPORT_COMMAND_LEFT.equals(command)
                            ? -0.05f
                            : IpcContract.VIEWPORT_COMMAND_RIGHT.equals(command) ? 0.05f : 0.0f;
                    float displayDeltaV = IpcContract.VIEWPORT_COMMAND_UP.equals(command)
                            ? -0.05f
                            : IpcContract.VIEWPORT_COMMAND_DOWN.equals(command) ? 0.05f : 0.0f;
                    float[] logicalDelta = Camera1PreviewTransform.inverseDelta(
                            displayDeltaU, displayDeltaV, transform);
                    float[] logical = VirtualSensorTransform.sourceToLogical(
                            result.getViewport().getAnchorU(),
                            result.getViewport().getAnchorV(), rotation);
                    assertEquals(0.5f + logicalDelta[0], logical[0], EPSILON);
                    assertEquals(0.5f + logicalDelta[1], logical[1], EPSILON);
                }
            }
        }
    }

    @Test
    public void unknownCommandDoesNotChangeViewport() {
        ConfigManager.Viewport current = viewport(0.3f, 0.7f, 2.0f);
        ViewportCommandController.Result result = ViewportCommandController.apply(
                current, "not-a-command", 0, Camera1PreviewTransform.IDENTITY,
                1920, 1080, 640, 480, 10);

        assertFalse(result.isChanged());
        assertEquals(current, result.getViewport());
    }
}
