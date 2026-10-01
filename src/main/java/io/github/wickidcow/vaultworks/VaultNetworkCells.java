package io.github.wickidcow.vaultworks;

import java.util.List;

/**
 * Loaded, powered physical storage endpoints reachable from one Vault network root.
 *
 * The list is a point-in-time view. Callers must still revalidate each cell before
 * mutating it because power/topology can change between discovery and commit.
 */
record VaultNetworkCells(
        List<BasicVaultCell> cells,
        boolean truncated
) {
    VaultNetworkCells {
        cells = List.copyOf(cells);
    }
}
