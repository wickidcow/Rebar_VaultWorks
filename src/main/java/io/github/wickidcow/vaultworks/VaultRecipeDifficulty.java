package io.github.wickidcow.vaultworks;

import java.util.List;
import java.util.Locale;

/** Crafting progression mode; changing the mode never changes existing stored cells. */
enum VaultRecipeDifficulty {
    CLASSIC,
    BALANCED,
    HARD;

    static VaultRecipeDifficulty parse(String value) {
        try {
            return valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid recipes.difficulty '" + value
                            + "'; expected CLASSIC, BALANCED or HARD.", exception
            );
        }
    }

    boolean requiresPylon() {
        return this != CLASSIC;
    }

    List<String> requiredPylonItems() {
        return switch (this) {
            case CLASSIC -> List.of();
            case BALANCED -> List.of("copper_sheet", "bronze_sheet", "steel_sheet", "cargo_controller");
            case HARD -> List.of("copper_sheet", "bronze_sheet", "steel_sheet", "cargo_controller",
                    "capacitor_1_kj", "igneous_composite");
        };
    }
}
