package io.github.wickidcow.vaultworks;

public record PowerPolicy(double baseWatts, double wattsPerVault, int transferRate) {

    private static final double MAX_COLUMN_WATTS = 1_000_000D;

    public PowerPolicy {
        if (!Double.isFinite(baseWatts) || baseWatts <= 0D || baseWatts > MAX_COLUMN_WATTS) {
            throw new IllegalArgumentException("power.base-watts must be between 0 (exclusive) and 1000000");
        }
        if (!Double.isFinite(wattsPerVault) || wattsPerVault < 0D || wattsPerVault > MAX_COLUMN_WATTS) {
            throw new IllegalArgumentException("power.watts-per-vault must be between 0 and 1000000");
        }
        if (baseWatts + wattsPerVault * BasicVaultCell.MAX_COLUMN_HEIGHT > MAX_COLUMN_WATTS) {
            throw new IllegalArgumentException("maximum six-Vault column demand must not exceed 1000000 W");
        }
        if (transferRate < 1 || transferRate > 64) {
            throw new IllegalArgumentException("cargo.items-per-tick must be between 1 and 64");
        }
    }

    /**
     * Compatibility constructor for the original fixed-load policy.
     */
    public PowerPolicy(double watts, int transferRate) {
        this(watts, 0D, transferRate);
    }

    public double demandForCells(int cells) {
        int bounded = Math.max(0, Math.min(cells, BasicVaultCell.MAX_COLUMN_HEIGHT));
        return baseWatts + wattsPerVault * bounded;
    }
}
