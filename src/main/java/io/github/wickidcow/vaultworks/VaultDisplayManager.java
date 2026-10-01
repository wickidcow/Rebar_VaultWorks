package io.github.wickidcow.vaultworks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;

/**
 * Maintains the single non-interactive item floating inside a Vault Cell.
 *
 * There is no repeating task: the display is reconciled when a cell loads or
 * when its registered item changes.
 */
public final class VaultDisplayManager {

    private static final NamespacedKey OWNER_KEY = new NamespacedKey("vaultworks", "vault_display_owner");
    private static final float SCALE = 0.46F;
    private static final Map<String, UUID> TRACKED = new ConcurrentHashMap<>();

    private VaultDisplayManager() {
    }

    public static void update(BasicVaultCell cell) {
        update(cell, cell.isOperational());
    }

    public static void update(BasicVaultCell cell, boolean online) {
        ItemStack registered = cell.getStoredItem();
        String owner = owner(cell);
        ItemDisplay display = getTracked(owner);

        if (registered == null || registered.isEmpty()) {
            remove(cell);
            return;
        }

        if (display == null) {
            List<ItemDisplay> displays = findDisplays(cell, owner);
            if (displays.isEmpty()) {
                Location center = cell.getBlock().getLocation().add(0.5D, 0.53D, 0.5D);
                display = cell.getBlock().getWorld().spawn(center, ItemDisplay.class);
            } else {
                display = displays.removeFirst();
                displays.forEach(Entity::remove);
            }
        }

        TRACKED.put(owner, display.getUniqueId());

        ItemStack shown = registered.asOne();
        display.setItemStack(shown);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setBillboard(Display.Billboard.CENTER);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.setPersistent(true);
        display.setGlowing(online);
        display.setBrightness(online ? new Display.Brightness(15, 15) : null);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
        display.getPersistentDataContainer().set(OWNER_KEY, PersistentDataType.STRING, owner);

        Transformation transformation = display.getTransformation();
        transformation.getScale().set(SCALE, SCALE, SCALE);
        display.setTransformation(transformation);

        Location target = cell.getBlock().getLocation().add(0.5D, 0.53D, 0.5D);
        if (display.getLocation().distanceSquared(target) > 0.0001D) {
            display.teleport(target);
        }
    }

    public static void remove(BasicVaultCell cell) {
        String owner = owner(cell);
        UUID tracked = TRACKED.remove(owner);
        if (tracked != null) {
            Entity entity = Bukkit.getServer().getEntity(tracked);
            if (entity instanceof ItemDisplay) {
                entity.remove();
            }
        }

        // Recovery cleanup for a stale cache or duplicate entity after an interrupted reload.
        findDisplays(cell, owner).forEach(Entity::remove);
    }

    public static void forget(BasicVaultCell cell) {
        TRACKED.remove(owner(cell));
    }

    private static ItemDisplay getTracked(String owner) {
        UUID uuid = TRACKED.get(owner);
        if (uuid == null) {
            return null;
        }

        Entity entity = Bukkit.getServer().getEntity(uuid);
        if (entity instanceof ItemDisplay display && display.isValid()) {
            return display;
        }

        TRACKED.remove(owner, uuid);
        return null;
    }

    private static List<ItemDisplay> findDisplays(BasicVaultCell cell, String owner) {
        List<ItemDisplay> result = new ArrayList<>();
        Location center = cell.getBlock().getLocation().add(0.5D, 0.5D, 0.5D);
        for (Entity entity : cell.getBlock().getWorld().getNearbyEntities(center, 1.0D, 1.0D, 1.0D)) {
            if (!(entity instanceof ItemDisplay display)) {
                continue;
            }
            String displayOwner = display.getPersistentDataContainer().get(OWNER_KEY, PersistentDataType.STRING);
            if (owner.equals(displayOwner)) {
                result.add(display);
            }
        }
        return result;
    }

    private static String owner(BasicVaultCell cell) {
        return cell.getBlock().getWorld().getUID() + ":"
                + cell.getBlock().getX() + ":"
                + cell.getBlock().getY() + ":"
                + cell.getBlock().getZ();
    }
}
