package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.block.interfaces.SimpleElectricRebarBlock;
import io.github.pylonmc.rebar.electricity.nodes.ElectricNodeType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

/** Administrator test fixture using the normal Rebar electricity network. */
public final class VaultTestPowerSource extends RebarBlock implements SimpleElectricRebarBlock {
    public static final double OUTPUT_WATTS = 1_000_000.0;

    public VaultTestPowerSource(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST,
                BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
            createSimpleElectricPort(ElectricNodeType.PRODUCER, face);
        }
        setPowerProduced(OUTPUT_WATTS);
    }

    public VaultTestPowerSource(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override public void postInitialise() {
        super.postInitialise();
        setPowerProduced(OUTPUT_WATTS);
    }
}
