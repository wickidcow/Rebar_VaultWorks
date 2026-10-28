package io.github.wickidcow.vaultworks;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class VaultHologramPolicyTest {
    @Test void holoRequiresUserTogglePowerRegistrationAndStock() {
        assertTrue(VaultHologramPolicy.visible(true, true, true, 1L));
        assertTrue(VaultHologramPolicy.visible(true, true, true, Long.MAX_VALUE));
        assertFalse(VaultHologramPolicy.visible(false, true, true, 64L));
        assertFalse(VaultHologramPolicy.visible(true, false, true, 64L));
        assertFalse(VaultHologramPolicy.visible(true, true, false, 64L));
        assertFalse(VaultHologramPolicy.visible(true, true, true, 0L));
        assertFalse(VaultHologramPolicy.visible(true, true, true, -1L));
    }

    @Test void outlineRequiresVisibleHologramAndTerminalLink() {
        assertFalse(VaultHologramPolicy.glowing(false, false));
        assertFalse(VaultHologramPolicy.glowing(false, true));
        assertFalse(VaultHologramPolicy.glowing(true, false));
        assertTrue(VaultHologramPolicy.glowing(true, true));
    }
}
