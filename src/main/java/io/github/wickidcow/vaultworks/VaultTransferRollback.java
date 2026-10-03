package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.RebarBlock;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Captures all participants before mutation; one failed restore never skips another. */
final class VaultTransferRollback {
    private final RebarBlock root;
    private final Inventory inventory;
    private final ItemStack[] beforeInventory;
    private final List<CellState> cells;
    private final String operation;

    record CellState(BasicVaultCell cell, ItemStack item, long amount, boolean purge, long revision,
                     List<ItemStack> legacy) {
        static CellState capture(BasicVaultCell cell) {
            return new CellState(cell, cell.getStoredItem(), cell.getStoredAmount(), cell.isPurgeOverflow(),
                    cell.getEndpointRevision(), cell.legacyRecovery.stream().map(ItemStack::clone).toList());
        }
        void restore() {
            cell.storedItem = item == null ? null : item.clone();
            cell.storedAmount = amount;
            cell.purgeOverflow = purge;
            cell.legacyRecovery.clear();
            legacy.forEach(stack -> cell.legacyRecovery.add(stack.clone()));
            cell.touchRevision();
            cell.refreshGuiItems();
            VaultDisplayManager.update(cell);
        }
        boolean matches() {
            return java.util.Objects.equals(item, cell.getStoredItem()) && amount == cell.getStoredAmount()
                    && purge == cell.isPurgeOverflow() && legacy.equals(cell.legacyRecovery);
        }
        void evidence(Properties target, String prefix) {
            target.setProperty(prefix + ".id", cell.getEndpointId().toString());
            target.setProperty(prefix + ".location", key(cell.getBlock()));
            target.setProperty(prefix + ".amount", Long.toString(amount));
            target.setProperty(prefix + ".revision", Long.toString(revision));
            target.setProperty(prefix + ".purge", Boolean.toString(purge));
            target.setProperty(prefix + ".item", encode(item));
            items(target, prefix + ".legacy", legacy.toArray(ItemStack[]::new));
        }
    }

    VaultTransferRollback(RebarBlock root, Inventory inventory, List<BasicVaultCell> cells, String operation) {
        this.root = root;
        this.inventory = inventory;
        this.beforeInventory = snapshot(inventory);
        this.cells = cells.stream().map(CellState::capture).toList();
        this.operation = operation;
    }

    void recover(RuntimeException original) {
        VaultRecoveryStore store = VaultWorks.instance().recoveryStore();
        UUID incident = null;
        try {
            Properties evidence = new Properties();
            evidence.setProperty("failure", original.toString());
            evidence.setProperty("root.world", root.getBlock().getWorld().getName());
            try { evidence.setProperty("inventory.type", String.valueOf(inventory.getType())); }
            catch (RuntimeException unavailable) { evidence.setProperty("inventory.type", "unavailable"); }
            evidence.setProperty("vaultworks.version", VaultWorks.instance().getPluginMeta().getVersion());
            evidence.setProperty("paper.version", org.bukkit.Bukkit.getVersion());
            try {
                if (inventory.getHolder() instanceof Player player) evidence.setProperty("player", player.getUniqueId().toString());
            } catch (RuntimeException unavailable) { evidence.setProperty("player", "holder unavailable"); }
            items(evidence, "inventory.before", beforeInventory);
            try { items(evidence, "inventory.observed", snapshot(inventory)); }
            catch (RuntimeException unreadable) { evidence.setProperty("inventory.observed.error", unreadable.toString()); }
            for (int i = 0; i < cells.size(); i++) {
                cells.get(i).evidence(evidence, "cell." + i + ".before");
                CellState.capture(cells.get(i).cell()).evidence(evidence, "cell." + i + ".observed");
            }
            incident = store.record(evidence, cells.stream().map(s -> s.cell().getEndpointId()).collect(Collectors.toSet()),
                    key(root.getBlock()), operation);
        } catch (Exception recordingFailure) {
            suppress(original, recordingFailure);
            store.failClosed("Failed to capture transfer recovery evidence; inspect server log");
        }

        boolean restored = true;
        // Always try every participant, including when the inventory setter throws.
        try { inventory.setStorageContents(copy(beforeInventory)); }
        catch (RuntimeException failure) { suppress(original, failure); restored = false; }
        for (CellState cell : cells) {
            try { cell.restore(); }
            catch (RuntimeException failure) { suppress(original, failure); restored = false; }
        }
        try {
            if (!Arrays.equals(beforeInventory, snapshot(inventory)) || cells.stream().anyMatch(s -> !s.matches())) {
                original.addSuppressed(new IllegalStateException("Rollback verification did not match the captured contents"));
                restored = false;
            }
        } catch (RuntimeException failure) { suppress(original, failure); restored = false; }

        if (restored && incident != null) {
            try {
                store.resolved(incident);
                for (CellState cell : cells) cell.cell().updatePowerVisual(cell.cell().isOperational());
            }
            catch (Exception failure) { suppress(original, failure); }
        }
        VaultWorks.instance().getLogger().log(java.util.logging.Level.SEVERE,
                restored && !store.globallyLocked() ? "Vault transfer failed; all contents restored."
                        : "Vault transfer recovery required. Affected storage is locked; run /vaultworks recovery.", original);
    }

    private static void suppress(RuntimeException original, Exception failure) {
        if (original != failure) original.addSuppressed(failure);
    }

    static String key(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
    static ItemStack[] snapshot(Inventory inventory) { return copy(inventory.getStorageContents()); }
    private static ItemStack[] copy(ItemStack[] items) {
        return Arrays.stream(items).map(item -> item == null || item.isEmpty() ? null : item.clone()).toArray(ItemStack[]::new);
    }
    private static String encode(ItemStack item) {
        return item == null || item.isEmpty() ? "empty" : Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }
    private static void items(Properties target, String prefix, ItemStack[] items) {
        target.setProperty(prefix + ".slots", Integer.toString(items.length));
        for (int i = 0; i < items.length; i++) target.setProperty(prefix + "." + i, encode(items[i]));
    }
}
