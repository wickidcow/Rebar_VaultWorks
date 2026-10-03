package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.block.interfaces.BlockBreakRebarBlockHandler;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.UnloadRebarBlockHandler;
import io.github.pylonmc.rebar.datatypes.RebarSerializers;
import io.github.pylonmc.rebar.event.RebarBlockUnloadEvent;
import io.github.pylonmc.rebar.item.RebarItemSchema;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.papermc.paper.persistence.PersistentDataContainerView;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Vault;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
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
public class BasicVaultCell extends RebarBlock implements GuiRebarBlock, BlockBreakRebarBlockHandler, UnloadRebarBlockHandler {

    public static final int MAX_COLUMN_HEIGHT = 6;

    private static final NamespacedKey STORED_ITEM_KEY = new NamespacedKey("vaultworks", "stored_item");
    private static final NamespacedKey STORED_AMOUNT_KEY = new NamespacedKey("vaultworks", "stored_amount");
    private static final NamespacedKey PURGE_OVERFLOW_KEY = new NamespacedKey("vaultworks", "purge_overflow");
    private static final NamespacedKey LEGACY_RECOVERY_KEY = new NamespacedKey("vaultworks", "legacy_recovery");
    private static final NamespacedKey ENDPOINT_ID_KEY = new NamespacedKey("vaultworks", "endpoint_id");
    private static final NamespacedKey ENDPOINT_REVISION_KEY = new NamespacedKey("vaultworks", "endpoint_revision");
    private static final NamespacedKey IDENTITY_CONFLICT_KEY = new NamespacedKey("vaultworks", "identity_conflict");
    private static final NamespacedKey LEGACY_INVENTORY_KEY = new NamespacedKey("rebar", "virtual_inventory_items");

    private static final PersistentDataType<?, List<ItemStack>> ITEM_LIST_TYPE =
            RebarSerializers.LIST.listTypeFrom(RebarSerializers.ITEM_STACK);
    private static final PersistentDataType<PersistentDataContainer, Map<String, List<ItemStack>>> LEGACY_INVENTORY_TYPE =
            RebarSerializers.MAP.mapTypeFrom(RebarSerializers.STRING, ITEM_LIST_TYPE);

    protected ItemStack storedItem;
    protected long storedAmount;
    protected boolean purgeOverflow;
    protected final List<ItemStack> legacyRecovery = new ArrayList<>();

    private UUID endpointId;
    private long endpointRevision;
    private boolean identityConflict;

    private final List<VaultButton> guiButtons = new ArrayList<>();

    public BasicVaultCell(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        loadPortableState(context.getItem() == null ? null : context.getItem().getPersistentDataContainer());
        ensureEndpointIdentity();
        VaultPowerBase base = getPowerBase();
        setVaultShell(false, base == null ? context.getFacing() : base.getFacing());
    }

    public BasicVaultCell(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        loadPortableState(pdc);
        ensureEndpointIdentity();
        migrateLegacyInventory(pdc);
        setVaultShell(false, null);
    }

