package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.addon.RebarAddon;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.content.guide.RebarGuide;
import io.github.pylonmc.rebar.guide.button.PageButton;
import io.github.pylonmc.rebar.guide.pages.base.SimpleStaticGuidePage;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.pylonmc.rebar.recipe.vanilla.ShapedRebarRecipe;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public final class VaultWorks extends JavaPlugin implements RebarAddon {
    private static VaultWorks instance;

    private final VaultDeleteConfirmations deleteConfirmations = new VaultDeleteConfirmations();
    private PowerPolicy policy;
    private StoragePolicy storagePolicy;
    private WirelessPolicy wirelessPolicy;
    private final VaultWirelessRegistry wirelessRegistry = new VaultWirelessRegistry();
    private PageButton guide;
    private final List<NamespacedKey> recipes = new ArrayList<>();

    @Override
    public void onEnable() {
        try {
            Class.forName("io.github.pylonmc.rebar.block.interfaces.ElectricRebarBlock", false, getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(
                    "VaultWorks requires Rebar 0.44.2-26.2 or a compatible electricity-enabled build. "
                            + "Install the matching Rebar server plugin for your Paper version.",
                    exception
            );
        }

        VaultEndpointRegistry.clear();
        instance = this;
        VaultWorksContentCatalog.validate();
        java.util.Objects.requireNonNull(getCommand("vaultworks"))
                .setExecutor(new VaultWorksCommand(this));
        Bukkit.getPluginManager().registerEvents(deleteConfirmations, this);
        saveDefaultConfig();
        migrateConfig();
        double legacyWatts = getConfig().getDouble("power.watts", 48.0D);
        policy = new PowerPolicy(
                getConfig().getDouble("power.base-watts", legacyWatts),
                getConfig().getDouble("power.watts-per-vault", 0.0D),
                getConfig().getInt("cargo.items-per-tick", 8)
        );
        storagePolicy = new StoragePolicy(
                getConfig().getLong("storage.basic-capacity", 1_000_000L),
                getConfig().getLong("storage.powered-capacity", 4_000_000L),
                getConfig().getInt("index.max-network-nodes", 4096),
                getConfig().getInt("terminal.max-item-types", 4096)
        );
        wirelessPolicy = new WirelessPolicy(
                getConfig().getDouble("wireless.transmitter-watts", 32.0D),
                getConfig().getDouble("wireless.base-range-blocks", 64.0D),
                getConfig().getDouble("wireless.antenna-range-blocks", 512.0D),
                getConfig().getBoolean("wireless.allow-cross-dimension", true)
        );

        registerWithRebar();
        NamespacedKey testPower = new NamespacedKey(this, "test_power_source");
        RebarItem.register(VaultTestPowerSourceItem.class,
                ItemStackBuilder.rebar(Material.DAYLIGHT_DETECTOR, testPower).build(), testPower);
        RebarBlock.register(testPower, Material.DAYLIGHT_DETECTOR, VaultTestPowerSource.class);

        NamespacedKey circuitKey = new NamespacedKey(this, "encoded_circuit");
        NamespacedKey waferKey = new NamespacedKey(this, "memory_wafer");
        NamespacedKey latticeKey = new NamespacedKey(this, "storage_lattice");
        NamespacedKey linkKey = new NamespacedKey(this, "vault_link");
        NamespacedKey indexKey = new NamespacedKey(this, "vault_index");
        NamespacedKey terminalKey = new NamespacedKey(this, "vault_terminal");
        NamespacedKey transmitterKey = new NamespacedKey(this, "vault_transmitter");
        NamespacedKey antennaKey = new NamespacedKey(this, "vault_antenna");
        NamespacedKey dimensionalAntennaKey = new NamespacedKey(this, "dimensional_vault_antenna");
        NamespacedKey wirelessTerminalKey = new NamespacedKey(this, "wireless_vault_terminal");
        NamespacedKey powerBaseKey = new NamespacedKey(this, "vault_power_base");
        NamespacedKey cargoNodeKey = new NamespacedKey(this, "vault_cargo_node");
        NamespacedKey basicKey = new NamespacedKey(this, "basic_vault_cell");
        NamespacedKey poweredKey = new NamespacedKey(this, "powered_vault_cell");

        ItemStack circuitItem = ItemStackBuilder.rebar(Material.CLOCK, circuitKey).build();
        ItemStack waferItem = ItemStackBuilder.rebar(Material.ECHO_SHARD, waferKey).build();
        ItemStack latticeItem = ItemStackBuilder.rebar(Material.HONEYCOMB, latticeKey).build();
        ItemStack linkItem = ItemStackBuilder.rebar(Material.COPPER_GRATE, linkKey).build();
        ItemStack indexItem = ItemStackBuilder.rebar(Material.LODESTONE, indexKey).build();
        ItemStack terminalItem = ItemStackBuilder.rebar(Material.ENDER_CHEST, terminalKey).build();
        ItemStack transmitterItem = ItemStackBuilder.rebar(
                Material.CALIBRATED_SCULK_SENSOR,
                transmitterKey
        ).build();
        ItemStack antennaItem = ItemStackBuilder.rebar(Material.LIGHTNING_ROD, antennaKey).build();
        ItemStack dimensionalAntennaItem = ItemStackBuilder.rebar(
                Material.END_ROD,
                dimensionalAntennaKey
        ).build();
        ItemStack wirelessTerminalItem = ItemStackBuilder.rebar(
                        Material.RECOVERY_COMPASS,
                        wirelessTerminalKey
                )
                .set(DataComponentTypes.MAX_STACK_SIZE, 1)
                .build();

        ItemStack powerBaseItem = ItemStackBuilder.rebar(Material.COPPER_BULB, powerBaseKey).build();
        ItemStack cargoNodeItem = ItemStackBuilder.rebar(Material.CHISELED_COPPER, cargoNodeKey).build();

        // Crafted empty cells stack. Filled/conflicted portable drops explicitly use a stack limit of one.
        ItemStack basicItem = ItemStackBuilder.rebar(Material.VAULT, basicKey)
                .set(DataComponentTypes.MAX_STACK_SIZE, 64)
                .build();
        ItemStack poweredItem = ItemStackBuilder.rebar(Material.VAULT, poweredKey)
                .set(DataComponentTypes.MAX_STACK_SIZE, 64)
                .build();

        RebarItem.register(RebarItem.class, circuitItem);
        RebarItem.register(RebarItem.class, waferItem);
        RebarItem.register(RebarItem.class, latticeItem);

        RebarItem.register(RebarItem.class, linkItem, linkKey);
        RebarItem.register(RebarItem.class, indexItem, indexKey);
        RebarItem.register(RebarItem.class, terminalItem, terminalKey);
        RebarItem.register(RebarItem.class, transmitterItem, transmitterKey);
        RebarItem.register(RebarItem.class, antennaItem, antennaKey);
        RebarItem.register(RebarItem.class, dimensionalAntennaItem, dimensionalAntennaKey);
        RebarItem.register(WirelessVaultTerminalItem.class, wirelessTerminalItem);
        RebarItem.register(RebarItem.class, powerBaseItem, powerBaseKey);
        RebarItem.register(VaultCargoNodeItem.class, cargoNodeItem, cargoNodeKey);
        RebarItem.register(VaultCellItem.class, basicItem, basicKey);
        RebarItem.register(VaultCellItem.class, poweredItem, poweredKey);

        // Rebar captures the default drop item when its block schema is registered.
        RebarBlock.register(linkKey, Material.COPPER_GRATE, VaultLinkCable.class);
        RebarBlock.register(indexKey, Material.LODESTONE, VaultIndex.class);
        RebarBlock.register(terminalKey, Material.ENDER_CHEST, VaultTerminal.class);
        RebarBlock.register(
                transmitterKey,
                Material.CALIBRATED_SCULK_SENSOR,
                VaultTransmitter.class
        );
        RebarBlock.register(antennaKey, Material.LIGHTNING_ROD, VaultAntenna.class);
        RebarBlock.register(
                dimensionalAntennaKey,
                Material.END_ROD,
                DimensionalVaultAntenna.class
        );
        RebarBlock.register(powerBaseKey, Material.COPPER_BULB, VaultPowerBase.class);
        RebarBlock.register(cargoNodeKey, Material.CHISELED_COPPER, VaultCargoNode.class);
        RebarBlock.register(basicKey, Material.VAULT, BasicVaultCell.class);
        RebarBlock.register(poweredKey, Material.VAULT, PoweredVaultCell.class);


        recipe(new ShapedRecipe(circuitKey, circuitItem).shape("CRC", "RQR", "CRC")
                .setIngredient('C', Material.COPPER_INGOT)
                .setIngredient('R', Material.REDSTONE)
                .setIngredient('Q', Material.QUARTZ));

        recipe(new ShapedRecipe(waferKey, waferItem).shape("QAQ", "ACA", "QAQ")
                .setIngredient('Q', Material.QUARTZ)
                .setIngredient('A', Material.AMETHYST_SHARD)
                .setIngredient('C', exact(circuitItem)));

        recipe(new ShapedRecipe(latticeKey, latticeItem).shape("QCQ", "CWC", "QCQ")
                .setIngredient('Q', Material.QUARTZ)
                .setIngredient('C', Material.COPPER_INGOT)
                .setIngredient('W', exact(waferItem)));

        recipe(new ShapedRecipe(linkKey, linkItem.asQuantity(8)).shape("CWC", "RQR", "CWC")
                .setIngredient('C', Material.COPPER_INGOT)
                .setIngredient('W', exact(waferItem))
                .setIngredient('R', Material.REDSTONE)
                .setIngredient('Q', Material.QUARTZ));

        recipe(new ShapedRecipe(indexKey, indexItem).shape("LWL", "ECE", "LWL")
                .setIngredient('L', exact(latticeItem))
                .setIngredient('W', exact(waferItem))
                .setIngredient('E', Material.ENDER_EYE)
                .setIngredient('C', Material.COMPARATOR));

        recipe(new ShapedRecipe(terminalKey, terminalItem).shape("WIW", "ECE", "WIW")
                .setIngredient('W', exact(waferItem))
                .setIngredient('I', exact(indexItem))
                .setIngredient('E', Material.ENDER_EYE)
                .setIngredient('C', Material.ENDER_CHEST));

        recipe(new ShapedRecipe(transmitterKey, transmitterItem).shape("AWA", "ITI", "ACA")
                .setIngredient('A', Material.AMETHYST_SHARD)
                .setIngredient('W', exact(waferItem))
                .setIngredient('I', Material.IRON_INGOT)
                .setIngredient('T', exact(terminalItem))
                .setIngredient('C', exact(circuitItem)));

        recipe(new ShapedRecipe(antennaKey, antennaItem).shape(" A ", "ACA", " C ")
                .setIngredient('A', Material.AMETHYST_SHARD)
                .setIngredient('C', Material.COPPER_INGOT));

        recipe(new ShapedRecipe(dimensionalAntennaKey, dimensionalAntennaItem).shape("EAE", "AWA", "EAE")
                .setIngredient('E', Material.ENDER_EYE)
                .setIngredient('A', Material.AMETHYST_SHARD)
                .setIngredient('W', exact(waferItem)));

        recipe(new ShapedRecipe(wirelessTerminalKey, wirelessTerminalItem).shape("EWE", "CTC", "EWE")
                .setIngredient('E', Material.ENDER_PEARL)
                .setIngredient('W', exact(waferItem))
                .setIngredient('C', Material.COPPER_INGOT)
                .setIngredient('T', exact(terminalItem)));

        recipe(new ShapedRecipe(powerBaseKey, powerBaseItem).shape("CRC", "LUL", "CRC")
                .setIngredient('C', Material.COPPER_INGOT)
                .setIngredient('R', Material.REDSTONE)
                .setIngredient('L', exact(latticeItem))
                .setIngredient('U', Material.COPPER_BULB));

        recipe(new ShapedRecipe(cargoNodeKey, cargoNodeItem).shape("CRC", "WHW", "CRC")
                .setIngredient('C', Material.COPPER_INGOT)
                .setIngredient('R', Material.REDSTONE)
                .setIngredient('W', exact(waferItem))
                .setIngredient('H', Material.HOPPER));

        recipe(new ShapedRecipe(basicKey, basicItem).shape("LCL", "CBC", "LCL")
                .setIngredient('L', exact(latticeItem))
                .setIngredient('C', Material.COPPER_INGOT)
                .setIngredient('B', Material.BARREL));

        // A filled cell has extra portable PDC and therefore will not match this exact empty-cell upgrade ingredient.
        recipe(new ShapedRecipe(poweredKey, poweredItem).shape("QWQ", "ECE", "QWQ")
                .setIngredient('Q', Material.QUARTZ)
                .setIngredient('W', exact(waferItem))
                .setIngredient('E', Material.ENDER_PEARL)
                .setIngredient('C', exact(basicItem)));

        if (recipes.size() != VaultWorksContentCatalog.ALL_IDS.size()) {
            throw new IllegalStateException(
                    "VaultWorks recipe count does not match content catalog: "
                            + recipes.size() + " != "
                            + VaultWorksContentCatalog.ALL_IDS.size()
            );
        }

        SimpleStaticGuidePage page = new SimpleStaticGuidePage(new NamespacedKey(this, "vaultworks"));
        page.addItem(circuitItem);
        page.addItem(waferItem);
        page.addItem(latticeItem);
        page.addItem(linkItem);
        page.addItem(indexItem);
        page.addItem(terminalItem);
        page.addItem(transmitterItem);
        page.addItem(antennaItem);
        page.addItem(dimensionalAntennaItem);
        page.addItem(wirelessTerminalItem);
        page.addItem(powerBaseItem);
        page.addItem(basicItem);
        page.addItem(poweredItem);
        page.addItem(cargoNodeItem);

        guide = new PageButton(Material.VAULT, page);
        RebarGuide.getRootPage().addButton(guide);

        getLogger().info(
                "Validated " + VaultWorksContentCatalog.ALL_IDS.size()
                        + " VaultWorks content entries and survival recipes."
        );
        getLogger().info(
                "VaultWorks ready: powered portable storage, transactional terminals, explicit indexing and loaded-only wireless access."
        );
    }

    private void migrateConfig() {
        boolean hasLegacyFixedLoad = getConfig().contains("power.watts", true);
        boolean hasNewBaseLoad = getConfig().contains("power.base-watts", true);

        if (hasLegacyFixedLoad && !hasNewBaseLoad) {
            double legacyWatts = getConfig().getDouble("power.watts", 48.0D);
            getConfig().set("power.base-watts", legacyWatts);
            getConfig().set("power.watts-per-vault", 0.0D);
            getConfig().set("power.watts", null);
            saveConfig();
            getLogger().info("Migrated legacy fixed Vault power load to base-watts="
                    + legacyWatts + " with watts-per-vault=0 to preserve existing behavior.");
        }
    }

    private static RecipeChoice exact(ItemStack stack) {
        return RecipeChoice.exactChoice(stack);
    }

    private void recipe(ShapedRecipe recipe) {
        RecipeType.VANILLA_SHAPED.addRecipe(ShapedRebarRecipe.fromVanilla(recipe));
        recipes.add(recipe.getKey());
    }

    @Override
    public void onDisable() {
        if (guide != null) {
            RebarGuide.getRootPage().getButtons().remove(guide);
            guide = null;
        }

        for (NamespacedKey key : recipes) {
            RecipeType.VANILLA_SHAPED.removeRecipe(key);
            Bukkit.removeRecipe(key);
        }
        recipes.clear();
        deleteConfirmations.clear();
        wirelessRegistry.clear();
        VaultEndpointRegistry.clear();
        storagePolicy = null;
        wirelessPolicy = null;
        instance = null;
    }

    public static VaultWorks instance() {
        return java.util.Objects.requireNonNull(instance, "VaultWorks is not enabled");
    }

    public PowerPolicy policy() {
        return policy;
    }

    public StoragePolicy storagePolicy() {
        return java.util.Objects.requireNonNull(storagePolicy, "Storage policy is not loaded");
    }

    public WirelessPolicy wirelessPolicy() {
        return java.util.Objects.requireNonNull(wirelessPolicy, "Wireless policy is not loaded");
    }

    VaultDeleteConfirmations deleteConfirmations() { return deleteConfirmations; }

    VaultWirelessRegistry wirelessRegistry() {
        return wirelessRegistry;
    }

    int registeredRecipeCount() {
        return recipes.size();
    }

    @Override
    public JavaPlugin getJavaPlugin() {
        return this;
    }

    @Override
    public Material getMaterial() {
        return Material.VAULT;
    }

    @Override
    public Locale getDefaultLanguage() {
        return Locale.ENGLISH;
    }
}
