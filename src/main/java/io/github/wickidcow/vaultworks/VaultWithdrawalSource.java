package io.github.wickidcow.vaultworks;

import java.util.UUID;

record VaultWithdrawalSource(
        UUID endpointId,
        long expectedRevision,
        long amount
) {
}
