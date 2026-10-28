package io.github.wickidcow.vaultworks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Compact GUI title. Network means a loaded path from this cell's Power Base
 * to a VaultWorks Terminal (including a Transmitter acting as a terminal),
 * not a Rebar Cargo duct route. A lock always overrides other colors.
 */
enum VaultCellTitleState {
    TERMINAL("Terminal", NamedTextColor.DARK_GREEN),
    POWER_AND_CARGO("Pwr+Cargo", NamedTextColor.GREEN),
    POWERED("Powered", NamedTextColor.YELLOW),
    CARGO("Cargo", TextColor.color(0x55AAFF)),
    OFFLINE("Offline", NamedTextColor.GRAY),
    LOCKED("Locked", NamedTextColor.RED);

    private final String label;
    private final TextColor color;

    VaultCellTitleState(String label, TextColor color) {
        this.label = label;
        this.color = color;
    }

    static VaultCellTitleState of(boolean powered, boolean attachedCargo, boolean hasTerminal, boolean locked) {
        if (locked) return LOCKED;
        if (powered && hasTerminal) return TERMINAL;
        if (powered && attachedCargo) return POWER_AND_CARGO;
        if (powered) return POWERED;
        if (attachedCargo) return CARGO;
        return OFFLINE;
    }

    String label() {
        return label;
    }

    TextColor color() {
        return color;
    }

    Component title(String shortName) {
        return Component.text(shortName + " • " + label, color);
    }
}
