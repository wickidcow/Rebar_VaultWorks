package io.github.wickidcow.vaultworks;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Durable transfer evidence. The existing failure path writes OPEN incidents.
 * The PREPARED state is a *dormant* prerequisite for future pre-mutation
 * journaling: no gameplay path creates one until cross-save durability is
 * implemented and verified. Never mark PREPARED intents resolved just because
 * an in-memory operation returned successfully.
 */
final class VaultRecoveryStore {
    private static final String HEADER = "VAULTWORKS_RECOVERY_1 ";
    private static final long MAX_RECORD_BYTES = 16 * 1024 * 1024;
    private final Path directory;
    private final Map<UUID, Incident> incidents = new HashMap<>();
    private final List<String> faults = new ArrayList<>();

    record Incident(UUID id, Set<UUID> cells, String root, String operation) { }

    VaultRecoveryStore(Path directory) {
        this.directory = directory;
        try {
            Files.createDirectories(directory);
            try (var paths = Files.list(directory)) {
                for (Path path : paths.toList()) {
                    String name = path.getFileName().toString();
                    if (name.equals("store.fault")) {
                        faults.add("Recovery store fault marker requires offline review");
                    } else if (name.endsWith(".pending")) {
                        faults.add("Incomplete recovery record: " + name);
                    } else if (name.endsWith(".incident")) {
                        try {
                            Properties record = read(path);
                            Incident incident = parse(record);
                            if (!name.equals(incident.id() + ".incident")) throw new IOException("Record ID mismatch");
                            String state = record.getProperty("state", "open");
                            if (state.equals("resolved")) continue;
                            if (!state.equals("open") && !state.equals("prepared")) throw new IOException("Unknown recovery state");
                            if (incidents.putIfAbsent(incident.id(), incident) != null) throw new IOException("Duplicate record ID");
                        } catch (IOException | RuntimeException failure) {
                            faults.add("Unreadable recovery record: " + name);
                        }
                    }
                }
            }
        } catch (IOException failure) {
            faults.add("Cannot open recovery directory: " + failure.getMessage());
        }
    }

    UUID record(Properties evidence, Set<UUID> cells, String root, String operation) throws IOException {
        return createRecord(evidence, cells, root, operation, "open");
    }

    /**
     * Persist a PREPARED intent before touching any player or cell storage.
     * This is intentionally not called from live transfers yet. A prepared
     * intent survives restart, holds its participants locked and requires
     * offline reconciliation, even if the initiating Java call had completed.
     *
     * <p>Integration must first establish a durable, cross-save commit barrier:
     * neither a successful return nor an in-memory rollback is enough to
     * prove that both player inventory and cell state survived process loss.</p>
     */
    UUID prepare(Properties evidence, Set<UUID> cells, String root, String operation) throws IOException {
        if (evidence == null || cells == null || cells.isEmpty()
                || root == null || root.isBlank() || operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("Prepared transfer must name its participants, root and operation");
        }
        if (globallyLocked() || locked(root) || cells.stream().anyMatch(this::locked)) {
            throw new IllegalStateException("A prepared transfer cannot overlap unresolved recovery locks");
        }
        return createRecord(evidence, cells, root, operation, "prepared");
    }

    private UUID createRecord(Properties evidence, Set<UUID> cells, String root,
                              String operation, String state) throws IOException {
        UUID id = UUID.randomUUID();
        // Install the lock before any serialization or filesystem operation can fail.
        incidents.put(id, new Incident(id, Set.copyOf(cells), root, operation));
        evidence.setProperty("id", id.toString());
        evidence.setProperty("cells", cells.stream().map(UUID::toString).sorted().collect(Collectors.joining(",")));
        evidence.setProperty("root", root);
        evidence.setProperty("operation", operation);
        evidence.setProperty("created", java.time.Instant.now().toString());
        evidence.setProperty("state", state);
        try {
            persist(id, evidence);
            return id;
        } catch (IOException | RuntimeException failure) {
            failClosed("Could not persist recovery record " + id);
            throw failure;
        }
    }

