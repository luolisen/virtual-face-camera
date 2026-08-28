package io.github.alanlaw.vfc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class RendererStateTest {
    @Test
    public void renderConfigurationPublishesOneCompleteGeneration() {
        RendererState initial = RendererState.initial(RenderTargetRole.PREVIEW);
        RendererState next = initial.withRenderConfiguration(
                90, ConfigManager.ASPECT_MODE_DYNAMIC, null,
                ConfigManager.DEFAULT_VIEWPORT_MOVE_STEP_PERCENT,
                new HostWindowGeometry.Snapshot(1080, 2376, 0));

        assertNotSame(initial, next);
        assertEquals(initial.generation + 1L, next.generation);
        assertEquals(90, next.rotationDegrees);
        assertEquals(ConfigManager.ASPECT_MODE_DYNAMIC, next.aspectMode);
        assertEquals(1080, next.hostWindowGeometry.getWidth());
        assertEquals(2376, next.hostWindowGeometry.getHeight());
    }

    @Test
    public void sourceAndRotationRemainInOneSnapshot() {
        RendererState state = RendererState.initial(RenderTargetRole.READER)
                .withSourceSize(1920, 1080)
                .withRotation(270);

        assertEquals(1920, state.sourceWidth);
        assertEquals(1080, state.sourceHeight);
        assertEquals(270, state.rotationDegrees);
        assertEquals(RenderTargetRole.READER, state.role);
    }
}
