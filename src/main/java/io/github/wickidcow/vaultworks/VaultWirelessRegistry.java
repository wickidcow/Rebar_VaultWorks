package io.github.wickidcow.vaultworks;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Registry of currently loaded wireless transmitters.
 *
 * Only loaded blocks are registered. Resolving a transmitter can therefore
 * never force-load a chunk or world.
 */
final class VaultWirelessRegistry {

    private final Map<UUID, VaultTransmitter> transmitters = new HashMap<>();

    synchronized boolean register(VaultTransmitter transmitter) {
        VaultTransmitter existing = transmitters.get(transmitter.getTransmitterId());
        if (existing != null && existing != transmitter) {
            return false;
        }
        transmitters.put(transmitter.getTransmitterId(), transmitter);
        return true;
    }

    synchronized void unregister(VaultTransmitter transmitter) {
        transmitters.remove(transmitter.getTransmitterId(), transmitter);
    }

    synchronized Optional<VaultTransmitter> resolve(UUID id) {
        return Optional.ofNullable(transmitters.get(id));
    }

    synchronized int loadedCount() {
        return transmitters.size();
    }

    synchronized void clear() {
        transmitters.clear();
    }
}