    @Override
    public void postInitialise() {
        super.postInitialise();
        VaultEndpointRegistry.register(this);
        VaultDisplayManager.update(this);

        VaultPowerBase base = getPowerBase();
        if (base == null) {
            updatePowerVisual(false);
        } else {
            base.refreshColumnVisuals();
        }
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

    protected long configuredCapacity(StoragePolicy policy) {
        return policy.basicCapacity();
    }

    public long getCapacity() {
        return configuredCapacity(VaultWorks.instance().storagePolicy());
    }

    public ItemStack getStoredItem() {
        return storedItem == null ? null : storedItem.clone();
    }

    public long getStoredAmount() {
        return storedAmount;
    }

    synchronized long networkAvailable(ItemStack identity) {
        if (!isOperational()
                || identity == null
                || identity.isEmpty()
                || storedItem == null
                || storedAmount <= 0L
                || !storedItem.isSimilar(identity)
                || hasLegacyRecovery()) {
            return 0L;
        }
        return storedAmount;
    }

    synchronized long networkFreeCapacity(ItemStack identity) {
        if (!isOperational()
                || identity == null
                || identity.isEmpty()
                || storedItem == null
                || !storedItem.isSimilar(identity)
                || isVaultCell(identity)
                || hasLegacyRecovery()) {
            return 0L;
        }
        return Math.max(0L, getCapacity() - storedAmount);
    }

    synchronized long networkInsert(ItemStack identity, long requested) {
        if (requested <= 0L) {
            return 0L;
        }

        long accepted = Math.min(requested, networkFreeCapacity(identity));
        if (accepted <= 0L) {
            return 0L;
        }

        storedAmount += accepted;
        touchRevision();
        refreshGuiItems();
        return accepted;
    }

    synchronized long networkExtract(ItemStack identity, long requested) {
        if (requested <= 0L) {
            return 0L;
        }

        long removed = VaultStorageMath.withdrawable(
                networkAvailable(identity),
                requested
        );
        if (removed <= 0L) {
            return 0L;
        }

        storedAmount -= removed;
        touchRevision();
        refreshGuiItems();
        return removed;
    }

    synchronized void networkRollbackExtract(ItemStack identity, long amount) {
        if (amount <= 0L
                || identity == null
                || identity.isEmpty()
                || storedItem == null
                || !storedItem.isSimilar(identity)) {
            throw new IllegalStateException("Cannot restore a Vault transaction to a different cell identity");
        }

        if (storedAmount > Long.MAX_VALUE - amount) {
            throw new IllegalStateException("Vault transaction rollback would overflow stored amount");
        }
        storedAmount += amount;
        touchRevision();
        refreshGuiItems();
    }

    synchronized void restoreTransactionAmount(ItemStack identity, long amount) {
        if (storedItem == null || !storedItem.isSimilar(identity) || amount < 0L) {
            throw new IllegalStateException("Cannot restore a changed Vault identity");
        }
        storedAmount = amount;
        touchRevision();
        refreshGuiItems();
    }

    public boolean isPurgeOverflow() {
        return purgeOverflow;
    }

    public boolean hasLegacyRecovery() {
        return !legacyRecovery.isEmpty();
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public long getEndpointRevision() {
        return endpointRevision;
    }

    public boolean hasIdentityConflict() {
        return identityConflict;
    }

    public boolean hasRecoveryLock() {
        return VaultWorks.instance().recoveryStore().locked(endpointId)
                || VaultWorks.instance().recoveryStore().locked(VaultTransferRollback.key(getBlock()));
    }

    void flagIdentityConflict() {
        if (!identityConflict) {
            identityConflict = true;
            touchRevision();
            updatePowerVisual(false);
        }
    }

    private void ensureEndpointIdentity() {
        if (endpointId == null) {
            endpointId = UUID.randomUUID();
            endpointRevision = Math.max(1L, endpointRevision);
        }
    }

    void touchRevision() {
        endpointRevision = VaultRevisionMath.next(endpointRevision);
    }

    public VaultPowerBase getPowerBase() {
        Block cursor = getBlock().getRelative(BlockFace.DOWN);
        for (int depth = 1; depth <= MAX_COLUMN_HEIGHT; depth++) {
            RebarBlock rebarBlock;
            try {
                rebarBlock = BlockStorage.get(cursor);
            } catch (IllegalArgumentException ignored) {
                return null;
            }

            if (rebarBlock instanceof VaultPowerBase base) {
                return base;
            }

            // Power conducts vertically only through another contiguous Vault Cell.
            if (!(rebarBlock instanceof BasicVaultCell)) {
                return null;
            }

            cursor = cursor.getRelative(BlockFace.DOWN);
        }
        return null;
    }

    public boolean isOperational() {
        if (identityConflict || hasRecoveryLock()) {
            return false;
        }
        VaultPowerBase base = getPowerBase();
        return base != null && base.isOnline();
    }

    void updatePowerVisual(boolean online) {
        // Keep the vanilla Vault machinery inert. Power is represented by the
        // shared base plus the brightness/glow of our own floating item display.
        if (getBlock().getBlockData() instanceof Vault vault && vault.getVaultState() != Vault.State.INACTIVE) {
            vault.setVaultState(Vault.State.INACTIVE);
            getBlock().setBlockData(vault, false);
        }
        VaultDisplayManager.update(this, online);
        refreshGuiItems();
    }

    protected boolean requireOperational(Player player) {
        try {
            if (BlockStorage.get(getBlock()) != this
                    || !player.getWorld().equals(getBlock().getWorld())
                    || player.getLocation().distanceSquared(getBlock().getLocation().add(0.5, 0.5, 0.5)) > 64.0) {
                player.closeInventory();
                return false;
            }
        } catch (IllegalArgumentException unavailable) {
            player.closeInventory();
            return false;
        }
        if (hasRecoveryLock()) {
            player.sendMessage(Component.text("Vault locked after a failed transfer. An administrator must review /vaultworks recovery."));
            return false;
        }
        if (identityConflict) {
            player.sendMessage(Component.text(
                    "Vault locked: duplicate endpoint identity detected. Stored contents are preserved, but mutation is disabled."
            ));
            return false;
        }

        if (isOperational()) {
            return true;
        }

        player.sendMessage(Component.text(
                "Vault offline. Place it in a contiguous column of up to 6 Vault Cells above a powered Vault Power Base."
        ));
        return false;
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
        touchRevision();
        VaultDisplayManager.update(this);
        refreshGuiItems();
    }

    protected void setStoredState(ItemStack stack, long amount) {
        ItemStack previousItem = storedItem == null ? null : storedItem.asOne();
        long previousAmount = storedAmount;

        if (stack != null && !stack.isEmpty()) {
            if (storedItem == null) {
                storedItem = stack.asOne();
            } else if (!storedItem.isSimilar(stack)) {
                throw new IllegalArgumentException("Attempted to store a different item type in a keyed Vault Cell");
            }
        }
        storedAmount = Math.max(0L, amount);

        boolean itemChanged = previousItem == null
                ? storedItem != null
                : storedItem == null || !previousItem.isSimilar(storedItem);
        if (itemChanged || previousAmount != storedAmount) {
            touchRevision();
        }
    }

    void applyCargoAmount(ItemStack stack, long amount) {
        if (!isOperational() || hasLegacyRecovery() || storedItem == null || amount < 0L
                || (amount > 0L && (stack == null || !storedItem.isSimilar(stack)))) return;
        long previousAmount = storedAmount;
        storedAmount = VaultStorageMath.applyProposedAmount(
                storedAmount,
                amount,
                getCapacity(),
                purgeOverflow
        );
        if (previousAmount != storedAmount) {
            touchRevision();
        }
        refreshGuiItems();
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
        UUID loadedEndpointId = pdc.get(ENDPOINT_ID_KEY, RebarSerializers.UUID);
        Long loadedRevision = pdc.get(ENDPOINT_REVISION_KEY, RebarSerializers.LONG);
        Boolean loadedConflict = pdc.get(IDENTITY_CONFLICT_KEY, RebarSerializers.BOOLEAN);

        if (loadedEndpointId != null) {
            endpointId = loadedEndpointId;
        }
        endpointRevision = Math.max(0L, loadedRevision == null ? 0L : loadedRevision);
        identityConflict = loadedConflict != null && loadedConflict;

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
        pdc.set(ENDPOINT_ID_KEY, RebarSerializers.UUID, endpointId);
        pdc.set(ENDPOINT_REVISION_KEY, RebarSerializers.LONG, Math.max(0L, endpointRevision));
        pdc.set(IDENTITY_CONFLICT_KEY, RebarSerializers.BOOLEAN, identityConflict);

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
        boolean stackableEmpty = storedAmount == 0L && legacyRecovery.isEmpty() && !identityConflict && !hasRecoveryLock();
        boolean defaultPurge = VaultWorks.instance().getConfig().getBoolean("storage.overflow-purge-default", false);
        if (stackableEmpty && storedItem == null && purgeOverflow == defaultPurge) {
            return drop;
        }
        drop.setData(DataComponentTypes.MAX_STACK_SIZE, stackableEmpty ? 64 : 1);
        drop.editPersistentDataContainer(pdc -> {
            writePortableState(pdc);
            if (stackableEmpty) {
                // No contents can be duplicated. A freshly placed empty cell gets
                // a new endpoint; matching empty registration/settings can stack.
                pdc.remove(ENDPOINT_ID_KEY);
                pdc.remove(ENDPOINT_REVISION_KEY);
                pdc.remove(IDENTITY_CONFLICT_KEY);
            }
        });

        List<Component> lore = new ArrayList<>();
        if (storedItem != null) {
            lore.add(Component.text("Stored: ").append(storedItem.effectiveName())
                    .append(Component.text(" × " + format(storedAmount))));
        } else {
            lore.add(Component.text("Stored: empty"));
        }
        lore.add(Component.text("Overflow purge: " + (purgeOverflow ? "ON" : "OFF")));
        if (!stackableEmpty) lore.add(Component.text("Endpoint: " + endpointId.toString().substring(0, 8)
                + " / rev " + endpointRevision));
        else lore.add(Component.text("Empty cells with matching settings stack up to 64."));
        if (identityConflict) {
            lore.add(Component.text("LOCKED: duplicate endpoint identity"));
        }
        if (hasRecoveryLock()) lore.add(Component.text("LOCKED: transfer recovery requires administrator review"));
        if (!legacyRecovery.isEmpty()) {
            lore.add(Component.text("Legacy recovery stacks: " + legacyRecovery.size()));
        }
        return ItemStackBuilder.of(drop).lore(lore).build();
    }

    @Override
    public boolean onPreBlockBreak(@NotNull BlockBreakContext context) {
        return !hasRecoveryLock();
    }

    @Override
    public void onUnload(@NotNull RebarBlockUnloadEvent event, @NotNull EventPriority priority) {
        // The ItemDisplay itself is persistent and unloads with the chunk. Forget only
        // the live UUID cache so the next load can recover it without retaining cells forever.
        VaultEndpointRegistry.unregister(this);
        VaultDisplayManager.forget(this);
    }

    @Override
    public void onBlockBreak(@NotNull List<ItemStack> drops, @NotNull BlockBreakContext context) {
        VaultEndpointRegistry.unregister(this);
        VaultDisplayManager.remove(this);

        VaultPowerBase base = getPowerBase();
        if (base != null) {
            base.refreshAfterCellBreak(this);
        }

        // Breaking any cell in the column immediately de-energizes every cell above
        // the gap. Their contents remain portable and untouched.
        for (int height = 1; height <= MAX_COLUMN_HEIGHT; height++) {
            BasicVaultCell upper;
            try {
                upper = BlockStorage.getAs(BasicVaultCell.class, getBlock().getRelative(BlockFace.UP, height));
            } catch (IllegalArgumentException ignored) {
                break;
            }
            if (upper == null) {
                break;
            }
            upper.updatePowerVisual(false);
        }
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
        VaultButton identity = remember(new IdentityRecoveryButton());

        return Gui.builder()
                .setStructure(
                        "# # # # # # # # 0",
                        "# d # s # i # w 1",
                        "# # # # # # # # 2",
                        "# p # c # r # k 3",
                        "# # # # # # # # 4"
                )
                .addIngredient('#', GuiItems.backgroundBlack())
                .addIngredient('d', deposit)
                .addIngredient('s', status)
                .addIngredient('i', registered)
                .addIngredient('w', withdraw)
                .addIngredient('p', purge)
                .addIngredient('c', clear)
                .addIngredient('r', recovery)
                .addIngredient('k', identity)
                .addIngredient('0', remember(new DeleteContentsButton(0)))
                .addIngredient('1', remember(new DeleteContentsButton(1)))
                .addIngredient('2', remember(new DeleteContentsButton(2)))
                .addIngredient('3', remember(new DeleteContentsButton(3)))
                .addIngredient('4', remember(new DeleteContentsButton(4)))
                .build();
    }

    private <T extends VaultButton> T remember(T button) {
        guiButtons.add(button);
        return button;
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return Component.text("Basic Powered Vault Cell — "
                + (hasRecoveryLock() ? "LOCKED / RECOVERY" : identityConflict ? "LOCKED / ID CONFLICT" : (isOperational() ? "Online" : "Offline")));
    }

    @Override
    public WailaDisplay getWaila(@NotNull Player player) {
        boolean online = isOperational();
        WailaDisplay display = WailaDisplay.of(this, player)
                .add(Component.text(hasRecoveryLock() ? "LOCKED-RECOVERY" : identityConflict ? "LOCKED-ID-CONFLICT" : (online ? "ONLINE" : "OFFLINE")))
                .add(Component.text("rev " + endpointRevision));

        if (storedItem == null) {
            return display.add(Component.text("Empty / unregistered"));
        }

        return display
                .add(storedItem.effectiveName())
                .add(Component.text(format(storedAmount) + " / " + format(getCapacity())));
    }

    protected boolean registerFromMainHand(Player player) {
        if (!requireOperational(player)) {
            return false;
        }
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
        VaultTransferRollback recovery = new VaultTransferRollback(this, player.getInventory(), List.of(this), "cell registration");
        try {
            player.getInventory().setItemInMainHand(hand.getAmount() <= 1 ? null : hand.asQuantity(hand.getAmount() - 1));
            register(keyItem);
        } catch (RuntimeException failure) {
            recoverTransfer(player, recovery, failure);
            return false;
        }
        player.sendMessage(Component.text("Registered ").append(keyItem.effectiveName())
                .append(Component.text(" using 1 item.")));
        return true;
    }

    protected void quickDeposit(Player player) {
        if (!requireOperational(player)) {
            return;
        }
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
        VaultTransferRollback recovery = new VaultTransferRollback(this, inventory, List.of(this), "cell deposit");
        try {
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
                    inventory.setItem(slot, stack.asQuantity(stack.getAmount() - toRemove));
                }
            }

            if (accepted > 0L || voided > 0L) {
                touchRevision();
            }
            refreshGuiItems();
        } catch (RuntimeException failure) {
            recoverTransfer(player, recovery, failure);
            return;
        }
        if (accepted > 0 || voided > 0) {
            player.sendMessage(Component.text("Deposited " + format(accepted)
                    + (voided > 0 ? " and purged " + format(voided) + " overflow." : ".")));
        } else {
            player.sendMessage(Component.text(storedAmount >= capacity
                    ? "This Vault Cell is full."
                    : "No matching items were found in your inventory."));
        }
    }

