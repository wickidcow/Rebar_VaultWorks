package io.github.wickidcow.vaultworks;

import java.util.List;
import java.util.Set;

/**
 * Canonical player-facing VaultWorks content identifiers for the 0.3 storage line.
 */
public final class VaultWorksContentCatalog {

    public static final List<String> ALL_IDS = List.of(
            "encoded_circuit",
            "memory_wafer",
            "storage_lattice",
            "vault_link",
            "vault_index",
            "vault_terminal",
            "vault_transmitter",
            "vault_antenna",
            "dimensional_vault_antenna",
            "wireless_vault_terminal",
            "vault_power_base",
            "vault_cargo_node",
            "basic_vault_cell",
            "powered_vault_cell"
    );

    public static final Set<String> ALL_ID_SET = Set.copyOf(ALL_IDS);

    private VaultWorksContentCatalog() {
        throw new AssertionError("Utility class");
    }

    public static void validate() {
        if (ALL_IDS.size() != ALL_ID_SET.size()) {
            throw new IllegalStateException("VaultWorks content catalog contains duplicate ids");
        }
        for (String id : ALL_IDS) {
            if (!id.matches("[a-z0-9_]+")) {
                throw new IllegalStateException("Invalid VaultWorks content id: " + id);
            }
        }
    }
}
