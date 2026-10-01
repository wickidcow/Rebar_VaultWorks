package io.github.wickidcow.vaultworks;

import java.util.List;

record VaultWithdrawalAllocation(
        long requested,
        long planned,
        List<VaultWithdrawalSource> sources
) {
    boolean complete() {
        return planned >= requested;
    }
}
