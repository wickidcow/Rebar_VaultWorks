package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.RebarItemSchema;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Reusable pickaxe-shaped tool for relocating a Vault Cell. It never directly
 * removes a world block or mutates its storage; regular protected player
 * BlockBreakEvent handling in Paper and Rebar remains authoritative.
 */
public final class VaultContainmentDolly extends RebarItem {
    private static final NamespacedKey KEY = new NamespacedKey("vaultworks", "vault_containment_dolly");

    public VaultContainmentDolly(@NotNull ItemStack stack) {
        super(stack);
    }

    public static boolean isDolly(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            RebarItemSchema schema = RebarItemSchema.fromStack(stack);
            return schema != null && KEY.equals(schema.getKey());
        } catch (RuntimeException malformed) {
            // Fail closed for malformed or forged item metadata.
            return false;
        }
    }
}
