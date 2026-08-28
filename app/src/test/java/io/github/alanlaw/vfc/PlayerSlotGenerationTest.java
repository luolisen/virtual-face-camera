package io.github.alanlaw.vfc;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerSlotGenerationTest {
    @Test
    public void lateSourceCallbackCannotWinAfterNewSwitch() {
        PlayerSlotGeneration gate = new PlayerSlotGeneration();
        long sourceA = gate.begin();
        long sourceB = gate.begin();

        assertFalse(gate.isCurrent(sourceA));
        assertTrue(gate.isCurrent(sourceB));
    }
}
