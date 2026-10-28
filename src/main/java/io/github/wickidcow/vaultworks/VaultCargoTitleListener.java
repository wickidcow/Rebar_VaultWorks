package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.event.RebarBlockBreakEvent;
import io.github.pylonmc.rebar.event.RebarBlockLoadEvent;
import io.github.pylonmc.rebar.event.RebarBlockPlaceEvent;
import io.github.pylonmc.rebar.event.RebarBlockUnloadEvent;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Refreshes already-open Vault titles when a rear Cargo Node is added or
 * removed. No background polling or forced chunk loads, and no cargo transfer
 * data is changed. Rebar's own lifecycle remains authoritative.
 */
public final class VaultCargoTitleListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlace(RebarBlockPlaceEvent event) {
        refresh(event.getRebarBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(RebarBlockBreakEvent event) {
        refresh(event.getRebarBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoad(RebarBlockLoadEvent event) {
        refresh(event.getRebarBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnload(RebarBlockUnloadEvent event) {
        refresh(event.getRebarBlock());
    }

    private void refresh(RebarBlock candidate) {
        if (!(candidate instanceof VaultCargoNode cargoNode)) return;
        BlockFace targetFace = cargoNode.getFacing();
        if (targetFace == null || !targetFace.isCartesian() || targetFace.getModY() != 0) return;

        Block target = cargoNode.getBlock().getRelative(targetFace);
        if (!target.getWorld().isChunkLoaded(target.getX() >> 4, target.getZ() >> 4)) return;
        try {
            if (BlockStorage.get(target) instanceof BasicVaultCell cell) {
                cell.refreshCargoStatus();
            }
        } catch (IllegalArgumentException | IllegalStateException unloaded) {
            // Display status must never force-load a chunk or block cargo events.
        }
    }
}
