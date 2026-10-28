package io.github.wickidcow.vaultworks;

import static org.junit.jupiter.api.Assertions.*;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

class VaultCellTitleStateTest {

    @Test void terminalConnectedAndPoweredTakesPriorityOverCargo() {
        for (boolean cargo : new boolean[]{false, true}) {
            VaultCellTitleState state = VaultCellTitleState.of(true, cargo, true, false);
            assertEquals(VaultCellTitleState.TERMINAL, state);
            assertEquals(NamedTextColor.DARK_GREEN, state.color());
        }
    }

    @Test void powerAndCargoProduceOtherFourColors() {
        assertEquals(VaultCellTitleState.OFFLINE, VaultCellTitleState.of(false, false, false, false));
        assertEquals(NamedTextColor.GRAY, VaultCellTitleState.of(false, false, false, false).color());
        assertEquals(VaultCellTitleState.POWERED, VaultCellTitleState.of(true, false, false, false));
        assertEquals(NamedTextColor.YELLOW, VaultCellTitleState.of(true, false, false, false).color());
        assertEquals(VaultCellTitleState.CARGO, VaultCellTitleState.of(false, true, false, false));
        assertEquals(TextColor.color(0x55AAFF), VaultCellTitleState.of(false, true, false, false).color());
        assertEquals(VaultCellTitleState.POWER_AND_CARGO, VaultCellTitleState.of(true, true, false, false));
        assertEquals(NamedTextColor.GREEN, VaultCellTitleState.of(true, true, false, false).color());
        // An unpowered terminal connection is not operational, so do not color green.
        assertEquals(VaultCellTitleState.OFFLINE, VaultCellTitleState.of(false, false, true, false));
    }

    @Test void locksTakePrecedenceOverPowerAndBothConnections() {
        for (boolean powered : new boolean[]{false, true}) {
            for (boolean cargo : new boolean[]{false, true}) {
                for (boolean terminal : new boolean[]{false, true}) {
                    assertEquals(VaultCellTitleState.LOCKED,
                            VaultCellTitleState.of(powered, cargo, terminal, true));
                }
            }
        }
        assertEquals(NamedTextColor.RED, VaultCellTitleState.LOCKED.color());
    }

    @Test void bothNamesAndAllStatusesFitShortTitles() {
        for (String name : new String[]{"Basic Vault Cell", "Adv Vault Cell"}) {
            for (VaultCellTitleState state : VaultCellTitleState.values()) {
                assertTrue(((net.kyori.adventure.text.TextComponent) state.title(name))
                                .content().length() <= 30, () -> name + " " + state);
                assertEquals(state.color(), state.title(name).color());
            }
        }
    }
}
