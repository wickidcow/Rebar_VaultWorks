package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.VirtualInventoryRebarBlock;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.inventory.VirtualInventory;

/** The inventory belongs to this block. Rebar persists it and drops its contents on break. */
public class BasicVaultCell extends RebarBlock implements VirtualInventoryRebarBlock, GuiRebarBlock {
    protected final VirtualInventory contents = new VirtualInventory(54);

    public BasicVaultCell(Block block, BlockCreateContext context) { super(block, context); }
    public BasicVaultCell(Block block, PersistentDataContainer pdc) { super(block, pdc); }

    @Override public Map<String, VirtualInventory> getVirtualInventories() { return Map.of("contents", contents); }

    @Override public Gui createGui() {
        return Gui.builder().setStructure(
                "v v v v v v v v v", "v v v v v v v v v", "v v v v v v v v v",
                "v v v v v v v v v", "v v v v v v v v v", "v v v v v v v v v")
                .addIngredient('v', contents).build();
    }

    @Override public Component getGuiTitle() { return Component.text("Basic Vault Cell"); }
}
