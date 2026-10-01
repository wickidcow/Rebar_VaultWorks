package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

/**
 * Endgame antenna that permits a bound Wireless Vault Terminal to reach a
 * loaded transmitter from another dimension/world.
 *
 * It still does not chunk-load the source network.
 */
public final class DimensionalVaultAntenna extends VaultAntenna {

    public DimensionalVaultAntenna(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    public DimensionalVaultAntenna(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }
}
