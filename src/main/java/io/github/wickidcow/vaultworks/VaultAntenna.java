package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

/**
 * Passive range upgrade for an adjacent Vault Transmitter.
 */
public class VaultAntenna extends RebarBlock {

    public VaultAntenna(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    public VaultAntenna(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }
}
