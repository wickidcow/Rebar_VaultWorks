package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

/**
 * Passive explicit topology block for VaultWorks indexing.
 *
 * It performs no ticking and owns no items. The Vault Index traverses loaded
 * Vault Link Cables and Vault Power Bases only when a player asks for a snapshot.
 */
public final class VaultLinkCable extends RebarBlock {

    public VaultLinkCable(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    public VaultLinkCable(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }
}
