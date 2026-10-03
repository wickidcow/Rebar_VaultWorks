package io.github.wickidcow.vaultworks;

/**
 * Validated storage/index rendering settings snapshot.
 */
public record StoragePolicy(
        long basicCapacity,
        long poweredCapacity,
        int maxNetworkNodes,
        int maxTerminalItemTypes
) {
    public StoragePolicy {
        if (basicCapacity < 1L) {
            throw new IllegalArgumentException("storage.basic-capacity must be at least 1");
        }
        if (poweredCapacity < 1L) {
            throw new IllegalArgumentException("storage.powered-capacity must be at least 1");
        }
        if (maxNetworkNodes < 32 || maxNetworkNodes > 65_536) {
            throw new IllegalArgumentException(
                    "index.max-network-nodes must be between 32 and 65536"
            );
        }
        if (maxTerminalItemTypes < 128 || maxTerminalItemTypes > 16_384) {
            throw new IllegalArgumentException(
                    "terminal.max-item-types must be between 128 and 16384"
            );
        }
    }
}
