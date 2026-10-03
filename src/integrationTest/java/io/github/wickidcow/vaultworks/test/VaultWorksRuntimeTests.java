package io.github.wickidcow.vaultworks.test;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.interfaces.SimpleElectricRebarBlock;
import io.github.pylonmc.rebar.electricity.nodes.ElectricConsumerNode;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.registry.RebarRegistry;
import io.github.wickidcow.vaultworks.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.inventory.VirtualInventory;

/** Runs only on a disposable local/CI server. Tests production code with real Paper items. */
public final class VaultWorksRuntimeTests extends JavaPlugin {
    private World world;
    private int checks;
    private Location playerLocation;
    private PlayerInventory playerInventory;
    private Player player;
    private VaultPowerBase liveBase;
    private final List<RebarBlock> fixtures = new ArrayList<>();

    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                Files.deleteIfExists(getDataFolder().toPath().resolve("result.txt"));
                world = Bukkit.getWorlds().getFirst();
                world.getChunkAt(0, 0).load();
                playerLocation = new Location(world, 2, 101, 2);
                Inventory backing = Bukkit.createInventory(null, 36);
                playerInventory = (PlayerInventory) Proxy.newProxyInstance(getClassLoader(), new Class<?>[]{PlayerInventory.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("getItemInMainHand")) return backing.getItem(0);
                            if (method.getName().equals("setItemInMainHand")) { backing.setItem(0, (ItemStack)args[0]); return null; }
                            try { return Inventory.class.getMethod(method.getName(), method.getParameterTypes()).invoke(backing, args); }
                            catch (NoSuchMethodException e) { return defaultValue(method.getReturnType()); }
                        });
                UUID playerId = UUID.randomUUID();
                player = (Player) Proxy.newProxyInstance(getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> switch(method.getName()) {
                    case "getInventory" -> playerInventory;
                    case "getLocation" -> playerLocation.clone();
                    case "getWorld" -> playerLocation.getWorld();
                    case "getUniqueId" -> playerId;
                    case "locale" -> Locale.ENGLISH;
                    case "getName" -> "VaultWorksRuntimeTest";
                    case "hashCode" -> playerId.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                });
                if (getConfig().contains("expected-cell-uuid")) {
                    BasicVaultCell restored = (BasicVaultCell) BlockStorage.get(world.getBlockAt(2,101,2));
                    check(restored.getEndpointId().toString().equals(getConfig().getString("expected-cell-uuid")), "endpoint UUID survives clean restart");
                    check(restored.getStoredAmount() == getConfig().getLong("expected-cell-amount"), "cell contents survive clean restart");
                    VaultTerminal restoredTerminal = (VaultTerminal) BlockStorage.get(world.getBlockAt(3,100,2));
                    check(count(restoredTerminal.getVirtualInventories().get("claim").asBukkitInventory(), Material.DIAMOND)
                            == getConfig().getInt("expected-claim-diamonds"), "claim buffer survives clean restart");
                }
                getConfig().set("expected-cell-uuid", null);
                saveConfig();
                runChecks();
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    try {
                        check(liveBase.isOnline(), "test source supplies real Rebar network power");
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "vaultworks doctor");
                        getLogger().info("VAULTWORKS RUNTIME PASS: " + checks + " checks");
                        Files.writeString(getDataFolder().toPath().resolve("result.txt"), "PASS " + checks);
                    } catch (Throwable failure) {
                        getLogger().log(java.util.logging.Level.SEVERE, "VAULTWORKS RUNTIME FAIL", failure);
                    } finally { Bukkit.shutdown(); }
                }, 40L);
            } catch (Throwable failure) {
                getLogger().log(java.util.logging.Level.SEVERE, "VAULTWORKS RUNTIME FAIL", failure);
                Bukkit.shutdown();
            }
        }, 40L);
        getDataFolder().mkdirs();
    }

    private void runChecks() throws Exception {
        // This harness is opt-in and uses only a disposable server fixture area.
        for (int y=106;y>=100;y--) for (int x=2;x<=9;x++) {
            RebarBlock previous=BlockStorage.get(world.getBlockAt(x,y,2));
            if(previous!=null) BlockStorage.breakBlock(previous);
        }
        VaultPowerBase base = (VaultPowerBase) place("vault_power_base", 2, 100, 2);
        PoweredVaultCell cell = (PoweredVaultCell) place("powered_vault_cell", 2, 101, 2);
        power(base, true);
        call(cell, "setStoredState", new ItemStack(Material.DIAMOND), 10000L);
        check(cell.isOperational(), "powered column online");
        check((long)call(cell, "quickWithdrawAmount", ClickType.LEFT) == 1, "left-click requests 1");
        check((long)call(cell, "quickWithdrawAmount", ClickType.RIGHT) == 64, "right-click requests 64");
        check((long)call(cell, "quickWithdrawAmount", ClickType.SHIFT_LEFT) == Long.MAX_VALUE, "shift-left fills inventory");
        check((long)call(cell, "quickWithdrawAmount", ClickType.SHIFT_RIGHT) == 0, "unmapped click ignored");
        call(cell, "withdraw", player, 1L);
        check(count(playerInventory, Material.DIAMOND) == 1 && cell.getStoredAmount() == 9999, "withdraw exactly one");
        call(cell, "withdraw", player, 64L);
        check(count(playerInventory, Material.DIAMOND) == 65 && cell.getStoredAmount() == 9935, "withdraw exactly 64");
        call(cell, "withdraw", player, Long.MAX_VALUE);
        check(count(playerInventory, Material.DIAMOND) == 2304 && cell.getStoredAmount() == 7696, "fill exactly 36 storage slots");
        call(cell, "withdraw", player, 64L);
        check(cell.getStoredAmount() == 7696, "full player inventory leaves cell untouched");
        playerInventory.clear();
        call(cell, "setStoredState", new ItemStack(Material.DIAMOND), 3L);
        call(cell, "withdraw", player, 64L);
        check(count(playerInventory, Material.DIAMOND) == 3 && cell.getStoredAmount() == 0, "less than 64 drains only available");
        call(cell, "setStoredState", new ItemStack(Material.DIAMOND), 10000L);
        playerInventory.clear();
        playerInventory.setItem(0, new ItemStack(Material.DIAMOND, 60));
        for (int i=1;i<36;i++) playerInventory.setItem(i,new ItemStack(Material.STONE,64));
        call(cell, "withdraw", player, 64L);
        check(playerInventory.getItem(0).getAmount() == 64 && cell.getStoredAmount() == 9996, "partial inventory room respected");
        power(base, false);
        playerInventory.clear();
        call(cell, "withdraw", player, 1L);
        check(cell.getStoredAmount() == 9996, "unpowered cell cannot withdraw");
        power(base, true);

        VaultTerminal terminal = (VaultTerminal)place("vault_terminal", 3, 100, 2);
        VirtualInventory claim = terminal.getVirtualInventories().get("claim");
        Class<?> tx = Class.forName("io.github.wickidcow.vaultworks.VaultStorageTransaction",true, VaultWorks.class.getClassLoader());
        Object moved = callStatic(tx,"withdraw",terminal,claim.asBukkitInventory(),new ItemStack(Material.DIAMOND),64L);
        getLogger().info("Withdrawal diagnostic: " + moved + " claim=" + count(claim.asBukkitInventory(),Material.DIAMOND) + " size=" + claim.asBukkitInventory().getStorageContents().length + " max=" + claim.asBukkitInventory().getMaxStackSize() + " online=" + cell.isOperational());
        long beforeFailure=cell.getStoredAmount();
        Inventory failedBacking=Bukkit.createInventory(null,9);
        java.util.concurrent.atomic.AtomicBoolean failOnce=new java.util.concurrent.atomic.AtomicBoolean(true);
        Inventory failedDestination=(Inventory)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{Inventory.class},(proxy,method,args)->{
            if(method.getName().equals("setItem") && failOnce.getAndSet(false))throw new IllegalStateException("simulated delivery failure");
            return method.invoke(failedBacking,args);
        });
        try { callStatic(tx,"withdraw",terminal,failedDestination,new ItemStack(Material.DIAMOND),64L); throw new AssertionError("expected delivery failure"); }
        catch(RuntimeException expected) { check(cell.getStoredAmount()==beforeFailure && count(failedBacking,Material.DIAMOND)==0,"failed delivery restores source without duplicated items"); }
        check((long)call(moved,"moved") == 64 && count(claim.asBukkitInventory(),Material.DIAMOND) == 64, "network withdrawal goes to persisted claim buffer");
        long before = cell.getStoredAmount();
        callStatic(tx,"withdraw",terminal,claim.asBukkitInventory(),new ItemStack(Material.DIAMOND),Long.MAX_VALUE);
        check(count(claim.asBukkitInventory(),Material.DIAMOND) == 320 && cell.getStoredAmount() == before-256, "claim buffer bounds bulk withdrawal");
        before = cell.getStoredAmount();
        callStatic(tx,"withdraw",terminal,claim.asBukkitInventory(),new ItemStack(Material.DIAMOND),1L);
        check(cell.getStoredAmount() == before, "full claim buffer leaves source untouched");
        call(cell,"setStoredState",new ItemStack(Material.DIAMOND),cell.getCapacity()-2);
        playerInventory.setItem(0,new ItemStack(Material.DIAMOND,64));
        callStatic(tx,"depositInventory",terminal,playerInventory);
        check(cell.getStoredAmount() == cell.getCapacity() && count(playerInventory,Material.DIAMOND)==62, "deposit retains excess in player inventory");
        call(cell,"togglePurge",player);
        callStatic(tx,"depositInventory",terminal,playerInventory);
        check(count(playerInventory,Material.DIAMOND)==62, "terminal never applies overflow purge");
        check((long)call(cell,"networkInsert",new ItemStack(Material.GOLD_INGOT),20L)==0, "wrong item insertion rejected");
        call(cell,"setStoredState",new ItemStack(Material.DIAMOND),cell.getCapacity()-100);
        check((long)call(cell,"networkInsert",new ItemStack(Material.GOLD_INGOT),20L)==0, "wrong item rejected with free space");
        power(base,false);
        check((long)call(cell,"networkInsert",new ItemStack(Material.DIAMOND),20L)==0, "unpowered insert rejected");
        power(base,true);
        ItemStack custom = new ItemStack(Material.DIAMOND);
        custom.editMeta(meta->meta.customName(net.kyori.adventure.text.Component.text("Different diamond")));
        check((long)call(cell,"networkInsert",custom,20L)==0,"custom item metadata stays distinct");

        Gui gui=cell.createGui();
        long original=cell.getStoredAmount();
        check(gui.getItem(17).getItemProvider(player).get(Locale.ENGLISH).getType()==Material.BLACK_STAINED_GLASS_PANE,
                "only first delete button initially visible");
        click(gui,8,player);
        check(gui.getItem(17).getItemProvider(player).get(Locale.ENGLISH).getType()==Material.RED_STAINED_GLASS_PANE,
                "first click reveals second confirmation");
        check(gui.getItem(26).getItemProvider(player).get(Locale.ENGLISH).getType()==Material.BLACK_STAINED_GLASS_PANE,
                "later confirmations remain hidden");
        click(gui,44,player);
        check(cell.getStoredAmount()==original,"final delete button cannot skip confirmation");
        click(gui,8,player); click(gui,17,player);
        call(cell,"networkExtract",new ItemStack(Material.DIAMOND),1L);
        click(gui,26,player); click(gui,35,player); click(gui,44,player);
        check(cell.getStoredAmount()==original-1,"changed contents invalidate deletion");
        for(int slot:new int[]{8,17,26,35}) click(gui,slot,player);
        UUID otherId = UUID.randomUUID();
        Player other = (Player) Proxy.newProxyInstance(getClassLoader(), new Class<?>[]{Player.class},
                (proxy,method,args) -> method.getName().equals("getUniqueId") ? otherId
                        : Proxy.getInvocationHandler(player).invoke(player,method,args));
        click(gui,44,other);
        check(cell.getStoredAmount()==original-1,"another player cannot complete deletion");
        click(gui,44,player);
        check(cell.getStoredAmount()==0 && cell.getStoredItem()==null,"five steps delete contents and clear registration");
        check(!cell.hasIdentityConflict(),"deletion preserves healthy endpoint identity");
        call(cell,"setStoredState",new ItemStack(Material.SNOWBALL),1000L);
        playerInventory.clear();
        call(cell,"withdraw",player,64L);
        check(count(playerInventory,Material.SNOWBALL)==64,"right-click 64 works with 16-stack items");
        for(ItemStack stack:playerInventory.getStorageContents()) if(stack!=null) check(stack.getAmount()<=16,"no oversized snowball stacks");

        VaultTransmitter transmitter=(VaultTransmitter)place("vault_transmitter",4,100,2);
        power(transmitter,true);
        playerLocation=new Location(world,100,101,2);
        check(transmitter.accessStatus(player)==VaultTransmitter.AccessStatus.OUT_OF_RANGE,"wireless basic range enforced");
        place("vault_antenna",4,101,2);
        check(transmitter.accessStatus(player)==VaultTransmitter.AccessStatus.ALLOWED,"antenna extends range");
        check(!(boolean)call(transmitter,"canAccess",player),"remote access requires bound terminal item");
        ItemStack wireless=RebarRegistry.ITEMS.get(NamespacedKey.fromString("vaultworks:wireless_vault_terminal")).createNewItemStack();
        Player sneaking=(Player)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{Player.class},
                (proxy,method,args)->method.getName().equals("isSneaking") ? true : Proxy.getInvocationHandler(player).invoke(player,method,args));
        org.bukkit.event.player.PlayerInteractEvent bindEvent=new org.bukkit.event.player.PlayerInteractEvent(sneaking,
                org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,wireless,transmitter.getBlock(),BlockFace.UP,EquipmentSlot.HAND);
        new WirelessVaultTerminalItem(wireless).onInteract(bindEvent,org.bukkit.event.EventPriority.NORMAL);
        new WirelessVaultTerminalItem(wireless).onInteract(bindEvent,org.bukkit.event.EventPriority.MONITOR);
        check(transmitter.getTransmitterId().toString().equals(wireless.getPersistentDataContainer()
                .get(NamespacedKey.fromString("vaultworks:wireless_terminal_transmitter"),PersistentDataType.STRING)),"wireless binding completes after click-through is cancelled");
        ItemStack deniedWireless=RebarRegistry.ITEMS.get(NamespacedKey.fromString("vaultworks:wireless_vault_terminal")).createNewItemStack();
        org.bukkit.event.player.PlayerInteractEvent deniedEvent=new org.bukkit.event.player.PlayerInteractEvent(sneaking,
                org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,deniedWireless,transmitter.getBlock(),BlockFace.UP,EquipmentSlot.HAND);
        new WirelessVaultTerminalItem(deniedWireless).onInteract(deniedEvent,org.bukkit.event.EventPriority.NORMAL);
        deniedEvent.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        new WirelessVaultTerminalItem(deniedWireless).onInteract(deniedEvent,org.bukkit.event.EventPriority.MONITOR);
        check(!deniedWireless.getPersistentDataContainer().has(NamespacedKey.fromString("vaultworks:wireless_terminal_transmitter")),"cancelled item interaction cannot bind a wireless terminal");
        playerInventory.setItem(0,wireless);
        check((boolean)call(transmitter,"canAccess",player),"bound wireless terminal permits in-range access");
        power(transmitter,false);
        check(!(boolean)call(transmitter,"canAccess",player),"power loss revokes existing wireless access");
        power(transmitter,true);
        playerLocation=new Location(world,1000,101,2);
        check(!(boolean)call(transmitter,"canAccess",player),"moving out of range revokes access");
        World otherWorld=Bukkit.getWorlds().stream().filter(w->!w.equals(world)).findFirst().orElseThrow();
        playerLocation=new Location(otherWorld,2,101,2);
        check(transmitter.accessStatus(player)==VaultTransmitter.AccessStatus.DIMENSIONAL_ANTENNA_REQUIRED,"another dimension requires dimensional antenna");
        place("dimensional_vault_antenna",5,100,2);
        check((boolean)call(transmitter,"canAccess",player),"dimensional antenna allows bound access from another world");
        playerLocation=new Location(world,2,101,2);

        VaultPowerBase duplicateBase = (VaultPowerBase)place("vault_power_base",8,100,2);
        BasicVaultCell portable = (BasicVaultCell)place("basic_vault_cell",8,101,2);
        power(duplicateBase,true);
        call(portable,"setStoredState",new ItemStack(Material.EMERALD),1234L);
        UUID portableId=portable.getEndpointId();
        List<ItemStack> drops=BlockStorage.breakBlock(portable).stream().map(org.bukkit.entity.Item::getItemStack).toList();
        ItemStack packed=drops.stream().filter(i->io.github.pylonmc.rebar.item.RebarItemSchema.fromStack(i)!=null).findFirst().orElseThrow();
        Block location=world.getBlockAt(8,101,2);
        BasicVaultCell replaced=(BasicVaultCell)BlockStorage.placeBlock(location,NamespacedKey.fromString("vaultworks:basic_vault_cell"),
                new BlockCreateContext.Default(null,location,BlockFace.NORTH,BlockFace.NORTH,packed,true));
        check(replaced.getStoredAmount()==1234 && replaced.getEndpointId().equals(portableId),"filled break/re-place preserves contents and UUID");
        Block duplicateLocation=world.getBlockAt(8,102,2);
        BasicVaultCell duplicate=(BasicVaultCell)BlockStorage.placeBlock(duplicateLocation,NamespacedKey.fromString("vaultworks:basic_vault_cell"),
                new BlockCreateContext.Default(null,duplicateLocation,BlockFace.NORTH,BlockFace.NORTH,packed.clone(),true));
        check(replaced.hasIdentityConflict() && duplicate.hasIdentityConflict(),"duplicate portable UUID locks both copies");
        check((long)call(replaced,"networkExtract",new ItemStack(Material.EMERALD),1L)==0,"duplicate endpoint cannot withdraw");
        BlockStorage.breakBlock(duplicate);
        check(replaced.hasIdentityConflict(),"removing one duplicate does not unlock the other");
        BlockStorage.breakBlock(replaced);
        BlockStorage.breakBlock(duplicateBase);

        VaultPowerBase emptyBase = (VaultPowerBase)place("vault_power_base",8,100,2);
        BasicVaultCell emptyA = (BasicVaultCell)place("basic_vault_cell",8,101,2);
        BasicVaultCell emptyB = (BasicVaultCell)place("basic_vault_cell",8,102,2);
        ItemStack emptyDropA=emptyA.getDropItem(new BlockBreakContext.PluginBreak(emptyA.getBlock()));
        ItemStack emptyDropB=emptyB.getDropItem(new BlockBreakContext.PluginBreak(emptyB.getBlock()));
        check(emptyDropA.getMaxStackSize()==64 && emptyDropA.isSimilar(emptyDropB),"empty cells stack despite different placed UUIDs");
        check(emptyDropA.isSimilar(emptyA.getDefaultItem().createNewItemStack()),"unregistered empty drops stack with crafted cells");
        call(emptyA,"setStoredState",new ItemStack(Material.EMERALD),0L);
        call(emptyB,"setStoredState",new ItemStack(Material.EMERALD),0L);
        check(emptyA.getDropItem(new BlockBreakContext.PluginBreak(emptyA.getBlock())).isSimilar(
                emptyB.getDropItem(new BlockBreakContext.PluginBreak(emptyB.getBlock()))),"empty registered cells stack with matching settings");
        call(emptyA,"setStoredState",new ItemStack(Material.EMERALD),1L);
        check(emptyA.getDropItem(new BlockBreakContext.PluginBreak(emptyA.getBlock())).getMaxStackSize()==1,"filled cells stay unstackable");
        BlockStorage.breakBlock(emptyB);BlockStorage.breakBlock(emptyA);BlockStorage.breakBlock(emptyBase);

        VaultTestPowerSource source = (VaultTestPowerSource)place("test_power_source",6,100,2);
        check(source.getPowerProduced()==1_000_000D,"test source provides 1 MW");
        check(source.getDefaultItem()!=null && base.getDefaultItem()!=null,"Rebar block drop items are registered");
        check(Bukkit.getRecipesFor(source.getDefaultItem().createNewItemStack()).stream().noneMatch(r -> r.getResult().isSimilar(source.getDefaultItem().createNewItemStack())),"test source has no survival recipe");
        power(base,false);
        source.getElectricNode("producer_0",io.github.pylonmc.rebar.electricity.nodes.ElectricProducerNode.class)
                .connect(base.getElectricNode("consumer_0",ElectricConsumerNode.class));
        liveBase=base;

        // Rebar serializes the physical stored state and claim inventory on clean shutdown.
        call(cell,"setStoredState",new ItemStack(Material.SNOWBALL),12345L);
        getConfig().set("expected-cell-uuid",cell.getEndpointId().toString());
        getConfig().set("expected-cell-amount",12345L);
        getConfig().set("expected-claim-diamonds",320);
        saveConfig();
    }

    private RebarBlock place(String id,int x,int y,int z) {
        Block b=world.getBlockAt(x,y,z);
        RebarBlock existing=BlockStorage.get(b);
        if(existing!=null) BlockStorage.breakBlock(existing);
        RebarBlock block=Objects.requireNonNull(BlockStorage.placeBlock(b,NamespacedKey.fromString("vaultworks:"+id)));
        fixtures.add(block); return block;
    }
    private void power(SimpleElectricRebarBlock block, boolean value) throws Exception {
        ElectricConsumerNode consumer=block.getElectricNode("consumer_0",ElectricConsumerNode.class);
        Field f=ElectricConsumerNode.class.getDeclaredField("isPowered"); f.setAccessible(true); f.set(consumer,value);
    }
    private void click(Gui gui,int slot,Player who) { gui.getItem(slot).handleClick(ClickType.LEFT,who,null); }
    private static long count(Inventory inv,Material type) { return Arrays.stream(inv.getStorageContents()).filter(Objects::nonNull).filter(i->i.getType()==type).mapToLong(ItemStack::getAmount).sum(); }
    private void check(boolean condition,String text) { if(!condition)throw new AssertionError(text); checks++; getLogger().info("PASS "+text); }
    private static Object defaultValue(Class<?> type) { if(!type.isPrimitive()||type==void.class)return null; if(type==boolean.class)return false; if(type==double.class)return 0D; if(type==float.class)return 0F; if(type==long.class)return 0L; return 0; }
    private static Object call(Object target,String name,Object...args) throws Exception { return invoke(target,target.getClass(),name,args); }
    private static Object callStatic(Class<?> type,String name,Object...args)throws Exception { return invoke(null,type,name,args); }
    private static Object invoke(Object target,Class<?> type,String name,Object[] args)throws Exception {
        for(Class<?> c=type;c!=null;c=c.getSuperclass()) for(Method m:c.getDeclaredMethods()) if(m.getName().equals(name)&&m.getParameterCount()==args.length) {
            boolean matches=true;
            Class<?>[] params=m.getParameterTypes();
            for(int i=0;i<params.length;i++) if(args[i]!=null&&!params[i].isPrimitive()&&!params[i].isInstance(args[i])) matches=false;
            if(matches){ m.setAccessible(true); try{return m.invoke(target,args);}catch(InvocationTargetException e){throw new RuntimeException(name,e.getCause());} }
        }
        throw new NoSuchMethodException(name);
    }
}
