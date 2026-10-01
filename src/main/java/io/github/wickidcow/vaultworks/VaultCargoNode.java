package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.block.interfaces.CargoRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock;
import io.github.pylonmc.rebar.logistics.LogisticGroupType;
import io.github.pylonmc.rebar.logistics.slot.LogisticSlot;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

/**
 * Rear cargo adapter for one adjacent Vault Cell.
 *
 * The node faces the Vault. Its opposite face is the Rebar cargo connection,
 * allowing a vertical row of nodes/ducts to live behind a six-high Vault wall.
 */
public final class VaultCargoNode extends RebarBlock implements CargoRebarBlock, DirectionalRebarBlock {

    private static final BlockFace[] HORIZONTAL = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    public VaultCargoNode(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);

        BlockFace target = findAdjacentVaultFace();
        if (target == null) {
            target = context.getFacing();
        }

        setFacing(target);
        addCargoLogisticGroup(target.getOppositeFace(), "vault");
        setCargoTransferRate(VaultWorks.instance().policy().transferRate());
    }

    public VaultCargoNode(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    public void postInitialise() {
        super.postInitialise();
        setCargoTransferRate(VaultWorks.instance().policy().transferRate());
        createLogisticGroup("vault", LogisticGroupType.BOTH, List.of(new VaultBulkSlot()));
    }

    private BlockFace findAdjacentVaultFace() {
        BlockFace found = null;
        for (BlockFace face : HORIZONTAL) {
            if (BlockStorage.getAs(BasicVaultCell.class, getBlock().getRelative(face)) == null) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = face;
        }
        return found;
    }

    private BasicVaultCell targetVault() {
        try {
            return BlockStorage.getAs(BasicVaultCell.class, getBlock().getRelative(getFacing()));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private final class VaultBulkSlot implements LogisticSlot {

        @Override
        public ItemStack getItemStack() {
            BasicVaultCell vault = targetVault();
            if (vault == null || !vault.isOperational() || vault.storedItem == null || vault.storedAmount <= 0L) {
                return null;
            }
            return vault.storedItem.asOne();
        }

        @Override
        public long getAmount() {
            BasicVaultCell vault = targetVault();
            if (vault == null || !vault.isOperational() || vault.storedItem == null) {
                return 0L;
            }
            return vault.storedAmount;
        }

        @Override
        public long getMaxAmount(@NotNull ItemStack stack) {
            BasicVaultCell vault = targetVault();
            if (vault == null || !vault.isOperational() || vault.storedItem == null || !vault.canStore(stack)) {
                return 0L;
            }

            if (vault.purgeOverflow) {
                return Long.MAX_VALUE;
            }

            return Math.max(vault.getCapacity(), vault.storedAmount);
        }

        @Override
        public boolean canSet(ItemStack stack, long amount) {
            BasicVaultCell vault = targetVault();
            if (vault == null || !vault.isOperational() || vault.storedItem == null || amount < 0L) {
                return false;
            }

            long current = vault.storedAmount;

            if (amount <= current) {
                return amount == 0L || (stack != null && vault.storedItem.isSimilar(stack));
            }

            if (stack == null || !vault.storedItem.isSimilar(stack) || vault.isVaultCell(stack)) {
                return false;
            }

            long safeLimit = Math.max(vault.getCapacity(), current);
            return amount <= safeLimit || vault.purgeOverflow;
        }

        @Override
        public void set(ItemStack stack, long amount) {
            BasicVaultCell vault = targetVault();
            if (vault == null) {
                return;
            }

            vault.storedAmount = VaultStorageMath.applyProposedAmount(
                    vault.storedAmount,
                    amount,
                    vault.getCapacity(),
                    vault.purgeOverflow
            );
            vault.refreshGuiItems();
        }
    }
}
