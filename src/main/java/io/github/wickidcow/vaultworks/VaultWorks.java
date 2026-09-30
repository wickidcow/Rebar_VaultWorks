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
        var basic = new NamespacedKey(this, "basic_vault_cell");
        var powered = new NamespacedKey(this, "powered_vault_cell");
        ItemStack basicItem = ItemStackBuilder.rebar(Material.IRON_BLOCK, basic).build();
        ItemStack poweredItem = ItemStackBuilder.rebar(Material.COPPER_BLOCK, powered).build();
        RebarBlock.register(basic, Material.IRON_BLOCK, BasicVaultCell.class);
        RebarBlock.register(powered, Material.COPPER_BLOCK, PoweredVaultCell.class);
        RebarItem.register(RebarItem.class, basicItem, basic);
        RebarItem.register(RebarItem.class, poweredItem, powered);
        recipe(new ShapedRecipe(basic, basicItem).shape("ICI", "CRC", "ICI")
                .setIngredient('I', Material.IRON_INGOT).setIngredient('C', Material.CHEST).setIngredient('R', Material.REDSTONE));
        recipe(new ShapedRecipe(powered, poweredItem).shape("CQC", "QDQ", "CQC")
                .setIngredient('C', Material.COPPER_INGOT).setIngredient('Q', Material.QUARTZ).setIngredient('D', Material.DIAMOND));
        // The powered recipe never consumes a placed/filled cell or its stored inventory.
        var page = new SimpleStaticGuidePage(new NamespacedKey(this, "vaultworks"));
        page.addItem(basicItem); page.addItem(poweredItem);
        guide = new PageButton(Material.CHEST, page);
        RebarGuide.getRootPage().addButton(guide);
        getLogger().info("VaultWorks powered storage ready: 54 slots per cell, " + policy.watts() + " W per powered cell.");
    }

    private void recipe(ShapedRecipe recipe) {
        RecipeType.VANILLA_SHAPED.addRecipe(ShapedRebarRecipe.fromVanilla(recipe));
        recipes.add(recipe.getKey());
    }

    @Override public void onDisable() {
        if (guide != null) { RebarGuide.getRootPage().getButtons().remove(guide); guide = null; }
        for (var key : recipes) { RecipeType.VANILLA_SHAPED.removeRecipe(key); Bukkit.removeRecipe(key); }
        recipes.clear(); instance = null;
    }

    public static VaultWorks instance() { return java.util.Objects.requireNonNull(instance, "VaultWorks is not enabled"); }
    public PowerPolicy policy() { return policy; }
    @Override public JavaPlugin getJavaPlugin() { return this; }
    @Override public Material getMaterial() { return Material.CHEST; }
    @Override public Locale getDefaultLanguage() { return Locale.ENGLISH; }
}
