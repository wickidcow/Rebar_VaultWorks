package io.github.wickidcow.vaultworks;

/**
 * Conservative, testable policy: native Vaults are slow to mine, so an intentional
 * Survival sneaking with a registered Containment Dolly may accelerate only VaultWorks cells.
 * The eventual BlockBreakEvent still enforces world protection and Rebar checks.
 */
final class VaultPickupPolicy {
    private VaultPickupPolicy() { }

    static boolean fastBreak(boolean enabled, boolean survival, boolean sneaking,
                             boolean authorizedDolly, boolean recoveryLocked) {
        return enabled && survival && sneaking && authorizedDolly && !recoveryLocked;
    }
}
