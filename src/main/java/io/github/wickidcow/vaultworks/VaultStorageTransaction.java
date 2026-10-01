package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.RebarBlock;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Main-thread transaction boundary for player-facing Vault network movement.
 *
 * Physical Vault Cells remain authoritative. The terminal never mutates a cached
 * index entry; every operation resolves loaded endpoints and revalidates the cell
 * immediately before changing its stored amount.
 */
final class VaultStorageTransaction {

    enum Status {
        OK,
        NETWORK_TRUNCATED,
        NO_ACCESSIBLE_STORAGE,
        NO_MATCHING_ITEMS,
        STORAGE_FULL,
        INVENTORY_FULL
    }

    record Result(long moved, Status status) {
        static Result of(long moved, Status status) {
            return new Result(Math.max(0L, moved), status);
        }
    }

    private VaultStorageTransaction() {
    }

    static Result withdraw(
            RebarBlock root,
            PlayerInventory inventory,
            ItemStack identity,
            long requested
    ) {
        requirePrimaryThread();
        if (identity == null || identity.isEmpty() || requested <= 0L) {
            return Result.of(0L, Status.NO_MATCHING_ITEMS);
        }

        VaultNetworkCells network = VaultNetworkScanner.scanAccessibleCells(root);
        if (network.truncated()) {
            return Result.of(0L, Status.NETWORK_TRUNCATED);
        }

        List<BasicVaultCell> matches = matchingCells(network.cells(), identity);
        if (matches.isEmpty()) {
            return Result.of(0L, Status.NO_ACCESSIBLE_STORAGE);
        }

        long available = 0L;
        for (BasicVaultCell cell : matches) {
            available = addClamped(available, cell.networkAvailable(identity));
        }
        if (available <= 0L) {
            return Result.of(0L, Status.NO_ACCESSIBLE_STORAGE);
        }

        long inventoryCapacity = playerCapacity(inventory, identity);
        if (inventoryCapacity <= 0L) {
            return Result.of(0L, Status.INVENTORY_FULL);
        }

        long target = Math.min(Math.min(requested, available), inventoryCapacity);
        long removed = 0L;
        List<CellDebit> debits = new ArrayList<>();

        for (BasicVaultCell cell : matches) {
            if (removed >= target) {
                break;
            }

            long amount = cell.networkExtract(identity, target - removed);
            if (amount > 0L) {
                debits.add(new CellDebit(cell, amount));
                removed += amount;
            }
        }

        if (removed <= 0L) {
            return Result.of(0L, Status.NO_ACCESSIBLE_STORAGE);
        }

        long delivered = deliver(inventory, identity, removed);
        if (delivered < removed) {
            long rollback = removed - delivered;
            for (int i = debits.size() - 1; i >= 0 && rollback > 0L; i--) {
                CellDebit debit = debits.get(i);
                long restore = Math.min(rollback, debit.amount());
                debit.cell().networkRollbackExtract(identity, restore);
                rollback -= restore;
            }

            if (rollback > 0L) {
                throw new IllegalStateException(
                        "Vault terminal could not compensate an unexpected player-inventory delivery shortfall"
                );
            }
        }

        return Result.of(delivered, delivered > 0L ? Status.OK : Status.INVENTORY_FULL);
    }

    static Result depositMatching(
            RebarBlock root,
            PlayerInventory inventory,
            ItemStack identity
    ) {
        requirePrimaryThread();
        VaultNetworkCells network = VaultNetworkScanner.scanAccessibleCells(root);
        if (network.truncated()) {
            return Result.of(0L, Status.NETWORK_TRUNCATED);
        }
        return depositMatching(network.cells(), inventory, identity);
    }

    private static Result depositMatching(
            List<BasicVaultCell> networkCells,
            PlayerInventory inventory,
            ItemStack identity
    ) {
        if (identity == null || identity.isEmpty()) {
            return Result.of(0L, Status.NO_MATCHING_ITEMS);
        }

        List<BasicVaultCell> matches = matchingCells(networkCells, identity);
        if (matches.isEmpty()) {
            return Result.of(0L, Status.NO_ACCESSIBLE_STORAGE);
        }

        long availableInInventory = countMatching(inventory, identity);
        if (availableInInventory <= 0L) {
            return Result.of(0L, Status.NO_MATCHING_ITEMS);
        }

        long free = 0L;
        for (BasicVaultCell cell : matches) {
            free = addClamped(free, cell.networkFreeCapacity(identity));
        }
        if (free <= 0L) {
            return Result.of(0L, Status.STORAGE_FULL);
        }

        long target = Math.min(availableInInventory, free);

        // Remove first. The same inventory slots create enough deterministic room
        // to compensate if a cell becomes unavailable before commit.
        long removed = removeMatching(inventory, identity, target);
        long inserted = 0L;

        for (BasicVaultCell cell : matches) {
            if (inserted >= removed) {
                break;
            }
            inserted += cell.networkInsert(identity, removed - inserted);
        }

        if (inserted < removed) {
            long restored = deliver(inventory, identity, removed - inserted);
            if (restored != removed - inserted) {
                throw new IllegalStateException(
                        "Vault terminal could not compensate an unexpected storage insert shortfall"
                );
            }
        }

        if (inserted <= 0L) {
            return Result.of(0L, Status.STORAGE_FULL);
        }
        return Result.of(inserted, Status.OK);
    }

