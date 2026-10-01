package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.block.interfaces.BlockBreakRebarBlockHandler;
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.SimpleElectricRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.UnloadRebarBlockHandler;
import io.github.pylonmc.rebar.electricity.nodes.ElectricNodeType;
import io.github.pylonmc.rebar.event.RebarBlockUnloadEvent;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * Powered wireless access point for one explicitly linked Vault network.
 *
 * The transmitter is itself a Vault Terminal, so local and remote sessions share
 * the exact same search and storage-transaction implementation.
 */
public final class VaultTransmitter extends VaultTerminal implements
        SimpleElectricRebarBlock,
        DirectionalRebarBlock,
        UnloadRebarBlockHandler,
        BlockBreakRebarBlockHandler {

    private static final NamespacedKey TRANSMITTER_ID_KEY = Objects.requireNonNull(
            NamespacedKey.fromString("vaultworks:transmitter_id")
    );

    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH,
            BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    public enum AccessStatus {
        ALLOWED,
        TRANSMITTER_UNPOWERED,
        OUT_OF_RANGE,
        DIMENSIONAL_ANTENNA_REQUIRED
    }

    private UUID transmitterId;

    public VaultTransmitter(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        transmitterId = UUID.randomUUID();

        BlockFace front = context.getFacing();
        setFacing(front);
        createSimpleElectricPort(ElectricNodeType.CONSUMER, front.getOppositeFace());
        refreshPowerDemand();
    }

    public VaultTransmitter(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        transmitterId = loadId(pdc.get(TRANSMITTER_ID_KEY, PersistentDataType.STRING));
    }

    @Override
    public void postInitialise() {
        super.postInitialise();
        refreshPowerDemand();

        VaultWirelessRegistry registry = VaultWorks.instance().wirelessRegistry();
        if (!registry.register(this)) {
            UUID duplicate = transmitterId;
            do {
                transmitterId = UUID.randomUUID();
            } while (!registry.register(this));

            VaultWorks.instance().getLogger().warning(
                    "Duplicate Vault Transmitter id " + duplicate
                            + " detected at " + locationText()
                            + "; assigned replacement id " + transmitterId
            );
        }
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        super.write(pdc);
        pdc.set(TRANSMITTER_ID_KEY, PersistentDataType.STRING, transmitterId.toString());
    }

    @Override
    public void onUnload(
            @NotNull RebarBlockUnloadEvent event,
            @NotNull EventPriority priority
    ) {
        VaultWorks.instance().wirelessRegistry().unregister(this);
    }

    @Override
    public void onBlockBreak(
            @NotNull List<ItemStack> drops,
            @NotNull BlockBreakContext context
    ) {
        VaultWorks.instance().wirelessRegistry().unregister(this);
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return Component.text("Vault Transmitter — Network Access");
    }

    public UUID getTransmitterId() {
        return transmitterId;
    }

    public boolean isWirelessOnline() {
        try {
            return isPowered();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    public AccessStatus accessStatus(Player player) {
        if (!isWirelessOnline()) {
            return AccessStatus.TRANSMITTER_UNPOWERED;
        }

        if (player.getWorld().equals(getBlock().getWorld())) {
            double range = effectiveRange();
            double dx = player.getLocation().getX() - (getBlock().getX() + 0.5D);
            double dy = player.getLocation().getY() - (getBlock().getY() + 0.5D);
            double dz = player.getLocation().getZ() - (getBlock().getZ() + 0.5D);
            return dx * dx + dy * dy + dz * dz <= range * range
                    ? AccessStatus.ALLOWED
                    : AccessStatus.OUT_OF_RANGE;
        }

        boolean crossWorldEnabled = VaultWorks.instance()
                .getConfig()
                .getBoolean("wireless.allow-cross-dimension", true);
        return crossWorldEnabled && hasDimensionalAntenna()
                ? AccessStatus.ALLOWED
                : AccessStatus.DIMENSIONAL_ANTENNA_REQUIRED;
    }

    public double effectiveRange() {
        double base = boundedRange(
                VaultWorks.instance().getConfig().getDouble("wireless.base-range-blocks", 64.0D)
        );
        if (!hasAntenna()) {
            return base;
        }

        return Math.max(
                base,
                boundedRange(
                        VaultWorks.instance().getConfig().getDouble(
                                "wireless.antenna-range-blocks",
                                512.0D
                        )
                )
        );
    }

    public boolean hasAntenna() {
        for (BlockFace face : FACES) {
            try {
                if (BlockStorage.get(getBlock().getRelative(face)) instanceof VaultAntenna) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // Unloaded/invalid adjacent blocks simply provide no antenna.
            }
        }
        return false;
    }

    public boolean hasDimensionalAntenna() {
        for (BlockFace face : FACES) {
            try {
                if (BlockStorage.get(getBlock().getRelative(face))
                        instanceof DimensionalVaultAntenna) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // Unloaded/invalid adjacent blocks simply provide no antenna.
            }
        }
        return false;
    }

    private void refreshPowerDemand() {
        double watts = VaultWorks.instance()
                .getConfig()
                .getDouble("wireless.transmitter-watts", 32.0D);
        setRequiredPower(Math.max(1.0D, Math.min(watts, 1_000_000.0D)));
    }

    private static double boundedRange(double configured) {
        if (!Double.isFinite(configured)) {
            return 64.0D;
        }
        return Math.max(8.0D, Math.min(configured, 8192.0D));
    }

    private static UUID loadId(String raw) {
        if (raw != null) {
            try {
                return UUID.fromString(raw);
            } catch (IllegalArgumentException ignored) {
                // Fall through to a new identity.
            }
        }
        return UUID.randomUUID();
    }

    private String locationText() {
        return getBlock().getWorld().getName()
                + " " + getBlock().getX()
                + "," + getBlock().getY()
                + "," + getBlock().getZ();
    }
}
