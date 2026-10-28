package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Makes an intentional sneak-pickaxe attempt break a VaultWorks cell without
 * the vanilla Vault's approximately 75-second mining delay.
 *
 * Only sets BlockDamageEvent.instaBreak: Paper still emits its ordinary
 * BlockBreakEvent, so Rebar supplies the portable drop and Towny/WorldGuard
 * can cancel the player's break. No raw block deletion or synthetic events.
 */
public final class VaultCellFastBreakListener implements Listener {
    private final VaultWorks plugin;

    public VaultCellFastBreakListener(VaultWorks plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        final RebarBlock block;
        try {
            block = BlockStorage.get(event.getBlock());
        } catch (IllegalArgumentException unavailable) {
            return; // Never force-load or alter an unavailable Rebar block.
        }
        if (!(block instanceof BasicVaultCell cell)) {
            return; // Ordinary trial Vaults and unrelated Rebar blocks are untouched.
        }

        Player player = event.getPlayer();
        ItemStack tool = event.getItemInHand();
        boolean pickaxe = tool != null && tool.getType() != Material.AIR
                && tool.getType().name().endsWith("_PICKAXE");
        boolean enabled = plugin.getConfig().getBoolean("storage.fast-sneak-pickup", true);
        if (VaultPickupPolicy.fastBreak(enabled, player.getGameMode() == GameMode.SURVIVAL,
                player.isSneaking(), pickaxe, cell.hasRecoveryLock())) {
            event.setInstaBreak(true);
        }
    }
}
