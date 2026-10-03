package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import net.kyori.adventure.text.Component;
import org.bukkit.event.inventory.ClickType;
import java.util.List;
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
    protected long configuredCapacity(StoragePolicy policy) {
        return policy.poweredCapacity();
    }

    @Override
    protected long quickWithdrawAmount(ClickType clickType) {
        return switch (clickType) {
            case LEFT -> 1L;
            case RIGHT -> 64L;
            case SHIFT_LEFT -> Long.MAX_VALUE;
            default -> 0L;
        };
    }

    @Override
    protected List<Component> quickWithdrawLore() {
        return List.of(Component.text("Left-click: withdraw 1."),
                Component.text("Right-click: withdraw up to 64."),
                Component.text("Shift + left-click: fill inventory."));
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return Component.text("Advanced Powered Vault Cell — "
                + (hasIdentityConflict() ? "LOCKED / ID CONFLICT" : (isOperational() ? "Online" : "Offline")));
    }
}
