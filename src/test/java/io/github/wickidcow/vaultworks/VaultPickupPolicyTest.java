package io.github.wickidcow.vaultworks;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class VaultPickupPolicyTest {
    @Test void deliberateSurvivalSneakContainmentDollyCanTriggerFastPickup() {
        assertTrue(VaultPickupPolicy.fastBreak(true, true, true, true, false));
    }

    @Test void rejectsEverythingElseAndNeverBypassesRecoveryLock() {
        assertFalse(VaultPickupPolicy.fastBreak(false, true, true, true, false));
        assertFalse(VaultPickupPolicy.fastBreak(true, false, true, true, false));
        assertFalse(VaultPickupPolicy.fastBreak(true, true, false, true, false));
        assertFalse(VaultPickupPolicy.fastBreak(true, true, true, false, false));
        assertFalse(VaultPickupPolicy.fastBreak(true, true, true, true, true));
    }
}
