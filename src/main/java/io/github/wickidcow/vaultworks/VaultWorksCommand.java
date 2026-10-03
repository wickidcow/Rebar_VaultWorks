package io.github.wickidcow.vaultworks;

import java.util.ArrayList;
import java.util.List;
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
        if (args.length != 1 || !args[0].equalsIgnoreCase("doctor")) {
            sender.sendMessage(Component.text("Usage: /" + label + " doctor | testpower"));
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
