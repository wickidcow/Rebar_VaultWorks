package io.github.wickidcow.vaultworks;

/**
 * Pure storage arithmetic shared by cargo paths and tests.
 */
final class VaultStorageMath {

    private VaultStorageMath() {
    }

    /**
     * Applies a cargo system's proposed final amount to a Vault.
     *
     * When overflow purge is enabled, only newly arriving excess may be voided.
     * Existing contents above a newly lowered configured capacity are never
     * truncated by a later insertion attempt.
     */
    static long applyProposedAmount(long current, long proposed, long capacity, boolean purgeOverflow) {
        long safeCurrent = Math.max(0L, current);
        long safeProposed = Math.max(0L, proposed);
        long safeCapacity = Math.max(1L, capacity);

        // Withdrawals and no-op writes should always commit exactly.
        if (safeProposed <= safeCurrent) {
            return safeProposed;
        }

        if (!purgeOverflow) {
            return safeProposed;
        }

        // Purge only the portion of this incoming transfer that cannot fit.
        // If existing contents already exceed a lowered config capacity,
        // preserve them and purge all additional incoming items.
        return Math.max(safeCurrent, Math.min(safeProposed, safeCapacity));
    }
}
