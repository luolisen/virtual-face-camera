package io.github.zensu357.camswap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VideoAspectLayoutTest {
    private static final float EPSILON = 0.0001f;

    @Test
    public void fitPortraitIntoLandscapeUsesSideBars() {
        VideoAspectLayout.Layout layout = VideoAspectLayout.calculate(
                1080, 1920, 1920, 1080, 0, ConfigManager.ASPECT_MODE_FIT);

        assertEquals(0.5625f, layout.sourceAspect, EPSILON);
        assertEquals(0.31640625f, layout.scaleX, EPSILON);
        assertEquals(1.0f, layout.scaleY, EPSILON);
    }

    @Test
    public void fitLandscapeIntoPortraitUsesTopAndBottomBars() {
        VideoAspectLayout.Layout layout = VideoAspectLayout.calculate(
                1920, 1080, 1080, 1920, 0, ConfigManager.ASPECT_MODE_FIT);

        assertEquals(1.7777778f, layout.sourceAspect, EPSILON);
        assertEquals(1.0f, layout.scaleX, EPSILON);
        assertEquals(0.31640625f, layout.scaleY, EPSILON);
    }

    @Test
    public void cropFillsTargetWithoutStretching() {
        VideoAspectLayout.Layout layout = VideoAspectLayout.calculate(
                1080, 1920, 1920, 1080, 0, ConfigManager.ASPECT_MODE_CROP);

        assertEquals(1.0f, layout.scaleX, EPSILON);
        assertEquals(3.1604939f, layout.scaleY, EPSILON);
    }

    @Test
    public void quarterTurnSwapsEffectiveSourceDimensions() {
        VideoAspectLayout.Layout layout = VideoAspectLayout.calculate(
                1920, 1080, 1920, 1080, 90, ConfigManager.ASPECT_MODE_FIT);

        assertEquals(1080, layout.effectiveSourceWidth);
        assertEquals(1920, layout.effectiveSourceHeight);
        assertEquals(0.5625f, layout.sourceAspect, EPSILON);
    }
}
