package io.github.wickidcow.vaultworks;

import java.util.UUID;

record VaultWithdrawalCandidate(
        UUID endpointId,
        long expectedRevision,
        long available
) {
}
