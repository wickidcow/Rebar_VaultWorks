package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.block.interfaces.BlockBreakRebarBlockHandler;
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.SimpleElectricRebarBlock;
import io.github.pylonmc.rebar.electricity.nodes.ElectricConsumerNode;
import io.github.pylonmc.rebar.electricity.nodes.ElectricNodeType;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.CopperBulb;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

/**
 * Electrical power mat/pedestal for one vertical Vault column.
 *
 * New bases expose their Rebar power port on the rear service face so the
 * visible front of a storage wall stays clean. One base powers up to six
 * contiguous Vault Cells above it.
 */
public final class VaultPowerBase extends RebarBlock implements
        SimpleElectricRebarBlock,
        DirectionalRebarBlock,
        BlockBreakRebarBlockHandler {

    public VaultPowerBase(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);

        BlockFace front = context.getFacing();
        setFacing(front);
        createSimpleElectricPort(ElectricNodeType.CONSUMER, front.getOppositeFace());
        refreshPowerDemand();
        setBaseVisual(false);
    }

    public VaultPowerBase(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    public void postInitialise() {
        super.postInitialise();
        refreshPowerDemand();

        ElectricConsumerNode consumer = getElectricNode("consumer_0", ElectricConsumerNode.class);
        if (consumer != null) {
            consumer.onPowerChange(this::applyPowerState);
        }

        applyPowerState(isOnline());
    }

    public boolean isOnline() {
        try {
            return isPowered();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    /**
     * Reconciles every Vault shell in this base's contiguous column.
     * Called after cell placement/load as well as power changes.
     */
    public void refreshColumnVisuals() {
        refreshPowerDemand();
        applyVaultVisuals(isOnline());
    }

    public void refreshPowerDemand() {
        setRequiredPower(VaultWorks.instance().policy().demandForCells(getColumnHeight()));
    }

    public void refreshAfterCellBreak(BasicVaultCell removed) {
        int remainingContiguous = 0;
        for (int height = 1; height <= BasicVaultCell.MAX_COLUMN_HEIGHT; height++) {
            BasicVaultCell cell;
            try {
                cell = BlockStorage.getAs(
                        BasicVaultCell.class,
                        getBlock().getRelative(BlockFace.UP, height)
                );
            } catch (IllegalArgumentException ignored) {
                break;
            }

            if (cell == null || cell == removed) {
                break;
            }
            remainingContiguous++;
        }

        setRequiredPower(VaultWorks.instance().policy().demandForCells(remainingContiguous));
    }

    public int getColumnHeight() {
        int cells = 0;
        for (int height = 1; height <= BasicVaultCell.MAX_COLUMN_HEIGHT; height++) {
            try {
                if (BlockStorage.getAs(BasicVaultCell.class, getBlock().getRelative(BlockFace.UP, height)) == null) {
                    break;
                }
            } catch (IllegalArgumentException ignored) {
                break;
            }
            cells++;
        }
        return cells;
    }

    @Override
    public WailaDisplay getWaila(@NotNull Player player) {
        boolean online = isOnline();
        return WailaDisplay.of(this, player)
                .add(Component.text(online ? "ONLINE" : "OFFLINE"))
                .add(Component.text(getColumnHeight() + " / " + BasicVaultCell.MAX_COLUMN_HEIGHT + " Vaults"))
                .add(Component.text(VaultWorks.instance().policy().demandForCells(getColumnHeight()) + " W demand"));
    }

    private void applyPowerState(boolean online) {
        setBaseVisual(online);
        applyVaultVisuals(online);
    }

    private void setBaseVisual(boolean online) {
        if (getBlock().getBlockData() instanceof CopperBulb bulb) {
            // The lit copper bulb is the no-resource-pack fallback for the
            // powered-bedrock / energized-mat idea while staying safely breakable.
            bulb.setLit(online);
            getBlock().setBlockData(bulb, false);
        }
    }

    private void applyVaultVisuals(boolean online) {
        for (int height = 1; height <= BasicVaultCell.MAX_COLUMN_HEIGHT; height++) {
            BasicVaultCell cell;
            try {
                cell = BlockStorage.getAs(
                        BasicVaultCell.class,
                        getBlock().getRelative(BlockFace.UP, height)
                );
            } catch (IllegalArgumentException ignored) {
                break;
            }

            if (cell == null) {
                break;
            }

            cell.updatePowerVisual(online);
        }
    }

    @Override
    public void onBlockBreak(@NotNull List<ItemStack> drops, @NotNull BlockBreakContext context) {
        applyVaultVisuals(false);
    }
}
