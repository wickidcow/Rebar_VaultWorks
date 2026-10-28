package io.github.wickidcow.vaultworks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class VaultCursorTransferMathTest {
    @Test void rightClickDepositsJustOne() {
        assertEquals(1, VaultCursorTransferMath.depositAmount(64, 4, 100, true));
        assertEquals(64, VaultCursorTransferMath.depositAmount(64, 4, 100, false));
    }

    @Test void fullOrReducedCapacityNeverDeletesItems() {
        assertEquals(2, VaultCursorTransferMath.depositAmount(64, 98, 100, false));
        assertEquals(0, VaultCursorTransferMath.depositAmount(64, 100, 100, false));
        assertEquals(0, VaultCursorTransferMath.depositAmount(64, 150, 100, true));
        assertEquals(0, VaultCursorTransferMath.depositAmount(0, 1, 100, false));
    }

    @Test void cursorPickupNeverExceedsCustomItemStackLimits() {
        assertEquals(16, VaultCursorTransferMath.withdrawAmount(100, 0, 16, false));
        assertEquals(1, VaultCursorTransferMath.withdrawAmount(100, 15, 16, false));
        assertEquals(1, VaultCursorTransferMath.withdrawAmount(100, 0, 64, true));
        assertEquals(0, VaultCursorTransferMath.withdrawAmount(100, 64, 64, false));
        assertEquals(0, VaultCursorTransferMath.withdrawAmount(0, 0, 64, false));
        assertEquals(0, VaultCursorTransferMath.withdrawAmount(100, 0, 0, false));
    }
}
