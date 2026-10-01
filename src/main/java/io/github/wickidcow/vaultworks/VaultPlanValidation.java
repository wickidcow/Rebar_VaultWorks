package io.github.wickidcow.vaultworks;

import java.util.UUID;

record VaultPlanValidation(
        boolean valid,
        Reason reason,
        UUID endpointId
) {
    enum Reason {
        VALID,
        SOURCE_NOT_CONNECTED_OR_ACCESSIBLE,
        SOURCE_REVISION_CHANGED,
        SOURCE_AMOUNT_TOO_LOW
    }

    static VaultPlanValidation valid() {
        return new VaultPlanValidation(true, Reason.VALID, null);
    }

    static VaultPlanValidation invalid(Reason reason, UUID endpointId) {
        return new VaultPlanValidation(false, reason, endpointId);
    }
}
