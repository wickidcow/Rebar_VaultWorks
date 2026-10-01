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
import xyz.xenondevs.invui.gui.Markers;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemProvider;

/**
 * Read-only searchable-storage groundwork.
 *
 * The first Terminal phase deliberately exposes item browsing without item
 * mutation. This proves exact identity grouping, paging and network visibility
 * before transactional withdrawal/deposit is introduced.
 */
public final class VaultTerminal extends RebarBlock implements GuiRebarBlock {

    public VaultTerminal(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    public VaultTerminal(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    public @NotNull Gui createGui() {
        VaultNetworkView view = VaultNetworkScanner.scanView(this);
        List<Item> content = new ArrayList<>(view.items().size());

        for (VaultItemSummary summary : view.items()) {
            content.add(new StoredItemButton(summary));
        }

        return PagedGui.itemsBuilder()
                .setStructure(
                        "# # s # # # r # #",
                        "x x x x x x x x x",
                        "x x x x x x x x x",
                        "x x x x x x x x x",
                        "x x x x x x x x x",
                        "< # # # # # # # >"
                )
                .addIngredient('#', GuiItems.backgroundBlack())
                .addIngredient('s', new NetworkSummaryButton(view.snapshot(), view.itemsTruncated()))
                .addIngredient('r', new RefreshButton())
                .addIngredient('x', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
                .addIngredient('<', GuiItems.pagePrevious())
                .addIngredient('>', GuiItems.pageNext())
                .setContent(content)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return Component.text("Vault Terminal — Read Only");
    }

    private final class StoredItemButton extends AbstractItem {
        private final VaultItemSummary summary;

        private StoredItemButton(VaultItemSummary summary) {
            this.summary = summary;
        }

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            long unavailable = Math.max(0L, summary.totalStored() - summary.accessibleStored());

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Stored: " + BasicVaultCell.format(summary.totalStored())));
            lore.add(Component.text("Accessible now: " + BasicVaultCell.format(summary.accessibleStored())));
            lore.add(Component.text("Vault Cells: " + summary.vaultCount()));
            if (unavailable > 0L) {
                lore.add(Component.text("Offline/unavailable: " + BasicVaultCell.format(unavailable)));
            }
            lore.add(Component.text("Read-only terminal: item movement is not enabled yet."));

            return ItemStackBuilder.of(summary.item().asOne())
                    .lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            // Intentionally read-only until transactional terminal operations are implemented.
        }
    }

    private final class NetworkSummaryButton extends AbstractItem {
        private final VaultNetworkSnapshot snapshot;
        private final boolean itemsTruncated;

        private NetworkSummaryButton(VaultNetworkSnapshot snapshot) {
            this(snapshot, false);
        }

        private NetworkSummaryButton(VaultNetworkSnapshot snapshot, boolean itemsTruncated) {
            this.snapshot = snapshot;
            this.itemsTruncated = itemsTruncated;
        }

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Item types: " + snapshot.itemTypes()));
            lore.add(Component.text("Vault Cells: " + snapshot.vaults()));
            lore.add(Component.text("Columns online: " + snapshot.onlineColumns() + " / " + snapshot.columns()));
            lore.add(Component.text("Accessible items: " + BasicVaultCell.format(snapshot.accessibleStored())));
            if (snapshot.truncated()) {
                lore.add(Component.text("Warning: network traversal hit the safety limit."));
            }
            if (itemsTruncated) {
                lore.add(Component.text("Warning: displayed item types hit the Terminal safety limit."));
            }

            return ItemStackBuilder.of(Material.COMPASS)
                    .name(Component.text("Network Summary"))
                    .lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        }
    }

    private final class RefreshButton extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(Material.CLOCK)
                    .name(Component.text("Refresh Terminal"))
                    .lore(
                            Component.text("Rebuild pages from the currently loaded Vault network."),
                            Component.text("No background polling is used.")
                    );
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            VaultTerminal.this.refreshGui();
        }
    }
}
