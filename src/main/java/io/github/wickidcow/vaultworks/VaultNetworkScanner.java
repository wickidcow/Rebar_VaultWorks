package io.github.wickidcow.vaultworks;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
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
        return scanView(index).snapshot();
    }

    static VaultNetworkCells scanAccessibleCells(RebarBlock root) {
        Topology topology = collectTopology(root);
        List<BasicVaultCell> cells = new ArrayList<>();

        for (VaultPowerBase base : topology.bases()) {
            if (!base.isOnline()) {
                continue;
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
                cells.add(cell);
            }
        }

        return new VaultNetworkCells(cells, topology.truncated());
    }

    static VaultNetworkView scanView(RebarBlock root) {
        Topology topology = collectTopology(root);

        int vaults = 0;
        int registered = 0;
        int onlineColumns = 0;
        int legacyRecovery = 0;
        long totalStored = 0L;
        long accessibleStored = 0L;
        long totalCapacity = 0L;

        Map<ItemIdentity, MutableItemSummary> itemTotals = new HashMap<>();

        for (VaultPowerBase base : topology.bases()) {
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
                    ItemStack normalized = item.asOne();
                    ItemIdentity identity = ItemIdentity.of(normalized);
                    MutableItemSummary summary = itemTotals.computeIfAbsent(
                            identity,
                            ignored -> new MutableItemSummary(normalized)
                    );
                    summary.totalStored = addClamped(summary.totalStored, cell.getStoredAmount());
                    if (online) {
                        summary.accessibleStored = addClamped(summary.accessibleStored, cell.getStoredAmount());
                    }
                    summary.vaultCount++;
                }

                if (cell.hasLegacyRecovery()) {
                    legacyRecovery++;
                }
            }
        }

        List<VaultItemSummary> items = new ArrayList<>(itemTotals.size());
        for (MutableItemSummary summary : itemTotals.values()) {
            items.add(new VaultItemSummary(
                    summary.item.asOne(),
                    summary.totalStored,
                    summary.accessibleStored,
                    summary.vaultCount
            ));
        }

        // Keep page ordering deterministic without flattening custom item identity.
        items.sort(
                Comparator.comparing((VaultItemSummary summary) -> summary.item().getType().getKey().toString())
                        .thenComparingInt(summary -> Arrays.hashCode(summary.item().serializeAsBytes()))
        );

        VaultNetworkSnapshot snapshot = new VaultNetworkSnapshot(
                topology.networkNodes(),
                topology.bases().size(),
                onlineColumns,
                vaults,
                registered,
                items.size(),
                legacyRecovery,
                totalStored,
                accessibleStored,
                totalCapacity,
                topology.truncated()
        );

        int configuredItems = VaultWorks.instance().getConfig().getInt("terminal.max-item-types", 4096);
        int maxItems = Math.max(128, Math.min(configuredItems, 16_384));
        boolean itemsTruncated = items.size() > maxItems;
        List<VaultItemSummary> displayedItems = itemsTruncated
                ? List.copyOf(items.subList(0, maxItems))
                : List.copyOf(items);

        return new VaultNetworkView(snapshot, displayedItems, itemsTruncated);
    }

    private static Topology collectTopology(RebarBlock root) {
        int configuredMax = VaultWorks.instance().getConfig().getInt("index.max-network-nodes", 4096);
        int maxNodes = Math.max(32, Math.min(configuredMax, 65_536));

        Queue<Block> queue = new ArrayDeque<>();
        Set<NodePos> visited = new HashSet<>();
        Set<VaultPowerBase> bases = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        queue.add(root.getBlock());

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

                if (!isNetworkNode(neighborNode)) {
                    continue;
                }

                NodePos neighborPos = NodePos.of(neighbor);
                if (!visited.contains(neighborPos)) {
                    queue.add(neighbor);
                }
            }
        }

        return new Topology(
                Math.min(visited.size(), maxNodes),
                bases,
                truncated
        );
    }

    private static boolean isNetworkNode(RebarBlock block) {
        return block instanceof VaultIndex
                || block instanceof VaultTerminal
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

    private static final class ItemIdentity {
        private final byte[] bytes;
        private final int hash;

        private ItemIdentity(byte[] bytes) {
            this.bytes = bytes;
            this.hash = Arrays.hashCode(bytes);
        }

        static ItemIdentity of(ItemStack item) {
            return new ItemIdentity(item.asOne().serializeAsBytes());
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ItemIdentity identity && Arrays.equals(bytes, identity.bytes);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final class MutableItemSummary {
        private final ItemStack item;
        private long totalStored;
        private long accessibleStored;
        private int vaultCount;

        private MutableItemSummary(ItemStack item) {
            this.item = item.asOne();
        }
    }

    private record Topology(
            int networkNodes,
            Set<VaultPowerBase> bases,
            boolean truncated
    ) {
    }

    private record NodePos(UUID world, int x, int y, int z) {
        static NodePos of(Block block) {
            return new NodePos(
                    block.getWorld().getUID(),
                    block.getX(),
                    block.getY(),
                    block.getZ()
            );
        }
    }
}
