package io.github.alanlaw.vfc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VirtualSensorGeometryTest {
    private static final float EPSILON = 0.0001f;

    @Test
    public void requestedFootprintFitsPortraitCanvasWithoutExpanding() {
        VirtualSensorGeometry.Calculation result = VirtualSensorGeometry.calculate(
                1080, 1920, 1600, 728, 0, 0.5f, 0.5f, 1.0f,
                RenderTargetRole.PREVIEW);

        assertTrue(result.valid);
        assertEquals(0.675f, result.fitScale, EPSILON);
        assertEquals(1080.0f, result.baseViewportWidth, EPSILON);
        assertEquals(491.4f, result.baseViewportHeight, EPSILON);
        assertEquals(1.0f, result.logicalCropRect.width(), EPSILON);
        assertEquals(491.4f / 1920.0f, result.logicalCropRect.height(), EPSILON);
        assertEquals(1080.0f / 1920.0f, result.sourceAspect, EPSILON);
        assertEquals(1600.0f / 728.0f, result.targetAspect, EPSILON);
    }

    @Test
    public void requestedFootprintDoesNotBecomeMaximumAspectCrop() {
        VirtualSensorGeometry.Calculation result = VirtualSensorGeometry.calculate(
                1920, 1080, 640, 480, 0, 0.5f, 0.5f, 1.0f,
                RenderTargetRole.PREVIEW);

        assertTrue(result.valid);
        assertEquals(1.0f, result.fitScale, EPSILON);
        assertEquals(640.0f, result.baseViewportWidth, EPSILON);
        assertEquals(480.0f, result.baseViewportHeight, EPSILON);
        assertEquals(640.0f / 1920.0f, result.logicalCropRect.width(), EPSILON);
        assertEquals(480.0f / 1080.0f, result.logicalCropRect.height(), EPSILON);
    }

    @Test
    public void zoomReducesBothViewportAxesWithTheSameFactor() {
        VirtualSensorGeometry.Calculation zoomOne = VirtualSensorGeometry.calculate(
                1920, 1080, 640, 480, 0, 0.5f, 0.5f, 1.0f,
                RenderTargetRole.PREVIEW);
        VirtualSensorGeometry.Calculation zoomTwo = VirtualSensorGeometry.calculate(
                1920, 1080, 640, 480, 0, 0.5f, 0.5f, 2.0f,
                RenderTargetRole.PREVIEW);
        VirtualSensorGeometry.Calculation zoomFour = VirtualSensorGeometry.calculate(
                1920, 1080, 640, 480, 0, 0.5f, 0.5f, 4.0f,
                RenderTargetRole.PREVIEW);

        assertEquals(640.0f / 1920.0f, zoomOne.logicalCropRect.width(), EPSILON);
        assertEquals(480.0f / 1080.0f, zoomOne.logicalCropRect.height(), EPSILON);
        assertEquals(320.0f / 1920.0f, zoomTwo.logicalCropRect.width(), EPSILON);
        assertEquals(240.0f / 1080.0f, zoomTwo.logicalCropRect.height(), EPSILON);
        assertEquals(160.0f / 1920.0f, zoomFour.logicalCropRect.width(), EPSILON);
        assertEquals(120.0f / 1080.0f, zoomFour.logicalCropRect.height(), EPSILON);
    }

    @Test
    public void quarterTurnSwapsCanvasBeforeFitting() {
        VirtualSensorGeometry.Calculation result = VirtualSensorGeometry.calculate(
                1080, 1920, 1600, 728, 90, 0.5f, 0.5f, 1.0f,
                RenderTargetRole.PREVIEW);

        assertTrue(result.valid);
        assertEquals(1920, result.logicalSensorWidth);
        assertEquals(1080, result.logicalSensorHeight);
        assertEquals(1.0f, result.fitScale, EPSILON);
        assertEquals(1600.0f, result.baseViewportWidth, EPSILON);
        assertEquals(728.0f, result.baseViewportHeight, EPSILON);
        assertEquals(1600.0f / 1920.0f, result.logicalCropRect.width(), EPSILON);
        assertEquals(728.0f / 1080.0f, result.logicalCropRect.height(), EPSILON);
    }

    @Test
    public void readerKeepsItsRawRequestedGeometryAndRole() {
        VirtualSensorGeometry.Calculation reader = VirtualSensorGeometry.calculate(
                1080, 1920, 1600, 728, 0, 0.5f, 0.5f, 1.0f,
                RenderTargetRole.READER);

        assertTrue(reader.valid);
        assertEquals(RenderTargetRole.READER, reader.role);
        assertEquals(1600, reader.requestedWidth);
        assertEquals(728, reader.requestedHeight);
        assertEquals(1.0f, reader.logicalCropRect.width(), EPSILON);
        assertEquals(491.4f / 1920.0f, reader.logicalCropRect.height(), EPSILON);
    }

    @Test
    public void anchorsClampToTheEffectiveViewport() {
        VirtualSensorGeometry.Calculation result = VirtualSensorGeometry.calculate(
                1920, 1080, 640, 480, 0, 0.0f, 1.0f, 1.0f,
                RenderTargetRole.PREVIEW);

        assertEquals(0.5f * (640.0f / 1920.0f), result.logicalAnchorU, EPSILON);
        assertEquals(1.0f - 0.5f * (480.0f / 1080.0f), result.logicalAnchorV, EPSILON);
        assertEquals(0.0f, result.logicalCropRect.left, EPSILON);
        assertEquals(1.0f - 480.0f / 1080.0f, result.logicalCropRect.top, EPSILON);
        assertEquals(1.0f, result.logicalCropRect.bottom, EPSILON);
    }

    @Test
    public void dynamicTextureMatrixMapsQuarterTurnFromLogicalToSource() {
        VirtualSensorGeometry.Calculation result = VirtualSensorGeometry.calculate(
                1920, 1080, 640, 480, 90, 0.5f, 0.5f, 1.0f,
                RenderTargetRole.PREVIEW);
        float[] matrix = VirtualSensorGeometry.buildDynamicTextureMatrix(result);

        assertEquals(result.logicalCropRect.bottom, matrix[12], EPSILON);
        assertEquals(result.logicalCropRect.left, matrix[13], EPSILON);
        assertEquals(-result.logicalCropRect.height(), matrix[4], EPSILON);
        assertEquals(result.logicalCropRect.width(), matrix[1], EPSILON);
    }

    @Test
    public void invalidDimensionsReturnSafeFullTextureWithoutNonFiniteValues() {
        VirtualSensorGeometry.Calculation result = VirtualSensorGeometry.calculate(
                0, -1, 1600, 728, 270, Float.NaN, Float.POSITIVE_INFINITY,
                RenderTargetRole.PREVIEW);

        assertFalse(result.valid);
        assertEquals(0.0f, result.sourceCropRect.left, EPSILON);
        assertEquals(1.0f, result.sourceCropRect.right, EPSILON);
        assertTrue(Float.isFinite(result.sourceAspect));
        assertTrue(Float.isFinite(result.targetAspect));
    }

    @Test
    public void textureCropMatrixContainsOnlyNormalizedCropTransform() {
        VirtualSensorGeometry.NormalizedRect rect =
                new VirtualSensorGeometry.NormalizedRect(0.1f, 0.2f, 0.7f, 0.8f);
        float[] matrix = VirtualSensorGeometry.buildTextureCropMatrix(rect);

        assertEquals(0.6f, matrix[0], EPSILON);
        assertEquals(0.6f, matrix[5], EPSILON);
        assertEquals(0.1f, matrix[12], EPSILON);
        assertEquals(0.2f, matrix[13], EPSILON);
        for (float value : matrix) {
            assertTrue(Float.isFinite(value));
        }
    }
}
