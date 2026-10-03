package io.github.wickidcow.vaultworks;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VaultDeleteSequenceTest {
    @Test void requiresEveryConfirmationInOrder() {
        UUID endpoint = UUID.randomUUID();
        VaultDeleteSequence sequence = VaultDeleteSequence.start(endpoint, 42L, 1000L);
        assertFalse(sequence.accepts(endpoint, 42L, 4, 1001L));
        for (int step = 1; step <= 4; step++) {
            assertTrue(sequence.accepts(endpoint, 42L, step, 1001L));
            assertFalse(sequence.accepts(endpoint, 42L, step - 1, 1001L));
            sequence = sequence.advance();
        }
        assertFalse(sequence.accepts(endpoint, 42L, 4, 1001L));
    }

    @Test void rejectsChangedContentsDifferentCellAndTimeout() {
        UUID endpoint = UUID.randomUUID();
        VaultDeleteSequence sequence = VaultDeleteSequence.start(endpoint, 42L, 1000L);
        assertFalse(sequence.accepts(endpoint, 43L, 1, 1001L));
        assertFalse(sequence.accepts(UUID.randomUUID(), 42L, 1, 1001L));
        assertTrue(sequence.accepts(endpoint, 42L, 1, 30999L));
        assertFalse(sequence.accepts(endpoint, 42L, 1, 31000L));
    }
}
