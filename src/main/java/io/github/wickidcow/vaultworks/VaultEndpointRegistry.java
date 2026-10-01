package io.github.wickidcow.vaultworks;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Loaded physical Vault Cell identity registry.
 *
 * Endpoint ids travel with portable cells, so moving a legitimate cell preserves
 * identity. A duplicate id loaded at the same time is rejected and the later
 * cell is re-keyed by BasicVaultCell rather than aliasing two physical stores.
 */
final class VaultEndpointRegistry {

    private final Map<UUID, BasicVaultCell> cells = new HashMap<>();

    synchronized boolean register(BasicVaultCell cell) {
        BasicVaultCell existing = cells.get(cell.getEndpointId());
        if (existing != null && existing != cell) {
            return false;
        }
        cells.put(cell.getEndpointId(), cell);
        return true;
    }

    synchronized void unregister(BasicVaultCell cell) {
        cells.remove(cell.getEndpointId(), cell);
    }

    synchronized Optional<BasicVaultCell> resolve(UUID id) {
        return Optional.ofNullable(cells.get(id));
    }

    synchronized int loadedCount() {
        return cells.size();
    }

    synchronized void clear() {
        cells.clear();
    }
}
