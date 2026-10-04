# Rebar VaultWorks

**Distributed digital storage, indexed inventory, and request crafting for Pylon/Rebar.**

VaultWorks provides powered portable storage, searchable terminals, and wireless inventory access for Rebar. Request crafting is planned separately.

The goal is not to clone Slimefun Networks, Applied Energistics, Refined Storage, or Infinity Expansion. VaultWorks borrows the lessons that made those systems useful while giving Rebar its own storage model:

> **Items remain owned by physical storage cells. An index knows where they are. Terminals request them. Fabricators reserve and craft them.**

There is no magic global inventory object and no world-wide storage scan.

## Project status

**0.3.0-rc.2: release-candidate storage network with transactional and wireless access.**

Available now:

- Encoded Circuit, Memory Wafer and Storage Lattice progression components.
- **Basic Powered Vault Cell** — one registered item type, configurable default capacity of **1,000,000 items**.
- **Advanced Powered Vault Cell** — advanced ominous-Vault visual, configurable default capacity of **4,000,000 items**.
- **Vault Power Base** — powers one contiguous vertical column of up to **6 Vault Cells**.
- **Vault Cargo Node** — sits directly behind one Vault Cell and exposes one outward Rebar cargo connection.
- **Vault Link Cable** — passive explicit topology for metadata/index connections.
- **Vault Index** — read-only network overview across explicitly connected loaded Power Bases.
- **Vault Terminal** — paged/searchable inventory with transactional deposit and withdrawal against live physical cells.
- **Vault Transmitter** — powered wireless access point that reuses the same terminal transaction path.
- **Vault Antenna** — extends same-world portable-terminal range.
- **Dimensional Vault Antenna** — permits cross-world/dimension access while the source transmitter is loaded and powered.
- **Wireless Vault Terminal** — bindable portable terminal for player inventory access away from the storage room.
- Persistent Vault endpoint UUIDs and monotonic storage revisions for safe identity/change tracking.
- Floating registered-item display inside each Vault.
- Quick deposit, quick withdraw, clear-registration control and per-Vault overflow-purge toggle.
- Filled Vault Cells keep their stored item, count and overflow setting inside the dropped Vault item when broken, so the cell can be moved and placed elsewhere without dumping its contents.

Requires **Paper 26.2, Java 25, and Rebar 0.44.2-26.2**. The released Rebar API and server JAR are pinned for this build. Older Rebar versions without electricity are unsupported.

