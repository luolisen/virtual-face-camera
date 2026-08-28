package io.github.alanlaw.vfc;

/**
 * Generation gate for one asynchronous MediaPlayer slot.
 *
 * <p>A new source switch invalidates every callback issued by an earlier
 * switch. This class is deliberately Android-free so the race can be tested
 * without a device.</p>
 */
public final class PlayerSlotGeneration {
    private long currentGeneration;

    public synchronized long begin() {
        currentGeneration++;
        return currentGeneration;
    }

    public synchronized boolean isCurrent(long generation) {
        return generation == currentGeneration;
    }

    public synchronized long current() {
        return currentGeneration;
    }
}
