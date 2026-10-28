package io.github.wickidcow.vaultworks;

/** Pure visibility/glow policy used by both Vault tiers and their display manager. */
final class VaultHologramPolicy {
    private VaultHologramPolicy() { }

    static boolean visible(boolean enabled, boolean powered, boolean registered, long storedCount) {
        return enabled && powered && registered && storedCount > 0L;
    }

    static boolean glowing(boolean visible, boolean connectedToVaultTerminal) {
        return visible && connectedToVaultTerminal;
    }
}
