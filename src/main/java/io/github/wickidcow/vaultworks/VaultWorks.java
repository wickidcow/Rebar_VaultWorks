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

    private PowerPolicy policy;
    private PageButton guide;
    private final List<NamespacedKey> recipes = new ArrayList<>();

    @Override
    public void onEnable() {
        try {
            Class.forName("io.github.pylonmc.rebar.block.interfaces.ElectricRebarBlock", false, getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(
                    "VaultWorks requires electricity-enabled Rebar build 2064 (commit 5e34938). "
                            + "Stable Rebar 0.43.0-26.2 does not include electricity.",
                    exception
            );
        }

        instance = this;
        saveDefaultConfig();
        policy = new PowerPolicy(
                getConfig().getDouble("power.watts", 48.0D),
                getConfig().getInt("cargo.items-per-tick", 8)
        );

        registerWithRebar();

        NamespacedKey circuitKey = new NamespacedKey(this, "encoded_circuit");
        NamespacedKey waferKey = new NamespacedKey(this, "memory_wafer");
        NamespacedKey latticeKey = new NamespacedKey(this, "storage_lattice");
        NamespacedKey powerBaseKey = new NamespacedKey(this, "vault_power_base");
        NamespacedKey cargoNodeKey = new NamespacedKey(this, "vault_cargo_node");
        NamespacedKey basicKey = new NamespacedKey(this, "basic_vault_cell");
        NamespacedKey poweredKey = new NamespacedKey(this, "powered_vault_cell");

        ItemStack circuitItem = ItemStackBuilder.rebar(Material.CLOCK, circuitKey).build();
        ItemStack waferItem = ItemStackBuilder.rebar(Material.ECHO_SHARD, waferKey).build();
        ItemStack latticeItem = ItemStackBuilder.rebar(Material.HONEYCOMB, latticeKey).build();

        ItemStack powerBaseItem = ItemStackBuilder.rebar(Material.COPPER_BULB, powerBaseKey).build();
        ItemStack cargoNodeItem = ItemStackBuilder.rebar(Material.CHISELED_COPPER, cargoNodeKey).build();

        // Vault Cells are non-stackable because a broken cell may contain its complete portable state.
        ItemStack basicItem = ItemStackBuilder.rebar(Material.VAULT, basicKey)
                .set(DataComponentTypes.MAX_STACK_SIZE, 1)
                .build();
        ItemStack poweredItem = ItemStackBuilder.rebar(Material.VAULT, poweredKey)
                .set(DataComponentTypes.MAX_STACK_SIZE, 1)
                .build();

        RebarItem.register(RebarItem.class, circuitItem);
        RebarItem.register(RebarItem.class, waferItem);
        RebarItem.register(RebarItem.class, latticeItem);

        RebarBlock.register(powerBaseKey, Material.COPPER_BULB, VaultPowerBase.class);
        RebarBlock.register(cargoNodeKey, Material.CHISELED_COPPER, VaultCargoNode.class);
        RebarBlock.register(basicKey, Material.VAULT, BasicVaultCell.class);
        RebarBlock.register(poweredKey, Material.VAULT, PoweredVaultCell.class);

        RebarItem.register(RebarItem.class, powerBaseItem, powerBaseKey);
        RebarItem.register(RebarItem.class, cargoNodeItem, cargoNodeKey);
        RebarItem.register(RebarItem.class, basicItem, basicKey);
        RebarItem.register(RebarItem.class, poweredItem, poweredKey);

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

        SimpleStaticGuidePage page = new SimpleStaticGuidePage(new NamespacedKey(this, "vaultworks"));
        page.addItem(circuitItem);
        page.addItem(waferItem);
        page.addItem(latticeItem);
        page.addItem(powerBaseItem);
        page.addItem(basicItem);
        page.addItem(poweredItem);
        page.addItem(cargoNodeItem);

        guide = new PageButton(Material.VAULT, page);
        RebarGuide.getRootPage().addButton(guide);

        getLogger().info(
                "VaultWorks ready: portable bulk Vault Cells, six-high powered columns and rear Rebar cargo nodes."
        );
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
        instance = null;
    }

    public static VaultWorks instance() {
        return java.util.Objects.requireNonNull(instance, "VaultWorks is not enabled");
    }

    public PowerPolicy policy() {
        return policy;
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
