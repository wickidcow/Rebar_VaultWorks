package io.github.wickidcow.vaultworks;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VaultRecoveryStoreTest {
    @TempDir Path directory;

    @Test void unresolvedParticipantsStayLockedAfterReopening() throws Exception {
        UUID cell = UUID.randomUUID();
        VaultRecoveryStore store = new VaultRecoveryStore(directory);
        Properties evidence = new Properties();
        evidence.setProperty("inventory.before.0", "custom item bytes / unicode: \u2603");
        UUID id = store.record(evidence, Set.of(cell), "world:1:2:3", "withdrawal");
        VaultRecoveryStore reopened = new VaultRecoveryStore(directory);
        assertTrue(reopened.locked(cell));
        assertTrue(reopened.locked("world:1:2:3"));
        assertFalse(reopened.locked(UUID.randomUUID()));
        assertFalse(reopened.globallyLocked());
        assertEquals(1, reopened.incidents().size());
        assertEquals(evidence.getProperty("inventory.before.0"),
                VaultRecoveryStore.read(directory.resolve(id + ".incident")).getProperty("inventory.before.0"));
    }

    @Test void verifiedResolutionRemovesOnlyItsOwnLock() throws Exception {
        VaultRecoveryStore store = new VaultRecoveryStore(directory);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        UUID id = store.record(new Properties(), Set.of(first), "first", "deposit");
        store.record(new Properties(), Set.of(second), "second", "withdrawal");
        store.resolved(id);
        VaultRecoveryStore reopened = new VaultRecoveryStore(directory);
        assertFalse(reopened.locked(first));
        assertTrue(reopened.locked(second));
        assertEquals(1, reopened.incidents().size());
        assertEquals("resolved", VaultRecoveryStore.read(directory.resolve(id + ".incident")).getProperty("state"));
    }

    @Test void overlappingIncidentsDoNotUnlockEachOther() throws Exception {
        VaultRecoveryStore store = new VaultRecoveryStore(directory);
        UUID cell = UUID.randomUUID();
        UUID first = store.record(new Properties(), Set.of(cell), "same", "deposit");
        store.record(new Properties(), Set.of(cell), "same", "deposit");
        store.resolved(first);
        assertTrue(store.locked(cell));
        assertTrue(store.locked("same"));
    }

    @Test void modifiedEvidenceLocksAllStorageInsteadOfBeingIgnored() throws Exception {
        VaultRecoveryStore store = new VaultRecoveryStore(directory);
        UUID id = store.record(new Properties(), Set.of(UUID.randomUUID()), "root", "withdrawal");
        Path path = directory.resolve(id + ".incident");
        Files.writeString(path, Files.readString(path) + "changed=true\n");
        VaultRecoveryStore reopened = new VaultRecoveryStore(directory);
        assertTrue(reopened.globallyLocked());
        assertTrue(reopened.locked(UUID.randomUUID()));
        assertTrue(Files.exists(path));
    }

    @Test void interruptedWritesFailClosed() throws Exception {
        Files.writeString(directory.resolve(UUID.randomUUID() + ".pending"), "incomplete");
        assertTrue(new VaultRecoveryStore(directory).globallyLocked());
    }

    @Test void renamedEvidenceFailsClosed() throws Exception {
        VaultRecoveryStore store = new VaultRecoveryStore(directory);
        UUID id = store.record(new Properties(), Set.of(UUID.randomUUID()), "root", "deposit");
        Files.move(directory.resolve(id + ".incident"), directory.resolve(UUID.randomUUID() + ".incident"));
        assertTrue(new VaultRecoveryStore(directory).globallyLocked());
    }

    @Test void diskFailureDoesNotPretendToResolveTheTransfer() throws Exception {
        Path missing = directory.resolve("missing");
        VaultRecoveryStore store = new VaultRecoveryStore(missing);
        Files.delete(missing);
        UUID cell = UUID.randomUUID();
        assertThrows(java.io.IOException.class, () -> store.record(new Properties(), Set.of(cell), "root", "deposit"));
        assertTrue(store.globallyLocked());
        assertTrue(store.locked(cell));
        assertEquals(1, store.incidents().size());
    }

    @Test void faultMarkerSurvivesRestart() {
        VaultRecoveryStore store = new VaultRecoveryStore(directory);
        store.failClosed("serializer failed before evidence could be captured");
        assertTrue(new VaultRecoveryStore(directory).globallyLocked());
    }

    @Test void preparedIntentSurvivesRestartAndLocksEveryParticipant() throws Exception {
        VaultRecoveryStore store = new VaultRecoveryStore(directory);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        Properties before = new Properties();
        before.setProperty("inventory.before.0", "serialized custom stack");
        UUID id = store.prepare(before, Set.of(first, second), "world:5:6:7", "terminal withdrawal");

        assertEquals("prepared",
                VaultRecoveryStore.read(directory.resolve(id + ".incident")).getProperty("state"));

        VaultRecoveryStore reopened = new VaultRecoveryStore(directory);
        assertFalse(reopened.globallyLocked());
        assertTrue(reopened.locked(first));
        assertTrue(reopened.locked(second));
        assertTrue(reopened.locked("world:5:6:7"));
        assertEquals(1, reopened.incidents().size());
        assertEquals("serialized custom stack",
                VaultRecoveryStore.read(directory.resolve(id + ".incident"))
                        .getProperty("inventory.before.0"));
    }

    @Test void preparedIntentCannotUseFailedTransferResolutionShortcut() throws Exception {
        VaultRecoveryStore store = new VaultRecoveryStore(directory);
        UUID cell = UUID.randomUUID();
        UUID id = store.prepare(new Properties(), Set.of(cell), "root", "deposit");
        assertThrows(java.io.IOException.class, () -> store.resolved(id));

        // A fault survives restart; a nominal "commit" is not a durability proof.
        assertTrue(store.globallyLocked());
        VaultRecoveryStore reopened = new VaultRecoveryStore(directory);
        assertTrue(reopened.globallyLocked());
        assertEquals("prepared",
                VaultRecoveryStore.read(directory.resolve(id + ".incident")).getProperty("state"));
    }

    @Test void incompletePreparedMetadataCannotCreateAnUnscopedLock() {
        VaultRecoveryStore store = new VaultRecoveryStore(directory);
        assertThrows(IllegalArgumentException.class,
                () -> store.prepare(new Properties(), Set.of(), "root", "deposit"));
        assertThrows(IllegalArgumentException.class,
                () -> store.prepare(new Properties(), Set.of(UUID.randomUUID()), "", "deposit"));
        assertTrue(store.incidents().isEmpty());
        assertFalse(store.globallyLocked());
    }

    @Test void tamperedPreparedIntentFailsClosedOnRestart() throws Exception {
        VaultRecoveryStore store = new VaultRecoveryStore(directory);
        UUID id = store.prepare(new Properties(), Set.of(UUID.randomUUID()), "root", "withdrawal");
        Path file = directory.resolve(id + ".incident");
        Files.writeString(file, Files.readString(file) + "other=true\\n");
        assertTrue(new VaultRecoveryStore(directory).globallyLocked());
    }

    @Test void preparedIntentRejectsOverlappingEndpointAndTerminalLocks() throws Exception {
        VaultRecoveryStore store = new VaultRecoveryStore(directory);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        store.prepare(new Properties(), Set.of(first), "terminal-a", "withdrawal");
        assertThrows(IllegalStateException.class,
                () -> store.prepare(new Properties(), Set.of(first), "terminal-b", "deposit"));
        assertThrows(IllegalStateException.class,
                () -> store.prepare(new Properties(), Set.of(second), "terminal-a", "deposit"));
        // A disjoint terminal and cell remain available.
        store.prepare(new Properties(), Set.of(second), "terminal-b", "deposit");
        assertEquals(2, store.incidents().size());

        VaultRecoveryStore reopened = new VaultRecoveryStore(directory);
        assertThrows(IllegalStateException.class,
                () -> reopened.prepare(new Properties(), Set.of(first), "terminal-c", "deposit"));
        assertEquals(2, reopened.incidents().size());
    }

    @Test void preparedIntentRejectsGlobalStoreFaultWithoutWritingMoreEvidence() {
        VaultRecoveryStore store = new VaultRecoveryStore(directory);
        store.failClosed("unresolved recovery store fault");
        assertThrows(IllegalStateException.class,
                () -> store.prepare(new Properties(), Set.of(UUID.randomUUID()), "root", "deposit"));
        assertTrue(store.incidents().isEmpty());
    }
}
