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

    @Override public void onEnable() {
        try {
            Class.forName("io.github.pylonmc.rebar.block.interfaces.ElectricRebarBlock", false, getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("VaultWorks requires electricity-enabled Rebar build 2064 "
                    + "(commit 5e34938). Stable Rebar 0.43.0-26.2 does not include electricity.", exception);
        }

        instance = this;
        saveDefaultConfig();
        policy = new PowerPolicy(getConfig().getDouble("power.watts", 32), getConfig().getInt("cargo.items-per-tick", 8));
        registerWithRebar();

        var circuitKey = new NamespacedKey(this, "encoded_circuit");
        var waferKey = new NamespacedKey(this, "memory_wafer");
        var latticeKey = new NamespacedKey(this, "storage_lattice");
        var basicKey = new NamespacedKey(this, "basic_vault_cell");
        var poweredKey = new NamespacedKey(this, "powered_vault_cell");

        // Non-block components intentionally use distinct vanilla item silhouettes so the guide
        // feels like a real progression tree without requiring a resource pack.
        ItemStack circuitItem = ItemStackBuilder.rebar(Material.CLOCK, circuitKey).build();
        ItemStack waferItem = ItemStackBuilder.rebar(Material.ECHO_SHARD, waferKey).build();
        ItemStack latticeItem = ItemStackBuilder.rebar(Material.HONEYCOMB, latticeKey).build();

        // Storage blocks use container-like visuals rather than generic metal cubes.
        ItemStack basicItem = ItemStackBuilder.rebar(Material.BARREL, basicKey).build();
        ItemStack poweredItem = ItemStackBuilder.rebar(Material.ENDER_CHEST, poweredKey).build();

        RebarItem.register(RebarItem.class, circuitItem, circuitKey);
        RebarItem.register(RebarItem.class, waferItem, waferKey);
        RebarItem.register(RebarItem.class, latticeItem, latticeKey);

        RebarBlock.register(basicKey, Material.BARREL, BasicVaultCell.class);
        RebarBlock.register(poweredKey, Material.ENDER_CHEST, PoweredVaultCell.class);
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

        recipe(new ShapedRecipe(basicKey, basicItem).shape("LCL", "CBC", "LCL")
                .setIngredient('L', exact(latticeItem))
                .setIngredient('C', Material.COPPER_INGOT)
                .setIngredient('B', Material.BARREL));

        // Upgrading consumes only the empty Basic Vault Cell item, never a placed cell or its contents.
        recipe(new ShapedRecipe(poweredKey, poweredItem).shape("QWQ", "ECE", "QWQ")
                .setIngredient('Q', Material.QUARTZ)
                .setIngredient('W', exact(waferItem))
                .setIngredient('E', Material.ENDER_PEARL)
                .setIngredient('C', exact(basicItem)));

        var page = new SimpleStaticGuidePage(new NamespacedKey(this, "vaultworks"));
        page.addItem(circuitItem);
        page.addItem(waferItem);
        page.addItem(latticeItem);
        page.addItem(basicItem);
        page.addItem(poweredItem);
        guide = new PageButton(Material.ENDER_CHEST, page);
        RebarGuide.getRootPage().addButton(guide);

        getLogger().info("VaultWorks storage foundation ready: manual Basic Vault Cell plus powered Rebar cargo storage.");
    }

    private static RecipeChoice exact(ItemStack stack) {
        return new RecipeChoice.ExactChoice(stack);
    }

    private void recipe(ShapedRecipe recipe) {
        RecipeType.VANILLA_SHAPED.addRecipe(ShapedRebarRecipe.fromVanilla(recipe));
        recipes.add(recipe.getKey());
    }

    @Override public void onDisable() {
        if (guide != null) {
            RebarGuide.getRootPage().getButtons().remove(guide);
            guide = null;
        }
        for (var key : recipes) {
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

    @Override public JavaPlugin getJavaPlugin() {
        return this;
    }

    @Override public Material getMaterial() {
        return Material.ENDER_CHEST;
    }

    @Override public Locale getDefaultLanguage() {
        return Locale.ENGLISH;
    }
}
