package io.github.wickidcow.vaultworks;

public record PowerPolicy(double watts, int transferRate) {
    public PowerPolicy {
        if (!Double.isFinite(watts) || watts <= 0 || watts > 1_000_000) {
            throw new IllegalArgumentException("power.watts must be between 0 (exclusive) and 1000000");
        }
        if (transferRate < 1 || transferRate > 64) {
            throw new IllegalArgumentException("cargo.items-per-tick must be between 1 and 64");
        }
    }
}
