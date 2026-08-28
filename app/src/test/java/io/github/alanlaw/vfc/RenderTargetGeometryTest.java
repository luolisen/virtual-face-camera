package io.github.alanlaw.vfc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RenderTargetGeometryTest {
    private static final HostWindowGeometry.Snapshot PORTRAIT_HOST =
            new HostWindowGeometry.Snapshot(1080, 2376, 0);
    private static final HostWindowGeometry.Snapshot LANDSCAPE_HOST =
            new HostWindowGeometry.Snapshot(2376, 1080, 1);

    @Test
    public void previewLandscapeBufferAlwaysUsesRawTargetEvenForPortraitHost() {
        RenderTargetGeometry.Calculation result = RenderTargetGeometry.calculate(
                1600, 728, PORTRAIT_HOST, RenderTargetRole.PREVIEW);

        assertEquals(1600, result.logicalTargetWidth);
        assertEquals(728, result.logicalTargetHeight);
        assertFalse(result.orientationCompensated);
    }

    @Test
    public void previewLandscapeBufferStaysLandscapeForLandscapeHost() {
        RenderTargetGeometry.Calculation result = RenderTargetGeometry.calculate(
                1600, 728, LANDSCAPE_HOST, RenderTargetRole.PREVIEW);

        assertEquals(1600, result.logicalTargetWidth);
        assertEquals(728, result.logicalTargetHeight);
        assertFalse(result.orientationCompensated);
    }

    @Test
    public void portraitBufferStaysPortraitForPortraitHost() {
        RenderTargetGeometry.Calculation result = RenderTargetGeometry.calculate(
                728, 1600, PORTRAIT_HOST, RenderTargetRole.PREVIEW);

        assertEquals(728, result.logicalTargetWidth);
        assertEquals(1600, result.logicalTargetHeight);
        assertFalse(result.orientationCompensated);
    }

    @Test
    public void readerNeverUsesHostOrientationCompensation() {
        RenderTargetGeometry.Calculation result = RenderTargetGeometry.calculate(
                1600, 728, PORTRAIT_HOST, RenderTargetRole.READER);

        assertEquals(1600, result.logicalTargetWidth);
        assertEquals(728, result.logicalTargetHeight);
        assertFalse(result.orientationCompensated);
    }

    @Test
    public void captureNeverUsesHostOrientationCompensation() {
        RenderTargetGeometry.Calculation result = RenderTargetGeometry.calculate(
                1600, 728, PORTRAIT_HOST, RenderTargetRole.CAPTURE);

        assertEquals(1600, result.logicalTargetWidth);
        assertEquals(728, result.logicalTargetHeight);
        assertFalse(result.orientationCompensated);
    }

    @Test
    public void unavailableHostFallsBackToRawTarget() {
        RenderTargetGeometry.Calculation result = RenderTargetGeometry.calculate(
                1600, 728, HostWindowGeometry.Snapshot.unavailable(), RenderTargetRole.PREVIEW);

        assertEquals(1600, result.logicalTargetWidth);
        assertEquals(728, result.logicalTargetHeight);
        assertFalse(result.orientationCompensated);
    }
}
