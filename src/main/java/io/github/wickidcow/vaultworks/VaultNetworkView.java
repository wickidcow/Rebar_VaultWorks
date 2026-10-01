package io.github.wickidcow.vaultworks;

import java.util.List;

record VaultNetworkView(
        VaultNetworkSnapshot snapshot,
        List<VaultItemSummary> items
) {
}
