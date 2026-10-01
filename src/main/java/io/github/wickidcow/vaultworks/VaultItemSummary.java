package io.github.wickidcow.vaultworks;

import org.bukkit.inventory.ItemStack;

record VaultItemSummary(
        ItemStack item,
        long totalStored,
        long accessibleStored,
        int vaultCount
) {
}