    protected long quickWithdrawAmount(ClickType clickType) {
        if (clickType.isRightClick()) return 1L;
        if (clickType.isLeftClick()) return Long.MAX_VALUE;
        return 0L;
    }

    protected List<Component> quickWithdrawLore() {
        return List.of(Component.text("Left-click: fill inventory."),
                Component.text("Right-click: withdraw 1."));
    }

    protected void withdraw(Player player, long requested) {
        if (requested <= 0L || !requireOperational(player)) return;
        if (storedItem == null || storedAmount <= 0L || hasLegacyRecovery()) {
            player.sendMessage(Component.text("This Vault Cell has no available items."));
            return;
        }
        long target = Math.min(requested, Math.min(storedAmount,
                VaultStorageTransaction.playerCapacity(player.getInventory(), storedItem)));
        VaultTransferRollback recovery = new VaultTransferRollback(this, player.getInventory(), List.of(this), "cell withdrawal");
        try {
            storedAmount -= target;
            long delivered = VaultStorageTransaction.deliver(player.getInventory(), storedItem, target);
            storedAmount += target - delivered;
            if (delivered > 0L) touchRevision();
            else player.sendMessage(Component.text("Your inventory is full."));
        } catch (RuntimeException failure) {
            recoverTransfer(player, recovery, failure);
            return;
        }
        refreshGuiItems();
    }

