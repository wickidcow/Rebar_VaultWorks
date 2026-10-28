package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.event.RebarBlockBreakEvent;
import io.github.pylonmc.rebar.event.RebarBlockLoadEvent;
import io.github.pylonmc.rebar.event.RebarBlockPlaceEvent;
import io.github.pylonmc.rebar.event.RebarBlockUnloadEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Network connection indicators must reflect real loaded VaultWorks topology,
 * not the presence of any nearby Cargo duct. Invalidates lazily cached BFS
 * connectivity on lifecycle changes. One coalesced next-tick update refreshes
 * only open cell GUIs after Rebar finishes breaking/loading blocks.
 */
public final class VaultNetworkTitleListener implements Listener {
    private final VaultWorks plugin;
    private boolean refreshQueued;

    VaultNetworkTitleListener(VaultWorks plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlace(RebarBlockPlaceEvent event) {
        changed(event.getRebarBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(RebarBlockBreakEvent event) {
        changed(event.getRebarBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoad(RebarBlockLoadEvent event) {
        changed(event.getRebarBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnload(RebarBlockUnloadEvent event) {
        changed(event.getRebarBlock());
    }

    private void changed(RebarBlock block) {
        if (!(block instanceof VaultTerminal
                || block instanceof VaultIndex
                || block instanceof VaultLinkCable
                || block instanceof VaultPowerBase)) {
            return;
        }

        VaultTerminalReachability.invalidate();
        if (refreshQueued) return;
        refreshQueued = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            refreshQueued = false;
            // Unload events can fire before the old node leaves BlockStorage.
            VaultTerminalReachability.invalidate();
            for (BasicVaultCell cell : VaultEndpointRegistry.loadedCells()) {
                cell.refreshNetworkStatus();
            }
        });
    }
}
