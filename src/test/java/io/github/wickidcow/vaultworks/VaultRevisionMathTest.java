package io.github.wickidcow.vaultworks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VaultRevisionMathTest {

    @Test
    void incrementsNormally() {
        assertEquals(1L, VaultRevisionMath.next(0L));
        assertEquals(43L, VaultRevisionMath.next(42L));
    }

    @Test
    void clampsNegativeAndMaxValues() {
        assertEquals(1L, VaultRevisionMath.next(-100L));
        assertEquals(Long.MAX_VALUE, VaultRevisionMath.next(Long.MAX_VALUE));
    }
}