[Download the rolling raw 0.3.0-rc.2 JAR](https://github.com/wickidcow/Rebar_VaultWorks/releases/download/dev-build/Rebar_VaultWorks-0.3.0-rc.2.jar). Place it directly in `plugins/` and restart. The rolling development JAR is published only after the main branch passes build, package, live Paper/Rebar startup, `/vaultworks doctor`, clean shutdown, and byte-for-byte rebuild verification.

### Build a Vault column

1. Place a **Vault Power Base** first. Its front should face the player/storage aisle; its electrical port is on the **rear service side** so wiring can stay hidden.
2. Stack **1–6 Vault Cells directly above the base**. A seventh placement is rejected.
3. Connect Rebar electricity to the rear of the base. Default demand is **16 W base + 8 W per contiguous Vault**: 24 W for one Vault and 64 W for a full six-high column.
4. When power is available, the base lights and each registered item display inside the connected Vaults brightens/glows. The vanilla Vault block itself stays in its inert state so VaultWorks does not trigger Mojang's trial-key/reward behavior. Without power, Vault storage controls and cargo operations are locked, but stored data remains safe.
5. Open a Vault (an empty hand works), click **Register Stored Item**, then choose an item from your inventory. Registering consumes exactly **1 item** and permanently keys that Vault to that exact item identity until the count reaches zero and the registration is cleared.
6. Place a **Vault Cargo Node directly behind a Vault Cell**. Connect Rebar cargo to the node's outward-facing side. The front of the Vault wall remains clean.
7. Overflow purge is **OFF by default**. When enabled for a Vault, matching input beyond capacity is intentionally destroyed; when disabled, excess stays at the source.
8. Break a filled Vault Cell normally and the stored state travels with the dropped Vault Cell item. Place that item above another valid Vault Power Base to restore it.

The Power Base uses a lit copper-bulb shell as the safe vanilla fallback for the “powered bedrock / power mat” idea. It remains normally breakable; VaultWorks does not turn the actual world block into unbreakable Bedrock.

### Cell controls and empty stacking

Both tiers require a powered Vault Power Base. Display names are **Basic Powered Vault Cell** and **Advanced Powered Vault Cell**; their existing IDs remain `basic_vault_cell` and `powered_vault_cell`.

Advanced cell Quick Withdraw:

| Click | Result |
| --- | --- |
| Left | Withdraw 1 item |
| Right | Withdraw up to 64 items |
| Shift + left | Fill available inventory space |

Stack limits and available inventory space are respected. Basic cells retain left-click to fill inventory and right-click for one item.

Healthy empty cells stack up to 64 per tier. Unregistered empty drops with default overflow settings stack with freshly crafted cells. Registered empty cells stack only when their item registration and overflow settings match. Empty portable stacks receive fresh endpoint IDs when placed. Filled cells, cells with legacy recovery contents, and identity-conflicted or transfer-recovery-locked cells remain unstackable and preserve their endpoint IDs.

### Permanently empty a cell

Click **Delete Contents** at the top-right of the cell menu. Each click reveals the next confirmation beneath it: **Are you sure? → Are you really sure? → I'm completely sure → Delete permanently — no going back**.

Only the final step destroys the stored items (including any legacy recovery stacks) and clears item registration. The physical Vault stays in place. Confirmation belongs to one player, expires after 30 seconds, and resets on menu close or any cell revision change. Pause cargo first so transfers do not invalidate confirmation. There are no drops or refunds from deletion.

### Temporary test power

Administrators can run `/vaultworks testpower` in game to receive a **Vault Test Power Source**. It supplies **1 MW** continuously through normal Rebar electrical ports on all six sides. Connect it to the rear electrical port of a Vault Power Base or Transmitter. It does not require fuel, daylight, or a particular dimension.

The block has no survival recipe and only administrators can place it. Remove it after testing. `/vaultworks doctor` reports storage policies, loaded cells, transmitters, identity conflicts, and unresolved transfer recovery.

### Failed-transfer recovery

If an inventory operation throws, VaultWorks attempts to restore **every** participating cell and the inventory independently. A failure in one restore no longer skips the others. Registration, cell quick transfers, legacy recovery, and terminal transfers use cloned snapshots.

Before attempting compensation, VaultWorks writes a checksummed recovery record containing the original contents and the contents observed at failure. When every restore is verified, that record is marked resolved and retained for audit. If compensation fails, the record remains and the affected cells and terminal stay locked across restart. Cargo, manual access, wireless access, block breaking, and endpoint re-keying respect the lock. Healthy storage elsewhere stays usable.

Use `/vaultworks recovery` to list unresolved records; `/vaultworks doctor` reports them as failures. Evidence is stored in `plugins/VaultWorks/recovery/`. A corrupt/incomplete record or a recovery-store fault locks storage globally because its affected participants cannot be safely established. There is no automatic item replay or unlock command. See [recovery procedure](docs/TRANSFER-RECOVERY.md).

Successful transfers do not write recovery records. This failure-handling mechanism is **not** a write-ahead journal for normal transfers and does not make separate world/player saves crash-atomic.

### Portable endpoint identity

Every Vault Cell now owns a stable endpoint UUID and monotonic revision. Both values travel with the filled Vault item when it is broken and placed elsewhere. Existing pre-identity snapshots generate an endpoint UUID on first load without changing their stored item/count.

If two loaded Vault Cells ever present the same endpoint UUID, VaultWorks treats that as a duplication/corruption fault. **All loaded copies are locked from manual and automated mutation**, their contents remain preserved, and the conflict flag is persisted into the portable item. Unloading one copy does not silently unlock the other. The Index and Terminal report conflicted Vaults as preserved-but-inaccessible stock. An **empty** conflicted Vault with no legacy recovery stacks exposes a safe re-key button in its GUI; filled conflicts remain locked and cannot be silently legitimized.

### Build a read-only Vault Index

1. Place a **Vault Index**.
2. Connect it to one or more **Vault Power Bases** using **Vault Link Cables**. Adjacent Power Bases also connect directly to one another.
3. Right-click the Index to see connected node count, columns online, Vault count, registered item types, total stored items, capacity and currently accessible stock.
4. Use **Refresh Index** after changing the network. Traversal is bounded by `index.max-network-nodes` (default 4096).
5. The Index follows only loaded Vault Index, Vault Link and Vault Power Base blocks. It never scans the world and never force-loads chunks.

The Index remains intentionally **read-only metadata**. Item movement belongs to the Terminal transaction layer, which re-resolves loaded powered physical Vault Cells at commit time rather than mutating cached index results.

### Browse the Vault Terminal

Craft and connect a **Vault Terminal** anywhere on the same Vault Link topology. The terminal groups items by their full serialized one-item identity, so custom Rebar/Pylon item data stays distinct. It shows 36 item types per page with total stored amount, currently accessible amount and the number of Vault Cells holding that exact item.

The terminal rebuilds its normal browse pages only when it is loaded or when **Refresh Terminal** is clicked. There is no per-terminal polling task. A single render materializes at most `terminal.max-item-types` entries (default 4096, hard cap 16384); the network summary still reports the full discovered type count and warns if the displayed list is truncated.

Click **Search Vault Network** to open an anvil-backed live search. The network is captured once when search opens; typing filters only that in-memory snapshot. Plain words match the player's rendered item name, `@namespace` filters by addon/namespace, `#online` requires currently accessible stock, and `#offline` finds items with some stock unavailable.

Withdrawals arrive in the five persisted claim slots across the top row. Take items from these slots into your inventory; other players using the same terminal share this buffer. The buffer survives normal restart and drops its contents if the terminal is broken. Item buttons are transactional: **left-click** withdraws one stack, **right-click** withdraws one item, **Shift + left-click** fills the five claim slots, and **Shift + right-click** deposits all matching items. The top-row hopper deposits all inventory items that already have matching registered online Vault Cells. Empty cells are never silently auto-keyed and player terminal deposits never use overflow purge.

Every transaction scans the loaded topology once, rejects a truncated network, revalidates the physical cell immediately before mutation, and compensates unexpected insertion/delivery shortfalls. Search results are only a view; they are never the authoritative inventory.

### Wireless and cross-dimensional access

1. Connect a **Vault Transmitter** to the same Vault Link topology as the storage network and provide Rebar electricity.
2. **Sneak + right-click** the transmitter with a **Wireless Vault Terminal** to bind it.
3. Right-click the portable terminal within the configured base range (64 blocks by default).
4. Place a **Vault Antenna** directly adjacent to the transmitter to extend same-world range (512 blocks by default).
5. Place a **Dimensional Vault Antenna** directly adjacent to permit access from another world/dimension when enabled in config.
6. The transmitter must remain **loaded and powered**. VaultWorks never loads its chunk or any storage chunk just because a remote player opens the terminal.

Wireless access is rechecked for every transfer and claim-slot removal, including current power, range, loaded transmitter identity, and possession of a bound terminal. No world name is hard-coded into dimensional access, so future worlds/dimensions can use the same transmitter rules without a data migration.

Rebar cargo is the external machine-I/O layer, not VaultWorks' future internal network protocol. The Vault Terminal performs validated operations directly against attached Vault Cells, while future import/export interfaces will bridge indexed storage to Rebar cargo.

`power.base-watts`, `power.watts-per-vault`, and `cargo.items-per-tick` are configurable; restart after changing them. Rebar's global cargo multiplier also applies. The storage foundation has no world scan, forced chunk loading, or per-Vault polling task.

**Release-candidate boundary:** normal operation and orderly persistence are tested. A durable cross-storage crash journal is still future work; this build does not promise atomic recovery after process termination or power failure. Request crafting and dedicated import/export interfaces remain roadmap features.

Build with Java 25: `./gradlew clean build`. The raw plugin JAR is in `build/libs/`.

## Core idea

A VaultWorks network is built from four layers:

```text
Physical Storage
    |
    v
Storage Cells / Interfaces
    |
    v
Index Network
    |
    +----> Vault Terminal
    +----> Import / Export Interfaces
    +----> Crafting Terminal
    |
    v
Request Crafting
    |
    +----> Pattern Encoder
    +----> Fabrication Core
    +----> Fabrication Processors
```

### 1. Physical storage

Storage Cells actually own the items.

The network index never becomes the authoritative inventory. If a network disappears, the items are still stored in their cells.

That gives VaultWorks a much safer persistence model than treating one controller cache as the player's entire storage system.

### 2. Indexed access

Index Nodes maintain a lightweight view of explicitly attached storage endpoints.

They should:

- track only connected VaultWorks endpoints;
- update from storage changes rather than scan the world;
- never force-load arbitrary chunks;
- know **where** an item exists instead of duplicating the item itself;
- rebuild safely from physical storage after restart.

### 3. Terminals

The Vault Terminal is the player's searchable view of the indexed network.

Current terminal functions:

- search all indexed items;
- transactional insert and withdraw;
- paged sorting/filtering;
- show total and currently accessible counts;
- show how many cells hold an item;
- expose network health and capacity;
- support the same interface through a bound wireless terminal.

Favorites/pinned stock remain optional quality-of-life work rather than a storage prerequisite.

### 4. Request crafting

VaultWorks crafting should be **request driven**, not an instant “craft everything” button.

A player requests an item from a Crafting Terminal. VaultWorks then:

1. checks current stock;
2. resolves a known encoded recipe;
3. expands missing ingredients into a dependency graph;
4. reserves available ingredients;
5. creates a crafting job;
6. sends work to available Fabrication Processors;
7. returns completed output to storage;
8. releases reservations safely on completion/cancel/failure.

Crafting state must survive restart without duplicating reserved inputs or completed outputs.

## Planned progression

The working progression language is:

```text
Encoded Circuit
      |
      v
Memory Wafer
      |
      v
Storage Lattice
      |
      +--> Basic Powered Vault Cell
      +--> Vault Index
      +--> Vault Terminal
      |
      v
Dense Lattice
      |
      +--> Dense Vault Cell
      +--> Crafting Terminal
      +--> Pattern Encoder
      |
      v
Fabrication Matrix
      |
      +--> Fabrication Core
      +--> Fabrication Processor
      +--> Import / Export Interfaces
      |
      v
Singularity Lattice
      |
      +--> endgame storage cells
      +--> high-concurrency fabrication
      +--> advanced network services
```

Exact capacities and recipe costs are intentionally not locked yet.

See [Progression](docs/PROGRESSION.md).

## What makes VaultWorks different

### Distributed ownership

A cell owns its inventory. The index owns metadata.

This makes recovery, migration, and corruption handling much more understandable.

### Explicit topology

Storage is connected deliberately through VaultWorks components.

No global chest discovery. No “search every loaded inventory in the world.”

### Reservation-based crafting

Autocrafting should reserve ingredients before work begins. Two simultaneous jobs should not both believe they own the same stack.

### Failure-aware jobs

Crafting jobs should be able to report:

- waiting for ingredients;
- reserved;
- crafting;
- blocked output;
- completed;
- cancelled;
- failed.

### Bounded work

Search/index/crafting work should be incremental and bounded. One huge base should not create a giant synchronous scan on the server thread.

## Relationship to GridWorks

VaultWorks and [GridWorks](https://github.com/wickidcow/Rebar_GridWorks) should remain separate addons.

**VaultWorks owns storage and request crafting.**

**GridWorks owns factory automation and control.**

A future optional integration could expose VaultWorks telemetry such as:

- stored item count;
- cell capacity;
- free capacity;
- crafting-job state;
- requested item deficit;
- import/export activity.

GridWorks could then make decisions without owning VaultWorks storage:

```text
VaultWorks
  "Iron = 183"
      |
      v
GridWorks Stock Controller
      |
      v
Production Router
      |
      v
Rebar / Pylon factory
```

Neither addon should require the other to function.

## Things VaultWorks should not do

VaultWorks should avoid becoming:

- a replacement electricity system;
- a chunk loader;
- a world-wide inventory scanner;
- a generic ore-processing addon;
- an automation controller that duplicates GridWorks;
- a single opaque database pretending physical storage does not exist.

## Design priorities

1. **No dupes.**
2. **No item loss during normal restart/reload.**
3. **Physical storage remains authoritative.**
4. **No force-loaded chunks for ordinary indexing.**
5. **Bounded and incremental network work.**
6. **Clear recovery from missing/unloaded cells.**
7. **Public integration contracts instead of plugin-specific reflection.**
8. **A real progression path rather than one overpowered terminal.**

## Documents

- [Architecture](docs/ARCHITECTURE.md)
- [Progression](docs/PROGRESSION.md)
- [Roadmap](docs/ROADMAP.md)

---

VaultWorks is an independent Minecraft server plugin project intended for the Pylon/Rebar ecosystem.
