package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.item.RebarItem;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Placement rules for portable Vault Cells.
 *
 * A Vault Cell may only be placed in a contiguous vertical column above a
 * Vault Power Base. The sixth cell is the highest valid position.
 */
public final class VaultCellItem extends RebarItem {

    public VaultCellItem(@NotNull ItemStack stack) {
        super(stack);
    }

    @Override
    public boolean prePlace(@NotNull BlockCreateContext context) {
        if (!super.prePlace(context)) {
            return false;
        }

        Block cursor = context.getBlock().getRelative(BlockFace.DOWN);
        int cellsBelow = 0;

        // One extra step is needed so a seventh attempted cell can find the base
        // after six existing cells and be rejected with the correct reason.
        for (int depth = 1; depth <= BasicVaultCell.MAX_COLUMN_HEIGHT + 1; depth++) {
            RebarBlock rebarBlock;
            try {
                rebarBlock = BlockStorage.get(cursor);
            } catch (IllegalArgumentException ignored) {
                return deny(context, "Vault Cells must be placed above a loaded Vault Power Base.");
            }

            if (rebarBlock instanceof VaultPowerBase) {
                if (cellsBelow < BasicVaultCell.MAX_COLUMN_HEIGHT) {
                    return true;
                }
                return deny(context, "A Vault Power Base supports at most "
                        + BasicVaultCell.MAX_COLUMN_HEIGHT + " Vault Cells.");
            }

            if (!(rebarBlock instanceof BasicVaultCell)) {
                return deny(context, "Place the Vault Cell directly above a Vault Power Base or another Vault Cell.");
            }

            cellsBelow++;
            cursor = cursor.getRelative(BlockFace.DOWN);
        }

        return deny(context, "A Vault Power Base supports at most "
                + BasicVaultCell.MAX_COLUMN_HEIGHT + " Vault Cells.");
    }

    private boolean deny(BlockCreateContext context, String message) {
        if (context.getPlayer() != null) {
            context.getPlayer().sendMessage(Component.text(message));
        }
        return false;
    }
}
