package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.item.RebarItemSchema;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
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
import xyz.xenondevs.invui.window.AnvilWindow;

/**
 * Read-only searchable Vault network terminal.
 *
 * Search and browsing are deliberately separated from item mutation. This lets
 * topology, exact item identity and pagination mature before transactional
 * withdrawal/deposit is introduced.
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

        return PagedGui.itemsBuilder()
                .setStructure(
                        "# q # s # r # # #",
                        "x x x x x x x x x",
                        "x x x x x x x x x",
                        "x x x x x x x x x",
                        "x x x x x x x x x",
                        "< # # # # # # # >"
                )
                .addIngredient('#', GuiItems.backgroundBlack())
                .addIngredient('q', new SearchButton())
                .addIngredient('s', new NetworkSummaryButton(view.snapshot(), view.itemsTruncated()))
                .addIngredient('r', new RefreshButton())
                .addIngredient('x', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
                .addIngredient('<', GuiItems.pagePrevious())
                .addIngredient('>', GuiItems.pageNext())
                .setContent(toButtons(view.items()))
                .build();
    }

    private List<Item> toButtons(List<VaultItemSummary> summaries) {
        List<Item> content = new ArrayList<>(summaries.size());
        for (VaultItemSummary summary : summaries) {
            content.add(new StoredItemButton(summary));
        }
        return content;
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return Component.text("Vault Terminal — Read Only");
    }

    private void openSearch(Player player) {
        // Capture topology once. Rename events below filter only this immutable view.
        VaultNetworkView view = VaultNetworkScanner.scanView(this);
        List<VaultItemSummary> snapshotItems = view.items();

        PagedGui<Item> lowerGui = PagedGui.itemsBuilder()
                .setStructure(
                        "x x x x x x x x x",
                        "x x x x x x x x x",
                        "x x x x x x x x x",
                        "x x x x x x x x x",
                        "< # # # # # # # >"
                )
                .addIngredient('x', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
                .addIngredient('#', GuiItems.backgroundBlack())
                .addIngredient('<', GuiItems.pagePrevious())
                .addIngredient('>', GuiItems.pageNext())
                .setContent(toButtons(snapshotItems))
                .build();

        Gui upperGui = Gui.builder()
                .setStructure("# S #")
                .addIngredient('#', GuiItems.backgroundBlack())
                .addIngredient('S', new SearchHelpButton(view))
                .build();

        AtomicBoolean firstRename = new AtomicBoolean(true);

        AnvilWindow window = AnvilWindow.builder()
                .setViewer(player)
                .setUpperGui(upperGui)
                .setLowerGui(lowerGui)
                .setTitle(Component.text("Search Vault Network"))
                .addRenameHandler(search -> {
                    if (firstRename.getAndSet(false)) {
                        return;
                    }

                    try {
                        lowerGui.setContent(toButtons(filter(snapshotItems, player, search)));
                        lowerGui.setPage(0);
                    } catch (Throwable throwable) {
                        VaultWorks.instance().getLogger().warning(
                                "Vault Terminal search update failed: " + throwable.getMessage()
                        );
                    }
                })
                .build(player);

        window.open();
    }

    private List<VaultItemSummary> filter(
            List<VaultItemSummary> source,
            Player player,
            String rawSearch
    ) {
        String search = rawSearch == null ? "" : rawSearch.trim().toLowerCase(player.locale());
        if (search.isBlank()) {
            return source;
        }

        String[] pieces = search.split("\\s+");
        List<VaultItemSummary> filtered = new ArrayList<>();

        for (VaultItemSummary summary : source) {
            if (matchesAll(summary, player, pieces)) {
                filtered.add(summary);
            }
        }

        return filtered;
    }

    private boolean matchesAll(VaultItemSummary summary, Player player, String[] pieces) {
        String itemName = displayName(summary.item(), player);
        String namespace = namespace(summary.item());

        for (String piece : pieces) {
            if (piece.isBlank()) {
                continue;
            }

            if (piece.startsWith("@")) {
                String wanted = piece.substring(1);
                if (!wanted.isBlank() && !namespace.contains(wanted)) {
                    return false;
                }
                continue;
            }

            if (piece.equals("#online")) {
                if (summary.accessibleStored() <= 0L) {
                    return false;
                }
                continue;
            }

            if (piece.equals("#offline")) {
                if (summary.totalStored() <= summary.accessibleStored()) {
                    return false;
                }
                continue;
            }

            if (!itemName.contains(piece)) {
                return false;
            }
        }

        return true;
    }

    private String displayName(ItemStack item, Player player) {
        Component rendered = GlobalTranslator.render(item.effectiveName(), player.locale());
        return PlainTextComponentSerializer.plainText()
                .serialize(rendered)
                .toLowerCase(player.locale());
    }

    private String namespace(ItemStack item) {
        RebarItemSchema schema = RebarItemSchema.fromStack(item);
        if (schema != null) {
            return schema.getKey().getNamespace().toLowerCase(Locale.ROOT);
        }
        return item.getType().getKey().getNamespace().toLowerCase(Locale.ROOT);
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
            lore.add(Component.text("Namespace: " + namespace(summary.item())));
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

    private final class SearchButton extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(Material.NAME_TAG)
                    .name(Component.text("Search Vault Network"))
                    .lore(
                            Component.text("Search by item name."),
                            Component.text("@namespace filters by addon/namespace."),
                            Component.text("#online requires currently accessible stock."),
                            Component.text("#offline finds items with unavailable stock.")
                    );
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            openSearch(player);
        }
    }

    private final class SearchHelpButton extends AbstractItem {
        private final VaultNetworkView view;

        private SearchHelpButton(VaultNetworkView view) {
            this.view = view;
        }

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Type item-name words into the anvil field."));
            lore.add(Component.text("Use @namespace, #online, or #offline as filters."));
            lore.add(Component.text("Captured item types: " + view.items().size()));
            lore.add(Component.text("The network is scanned once when search opens."));
            if (view.itemsTruncated()) {
                lore.add(Component.text("Warning: captured results hit the Terminal safety limit."));
            }

            return ItemStackBuilder.of(Material.PAPER)
                    .name(Component.text("Vault Search"))
                    .lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        }
    }

    private final class NetworkSummaryButton extends AbstractItem {
        private final VaultNetworkSnapshot snapshot;
        private final boolean itemsTruncated;

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
