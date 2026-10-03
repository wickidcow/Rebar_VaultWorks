package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.item.RebarItem;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class VaultTestPowerSourceItem extends RebarItem {
    public VaultTestPowerSourceItem(@NotNull ItemStack stack) { super(stack); }

    @Override public boolean prePlace(@NotNull BlockCreateContext context) {
        if (context.getPlayer() != null && !context.getPlayer().hasPermission("vaultworks.admin")) {
            context.getPlayer().sendMessage(Component.text("Only administrators can place a Vault Test Power Source."));
            return false;
        }
        return super.prePlace(context);
    }
}
