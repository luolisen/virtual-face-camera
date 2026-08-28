package io.github.alanlaw.vfc;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FrameTaskCoalescerTest {
    @Test
    public void coalescesFramesAndSchedulesOneFollowUpAfterDraw() {
        FrameTaskCoalescer coalescer = new FrameTaskCoalescer();

        assertTrue(coalescer.onFrameAvailable());
        assertFalse(coalescer.onFrameAvailable());
        coalescer.beginTask();

        assertFalse(coalescer.onFrameAvailable());
        assertTrue(coalescer.finishTask());
        assertTrue(coalescer.isTaskScheduled());
        coalescer.beginTask();
        assertFalse(coalescer.finishTask());
        assertFalse(coalescer.isTaskScheduled());
    }

    @Test
    public void frameArrivingAfterTaskFinishesSchedulesAFreshTask() {
        FrameTaskCoalescer coalescer = new FrameTaskCoalescer();

        assertTrue(coalescer.onFrameAvailable());
        coalescer.beginTask();
        assertFalse(coalescer.finishTask());
        assertTrue(coalescer.onFrameAvailable());
    }

    @Test
    public void cancellationReleasesScheduledState() {
        FrameTaskCoalescer coalescer = new FrameTaskCoalescer();
        assertTrue(coalescer.onFrameAvailable());
        coalescer.cancel();
        assertFalse(coalescer.isTaskScheduled());
        assertFalse(coalescer.isFramePending());
        assertTrue(coalescer.onFrameAvailable());
    }
}
