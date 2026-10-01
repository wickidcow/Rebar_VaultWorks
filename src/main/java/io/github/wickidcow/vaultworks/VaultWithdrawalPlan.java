package io.github.wickidcow.vaultworks;

import org.bukkit.inventory.ItemStack;

final class VaultWithdrawalPlan {

    private final ItemStack item;
    private final VaultWithdrawalAllocation allocation;

    VaultWithdrawalPlan(ItemStack item, VaultWithdrawalAllocation allocation) {
        this.item = item.asOne();
        this.allocation = allocation;
    }

    ItemStack item() {
        return item.clone();
    }

    VaultWithdrawalAllocation allocation() {
        return allocation;
    }
}