    private void persist(UUID id, Properties evidence) throws IOException {
        StringWriter writer = new StringWriter();
        evidence.store(writer, "VaultWorks failed-transfer evidence; preserve until reviewed offline");
        byte[] payload = writer.toString().getBytes(StandardCharsets.UTF_8);
        byte[] header = (HEADER + digest(payload) + "\n").getBytes(StandardCharsets.US_ASCII);
        Path pending = directory.resolve(id + ".pending");
        try {
            if (header.length + payload.length > MAX_RECORD_BYTES) throw new IOException("Recovery record exceeds safety bound");
            try (FileChannel channel = FileChannel.open(pending, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                writeFully(channel, ByteBuffer.wrap(header));
                writeFully(channel, ByteBuffer.wrap(payload));
                channel.force(true);
            }
            Files.move(pending, directory.resolve(id + ".incident"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            forceDirectory();
        } catch (IOException | RuntimeException failure) {
            throw failure;
        }
    }

    void resolved(UUID id) throws IOException {
        // Called only after every participant was restored AND verified.
        try {
            Properties evidence = read(directory.resolve(id + ".incident"));
            if (!parse(evidence).id().equals(id)) throw new IOException("Resolution ID mismatch");
            if (!"open".equals(evidence.getProperty("state"))) {
                throw new IOException("Prepared or already-resolved transfer cannot be finalized by rollback resolution");
            }
            evidence.setProperty("state", "resolved");
            evidence.setProperty("resolved", java.time.Instant.now().toString());
            persist(id, evidence);
            incidents.remove(id);
        } catch (IOException | RuntimeException failure) {
            failClosed("Could not finalize recovered transfer " + id);
            throw failure;
        }
    }

    void failClosed(String reason) {
        if (!faults.contains(reason)) faults.add(reason);
        try (FileChannel channel = FileChannel.open(directory.resolve("store.fault"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            writeFully(channel, ByteBuffer.wrap((reason + "\n").getBytes(StandardCharsets.UTF_8)));
            channel.force(true);
            forceDirectory();
        } catch (IOException | RuntimeException ignored) {
            // A completely unavailable disk cannot preserve a marker. The in-memory
            // global lock and original exception still prevent further live transfers.
        }
    }
    boolean globallyLocked() { return !faults.isEmpty(); }
    boolean locked(UUID cell) { return globallyLocked() || incidents.values().stream().anyMatch(i -> i.cells().contains(cell)); }
    boolean locked(String root) { return globallyLocked() || incidents.values().stream().anyMatch(i -> i.root().equals(root)); }
    List<Incident> incidents() { return incidents.values().stream().sorted(java.util.Comparator.comparing(i -> i.id().toString())).toList(); }
    List<String> faults() { return List.copyOf(faults); }

    private static Incident parse(Properties record) {
        UUID id = UUID.fromString(record.getProperty("id"));
        Set<UUID> cells = java.util.Arrays.stream(record.getProperty("cells").split(",")).map(UUID::fromString).collect(Collectors.toUnmodifiableSet());
        String root = java.util.Objects.requireNonNull(record.getProperty("root"));
        String operation = java.util.Objects.requireNonNull(record.getProperty("operation"));
        if (cells.isEmpty() || root.isBlank() || operation.isBlank()) throw new IllegalArgumentException("Incomplete record");
        return new Incident(id, cells, root, operation);
    }

    static Properties read(Path path) throws IOException {
        if (Files.size(path) > MAX_RECORD_BYTES) throw new IOException("Oversized recovery record");
        String text = Files.readString(path, StandardCharsets.UTF_8);
        int newline = text.indexOf('\n');
        if (newline < 0 || !text.startsWith(HEADER)) throw new IOException("Unknown recovery format");
        String payload = text.substring(newline + 1);
        if (!text.substring(HEADER.length(), newline).equals(digest(payload.getBytes(StandardCharsets.UTF_8)))) {
            throw new IOException("Recovery checksum mismatch");
        }
        Properties record = new Properties();
        record.load(new StringReader(payload));
        return record;
    }

    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void writeFully(FileChannel channel, ByteBuffer bytes) throws IOException {
        while (bytes.hasRemaining()) channel.write(bytes);
    }

    private void forceDirectory() throws IOException {
        // Windows does not expose directory handles through FileChannel. The file
        // itself is still forced and the move must still be atomic on that platform.
        if (System.getProperty("os.name", "").startsWith("Windows")) return;
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
    }
}
