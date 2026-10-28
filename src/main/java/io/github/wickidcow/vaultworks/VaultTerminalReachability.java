package io.github.wickidcow.vaultworks;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Lightweight, main-thread, invalidation-based reachability cache.
 * No polling and no traversals on routine Cargo/GUI refreshes. Rebar node
 * lifecycle events invalidate the cache when network topology changes.
 */
final class VaultTerminalReachability {
    private static final Map<VaultPowerBase, Boolean> CACHE = new IdentityHashMap<>();

    private VaultTerminalReachability() { }

    static boolean connected(VaultPowerBase base) {
        if (base == null) return false;
        return CACHE.computeIfAbsent(base, VaultNetworkScanner::hasConnectedTerminal);
    }

    static void invalidate() {
        CACHE.clear();
    }
}
