package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.block.interfaces.CargoRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.SimpleElectricRebarBlock;
import io.github.pylonmc.rebar.electricity.nodes.ElectricNodeType;
import io.github.pylonmc.rebar.logistics.LogisticGroupType;
import io.github.pylonmc.rebar.logistics.slot.LogisticSlot;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

/**
 * The automated Vault Cell tier. Rebar electricity gates cargo access while
 * manual GUI access and the portable stored state remain available at all times.
 */
public final class PoweredVaultCell extends BasicVaultCell implements SimpleElectricRebarBlock, CargoRebarBlock {

    public PoweredVaultCell(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setVaultShell(true, context.getFacing());
        createSimpleElectricPort(ElectricNodeType.CONSUMER, BlockFace.UP);
        setRequiredPower(VaultWorks.instance().policy().watts());
        addCargoLogisticGroup(BlockFace.WEST, "input");
        addCargoLogisticGroup(BlockFace.EAST, "output");
        setCargoTransferRate(VaultWorks.instance().policy().transferRate());
    }

    public PoweredVaultCell(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        setVaultShell(true, null);
    }

    @Override
    protected String capacityConfigKey() {
        return "storage.powered-capacity";
    }

    @Override
    public void postInitialise() {
        super.postInitialise();
        setRequiredPower(VaultWorks.instance().policy().watts());
        setCargoTransferRate(VaultWorks.instance().policy().transferRate());

        createLogisticGroup("input", LogisticGroupType.INPUT, List.of(new BulkSlot(true)));
        createLogisticGroup("output", LogisticGroupType.OUTPUT, List.of(new BulkSlot(false)));
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return Component.text("Powered Vault Cell — " + (isPowered() ? "Online" : "No power"));
    }

    private final class BulkSlot implements LogisticSlot {
        private final boolean input;

        private BulkSlot(boolean input) {
            this.input = input;
        }

        @Override
        public ItemStack getItemStack() {
            if (!isPowered() || storedItem == null) {
                return null;
            }
            if (!input && storedAmount <= 0L) {
                return null;
            }
            return storedItem.asOne();
        }

        @Override
        public long getAmount() {
            if (!isPowered() || storedItem == null) {
                return 0L;
            }
            return storedAmount;
        }

        @Override
        public long getMaxAmount(@NotNull ItemStack stack) {
            if (!isPowered() || storedItem == null || !canStore(stack)) {
                return 0L;
            }

            // When purge is enabled the target deliberately advertises effectively
            // unlimited acceptance; set(...) clamps the persisted amount to capacity,
            // so the excess transferred by cargo is discarded.
            if (input && purgeOverflow) {
                return Long.MAX_VALUE;
            }

            // Never report a maximum below an already-preserved amount if an admin
            // lowered the configured capacity. This keeps old stock withdrawable.
            return Math.max(getCapacity(), storedAmount);
        }

        @Override
        public boolean canSet(ItemStack stack, long amount) {
            if (!isPowered() || amount < 0L || storedItem == null) {
                return false;
            }

            if (input) {
                if (stack == null || !storedItem.isSimilar(stack) || isVaultCell(stack)) {
                    return false;
                }
                if (amount < storedAmount) {
                    return false;
                }

                long safeLimit = Math.max(getCapacity(), storedAmount);
                return amount <= safeLimit || purgeOverflow;
            }

            // Output is source-only: Rebar may only reduce the authoritative amount.
            if (amount > storedAmount) {
                return false;
            }
            return amount == 0L || (stack != null && storedItem.isSimilar(stack));
        }

        @Override
        public void set(ItemStack stack, long amount) {
            // Rebar performs source and target canSet checks before either side is
            // committed. Do not re-check power here and risk applying half a transfer.
            if (input) {
                storedAmount = purgeOverflow ? Math.min(amount, getCapacity()) : amount;
            } else {
                storedAmount = Math.max(0L, amount);
            }
        }
    }
}
