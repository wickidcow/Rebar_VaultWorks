package io.github.wickidcow.vaultworks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WirelessPolicyTest {

    @Test
    void acceptsNormalWirelessProgression() {
        WirelessPolicy policy = new WirelessPolicy(32D, 64D, 512D, true);
        assertEquals(32D, policy.transmitterWatts());
        assertEquals(64D, policy.baseRangeBlocks());
        assertEquals(512D, policy.antennaRangeBlocks());
        assertTrue(policy.allowCrossDimension());
    }

    @Test
    void rejectsAntennaRangeShorterThanBaseRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WirelessPolicy(32D, 512D, 64D, true)
        );
    }

    @Test
    void rejectsInvalidPowerOrRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WirelessPolicy(0D, 64D, 512D, true)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new WirelessPolicy(32D, Double.NaN, 512D, true)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new WirelessPolicy(32D, 64D, 9000D, true)
        );
    }
}
