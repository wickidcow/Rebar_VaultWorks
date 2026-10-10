package io.github.wickidcow.vaultworks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import io.github.pylonmc.rebar.registry.RebarRegistry;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Compact live health diagnostics for release smoke tests and administrators.
 */
final class VaultWorksCommand implements CommandExecutor {

    private final VaultWorks plugin;

    VaultWorksCommand(VaultWorks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("vaultworks.admin")) {
            sender.sendMessage(Component.text("You do not have permission to administer VaultWorks."));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("testpower")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Run /vaultworks testpower in game to receive the block."));
                return true;
            }
            ItemStack source = RebarRegistry.ITEMS.get(new NamespacedKey(plugin, "test_power_source")).createNewItemStack();
            if (player.getInventory().firstEmpty() < 0) {
                sender.sendMessage(Component.text("Make room in your inventory first."));
                return true;
            }
            player.getInventory().addItem(source);
            sender.sendMessage(Component.text("Received a Vault Test Power Source (1 MW). Connect Rebar wiring to any side. Remove it after testing."));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("recovery")) {
            if (args.length == 3 && args[1].equalsIgnoreCase("inspect")) {
                UUID id;
                try {
                    id = UUID.fromString(args[2]);
                } catch (IllegalArgumentException invalid) {
                    sender.sendMessage(Component.text("Use /vaultworks recovery inspect <incident UUID>."));
                    return true;
                }
                try {
                    VaultRecoveryStore.IncidentDetails details = plugin.recoveryStore().inspect(id);
                    if (details == null) {
                        sender.sendMessage(Component.text("No unresolved recovery incident matches that UUID."));
                        return true;
                    }
                    sender.sendMessage(Component.text("Recovery " + details.incident().id()
                            + " | state=" + details.incident().state().toUpperCase(java.util.Locale.ROOT)
                            + " | operation=" + details.incident().operation()));
                    sender.sendMessage(Component.text("Created: " + details.created()
                            + " | player=" + details.player()
                            + " | terminal=" + details.incident().root()));
                    sender.sendMessage(Component.text("Participants: " + details.incident().cells().size()
                            + " | captured cell snapshots=" + details.capturedCellSnapshots()
                            + " | inventory before/observed slots="
                            + details.inventoryBeforeSlots() + "/" + details.inventoryObservedSlots()));
                    sender.sendMessage(Component.text("Read-only inspection. Stop the server, preserve a full backup, and reconcile offline. No contents were replayed or unlocked."));
                } catch (java.io.IOException failure) {
                    sender.sendMessage(Component.text(
                            "Recovery evidence verification failed. Storage is locked; preserve the recovery folder and server logs."));
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "Recovery inspection failed", failure);
                }
                return true;
            }
            if (args.length != 1) {
                sender.sendMessage(Component.text("Usage: /" + label + " recovery [inspect <UUID>]"));
                return true;
            }
            VaultRecoveryStore store = plugin.recoveryStore();
            sender.sendMessage(Component.text("Vault transfer recovery: " + store.incidents().size()
                    + " unresolved record(s); all-storage lock=" + store.globallyLocked()));
            for (VaultRecoveryStore.Incident incident : store.incidents()) {
                sender.sendMessage(Component.text(incident.id() + " | " + incident.state().toUpperCase(java.util.Locale.ROOT) + " | " + incident.operation()
                        + " | cells=" + incident.cells().size() + " | location=" + incident.root()));
            }
            for (String fault : store.faults()) sender.sendMessage(Component.text(fault));
            if (!store.incidents().isEmpty() || store.globallyLocked()) {
                sender.sendMessage(Component.text("Evidence: plugins/VaultWorks/recovery/. Stop the server and preserve this folder with the world and player data before reviewing. No items are replayed or unlocked automatically."));
            }
            return true;
        }
        if (args.length != 1 || !args[0].equalsIgnoreCase("doctor")) {
            sender.sendMessage(Component.text("Usage: /" + label + " doctor | recovery [inspect <UUID>] | testpower"));
            return true;
        }

        List<String> failures = new ArrayList<>();

        if (VaultWorks.instance() != plugin) {
            failures.add("plugin singleton mismatch");
        }
        if (plugin.policy() == null) {
            failures.add("power policy unavailable");
        }
        if (plugin.storagePolicy() == null) {
            failures.add("storage policy unavailable");
        }
        if (plugin.wirelessPolicy() == null) {
            failures.add("wireless policy unavailable");
        }
        if (plugin.registeredRecipeCount() != VaultWorksContentCatalog.ALL_IDS.size()) {
            failures.add(
                    "recipe/catalog mismatch "
                            + plugin.registeredRecipeCount()
                            + "/" + VaultWorksContentCatalog.ALL_IDS.size()
            );
        }

        sender.sendMessage(Component.text(
                "VaultWorks " + plugin.getPluginMeta().getVersion()
                        + " | recipes=" + plugin.registeredRecipeCount()
                        + " | loaded-cells=" + VaultEndpointRegistry.loadedCount()
                        + " | loaded-transmitters=" + plugin.wirelessRegistry().loadedCount()
        ));
        sender.sendMessage(Component.text(
                "Power: base=" + plugin.policy().baseWatts()
                        + "W + " + plugin.policy().wattsPerVault() + "W/vault"
                        + " | cargo=" + plugin.policy().transferRate() + "/tick"
        ));
        sender.sendMessage(Component.text(
                "Storage: basic=" + plugin.storagePolicy().basicCapacity()
                        + " | powered=" + plugin.storagePolicy().poweredCapacity()
                        + " | network-nodes=" + plugin.storagePolicy().maxNetworkNodes()
                        + " | terminal-types=" + plugin.storagePolicy().maxTerminalItemTypes()
        ));
        sender.sendMessage(Component.text(
                "Wireless: transmitter=" + plugin.wirelessPolicy().transmitterWatts()
                        + "W | range=" + plugin.wirelessPolicy().baseRangeBlocks()
                        + " / " + plugin.wirelessPolicy().antennaRangeBlocks()
                        + " blocks | cross-dimension="
                        + plugin.wirelessPolicy().allowCrossDimension()
        ));

        if (VaultEndpointRegistry.conflictedCount() > 0) {
            failures.add(VaultEndpointRegistry.conflictedCount() + " loaded Vault Cells have locked duplicate identities");
        }
        long prepared = plugin.recoveryStore().incidents().stream()
                .filter(incident -> incident.state().equals("prepared")).count();
        long open = plugin.recoveryStore().incidents().size() - prepared;
        sender.sendMessage(Component.text("Recovery: open=" + open + " | prepared=" + prepared
                + " | global-lock=" + plugin.recoveryStore().globallyLocked()));
        if (!plugin.recoveryStore().incidents().isEmpty() || plugin.recoveryStore().globallyLocked()) {
            failures.add("unresolved transfer recovery; run /vaultworks recovery");
        }
        boolean pass = failures.isEmpty();
        if (!pass) {
            sender.sendMessage(Component.text("VaultWorks Doctor failures: " + String.join(", ", failures)));
        }

        String result = "VaultWorks Doctor result: " + (pass ? "PASS" : "FAIL");
        sender.sendMessage(Component.text(result));
        plugin.getLogger().info(result);
        return true;
    }
}
