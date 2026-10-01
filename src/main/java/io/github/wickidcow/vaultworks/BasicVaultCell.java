package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.block.interfaces.BlockBreakRebarBlockHandler;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.datatypes.RebarSerializers;
import io.github.pylonmc.rebar.item.RebarItemSchema;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Vault;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

/**
 * A single-item high-capacity physical storage cell.
 *
 * The registered item and amount live on the block and are also copied into the
 * dropped Vault Cell item, so breaking/replacing a cell never sprays its contents.
 */
public class BasicVaultCell extends RebarBlock implements GuiRebarBlock, BlockBreakRebarBlockHandler {

    private static final NamespacedKey STORED_ITEM_KEY = new NamespacedKey("vaultworks", "stored_item");
    private static final NamespacedKey STORED_AMOUNT_KEY = new NamespacedKey("vaultworks", "stored_amount");
    private static final NamespacedKey PURGE_OVERFLOW_KEY = new NamespacedKey("vaultworks", "purge_overflow");
    private static final NamespacedKey LEGACY_RECOVERY_KEY = new NamespacedKey("vaultworks", "legacy_recovery");
    private static final NamespacedKey LEGACY_INVENTORY_KEY = new NamespacedKey("rebar", "virtual_inventory_items");

    private static final PersistentDataType<?, List<ItemStack>> ITEM_LIST_TYPE =
            RebarSerializers.LIST.listTypeFrom(RebarSerializers.ITEM_STACK);
    private static final PersistentDataType<PersistentDataContainer, Map<String, List<ItemStack>>> LEGACY_INVENTORY_TYPE =
            RebarSerializers.MAP.mapTypeFrom(RebarSerializers.STRING, ITEM_LIST_TYPE);

    protected ItemStack storedItem;
    protected long storedAmount;
    protected boolean purgeOverflow;
    protected final List<ItemStack> legacyRecovery = new ArrayList<>();

    private final List<VaultButton> guiButtons = new ArrayList<>();

    public BasicVaultCell(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        loadPortableState(context.getItem() == null ? null : context.getItem().getPersistentDataContainer());
        setVaultShell(false, context.getFacing());
    }

    public BasicVaultCell(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        loadPortableState(pdc);
        migrateLegacyInventory(pdc);
        setVaultShell(false, null);
    }

    @Override
    public void postInitialise() {
        super.postInitialise();
        VaultDisplayManager.update(this);
    }

    protected void setVaultShell(boolean ominous, org.bukkit.block.BlockFace facing) {
        if (getBlock().getType() != Material.VAULT) {
            getBlock().setType(Material.VAULT, false);
        }

        if (getBlock().getBlockData() instanceof Vault vault) {
            vault.setOminous(ominous);
            vault.setVaultState(Vault.State.INACTIVE);
            if (facing != null && facing.isCartesian() && facing.getModY() == 0) {
                vault.setFacing(facing);
            }
            getBlock().setBlockData(vault, false);
        }
    }

    protected String capacityConfigKey() {
        return "storage.basic-capacity";
    }

    public long getCapacity() {
        long configured = VaultWorks.instance().getConfig().getLong(capacityConfigKey(), 1_000_000L);
        return Math.max(1L, configured);
    }

    public ItemStack getStoredItem() {
        return storedItem == null ? null : storedItem.clone();
    }

    public long getStoredAmount() {
        return storedAmount;
    }

    public boolean isPurgeOverflow() {
        return purgeOverflow;
    }

    public boolean hasLegacyRecovery() {
        return !legacyRecovery.isEmpty();
    }

    protected boolean isRegistered() {
        return storedItem != null;
    }

    protected boolean canStore(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && !isVaultCell(stack)
                && (storedItem == null || storedItem.isSimilar(stack));
    }

