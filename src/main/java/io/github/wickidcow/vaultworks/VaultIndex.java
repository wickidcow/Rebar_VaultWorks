package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

/**
 * Read-only explicit-network index.
 *
 * This is intentionally not a storage owner and does not move items. It proves
 * the topology/index boundary before terminal mutation is added.
 */
public final class VaultIndex extends RebarBlock implements GuiRebarBlock {

    private final List<IndexButton> dynamicButtons = new ArrayList<>();

    public VaultIndex(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    public VaultIndex(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    public @NotNull Gui createGui() {
        dynamicButtons.clear();

        OverviewButton overview = remember(new OverviewButton());

        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# # # o # # # # #",
                        "# # t # # # r # #",
                        "# # # # # # # # #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.backgroundBlack())
                .addIngredient('o', overview)
                .addIngredient('t', new TopologyButton())
                .addIngredient('r', new RefreshButton())
                .build();
    }

    private <T extends IndexButton> T remember(T button) {
        dynamicButtons.add(button);
        return button;
    }

    private void refreshDynamicButtons() {
        dynamicButtons.forEach(IndexButton::refresh);
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return Component.text("Vault Index — Network Overview");
    }

    private abstract class IndexButton extends AbstractItem {
        void refresh() {
            notifyWindows();
        }
    }

    private final class OverviewButton extends IndexButton {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            VaultNetworkSnapshot snapshot = VaultNetworkScanner.scan(VaultIndex.this);
            int maxNodes = VaultWorks.instance().storagePolicy().maxNetworkNodes();

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Network nodes: " + BasicVaultCell.format(snapshot.networkNodes())
                    + " / " + BasicVaultCell.format(maxNodes)));
            lore.add(Component.text("Columns online: " + snapshot.onlineColumns()
                    + " / " + snapshot.columns()));
            lore.add(Component.text("Vault Cells: " + snapshot.vaults()
                    + " (" + snapshot.registeredVaults() + " registered)"));
            lore.add(Component.text("Distinct stored item types: " + snapshot.itemTypes()));
            lore.add(Component.text("Stored: " + BasicVaultCell.format(snapshot.totalStored())
                    + " / " + BasicVaultCell.format(snapshot.totalCapacity())));
            lore.add(Component.text("Currently accessible: "
                    + BasicVaultCell.format(snapshot.accessibleStored())));

            if (snapshot.legacyRecoveryVaults() > 0) {
                lore.add(Component.text("Legacy recovery required: "
                        + snapshot.legacyRecoveryVaults() + " Vault(s)"));
            }
            if (snapshot.identityConflictVaults() > 0) {
                lore.add(Component.text("LOCKED identity conflicts: "
                        + snapshot.identityConflictVaults() + " Vault(s)"));
            }
            if (snapshot.truncated()) {
                lore.add(Component.text("Network traversal hit the configured safety limit."));
            }

            return ItemStackBuilder.of(Material.COMPASS)
                    .name(Component.text("Vault Network Overview"))
                    .lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        }
    }

    private final class TopologyButton extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(Material.COPPER_GRATE)
                    .name(Component.text("Explicit Vault Links"))
                    .lore(
                            Component.text("Index only follows loaded VaultWorks topology."),
                            Component.text("Connect with Vault Link Cables and Vault Power Bases."),
                            Component.text("Adjacent Power Bases connect directly."),
                            Component.text("No world scan and no forced chunk loading."),
                            Component.text("This foundation is read-only; it does not move items.")
                    );
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        }
    }

    private final class RefreshButton extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(Material.CLOCK)
                    .name(Component.text("Refresh Index"))
                    .lore(Component.text("Re-scan the connected loaded Vault topology."));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            refreshDynamicButtons();
        }
    }
}
