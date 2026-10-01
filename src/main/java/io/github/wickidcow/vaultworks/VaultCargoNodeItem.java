package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.item.RebarItem;
import net.kyori.adventure.text.Component;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Vault;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Placement rules for the clean rear-service cargo layout.
 */
public final class VaultCargoNodeItem extends RebarItem {

    private static final BlockFace[] HORIZONTAL = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    public VaultCargoNodeItem(@NotNull ItemStack stack) {
        super(stack);
    }

    @Override
    public boolean prePlace(@NotNull BlockCreateContext context) {
        if (!super.prePlace(context)) {
            return false;
        }

        BasicVaultCell target = null;
        BlockFace targetFace = null;

        for (BlockFace face : HORIZONTAL) {
            BasicVaultCell candidate;
            try {
                candidate = BlockStorage.getAs(BasicVaultCell.class, context.getBlock().getRelative(face));
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            if (candidate == null) {
                continue;
            }

            if (target != null) {
                return deny(context, "A Vault Cargo Node must target exactly one adjacent Vault Cell.");
            }

            target = candidate;
            targetFace = face;
        }

        if (target == null || targetFace == null) {
            return deny(context, "Place the Vault Cargo Node directly behind a Vault Cell.");
        }

        if (!(target.getBlock().getBlockData() instanceof Vault vault)) {
            return deny(context, "The adjacent Vault Cell has an invalid shell state.");
        }

        // If the Vault faces SOUTH, its rear block is NORTH of it. From the
        // Cargo Node at NORTH, the face pointing toward the Vault is SOUTH.
        // Therefore targetFace must equal the Vault's front-facing direction.
        if (vault.getFacing() != targetFace) {
            return deny(context, "Vault Cargo Nodes may only be placed on the rear service side of a Vault Cell.");
        }

        return true;
    }

    private boolean deny(BlockCreateContext context, String message) {
        if (context.getPlayer() != null) {
            context.getPlayer().sendMessage(Component.text(message));
        }
        return false;
    }
}