    protected boolean isVaultCell(ItemStack stack) {
        RebarItemSchema schema = RebarItemSchema.fromStack(stack);
        if (schema == null) {
            return false;
        }

        String key = schema.getKey().toString();
        return key.equals("vaultworks:basic_vault_cell") || key.equals("vaultworks:powered_vault_cell");
    }

    protected void register(ItemStack stack) {
        if (stack == null || stack.isEmpty() || isVaultCell(stack) || hasLegacyRecovery()) {
            return;
        }
        storedItem = stack.asOne();
        storedAmount = 1L;
        VaultDisplayManager.update(this);
        refreshGuiItems();
    }

    protected void setStoredState(ItemStack stack, long amount) {
        if (stack != null && !stack.isEmpty()) {
            if (storedItem == null) {
                storedItem = stack.asOne();
            } else if (!storedItem.isSimilar(stack)) {
                throw new IllegalArgumentException("Attempted to store a different item type in a keyed Vault Cell");
            }
        }
        storedAmount = Math.max(0L, amount);
    }

    protected void refreshGuiItems() {
        guiButtons.forEach(VaultButton::refresh);
    }

    private void loadPortableState(PersistentDataContainerView pdc) {
        boolean defaultPurge = VaultWorks.instance().getConfig().getBoolean("storage.overflow-purge-default", false);
        purgeOverflow = defaultPurge;
        if (pdc == null) {
            return;
        }

        ItemStack item = pdc.get(STORED_ITEM_KEY, RebarSerializers.ITEM_STACK);
        Long amount = pdc.get(STORED_AMOUNT_KEY, RebarSerializers.LONG);
        Boolean purge = pdc.get(PURGE_OVERFLOW_KEY, RebarSerializers.BOOLEAN);
        List<ItemStack> recovery = pdc.get(LEGACY_RECOVERY_KEY, ITEM_LIST_TYPE);

        if (item != null && !item.isEmpty()) {
            storedItem = item.asOne();
        }
        storedAmount = Math.max(0L, amount == null ? 0L : amount);
        if (purge != null) {
            purgeOverflow = purge;
        }
        if (recovery != null) {
            for (ItemStack stack : recovery) {
                if (stack != null && !stack.isEmpty()) {
                    legacyRecovery.add(stack.clone());
                }
            }
        }
    }

    /**
     * Converts the early 54-slot prototype without deleting mixed inventories.
     * One item type becomes a bulk cell; mixed contents move to a recovery list.
     */
    private void migrateLegacyInventory(PersistentDataContainer pdc) {
        if (!pdc.has(LEGACY_INVENTORY_KEY) || storedItem != null || !legacyRecovery.isEmpty()) {
            return;
        }

        Map<String, List<ItemStack>> inventories = pdc.get(LEGACY_INVENTORY_KEY, LEGACY_INVENTORY_TYPE);
        if (inventories == null || inventories.isEmpty()) {
            return;
        }

        List<ItemStack> oldItems = new ArrayList<>();
        for (List<ItemStack> stacks : inventories.values()) {
            if (stacks == null) {
                continue;
            }
            for (ItemStack stack : stacks) {
                if (stack != null && !stack.isEmpty()) {
                    oldItems.add(stack.clone());
                }
            }
        }

        if (oldItems.isEmpty()) {
            return;
        }

        ItemStack first = oldItems.getFirst().asOne();
        boolean oneType = oldItems.stream().allMatch(first::isSimilar);
        if (oneType && !isVaultCell(first)) {
            long amount = 0L;
            for (ItemStack stack : oldItems) {
                amount = Math.addExact(amount, stack.getAmount());
            }
            storedItem = first;
            storedAmount = amount;
            VaultWorks.instance().getLogger().info("Migrated legacy Vault Cell at "
                    + locationText() + " into single-item bulk storage (" + amount + " items).");
        } else {
            legacyRecovery.addAll(oldItems);
            VaultWorks.instance().getLogger().warning("Vault Cell at " + locationText()
                    + " contained multiple legacy item types. Contents were preserved for recovery in its GUI.");
        }
    }

