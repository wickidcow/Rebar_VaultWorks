package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

final class VaultNetworkScanner {

    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST,
            BlockFace.UP, BlockFace.DOWN
    };

    private VaultNetworkScanner() {
    }

    static VaultNetworkSnapshot scan(VaultIndex index) {
        int maxNodes = Math.max(32, VaultWorks.instance().getConfig().getInt("index.max-network-nodes", 4096));

        Queue<Block> queue = new ArrayDeque<>();
        Set<NodePos> visited = new HashSet<>();
        Set<VaultPowerBase> bases = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        queue.add(index.getBlock());

        boolean truncated = false;

        while (!queue.isEmpty()) {
            Block block = queue.remove();
            NodePos pos = NodePos.of(block);
            if (!visited.add(pos)) {
                continue;
            }

            if (visited.size() > maxNodes) {
                truncated = true;
                break;
            }

            RebarBlock current;
            try {
                current = BlockStorage.get(block);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            if (!isNetworkNode(current)) {
                continue;
            }

            if (current instanceof VaultPowerBase base) {
                bases.add(base);
            }

            for (BlockFace face : FACES) {
                Block neighbor = block.getRelative(face);
                RebarBlock neighborNode;
                try {
                    neighborNode = BlockStorage.get(neighbor);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }

                if (isNetworkNode(neighborNode)) {
                    NodePos neighborPos = NodePos.of(neighbor);
                    if (!visited.contains(neighborPos)) {
                        queue.add(neighbor);
                    }
                }
            }
        }

        int vaults = 0;
        int registered = 0;
        int onlineColumns = 0;
        int legacyRecovery = 0;
        long totalStored = 0L;
        long accessibleStored = 0L;
        long totalCapacity = 0L;
        Set<ItemStack> types = new HashSet<>();

        for (VaultPowerBase base : bases) {
            boolean online = base.isOnline();
            if (online) {
                onlineColumns++;
            }

            for (int height = 1; height <= BasicVaultCell.MAX_COLUMN_HEIGHT; height++) {
                BasicVaultCell cell;
                try {
                    cell = BlockStorage.getAs(
                            BasicVaultCell.class,
                            base.getBlock().getRelative(BlockFace.UP, height)
                    );
                } catch (IllegalArgumentException ignored) {
                    break;
                }

                if (cell == null) {
                    break;
                }

                vaults++;
                totalCapacity = addClamped(totalCapacity, cell.getCapacity());
                totalStored = addClamped(totalStored, cell.getStoredAmount());

                if (online) {
                    accessibleStored = addClamped(accessibleStored, cell.getStoredAmount());
                }

                ItemStack item = cell.getStoredItem();
                if (item != null && !item.isEmpty()) {
                    registered++;
                    types.add(item.asOne());
                }

                if (cell.hasLegacyRecovery()) {
                    legacyRecovery++;
                }
            }
        }

        return new VaultNetworkSnapshot(
                Math.min(visited.size(), maxNodes),
                bases.size(),
                onlineColumns,
                vaults,
                registered,
                types.size(),
                legacyRecovery,
                totalStored,
                accessibleStored,
                totalCapacity,
                truncated
        );
    }

    private static boolean isNetworkNode(RebarBlock block) {
        return block instanceof VaultIndex
                || block instanceof VaultLinkCable
                || block instanceof VaultPowerBase;
    }

    private static long addClamped(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        if (left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private record NodePos(int x, int y, int z) {
        static NodePos of(Block block) {
            return new NodePos(block.getX(), block.getY(), block.getZ());
        }
    }
}
