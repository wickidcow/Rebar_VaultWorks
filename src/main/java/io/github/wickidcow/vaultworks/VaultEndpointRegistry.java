package io.github.wickidcow.vaultworks;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks loaded physical Vault endpoint identities.
 *
 * A duplicated endpoint UUID is treated as a hard safety fault. Every loaded
 * copy is marked conflicted and remains locked even after one duplicate unloads;
 * this prevents a cloned filled Vault from becoming usable merely by moving the
 * other copy out of memory.
 */
final class VaultEndpointRegistry {

    private static final Map<UUID, Set<BasicVaultCell>> LOADED = new HashMap<>();

    private VaultEndpointRegistry() {
    }

    static void register(BasicVaultCell cell) {
        Set<BasicVaultCell> cells = LOADED.computeIfAbsent(
                cell.getEndpointId(),
                ignored -> Collections.newSetFromMap(new IdentityHashMap<>())
        );
        cells.add(cell);

        if (cells.size() > 1) {
            for (BasicVaultCell duplicate : cells) {
                duplicate.flagIdentityConflict();
            }
            VaultWorks.instance().getLogger().severe(
                    "Duplicate Vault endpoint UUID " + cell.getEndpointId()
                            + " detected. All loaded copies are locked to prevent duplication."
            );
        }
    }

    static void unregister(BasicVaultCell cell) {
        Set<BasicVaultCell> cells = LOADED.get(cell.getEndpointId());
        if (cells == null) {
            return;
        }

        cells.remove(cell);
        if (cells.isEmpty()) {
            LOADED.remove(cell.getEndpointId());
        }
    }

    static int loadedCopies(UUID endpointId) {
        Set<BasicVaultCell> cells = LOADED.get(endpointId);
        return cells == null ? 0 : cells.size();
    }

    static void clear() {
        LOADED.clear();
    }
}
