package io.github.wickidcow.vaultworks;

import java.util.UUID;

/** Per-player confirmation token. Content/revision changes invalidate it. */
record VaultDeleteSequence(UUID endpoint, long revision, int nextStep, long expiresAt) {
    static final long TIMEOUT_MILLIS = 30_000L;

    static VaultDeleteSequence start(UUID endpoint, long revision, long now) {
        return new VaultDeleteSequence(endpoint, revision, 1, now + TIMEOUT_MILLIS);
    }

    boolean accepts(UUID currentEndpoint, long currentRevision, int step, long now) {
        return endpoint.equals(currentEndpoint) && revision == currentRevision
                && now < expiresAt && step == nextStep && step >= 1 && step <= 4;
    }

    VaultDeleteSequence advance() {
        return new VaultDeleteSequence(endpoint, revision, nextStep + 1, expiresAt);
    }
}