    private String locationText() {
        return getBlock().getWorld().getName() + " "
                + getBlock().getX() + "," + getBlock().getY() + "," + getBlock().getZ();
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        super.write(pdc);
        writePortableState(pdc);
        pdc.remove(LEGACY_INVENTORY_KEY);
    }

    private void writePortableState(PersistentDataContainer pdc) {
        if (storedItem == null) {
            pdc.remove(STORED_ITEM_KEY);
        } else {
            pdc.set(STORED_ITEM_KEY, RebarSerializers.ITEM_STACK, storedItem.asOne());
        }
        pdc.set(STORED_AMOUNT_KEY, RebarSerializers.LONG, Math.max(0L, storedAmount));
        pdc.set(PURGE_OVERFLOW_KEY, RebarSerializers.BOOLEAN, purgeOverflow);

        if (legacyRecovery.isEmpty()) {
            pdc.remove(LEGACY_RECOVERY_KEY);
        } else {
            pdc.set(LEGACY_RECOVERY_KEY, ITEM_LIST_TYPE, legacyRecovery.stream().map(ItemStack::clone).toList());
        }
    }

    @Override
    public ItemStack getDropItem(@NotNull BlockBreakContext context) {
        if (context instanceof BlockBreakContext.Delete) {
            return null;
        }

        boolean portableState = storedItem != null || storedAmount > 0L || !legacyRecovery.isEmpty();
        if (!context.normallyDrops() && !portableState) {
            return null;
        }

        ItemStack drop = getDefaultItem().createNewItemStack();
        drop.editPersistentDataContainer(this::writePortableState);

        List<Component> lore = new ArrayList<>();
        if (storedItem != null) {
            lore.add(Component.text("Stored: ").append(storedItem.effectiveName())
                    .append(Component.text(" × " + format(storedAmount))));
        } else {
            lore.add(Component.text("Stored: empty"));
        }
        lore.add(Component.text("Overflow purge: " + (purgeOverflow ? "ON" : "OFF")));
        if (!legacyRecovery.isEmpty()) {
            lore.add(Component.text("Legacy recovery stacks: " + legacyRecovery.size()));
        }
        ItemStackBuilder.of(drop).lore(lore);
        return drop;
    }

    @Override
    public void onBlockBreak(@NotNull List<ItemStack> drops, @NotNull BlockBreakContext context) {
        VaultDisplayManager.remove(this);
    }

    @Override
    public @NotNull Gui createGui() {
        guiButtons.clear();

        VaultButton deposit = remember(new DepositButton());
        VaultButton status = remember(new StatusButton());
        VaultButton registered = remember(new RegisteredItemButton());
        VaultButton withdraw = remember(new WithdrawButton());
        VaultButton purge = remember(new PurgeButton());
        VaultButton clear = remember(new ClearButton());
        VaultButton recovery = remember(new RecoveryButton());

        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# d # s # i # w #",
                        "# # # # # # # # #",
                        "# p # c # r # # #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.backgroundBlack())
                .addIngredient('d', deposit)
                .addIngredient('s', status)
                .addIngredient('i', registered)
                .addIngredient('w', withdraw)
                .addIngredient('p', purge)
                .addIngredient('c', clear)
                .addIngredient('r', recovery)
                .build();
    }

    private <T extends VaultButton> T remember(T button) {
        guiButtons.add(button);
        return button;
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return Component.text("Basic Vault Cell");
    }

