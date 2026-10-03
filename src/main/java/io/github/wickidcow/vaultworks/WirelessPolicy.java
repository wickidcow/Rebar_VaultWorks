package io.github.wickidcow.vaultworks;

/**
 * Validated wireless settings snapshot.
 */
public record WirelessPolicy(
        double transmitterWatts,
        double baseRangeBlocks,
        double antennaRangeBlocks,
        boolean allowCrossDimension
) {
    private static final double MAX_WATTS = 1_000_000D;
    private static final double MAX_RANGE = 8192D;

    public WirelessPolicy {
        if (!Double.isFinite(transmitterWatts)
                || transmitterWatts <= 0D
                || transmitterWatts > MAX_WATTS) {
            throw new IllegalArgumentException(
                    "wireless.transmitter-watts must be between 0 (exclusive) and 1000000"
            );
        }
        if (!Double.isFinite(baseRangeBlocks)
                || baseRangeBlocks < 8D
                || baseRangeBlocks > MAX_RANGE) {
            throw new IllegalArgumentException(
                    "wireless.base-range-blocks must be between 8 and 8192"
            );
        }
        if (!Double.isFinite(antennaRangeBlocks)
                || antennaRangeBlocks < baseRangeBlocks
                || antennaRangeBlocks > MAX_RANGE) {
            throw new IllegalArgumentException(
                    "wireless.antenna-range-blocks must be >= base range and <= 8192"
            );
        }
    }
}
