package io.github.wickidcow.vaultworks;

import static org.junit.jupiter.api.Assertions.*;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

class VaultCellTitleStateTest {

    @Test void powerAndCargoProduceFourDistinctColors() {
        assertEquals(VaultCellTitleState.OFFLINE, VaultCellTitleState.of(false, false, false));
        assertEquals(NamedTextColor.GRAY, VaultCellTitleState.of(false, false, false).color());
        assertEquals(VaultCellTitleState.POWERED, VaultCellTitleState.of(true, false, false));
        assertEquals(NamedTextColor.YELLOW, VaultCellTitleState.of(true, false, false).color());
        assertEquals(VaultCellTitleState.CARGO, VaultCellTitleState.of(false, true, false));
        assertEquals(TextColor.color(0x55AAFF), VaultCellTitleState.of(false, true, false).color());
        assertEquals(VaultCellTitleState.POWER_AND_CARGO, VaultCellTitleState.of(true, true, false));
        assertEquals(NamedTextColor.GREEN, VaultCellTitleState.of(true, true, false).color());
    }

    @Test void locksTakePrecedenceOverPowerAndCargo() {
        for (boolean powered : new boolean[]{false, true}) {
            for (boolean cargo : new boolean[]{false, true}) {
                assertEquals(VaultCellTitleState.LOCKED, VaultCellTitleState.of(powered, cargo, true));
                assertEquals(NamedTextColor.RED, VaultCellTitleState.of(powered, cargo, true).color());
            }
        }
    }

    @Test void bothNamesAndAllStatusesFitShortTitles() {
        for (String name : new String[]{"Basic Vault Cell", "Adv Vault Cell"}) {
            for (VaultCellTitleState state : VaultCellTitleState.values()) {
                assertTrue(((net.kyori.adventure.text.TextComponent) state.title(name))
                                .content().length() <= 30,
                        () -> name + " " + state);
                assertEquals(state.color(), state.title(name).color());
            }
        }
    }
}