    protected boolean registerFromMainHand(Player player) {
        if (storedItem != null) {
            player.sendMessage(Component.text("This Vault Cell is already registered."));
            return false;
        }
        if (hasLegacyRecovery()) {
            player.sendMessage(Component.text("Recover the legacy contents before registering this Vault Cell."));
            return false;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.isEmpty()) {
            player.sendMessage(Component.text("Hold the item you want to register, then click the center slot."));
            return false;
        }
        if (isVaultCell(hand)) {
            player.sendMessage(Component.text("Vault Cells cannot be stored inside Vault Cells."));
            return false;
        }

        ItemStack keyItem = hand.asOne();
        if (hand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(hand.getAmount() - 1);
        }
        register(keyItem);
        player.sendMessage(Component.text("Registered ").append(keyItem.effectiveName())
                .append(Component.text(" using 1 item.")));
        return true;
    }

    protected void quickDeposit(Player player) {
        if (storedItem == null) {
            player.sendMessage(Component.text("Register an item first using the center slot."));
            return;
        }
        if (hasLegacyRecovery()) {
            player.sendMessage(Component.text("Recover legacy contents before depositing."));
            return;
        }

        PlayerInventory inventory = player.getInventory();
        int storageSlots = inventory.getStorageContents().length;
        long capacity = getCapacity();
        long accepted = 0L;
        long voided = 0L;

        for (int slot = 0; slot < storageSlots; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.isEmpty() || !storedItem.isSimilar(stack)) {
                continue;
            }

            long free = Math.max(0L, capacity - storedAmount);
            int toStore = (int) Math.min((long) stack.getAmount(), free);
            int toRemove = purgeOverflow ? stack.getAmount() : toStore;

            if (toRemove <= 0) {
                continue;
            }

            accepted += toStore;
            voided += toRemove - toStore;
            storedAmount += toStore;

            if (toRemove >= stack.getAmount()) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(stack.getAmount() - toRemove);
            }
        }

        refreshGuiItems();
        if (accepted > 0 || voided > 0) {
            player.sendMessage(Component.text("Deposited " + format(accepted)
                    + (voided > 0 ? " and purged " + format(voided) + " overflow." : ".")));
        } else {
            player.sendMessage(Component.text(storedAmount >= capacity
                    ? "This Vault Cell is full."
                    : "No matching items were found in your inventory."));
        }
    }

    protected void withdraw(Player player, boolean single) {
        if (storedItem == null || storedAmount <= 0L) {
            player.sendMessage(Component.text("This Vault Cell has no stored items."));
            return;
        }

        long removed = 0L;
        int attempts = single ? 1 : player.getInventory().getStorageContents().length + 1;
        for (int i = 0; i < attempts && storedAmount > 0L; i++) {
            int requested = single ? 1 : (int) Math.min((long) storedItem.getMaxStackSize(), storedAmount);
            ItemStack out = storedItem.asQuantity(requested);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(out);
            int leftover = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            int delivered = requested - leftover;
            if (delivered <= 0) {
                break;
            }
            storedAmount -= delivered;
            removed += delivered;
            if (single) {
                break;
            }
        }

        refreshGuiItems();
        if (removed == 0L) {
            player.sendMessage(Component.text("Your inventory is full."));
        }
    }

    protected void togglePurge(Player player) {
        purgeOverflow = !purgeOverflow;
        refreshGuiItems();
        player.sendMessage(Component.text("Overflow purge " + (purgeOverflow ? "enabled." : "disabled.")));
    }

    protected void clearRegistration(Player player) {
        if (storedItem == null) {
            return;
        }
        if (storedAmount > 0L) {
            player.sendMessage(Component.text("Withdraw all stored items before clearing the registered item."));
            return;
        }
        if (hasLegacyRecovery()) {
            player.sendMessage(Component.text("Recover legacy contents first."));
            return;
        }

        storedItem = null;
        VaultDisplayManager.update(this);
        refreshGuiItems();
        player.sendMessage(Component.text("Vault Cell registration cleared."));
    }

    protected void recoverLegacy(Player player) {
        if (legacyRecovery.isEmpty()) {
            player.sendMessage(Component.text("There are no legacy contents to recover."));
            return;
        }

        Iterator<ItemStack> iterator = legacyRecovery.iterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack.clone());
            if (leftovers.isEmpty()) {
                iterator.remove();
                continue;
            }

            ItemStack remaining = leftovers.values().iterator().next();
            stack.setAmount(remaining.getAmount());
            break;
        }

        refreshGuiItems();
        if (legacyRecovery.isEmpty()) {
            player.sendMessage(Component.text("Legacy Vault contents fully recovered."));
        } else {
            player.sendMessage(Component.text("Recovered what would fit. "
                    + legacyRecovery.size() + " stack(s) remain."));
        }
    }

    protected static String format(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private abstract class VaultButton extends AbstractItem {
        void refresh() {
            notifyWindows();
        }
    }

    private final class StatusButton extends VaultButton {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            long capacity = getCapacity();
            String percent = capacity <= 0L ? "0" : String.format(Locale.US, "%.1f", (storedAmount * 100.0D) / capacity);
            return ItemStackBuilder.of(Material.CLOCK)
                    .name(Component.text("Vault Status"))
                    .lore(
                            Component.text("Stored: " + format(storedAmount) + " / " + format(capacity)),
                            Component.text("Used: " + percent + "%"),
                            Component.text(storedAmount > capacity ? "Over configured capacity — withdrawals remain safe." : "")
                    );
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        }
    }

    private final class RegisteredItemButton extends VaultButton {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            if (storedItem == null) {
                return ItemStackBuilder.of(Material.ITEM_FRAME)
                        .name(Component.text("Register Stored Item"))
                        .lore(
                                Component.text("Hold the item in your main hand."),
                                Component.text("Click here to register exactly 1 item.")
                        );
            }

            return ItemStackBuilder.of(storedItem.asOne())
                    .name(Component.text("Registered: ").append(storedItem.effectiveName()))
                    .lore(
                            Component.text("Stored: " + format(storedAmount)),
                            Component.text("The key remains registered at 0 until cleared.")
                    );
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            if (storedItem == null) {
                registerFromMainHand(player);
            }
        }
    }

    private final class DepositButton extends VaultButton {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(Material.HOPPER)
                    .name(Component.text("Quick Deposit"))
                    .lore(Component.text("Deposit all matching items from your inventory."));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            quickDeposit(player);
        }
    }

    private final class WithdrawButton extends VaultButton {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(Material.DROPPER)
                    .name(Component.text("Quick Withdraw"))
                    .lore(
                            Component.text("Left-click: fill your inventory"),
                            Component.text("Right-click: withdraw 1 item")
                    );
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            withdraw(player, clickType.isRightClick());
        }
    }

    private final class PurgeButton extends VaultButton {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            Material material = purgeOverflow ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
            return ItemStackBuilder.of(material)
                    .name(Component.text("Overflow Purge: " + (purgeOverflow ? "ON" : "OFF")))
                    .lore(
                            Component.text("When ON, matching input beyond capacity is destroyed."),
                            Component.text("Default is OFF for safety."),
                            Component.text("Click to toggle.")
                    );
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            togglePurge(player);
        }
    }

    private final class ClearButton extends VaultButton {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(Material.BARRIER)
                    .name(Component.text("Clear Registered Item"))
                    .lore(Component.text("Only available when stored amount is 0."));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            clearRegistration(player);
        }
    }

    private final class RecoveryButton extends VaultButton {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            if (legacyRecovery.isEmpty()) {
                return ItemStackBuilder.of(Material.GRAY_DYE)
                        .name(Component.text("Legacy Recovery"))
                        .lore(Component.text("No legacy contents are waiting."));
            }
            return ItemStackBuilder.of(Material.CHEST)
                    .name(Component.text("Recover Legacy Contents"))
                    .lore(
                            Component.text(legacyRecovery.size() + " stack(s) preserved from the 54-slot prototype."),
                            Component.text("Click to move as much as possible into your inventory.")
                    );
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            recoverLegacy(player);
        }
    }
}
