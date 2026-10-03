package io.github.wickidcow.vaultworks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StoragePolicyTest {

    @Test
    void acceptsDefaultStoragePolicy() {
        StoragePolicy policy = new StoragePolicy(
                1_000_000L,
                4_000_000L,
                4096,
                4096
        );
        assertEquals(1_000_000L, policy.basicCapacity());
        assertEquals(4_000_000L, policy.poweredCapacity());
        assertEquals(4096, policy.maxNetworkNodes());
        assertEquals(4096, policy.maxTerminalItemTypes());
    }

    @Test
    void rejectsInvalidCapacities() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StoragePolicy(0L, 4_000_000L, 4096, 4096)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new StoragePolicy(1_000_000L, 0L, 4096, 4096)
        );
    }

    @Test
    void rejectsUnsafeTopologyAndRenderBounds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StoragePolicy(1L, 1L, 31, 4096)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new StoragePolicy(1L, 1L, 4096, 16_385)
        );
    }
}
