package io.github.wickidcow.vaultworks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Tokens are private to the player and discarded on close, quit or disable. */
final class VaultDeleteConfirmations implements Listener {
    private final Map<UUID, VaultDeleteSequence> sequences = new HashMap<>();

    VaultDeleteSequence get(UUID player) { return sequences.get(player); }
    void put(UUID player, VaultDeleteSequence sequence) { sequences.put(player, sequence); }
    void remove(UUID player) { sequences.remove(player); }
    void clear() { sequences.clear(); }

    @EventHandler
    public void onClose(InventoryCloseEvent event) { remove(event.getPlayer().getUniqueId()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { remove(event.getPlayer().getUniqueId()); }
}
