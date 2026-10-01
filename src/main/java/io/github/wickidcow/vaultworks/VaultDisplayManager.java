package io.github.wickidcow.vaultworks;

import java.util.ArrayList;
import java.util.List;
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

    private VaultDisplayManager() {
    }

    public static void update(BasicVaultCell cell) {
        update(cell, cell.isOperational());
    }

    public static void update(BasicVaultCell cell, boolean online) {
        ItemStack registered = cell.getStoredItem();
        String owner = owner(cell);
        List<ItemDisplay> displays = findDisplays(cell, owner);

        if (registered == null || registered.isEmpty()) {
            displays.forEach(Entity::remove);
            return;
        }

        ItemDisplay display;
        if (displays.isEmpty()) {
            Location center = cell.getBlock().getLocation().add(0.5D, 0.53D, 0.5D);
            display = cell.getBlock().getWorld().spawn(center, ItemDisplay.class);
        } else {
            display = displays.removeFirst();
            displays.forEach(Entity::remove);
        }

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
        findDisplays(cell, owner(cell)).forEach(Entity::remove);
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
