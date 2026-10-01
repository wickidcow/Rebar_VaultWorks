package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.block.interfaces.SimpleElectricRebarBlock;
import io.github.pylonmc.rebar.electricity.nodes.ElectricNodeType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

/**
 * Clean electrical pedestal for a vertical Vault column.
 *
 * A base powers only the contiguous Vault Cells immediately above it, up to the
 * configured six-cell column limit. The electrical port is underneath the base
 * so the front of a storage wall can stay visually clean.
 */
public final class VaultPowerBase extends RebarBlock implements SimpleElectricRebarBlock {

    public VaultPowerBase(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        createSimpleElectricPort(ElectricNodeType.CONSUMER, BlockFace.DOWN);
        setRequiredPower(VaultWorks.instance().policy().watts());
    }

    public VaultPowerBase(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    public void postInitialise() {
        super.postInitialise();
        setRequiredPower(VaultWorks.instance().policy().watts());
    }

    public boolean isOnline() {
        try {
            return isPowered();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }
}
