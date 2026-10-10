package io.github.wickidcow.vaultworks;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VaultPreparedSnapshotValidatorTest {
    private static Properties complete(Set<UUID> participants) {
        Properties evidence = new Properties();
        evidence.setProperty("inventory.before.slots", "2");
        evidence.setProperty("inventory.before.0", "empty");
        evidence.setProperty("inventory.before.1", "empty");
        List<UUID> sorted = participants.stream().sorted().toList();
        for (int i = 0; i < sorted.size(); i++) {
            String prefix = "cell." + i + ".before";
            evidence.setProperty(prefix + ".id", sorted.get(i).toString());
            evidence.setProperty(prefix + ".location", "world:1:2:" + i);
            evidence.setProperty(prefix + ".amount", "0");
            evidence.setProperty(prefix + ".revision", "1");
            evidence.setProperty(prefix + ".purge", "false");
            evidence.setProperty(prefix + ".item", "empty");
            evidence.setProperty(prefix + ".legacy.slots", "0");
        }
        return evidence;
    }

    @Test void completeSnapshotsCoverExactParticipantIds() {
        Set<UUID> ids = Set.of(UUID.randomUUID(), UUID.randomUUID());
        assertDoesNotThrow(() -> VaultPreparedSnapshotValidator.validate(complete(ids), ids));
    }

    @Test void missingInventoryOrCellSnapshotIsRejected() {
        Set<UUID> ids = Set.of(UUID.randomUUID());
        Properties missingSlot = complete(ids);
        missingSlot.remove("inventory.before.1");
        assertThrows(IllegalArgumentException.class,
                () -> VaultPreparedSnapshotValidator.validate(missingSlot, ids));
        Properties missingRevision = complete(ids);
        missingRevision.remove("cell.0.before.revision");
        assertThrows(IllegalArgumentException.class,
                () -> VaultPreparedSnapshotValidator.validate(missingRevision, ids));
    }

    @Test void swappedOrExtraCellIdentitiesAreRejected() {
        Set<UUID> ids = Set.of(UUID.randomUUID(), UUID.randomUUID());
        Properties swapped = complete(ids);
        String first = swapped.getProperty("cell.0.before.id");
        String second = swapped.getProperty("cell.1.before.id");
        swapped.setProperty("cell.0.before.id", second);
        swapped.setProperty("cell.1.before.id", first);
        assertThrows(IllegalArgumentException.class,
                () -> VaultPreparedSnapshotValidator.validate(swapped, ids));
        Properties extra = complete(ids);
        extra.setProperty("cell.2.before.id", UUID.randomUUID().toString());
        assertThrows(IllegalArgumentException.class,
                () -> VaultPreparedSnapshotValidator.validate(extra, ids));
    }

    @Test void invalidAmountsOrMissingItemIdentityAreRejected() {
        Set<UUID> ids = Set.of(UUID.randomUUID());
        Properties evidence = complete(ids);
        evidence.setProperty("cell.0.before.amount", "-1");
        assertThrows(IllegalArgumentException.class,
                () -> VaultPreparedSnapshotValidator.validate(evidence, ids));
        evidence.setProperty("cell.0.before.amount", "100");
        assertThrows(IllegalArgumentException.class,
                () -> VaultPreparedSnapshotValidator.validate(evidence, ids));
        evidence.setProperty("cell.0.before.item", "AQIDBA==");
        assertDoesNotThrow(() -> VaultPreparedSnapshotValidator.validate(evidence, ids));
    }

    @Test void malformedSerializedItemAndHugeSlotCountAreRejected() {
        Set<UUID> ids = Set.of(UUID.randomUUID());
        Properties evidence = complete(ids);
        evidence.setProperty("inventory.before.0", "not-base64!!!");
        assertThrows(IllegalArgumentException.class,
                () -> VaultPreparedSnapshotValidator.validate(evidence, ids));
        evidence.setProperty("inventory.before.0", "empty");
        evidence.setProperty("inventory.before.slots", "1000000");
        assertThrows(IllegalArgumentException.class,
                () -> VaultPreparedSnapshotValidator.validate(evidence, ids));
    }

    @Test void noParticipantsCannotCreateAnUnscopedPreparedIntent() {
        assertThrows(IllegalArgumentException.class,
                () -> VaultPreparedSnapshotValidator.validate(complete(Set.of()), Set.of()));
    }
}
