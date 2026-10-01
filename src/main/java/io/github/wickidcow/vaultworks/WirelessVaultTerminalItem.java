package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.event.api.annotation.MultiHandler;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.interfaces.InteractRebarItemHandler;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * Portable client for a loaded Vault Transmitter.
 *
 * Sneak-right-click a transmitter to bind. Normal right-click resolves only the
 * loaded transmitter registry, so the item never force-loads remote storage.
 */
public final class WirelessVaultTerminalItem extends RebarItem
        implements InteractRebarItemHandler {

    private static final NamespacedKey TRANSMITTER_ID_KEY = Objects.requireNonNull(
            NamespacedKey.fromString("vaultworks:wireless_terminal_transmitter")
    );

    public WirelessVaultTerminalItem(@NotNull ItemStack stack) {
        super(stack);
    }

    @Override
    @MultiHandler(
            priorities = {EventPriority.NORMAL, EventPriority.MONITOR},
            ignoreCancelled = true
    )
    public void onInteract(
            @NotNull PlayerInteractEvent event,
            @NotNull EventPriority priority
    ) {
        if (!event.getAction().isRightClick()
                || event.useItemInHand() == Event.Result.DENY) {
            return;
        }

        VaultTransmitter clicked = transmitterAt(event.getClickedBlock());
        boolean binding = event.getPlayer().isSneaking() && clicked != null;

        if (priority == EventPriority.NORMAL) {
            // A wireless-terminal click is an intentional VaultWorks action, not
            // a click-through into the block the player happened to target.
            event.setUseInteractedBlock(Event.Result.DENY);
            return;
        }

        if (priority != EventPriority.MONITOR) {
            return;
        }

        if (binding) {
            bind(clicked);
            event.getPlayer().sendMessage(Component.text(
                    "Wireless Vault Terminal bound to transmitter "
                            + shortId(clicked.getTransmitterId()) + "."
            ));
            return;
        }

        UUID id = boundTransmitter();
        if (id == null) {
            event.getPlayer().sendMessage(Component.text(
                    "This Wireless Vault Terminal is unbound. Sneak-right-click a Vault Transmitter."
            ));
            return;
        }

        VaultTransmitter transmitter = VaultWorks.instance()
                .wirelessRegistry()
                .resolve(id)
                .orElse(null);
        if (transmitter == null) {
            event.getPlayer().sendMessage(Component.text(
                    "Bound Vault Transmitter is not currently loaded. VaultWorks will not force-load it."
            ));
            return;
        }

        VaultTransmitter.AccessStatus status = transmitter.accessStatus(event.getPlayer());
        if (status != VaultTransmitter.AccessStatus.ALLOWED) {
            event.getPlayer().sendMessage(Component.text(switch (status) {
                case TRANSMITTER_UNPOWERED ->
                        "Bound Vault Transmitter is loaded but not powered.";
                case OUT_OF_RANGE ->
                        "Wireless Vault Terminal is out of range. Add an adjacent Vault Antenna or move closer.";
                case DIMENSIONAL_ANTENNA_REQUIRED ->
                        "Cross-dimensional access requires an adjacent Dimensional Vault Antenna.";
                case ALLOWED -> "";
            }));
            return;
        }

        transmitter.open(event.getPlayer());
    }

    private void bind(VaultTransmitter transmitter) {
        getStack().editPersistentDataContainer(pdc -> pdc.set(
                TRANSMITTER_ID_KEY,
                PersistentDataType.STRING,
                transmitter.getTransmitterId().toString()
        ));
    }

    private UUID boundTransmitter() {
        String raw = getStack()
                .getPersistentDataContainer()
                .get(TRANSMITTER_ID_KEY, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static VaultTransmitter transmitterAt(Block block) {
        if (block == null) {
            return null;
        }
        try {
            return BlockStorage.getAs(VaultTransmitter.class, block);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }
}