    protected void togglePurge(Player player) {
        if (!requireOperational(player)) {
            return;
        }
        purgeOverflow = !purgeOverflow;
        touchRevision();
        refreshGuiItems();
        player.sendMessage(Component.text("Overflow purge " + (purgeOverflow ? "enabled." : "disabled.")));
    }

    protected void clearRegistration(Player player) {
        if (!requireOperational(player)) {
            return;
        }
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
        touchRevision();
        VaultDisplayManager.update(this);
        refreshGuiItems();
        player.sendMessage(Component.text("Vault Cell registration cleared."));
    }

    protected void recoverLegacy(Player player) {
        if (!requireOperational(player)) {
            return;
        }
        if (legacyRecovery.isEmpty()) {
            player.sendMessage(Component.text("There are no legacy contents to recover."));
            return;
        }

        boolean recoveryChanged = false;
        VaultTransferRollback recovery = new VaultTransferRollback(this, player.getInventory(), List.of(this), "legacy recovery");
        try {
            Iterator<ItemStack> iterator = legacyRecovery.iterator();
            while (iterator.hasNext()) {
                ItemStack stack = iterator.next();
                int beforeAmount = stack.getAmount();
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack.clone());
                if (leftovers.isEmpty()) {
                    iterator.remove();
                    recoveryChanged = true;
                    continue;
                }

                ItemStack remaining = leftovers.values().iterator().next();
                stack.setAmount(remaining.getAmount());
                recoveryChanged |= stack.getAmount() != beforeAmount;
                break;
            }

            if (recoveryChanged) {
                touchRevision();
            }
            refreshGuiItems();
        } catch (RuntimeException failure) {
            recoverTransfer(player, recovery, failure);
            return;
        }
        if (legacyRecovery.isEmpty()) {
            player.sendMessage(Component.text("Legacy Vault contents fully recovered."));
        } else {
            player.sendMessage(Component.text("Recovered what would fit. "
                    + legacyRecovery.size() + " stack(s) remain."));
        }
    }

    protected void rekeyEmptyIdentity(Player player) {
        if (hasRecoveryLock()) {
            player.sendMessage(Component.text("Transfer recovery locks cannot be cleared by changing the endpoint identity."));
            return;
        }
        if (!identityConflict) {
            player.sendMessage(Component.text("This Vault endpoint identity is healthy."));
            return;
        }

        if (storedAmount > 0L || !legacyRecovery.isEmpty()) {
            player.sendMessage(Component.text(
                    "Identity recovery refused: filled/conflicted Vaults must be reviewed without re-keying their contents."
            ));
            return;
        }

        UUID previous = endpointId;
        VaultEndpointRegistry.unregister(this);
        endpointId = UUID.randomUUID();
        identityConflict = false;
        touchRevision();
        VaultEndpointRegistry.register(this);

        VaultPowerBase base = getPowerBase();
        updatePowerVisual(base != null && base.isOnline() && !identityConflict);
        refreshGuiItems();

        player.sendMessage(Component.text(
                "Empty Vault endpoint re-keyed from "
                        + previous.toString().substring(0, 8)
                        + " to " + endpointId.toString().substring(0, 8) + "."
        ));
    }

    private void recoverTransfer(Player player, VaultTransferRollback recovery, RuntimeException failure) {
        recovery.recover(failure);
        player.sendMessage(Component.text(hasRecoveryLock()
                ? "Transfer failed and needs administrator review. Contents were recorded; run /vaultworks recovery."
                : "Transfer failed. Your inventory and Vault contents were restored."));
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
            boolean online = isOperational();
            return ItemStackBuilder.of(online ? Material.COPPER_BULB : Material.EXPOSED_COPPER_BULB)
                    .name(Component.text("Vault Status — "
                            + (hasRecoveryLock() ? "LOCKED / RECOVERY" : identityConflict ? "LOCKED / ID CONFLICT" : (online ? "ONLINE" : "OFFLINE"))))
                    .lore(
                            Component.text("Stored: " + format(storedAmount) + " / " + format(capacity)),
                            Component.text("Used: " + percent + "%"),
                            Component.text("Column limit: " + MAX_COLUMN_HEIGHT + " Vault Cells per Power Base"),
                            Component.text("Endpoint: " + endpointId.toString().substring(0, 8)
                                    + " / rev " + endpointRevision),
                            Component.text(hasRecoveryLock() ? "LOCKED: transfer recovery requires review." : identityConflict
                                    ? "LOCKED: duplicate endpoint identity detected."
                                    : (online
                                            ? "Powered by the Vault Power Base below."
                                            : "Storage controls and cargo are locked until powered.")),
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
                    .lore(quickWithdrawLore());
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            withdraw(player, quickWithdrawAmount(clickType));
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

    private final class DeleteContentsButton extends VaultButton {
        private final int step;
        private static final String[] LABELS = {
                "Delete Contents", "Are you sure?", "Are you really sure?",
                "I'm completely sure", "Delete permanently — no going back"
        };

        private DeleteContentsButton(int step) { this.step = step; }

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            VaultDeleteSequence sequence = VaultWorks.instance().deleteConfirmations().get(viewer.getUniqueId());
            boolean current = sequence != null && sequence.endpoint().equals(endpointId)
                    && sequence.revision() == endpointRevision && System.currentTimeMillis() < sequence.expiresAt();
            if (step > 0 && (!current || step > sequence.nextStep())) {
                return ItemStackBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(Component.empty());
            }
            boolean active = step == 0 || (current && step == sequence.nextStep());
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Step " + (step + 1) + " of 5 — click from top to bottom."));
            lore.add(Component.text("Permanently destroys " + format(storedAmount) + " stored items."));
            if (storedItem != null) lore.add(Component.text("Registered item: ").append(storedItem.effectiveName()));
            if (!legacyRecovery.isEmpty()) lore.add(Component.text("Also destroys " + legacyRecovery.size() + " legacy recovery stacks."));
            lore.add(Component.text("Clears registration so a new item can be stored."));
            lore.add(Component.text("No drops, refunds or undo. The Vault block remains."));
            lore.add(Component.text("Closing the menu or changing contents resets confirmation."));
            lore.add(Component.text(active ? "Left-click to continue. Sequence expires after 30 seconds." : "Complete the preceding steps first."));
            return ItemStackBuilder.of(active ? (step == 4 ? Material.TNT : Material.RED_STAINED_GLASS_PANE) : Material.GRAY_STAINED_GLASS_PANE)
                    .name(Component.text(LABELS[step])).lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            if (clickType != ClickType.LEFT || !requireOperational(player)) return;
            VaultDeleteConfirmations confirmations = VaultWorks.instance().deleteConfirmations();
            UUID playerId = player.getUniqueId();
            long now = System.currentTimeMillis();
            if (step == 0) {
                VaultDeleteSequence started = VaultDeleteSequence.start(endpointId, endpointRevision, now);
                confirmations.put(playerId, started);
                org.bukkit.Bukkit.getScheduler().runTaskLater(VaultWorks.instance(), () -> {
                    VaultDeleteSequence current = confirmations.get(playerId);
                    if (current != null && current.endpoint().equals(started.endpoint())
                            && current.expiresAt() == started.expiresAt()) {
                        confirmations.remove(playerId);
                        refreshGuiItems();
                    }
                }, VaultDeleteSequence.TIMEOUT_MILLIS / 50L);
            } else {
                VaultDeleteSequence sequence = confirmations.get(playerId);
                if (sequence == null || !sequence.endpoint().equals(endpointId)
                        || sequence.revision() != endpointRevision || now >= sequence.expiresAt()) {
                    confirmations.remove(playerId);
                    player.sendMessage(Component.text("Deletion confirmation reset. Start at the top-right button."));
                } else if (step != sequence.nextStep()) {
                    return;
                } else if (step < 4) {
                    confirmations.put(playerId, sequence.advance());
                } else {
                    // Same server-thread callback: validate the exact revision, consume
                    // the token, then clear. Another click cannot reuse the token.
                    confirmations.remove(playerId);
                    long deleted = storedAmount;
                    storedAmount = 0L;
                    storedItem = null;
                    legacyRecovery.clear();
                    touchRevision();
                    VaultDisplayManager.update(BasicVaultCell.this);
                    player.sendMessage(Component.text("Permanently deleted " + format(deleted)
                            + " stored items and any legacy recovery contents. Item registration cleared."));
                }
            }
            refreshGuiItems();
        }
    }

    private final class IdentityRecoveryButton extends VaultButton {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            if (!identityConflict) {
                return ItemStackBuilder.of(Material.LIME_DYE)
                        .name(Component.text("Endpoint Identity — Healthy"))
                        .lore(
                                Component.text(endpointId.toString()),
                                Component.text("Revision: " + endpointRevision)
                        );
            }

            boolean safeToRekey = storedAmount == 0L && legacyRecovery.isEmpty();
            return ItemStackBuilder.of(safeToRekey ? Material.YELLOW_DYE : Material.RED_DYE)
                    .name(Component.text("Endpoint Identity — CONFLICT"))
                    .lore(
                            Component.text(endpointId.toString()),
                            Component.text("Revision: " + endpointRevision),
                            Component.text(safeToRekey
                                    ? "Click to re-key this empty Vault safely."
                                    : "Filled conflicts remain locked; do not re-key stored contents.")
                    );
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            if (identityConflict) {
                rekeyEmptyIdentity(player);
            }
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
