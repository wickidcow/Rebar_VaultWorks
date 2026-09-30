package io.github.wickidcow.vaultworks;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PowerPolicyTest {
    @Test void rejectsFreeOrInvalidPoweredOperation() {
        for (double value : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, 1_000_001}) {
            assertThrows(IllegalArgumentException.class, () -> new PowerPolicy(value, 8));
        }
    }
    @Test void boundsCargoWork() {
        assertThrows(IllegalArgumentException.class, () -> new PowerPolicy(32, 0));
        assertThrows(IllegalArgumentException.class, () -> new PowerPolicy(32, 65));
        assertEquals(8, new PowerPolicy(32, 8).transferRate());
    }
}
