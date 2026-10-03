package io.github.wickidcow.vaultworks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VaultStorageMathTest {

    @Test
    void networkWithdrawalNeverOverdraws() {
        assertEquals(64L, VaultStorageMath.withdrawable(64L, 128L));
        assertEquals(0L, VaultStorageMath.withdrawable(0L, 64L));
        assertEquals(1L, VaultStorageMath.withdrawable(64L, 1L));
    }

    @Test
    void networkInsertionNeverExceedsCapacity() {
        assertEquals(2L, VaultStorageMath.insertable(998L, 1_000L, 64L));
        assertEquals(0L, VaultStorageMath.insertable(1_000L, 1_000L, 64L));
        assertEquals(64L, VaultStorageMath.insertable(500L, 1_000L, 64L));
    }

    @Test
    void loweredCapacityBlocksNewInsertWithoutDeletingExistingStock() {
        assertEquals(0L, VaultStorageMath.insertable(2_000L, 1_000L, 64L));
        assertEquals(64L, VaultStorageMath.withdrawable(2_000L, 64L));
    }

    @Test
    void purgeOnlyDeletesNewOverflow() {
        assertEquals(1_000_000L,
                VaultStorageMath.applyProposedAmount(999_998L, 1_000_006L, 1_000_000L, true));
    }

    @Test
    void loweredCapacityNeverDeletesExistingContents() {
        assertEquals(2_000_000L,
                VaultStorageMath.applyProposedAmount(2_000_000L, 2_000_008L, 1_000_000L, true));
    }

    @Test
    void withdrawalsCommitExactly() {
        assertEquals(750_000L,
                VaultStorageMath.applyProposedAmount(1_000_000L, 750_000L, 1_000_000L, true));
    }

    @Test
    void nonPurgeInsertKeepsValidatedProposedAmount() {
        assertEquals(500_008L,
                VaultStorageMath.applyProposedAmount(500_000L, 500_008L, 1_000_000L, false));
    }
}
