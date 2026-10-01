package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

/**
 * Higher-tier Vault Cell using the ominous Vault appearance.
 *
 * It deliberately has no direct wire or cargo ports. Like every Vault Cell,
 * it is powered by a Vault Power Base below the column and automated through
 * a rear Vault Cargo Node.
 */
public final class PoweredVaultCell extends BasicVaultCell {

    public PoweredVaultCell(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        // Preserve the column-facing direction selected by the Vault Power Base.
        setVaultShell(true, null);
    }

    public PoweredVaultCell(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        setVaultShell(true, null);
    }

    @Override
    protected String capacityConfigKey() {
        return "storage.powered-capacity";
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return Component.text("Powered Vault Cell — " + (isOperational() ? "Online" : "Offline"));
    }
}
