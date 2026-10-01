package io.github.wickidcow.vaultworks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class VaultWithdrawalMath {

    private VaultWithdrawalMath() {
    }

    static VaultWithdrawalAllocation allocate(
            List<VaultWithdrawalCandidate> candidates,
            long requested
    ) {
        long safeRequested = Math.max(0L, requested);
        if (safeRequested == 0L || candidates.isEmpty()) {
            return new VaultWithdrawalAllocation(safeRequested, 0L, List.of());
        }

        List<VaultWithdrawalCandidate> sorted = new ArrayList<>();
        for (VaultWithdrawalCandidate candidate : candidates) {
            if (candidate != null && candidate.available() > 0L) {
                sorted.add(candidate);
            }
        }

        // Drain smaller Vaults first so normal gameplay tends to consolidate stock.
        sorted.sort(
                Comparator.comparingLong(VaultWithdrawalCandidate::available)
                        .thenComparing(candidate -> candidate.endpointId().toString())
        );

        long remaining = safeRequested;
        long planned = 0L;
        List<VaultWithdrawalSource> sources = new ArrayList<>();

        for (VaultWithdrawalCandidate candidate : sorted) {
            if (remaining <= 0L) {
                break;
            }

            long take = Math.min(candidate.available(), remaining);
            if (take <= 0L) {
                continue;
            }

            sources.add(new VaultWithdrawalSource(
                    candidate.endpointId(),
                    candidate.expectedRevision(),
                    take
            ));
            planned += take;
            remaining -= take;
        }

        return new VaultWithdrawalAllocation(
                safeRequested,
                planned,
                List.copyOf(sources)
        );
    }
}
