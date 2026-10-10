package io.github.wickidcow.vaultworks;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * Validates the scope and complete BEFORE snapshots of an inactive PREPARED intent.
 *
 * <p>Successful validation is not a cross-save durability guarantee. It only
 * prevents an incomplete pre-mutation record from being accepted. The future
 * commit protocol must also record and verify post-mutation state.</p>
 */
final class VaultPreparedSnapshotValidator {
    private static final int MAX_INVENTORY_SLOTS = 1024;
    private static final int MAX_CELL_SNAPSHOTS = 4096;
    private static final int MAX_STACK_BYTES = 2 * 1024 * 1024;

    private VaultPreparedSnapshotValidator() {}

    static void validate(Properties evidence, Set<UUID> participants) {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(participants, "participants");
        if (participants.isEmpty() || participants.size() > MAX_CELL_SNAPSHOTS) {
            throw new IllegalArgumentException("Prepared intent must have 1..4096 cell participants");
        }

        int inventorySlots = integer(evidence, "inventory.before.slots", 1, MAX_INVENTORY_SLOTS);
        for (int i = 0; i < inventorySlots; i++) {
            item(evidence, "inventory.before." + i);
        }

        List<UUID> sorted = new ArrayList<>(participants);
        sorted.sort(Comparator.naturalOrder());
        for (int i = 0; i < sorted.size(); i++) {
            String prefix = "cell." + i + ".before";
            String expectedId = sorted.get(i).toString();
            if (!expectedId.equals(require(evidence, prefix + ".id"))) {
                throw new IllegalArgumentException("Incomplete or mismatched prepared cell identity: " + prefix);
            }
            require(evidence, prefix + ".location");
            long amount = number(evidence, prefix + ".amount");
            number(evidence, prefix + ".revision");
            String purge = require(evidence, prefix + ".purge");
            if (!purge.equals("true") && !purge.equals("false")) {
                throw new IllegalArgumentException("Invalid prepared purge flag: " + prefix);
            }
            String item = item(evidence, prefix + ".item");
            if (amount > 0L && item.equals("empty")) {
                throw new IllegalArgumentException("Stored items missing their registered identity: " + prefix);
            }

            int legacySlots = integer(evidence, prefix + ".legacy.slots", 0, MAX_INVENTORY_SLOTS);
            for (int slot = 0; slot < legacySlots; slot++) {
                item(evidence, prefix + ".legacy." + slot);
            }
        }

        // Surplus snapshots would create an ambiguous participant count. Future
        // inspection must know that every saved source is part of the lock set.
        long listed = evidence.stringPropertyNames().stream()
                .filter(key -> key.matches("cell\\.\\d+\\.before\\.id"))
                .count();
        if (listed != sorted.size()) {
            throw new IllegalArgumentException("Prepared cell snapshots do not match participant count");
        }

        // A hidden surplus slot is evidence outside the declared snapshot.
        // Reject it rather than allowing a future replay path to ignore items.
        for (String key : evidence.stringPropertyNames()) {
            if (key.matches("inventory\\.before\\.\\d+")) {
                int index = suffixIndex(key, "inventory.before.");
                if (index >= inventorySlots) {
                    throw new IllegalArgumentException("Undeclared prepared inventory slot: " + key);
                }
            }
            if (key.matches("cell\\.\\d+\\.before\\..*")) {
                int cellIndex = suffixIndex(key, "cell.");
                if (cellIndex >= sorted.size()) {
                    throw new IllegalArgumentException("Undeclared prepared cell snapshot: " + key);
                }
            }
        }
    }

    private static int suffixIndex(String key, String prefix) {
        int end = key.indexOf('.', prefix.length());
        String raw = key.substring(prefix.length(), end < 0 ? key.length() : end);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException overflow) {
            throw new IllegalArgumentException("Out of range snapshot index: " + key, overflow);
        }
    }

    private static String item(Properties evidence, String key) {
        String encoded = require(evidence, key);
        if (encoded.equals("empty")) {
            return encoded;
        }
        if (encoded.length() > (long) MAX_STACK_BYTES * 2L) {
            throw new IllegalArgumentException("Oversized prepared item bytes: " + key);
        }
        try {
            byte[] value = Base64.getDecoder().decode(encoded);
            if (value.length == 0 || value.length > MAX_STACK_BYTES) {
                throw new IllegalArgumentException("Invalid prepared item byte length: " + key);
            }
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Prepared item is not a bounded serialized stack: " + key, invalid);
        }
        return encoded;
    }

    private static String require(Properties evidence, String key) {
        String value = evidence.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing prepared snapshot field: " + key);
        }
        return value;
    }

    private static int integer(Properties evidence, String key, int minimum, int maximum) {
        long value = number(evidence, key);
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Prepared slot count out of range: " + key);
        }
        return (int) value;
    }

    private static long number(Properties evidence, String key) {
        String raw = require(evidence, key);
        try {
            long value = Long.parseLong(raw);
            if (value < 0) {
                throw new IllegalArgumentException("Negative prepared number: " + key);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid prepared number: " + key, exception);
        }
    }
}
