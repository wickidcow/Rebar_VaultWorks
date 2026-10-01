package io.github.wickidcow.vaultworks;

import java.util.Arrays;
import org.bukkit.inventory.ItemStack;

final class VaultItemIdentity {

    private final byte[] bytes;
    private final int hash;

    private VaultItemIdentity(byte[] bytes) {
        this.bytes = bytes;
        this.hash = Arrays.hashCode(bytes);
    }

    static VaultItemIdentity of(ItemStack item) {
        return new VaultItemIdentity(item.asOne().serializeAsBytes());
    }

    boolean matches(ItemStack item) {
        return item != null
                && !item.isEmpty()
                && Arrays.equals(bytes, item.asOne().serializeAsBytes());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VaultItemIdentity identity
                && Arrays.equals(bytes, identity.bytes);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
