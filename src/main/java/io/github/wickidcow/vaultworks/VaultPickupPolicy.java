package io.github.wickidcow.vaultworks;

/**
 * Conservative, testable policy: native Vaults are slow to mine, so an intentional
 * Survival sneak-pickaxe action may accelerate only registered VaultWorks cells.
 * The eventual BlockBreakEvent still enforces world protection and Rebar checks.
 */
final class VaultPickupPolicy {
    private VaultPickupPolicy() { }

    static boolean fastBreak(boolean enabled, boolean survival, boolean sneaking,
                             boolean pickaxe, boolean recoveryLocked) {
        return enabled && survival && sneaking && pickaxe && !recoveryLocked;
    }
}
