package io.github.wickidcow.vaultworks;

/** Numeric bounds for the click-to-deposit/pick-up action (never overflow-purges). */
final class VaultCursorTransferMath {
    private VaultCursorTransferMath() { }

    static int depositAmount(int held, long stored, long capacity, boolean singleItem) {
        if (held <= 0) return 0;
        return (int) VaultStorageMath.insertable(stored, capacity, singleItem ? 1L : held);
    }

    static int withdrawAmount(long stored, int carried, int maxStack, boolean singleItem) {
        if (stored <= 0L || maxStack <= 0 || carried < 0) return 0;
        long free = Math.max(0L, (long) maxStack - carried);
        long requested = singleItem ? 1L : free;
        return (int) Math.min(stored, Math.min(free, requested));
    }
}
