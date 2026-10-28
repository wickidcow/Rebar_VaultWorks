package io.github.wickidcow.vaultworks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Compact color-coded GUI title. Cargo means an attached rear Cargo Node,
 * not necessarily an end-to-end network route. A recovery/identity lock
 * overrides normal colors so operators cannot mistake the cell for healthy.
 */
enum VaultCellTitleState {
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

    static VaultCellTitleState of(boolean powered, boolean attachedCargo, boolean locked) {
        if (locked) return LOCKED;
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
