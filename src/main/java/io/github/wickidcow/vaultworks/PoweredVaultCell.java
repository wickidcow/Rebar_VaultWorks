package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.block.interfaces.CargoRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.SimpleElectricRebarBlock;
import io.github.pylonmc.rebar.electricity.nodes.ElectricNodeType;
import io.github.pylonmc.rebar.logistics.LogisticGroupType;
import io.github.pylonmc.rebar.logistics.slot.LogisticSlot;
import io.github.pylonmc.rebar.logistics.slot.VirtualInventoryLogisticSlot;
import java.util.ArrayList;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;

/** Electricity gates automation; direct inventory access is always available. No addon ticking task. */
public final class PoweredVaultCell extends BasicVaultCell implements SimpleElectricRebarBlock, CargoRebarBlock {
    public PoweredVaultCell(Block block, BlockCreateContext context) {
        super(block, context);
        createSimpleElectricPort(ElectricNodeType.CONSUMER, BlockFace.UP);
        setRequiredPower(VaultWorks.instance().policy().watts());
        addCargoLogisticGroup(BlockFace.WEST, "input");
        addCargoLogisticGroup(BlockFace.EAST, "output");
        setCargoTransferRate(VaultWorks.instance().policy().transferRate());
    }

    public PoweredVaultCell(Block block, PersistentDataContainer pdc) { super(block, pdc); }

    @Override public void postInitialise() {
        super.postInitialise();
        setRequiredPower(VaultWorks.instance().policy().watts());
        setCargoTransferRate(VaultWorks.instance().policy().transferRate());
        var slots = new ArrayList<LogisticSlot>(contents.getSize());
        for (int index = 0; index < contents.getSize(); index++) {
            slots.add(new PoweredSlot(index));
        }
        createLogisticGroup("input", LogisticGroupType.INPUT, slots);
        createLogisticGroup("output", LogisticGroupType.OUTPUT, slots);
    }

    @Override public Component getGuiTitle() {
        return Component.text("Powered Vault Cell — " + (isPowered() ? "Online" : "No power"));
    }

    private final class PoweredSlot extends VirtualInventoryLogisticSlot {
        PoweredSlot(int slot) { super(contents, slot); }

        @Override public boolean canSet(ItemStack stack, long amount) {
            return isPowered() && super.canSet(stack == null ? null : stack.clone(), amount);
        }

        // Returning a copy prevents Rebar's transfer setter from mutating our stored source stack.
        @Override public ItemStack getItemStack() {
            if (!isPowered()) return null;
            ItemStack stack = super.getItemStack();
            return stack == null ? null : stack.clone();
        }

        @Override public long getMaxAmount(ItemStack stack) {
            return isPowered() ? super.getMaxAmount(stack) : 0;
        }

        @Override public void set(ItemStack stack, long amount) {
            // Rebar commits synchronously after canSet checks on both slots. Do not veto half a transfer here.
            super.set(stack == null ? null : stack.clone(), amount);
        }
    }
}
