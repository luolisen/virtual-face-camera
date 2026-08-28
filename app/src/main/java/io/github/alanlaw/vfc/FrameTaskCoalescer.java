package io.github.alanlaw.vfc;

/**
 * Small synchronized state machine for one-frame render-task coalescing.
 *
 * <p>The state machine never performs rendering and never loops. A caller
 * posts at most one handler task while {@link #taskScheduled} is true. If a
 * frame arrives while that task is drawing, {@link #finishTask()} asks the
 * caller to post exactly one follow-up task after the current task returns.</p>
 */
public final class FrameTaskCoalescer {
    private boolean taskScheduled;
    private boolean framePending;

    /** Mark a frame available; return true when a new task must be posted. */
    public synchronized boolean onFrameAvailable() {
        framePending = true;
        if (taskScheduled) {
            return false;
        }
        taskScheduled = true;
        return true;
    }

    /** Consume the pending bit at the beginning of the one-frame task. */
    public synchronized void beginTask() {
        framePending = false;
    }

    /**
     * Finish the current task. The scheduled bit remains set when a frame
     * arrived during drawing, so another task can be posted without a race.
     */
    public synchronized boolean finishTask() {
        if (framePending) {
            return true;
        }
        taskScheduled = false;
        return false;
    }

    /** Cancel pending work when the handler or renderer is being released. */
    public synchronized void cancel() {
        taskScheduled = false;
        framePending = false;
    }

    public synchronized boolean isTaskScheduled() {
        return taskScheduled;
    }

    public synchronized boolean isFramePending() {
        return framePending;
    }
}