    static Result depositInventory(
            RebarBlock root,
            PlayerInventory inventory
    ) {
        requirePrimaryThread();

        VaultNetworkCells network = VaultNetworkScanner.scanAccessibleCells(root);
        if (network.truncated()) {
            return Result.of(0L, Status.NETWORK_TRUNCATED);
        }

        List<ItemStack> identities = new ArrayList<>();
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            boolean known = identities.stream().anyMatch(existing -> existing.isSimilar(stack));
            if (!known) {
                identities.add(stack.asOne());
            }
        }

        if (identities.isEmpty()) {
            return Result.of(0L, Status.NO_MATCHING_ITEMS);
        }

        long moved = 0L;
        Status lastStatus = Status.NO_ACCESSIBLE_STORAGE;
        for (ItemStack identity : identities) {
            Result result = depositMatching(network.cells(), inventory, identity);
            if (result.status() == Status.NETWORK_TRUNCATED) {
                return Result.of(moved, Status.NETWORK_TRUNCATED);
            }
            moved = addClamped(moved, result.moved());
            if (result.status() == Status.OK) {
                lastStatus = Status.OK;
            } else if (lastStatus != Status.OK) {
                lastStatus = result.status();
            }
        }

        return Result.of(moved, moved > 0L ? Status.OK : lastStatus);
    }

    private static List<BasicVaultCell> matchingCells(
            List<BasicVaultCell> cells,
            ItemStack identity
    ) {
        List<BasicVaultCell> matches = new ArrayList<>();
        for (BasicVaultCell cell : cells) {
            ItemStack registered = cell.getStoredItem();
            if (registered != null && registered.isSimilar(identity)) {
                matches.add(cell);
            }
        }
        return matches;
    }

    private static long playerCapacity(PlayerInventory inventory, ItemStack identity) {
        long capacity = 0L;
        int maxStack = Math.max(1, identity.getMaxStackSize());

        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null || stack.isEmpty()) {
                capacity = addClamped(capacity, maxStack);
            } else if (stack.isSimilar(identity) && stack.getAmount() < maxStack) {
                capacity = addClamped(capacity, maxStack - stack.getAmount());
            }
        }
        return capacity;
    }

    private static long countMatching(PlayerInventory inventory, ItemStack identity) {
        long amount = 0L;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && !stack.isEmpty() && stack.isSimilar(identity)) {
                amount = addClamped(amount, stack.getAmount());
            }
        }
        return amount;
    }

    private static long removeMatching(
            PlayerInventory inventory,
            ItemStack identity,
            long requested
    ) {
        long remaining = requested;
        int slots = inventory.getStorageContents().length;
        for (int slot = 0; slot < slots && remaining > 0L; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.isEmpty() || !stack.isSimilar(identity)) {
                continue;
            }

            int take = (int) Math.min(remaining, stack.getAmount());
            if (take == stack.getAmount()) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
            remaining -= take;
        }
        return requested - remaining;
    }

    private static long deliver(
            PlayerInventory inventory,
            ItemStack identity,
            long requested
    ) {
        long remaining = requested;
        int maxStack = Math.max(1, identity.getMaxStackSize());
        int slots = inventory.getStorageContents().length;

        for (int slot = 0; slot < slots && remaining > 0L; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.isEmpty() || !stack.isSimilar(identity)) {
                continue;
            }

            int free = Math.max(0, maxStack - stack.getAmount());
            if (free <= 0) {
                continue;
            }

            int add = (int) Math.min(remaining, free);
            stack.setAmount(stack.getAmount() + add);
            remaining -= add;
        }

        for (int slot = 0; slot < slots && remaining > 0L; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && !stack.isEmpty()) {
                continue;
            }

            int add = (int) Math.min(remaining, maxStack);
            inventory.setItem(slot, identity.asQuantity(add));
            remaining -= add;
        }

        return requested - remaining;
    }

    private static long addClamped(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        if (left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Vault storage transactions must run on the server thread");
        }
    }

    private record CellDebit(BasicVaultCell cell, long amount) {
    }
}
