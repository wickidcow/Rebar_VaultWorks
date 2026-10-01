package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.RebarBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

final class VaultWithdrawalPlanner {

    private VaultWithdrawalPlanner() {
    }

    static VaultWithdrawalPlan plan(
            RebarBlock root,
            ItemStack item,
            long requested
    ) {
        List<BasicVaultCell> cells = VaultNetworkScanner.findAccessibleCells(root, item);
        List<VaultWithdrawalCandidate> candidates = new ArrayList<>(cells.size());

        for (BasicVaultCell cell : cells) {
            candidates.add(new VaultWithdrawalCandidate(
                    cell.getEndpointId(),
                    cell.getEndpointRevision(),
                    cell.getStoredAmount()
            ));
        }

        return new VaultWithdrawalPlan(
                item,
                VaultWithdrawalMath.allocate(candidates, requested)
        );
    }

    /**
     * Re-walks the current explicit topology and checks every planned endpoint.
     *
     * A Terminal view is therefore never an authorization to mutate storage:
     * cargo, power, topology or player changes that advanced an endpoint
     * revision invalidate the old plan before a commit may start.
     */
    static VaultPlanValidation revalidate(
            RebarBlock root,
            VaultWithdrawalPlan plan
    ) {
        List<BasicVaultCell> currentCells = VaultNetworkScanner.findAccessibleCells(
                root,
                plan.item()
        );

        Map<UUID, BasicVaultCell> currentById = new HashMap<>();
        for (BasicVaultCell cell : currentCells) {
            BasicVaultCell registered = VaultEndpointRegistry.resolveUnique(cell.getEndpointId());
            if (registered == cell) {
                currentById.put(cell.getEndpointId(), cell);
            }
        }

        for (VaultWithdrawalSource source : plan.allocation().sources()) {
            BasicVaultCell cell = currentById.get(source.endpointId());
            if (cell == null) {
                return VaultPlanValidation.invalid(
                        VaultPlanValidation.Reason.SOURCE_NOT_CONNECTED_OR_ACCESSIBLE,
                        source.endpointId()
                );
            }

            if (cell.getEndpointRevision() != source.expectedRevision()) {
                return VaultPlanValidation.invalid(
                        VaultPlanValidation.Reason.SOURCE_REVISION_CHANGED,
                        source.endpointId()
                );
            }

            if (cell.getStoredAmount() < source.amount()) {
                return VaultPlanValidation.invalid(
                        VaultPlanValidation.Reason.SOURCE_AMOUNT_TOO_LOW,
                        source.endpointId()
                );
            }
        }

        return VaultPlanValidation.valid();
    }
}
