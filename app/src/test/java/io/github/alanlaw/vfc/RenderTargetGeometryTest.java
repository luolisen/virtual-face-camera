package io.github.alanlaw.vfc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RenderTargetGeometryTest {
    @Test
    public void portraitHostCompensatesLandscapePreviewBuffer() {
        RenderTargetGeometry.Calculation result = RenderTargetGeometry.calculate(
                1600, 728, new HostWindowGeometry.Snapshot(1080, 2376, 0),
                RenderTargetRole.PREVIEW);

        assertTrue(result.orientationCompensated);
        assertEquals(728, result.logicalTargetWidth);
        assertEquals(1600, result.logicalTargetHeight);
    }

    @Test
    public void landscapeHostKeepsLandscapePreviewBuffer() {
        RenderTargetGeometry.Calculation result = RenderTargetGeometry.calculate(
                1600, 728, new HostWindowGeometry.Snapshot(2376, 1080, 0),
                RenderTargetRole.PREVIEW);

        assertFalse(result.orientationCompensated);
        assertEquals(1600, result.logicalTargetWidth);
        assertEquals(728, result.logicalTargetHeight);
    }

    @Test
    public void portraitPreviewBufferIsNotSwappedForPortraitHost() {
        RenderTargetGeometry.Calculation result = RenderTargetGeometry.calculate(
                728, 1600, new HostWindowGeometry.Snapshot(1080, 2376, 0),
                RenderTargetRole.PREVIEW);

        assertFalse(result.orientationCompensated);
        assertEquals(728, result.logicalTargetWidth);
        assertEquals(1600, result.logicalTargetHeight);
    }

    @Test
    public void readerKeepsRawGeometryEvenWithOppositeHostOrientation() {
        RenderTargetGeometry.Calculation result = RenderTargetGeometry.calculate(
                1600, 728, new HostWindowGeometry.Snapshot(1080, 2376, 0),
                RenderTargetRole.READER);

        assertFalse(result.orientationCompensated);
        assertEquals(1600, result.logicalTargetWidth);
        assertEquals(728, result.logicalTargetHeight);
    }

    @Test
    public void captureKeepsRawGeometryEvenWithOppositeHostOrientation() {
        RenderTargetGeometry.Calculation result = RenderTargetGeometry.calculate(
                1600, 728, new HostWindowGeometry.Snapshot(1080, 2376, 0),
                RenderTargetRole.CAPTURE);

        assertFalse(result.orientationCompensated);
        assertEquals(1600, result.logicalTargetWidth);
        assertEquals(728, result.logicalTargetHeight);
    }

    @Test
    public void unavailableHostFallsBackToRawGeometry() {
        RenderTargetGeometry.Calculation result = RenderTargetGeometry.calculate(
                1600, 728, HostWindowGeometry.Snapshot.unavailable(),
                RenderTargetRole.PREVIEW);

        assertFalse(result.orientationCompensated);
        assertEquals(1600, result.logicalTargetWidth);
        assertEquals(728, result.logicalTargetHeight);
    }
}
