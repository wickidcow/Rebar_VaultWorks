package io.github.wickidcow.vaultworks;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VaultWithdrawalMathTest {

    @Test
    void allocatesAcrossMultipleVaultsAndDrainsSmallestFirst() {
        UUID large = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID small = UUID.fromString("00000000-0000-0000-0000-000000000001");

        VaultWithdrawalAllocation allocation = VaultWithdrawalMath.allocate(
                List.of(
                        new VaultWithdrawalCandidate(large, 10, 100),
                        new VaultWithdrawalCandidate(small, 20, 5)
                ),
                12
        );

        assertTrue(allocation.complete());
        assertEquals(12L, allocation.planned());
        assertEquals(2, allocation.sources().size());
        assertEquals(small, allocation.sources().get(0).endpointId());
        assertEquals(5L, allocation.sources().get(0).amount());
        assertEquals(7L, allocation.sources().get(1).amount());
    }

    @Test
    void reportsPartialWhenStockIsInsufficient() {
        VaultWithdrawalAllocation allocation = VaultWithdrawalMath.allocate(
                List.of(new VaultWithdrawalCandidate(UUID.randomUUID(), 1, 4)),
                10
        );

        assertFalse(allocation.complete());
        assertEquals(4L, allocation.planned());
    }

    @Test
    void ignoresEmptyCandidatesAndZeroRequests() {
        UUID id = UUID.randomUUID();
        assertEquals(0L, VaultWithdrawalMath.allocate(
                List.of(new VaultWithdrawalCandidate(id, 1, 0)),
                64
        ).planned());

        assertEquals(0L, VaultWithdrawalMath.allocate(
                List.of(new VaultWithdrawalCandidate(id, 1, 64)),
                0
        ).planned());
    }
}
