package io.github.wickidcow.vaultworks;

final class VaultRevisionMath {

    private VaultRevisionMath() {
    }

    static long next(long current) {
        return current == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0L, current) + 1L;
    }
}
