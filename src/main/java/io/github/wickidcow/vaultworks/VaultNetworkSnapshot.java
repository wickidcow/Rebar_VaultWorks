package io.github.wickidcow.vaultworks;

record VaultNetworkSnapshot(
        int networkNodes,
        int columns,
        int onlineColumns,
        int vaults,
        int registeredVaults,
        int itemTypes,
        int legacyRecoveryVaults,
        long totalStored,
        long accessibleStored,
        long totalCapacity,
        boolean truncated
) {
}
