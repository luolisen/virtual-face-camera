package io.github.alanlaw.vfc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VirtualSensorGeometryTest {
    private static final float EPSILON = 0.0001f;

    @Test
    public void matchingSixteenByNineOutputKeepsTheWholeSource() {
        VirtualSensorGeometry.Calculation result = VirtualSensorGeometry.calculate(
                1920, 1080, 1600, 900, 0, 0.5f, 0.5f, RenderTargetRole.PREVIEW);

        assertTrue(result.valid);
        assertEquals(1.0f, result.sourceCropRect.width(), EPSILON);
        assertEquals(1.0f, result.sourceCropRect.height(), EPSILON);
        assertEquals(1.7777778f, result.sourceAspect, EPSILON);
        assertEquals(1.7777778f, result.targetAspect, EPSILON);
    }

    @Test
    public void dynamicLandscapeTargetCropsTallSourceWithoutStretching() {
        VirtualSensorGeometry.Calculation result = VirtualSensorGeometry.calculate(
                1080, 1920, 1920, 1080, 0, 0.5f, 0.5f, RenderTargetRole.PREVIEW);

        assertTrue(result.valid);
        assertEquals(1080, result.logicalSensorWidth);
        assertEquals(1920, result.logicalSensorHeight);
        assertEquals(1.0f, result.sourceCropRect.width(), EPSILON);
        assertEquals(0.31640625f, result.sourceCropRect.height(), EPSILON);
        assertEquals(0.0f, result.sourceCropRect.left, EPSILON);
        assertEquals(0.341796875f, result.sourceCropRect.top, EPSILON);
    }

    @Test
    public void dynamicPortraitTargetCropsWideSourceWithoutStretching() {
        VirtualSensorGeometry.Calculation result = VirtualSensorGeometry.calculate(
                1920, 1080, 1080, 1920, 0, 0.5f, 0.5f, RenderTargetRole.PREVIEW);

        assertTrue(result.valid);
        assertEquals(0.31640625f, result.sourceCropRect.width(), EPSILON);
        assertEquals(1.0f, result.sourceCropRect.height(), EPSILON);
        assertEquals(0.341796875f, result.sourceCropRect.left, EPSILON);
        assertEquals(0.0f, result.sourceCropRect.top, EPSILON);
    }

    @Test
    public void commonPortraitAndFourByThreeTargetsRemainFiniteAndClamped() {
        VirtualSensorGeometry.Calculation portrait = VirtualSensorGeometry.calculate(
                1920, 1080, 720, 1280, 0, 0.5f, 0.5f, RenderTargetRole.PREVIEW);
        VirtualSensorGeometry.Calculation fourByThree = VirtualSensorGeometry.calculate(
                1920, 1080, 1440, 1080, 0, 0.0f, 1.0f, RenderTargetRole.PREVIEW);

        assertEquals(0.31640625f, portrait.sourceCropRect.width(), EPSILON);
        assertEquals(1.0f, portrait.sourceCropRect.height(), EPSILON);
        assertEquals(0.75f, fourByThree.sourceCropRect.width(), EPSILON);
        assertEquals(1.0f, fourByThree.sourceCropRect.height(), EPSILON);
        for (VirtualSensorGeometry.Calculation result : new VirtualSensorGeometry.Calculation[] {
                portrait, fourByThree
        }) {
            assertTrue(result.valid);
            assertTrue(result.sourceCropRect.left >= 0.0f);
            assertTrue(result.sourceCropRect.top >= 0.0f);
            assertTrue(result.sourceCropRect.right <= 1.0f);
            assertTrue(result.sourceCropRect.bottom <= 1.0f);
            assertTrue(Float.isFinite(result.sourceAspect));
            assertTrue(Float.isFinite(result.targetAspect));
        }
    }

    @Test
    public void quarterTurnSwapsLogicalSensorDimensionsAndSourceCropAxes() {
        VirtualSensorGeometry.Calculation result = VirtualSensorGeometry.calculate(
                1080, 1920, 1920, 1080, 90, 0.5f, 0.5f, RenderTargetRole.PREVIEW);

        assertTrue(result.valid);
        assertEquals(1920, result.logicalSensorWidth);
        assertEquals(1080, result.logicalSensorHeight);
        assertEquals(1.0f, result.logicalCropRect.width(), EPSILON);
        assertEquals(1.0f, result.logicalCropRect.height(), EPSILON);
        assertEquals(1.0f, result.sourceCropRect.width(), EPSILON);
        assertEquals(1.0f, result.sourceCropRect.height(), EPSILON);
    }

    @Test
    public void readerUsesRawTargetAndDoesNotUseHostOrientation() {
        VirtualSensorGeometry.Calculation preview = VirtualSensorGeometry.calculate(
                1080, 1920, 1600, 728, 0, 0.5f, 0.5f, RenderTargetRole.PREVIEW);
        VirtualSensorGeometry.Calculation reader = VirtualSensorGeometry.calculate(
                1080, 1920, 1600, 728, 0, 0.5f, 0.5f, RenderTargetRole.READER);

        assertEquals(preview.targetAspect, reader.targetAspect, EPSILON);
        assertEquals(preview.sourceCropRect.width(), reader.sourceCropRect.width(), EPSILON);
        assertEquals(preview.sourceCropRect.height(), reader.sourceCropRect.height(), EPSILON);
        assertEquals(RenderTargetRole.READER, reader.role);
    }

    @Test
    public void anchorsClampToTheMovableCropArea() {
        VirtualSensorGeometry.Calculation result = VirtualSensorGeometry.calculate(
                1080, 1920, 1920, 1080, 0, 0.0f, 1.0f, RenderTargetRole.PREVIEW);

        assertEquals(0.5f, result.logicalAnchorU, EPSILON);
        assertEquals(0.841796875f, result.logicalAnchorV, EPSILON);
        assertEquals(0.0f, result.sourceCropRect.left, EPSILON);
        assertEquals(0.68359375f, result.sourceCropRect.top, EPSILON);
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
