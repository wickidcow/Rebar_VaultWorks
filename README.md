# Rebar VaultWorks

**Distributed digital storage, indexed inventory, and request crafting for Pylon/Rebar.**

VaultWorks is an early design-stage Rebar addon intended to solve a problem large technical Minecraft bases eventually hit: **storage stops being about chest capacity and becomes an information problem**.

The goal is not to clone Slimefun Networks, Applied Energistics, Refined Storage, or Infinity Expansion. VaultWorks borrows the lessons that made those systems useful while giving Rebar its own storage model:

> **Items remain owned by physical storage cells. An index knows where they are. Terminals request them. Fabricators reserve and craft them.**

There is no magic global inventory object and no world-wide storage scan.

## Project status

**0.2.0-SNAPSHOT: portable bulk Vault storage and powered-column foundation implemented.**

Available now:

- Encoded Circuit, Memory Wafer and Storage Lattice progression components.
- **Basic Vault Cell** — one registered item type, configurable default capacity of **1,000,000 items**.
- **Powered Vault Cell** — advanced ominous-Vault visual, configurable default capacity of **4,000,000 items**.
- **Vault Power Base** — powers one contiguous vertical column of up to **6 Vault Cells**.
- **Vault Cargo Node** — sits directly behind one Vault Cell and exposes one outward Rebar cargo connection.
- **Vault Link Cable** — passive explicit topology for metadata/index connections.
- **Vault Index** — read-only network overview across explicitly connected loaded Power Bases.
- **Vault Terminal** — paged exact-item browser showing total and currently accessible stock.
- Floating registered-item display inside each Vault.
- Quick deposit, quick withdraw, clear-registration control and per-Vault overflow-purge toggle.
- Filled Vault Cells keep their stored item, count and overflow setting inside the dropped Vault item when broken, so the cell can be moved and placed elsewhere without dumping its contents.

Requires Paper 26.2, Java 25, and the electricity-enabled Rebar development server JAR from upstream commit `5e34938f044dc63c103213e80b07484bf4994639`. The build pins API snapshot `1.0.0-20260929.193904-140`. Rebar's stable 0.43.0-26.2 server JAR cannot load this build; the Maven API artifact is not the server plugin.

[Download the raw 0.2.0-SNAPSHOT JAR](https://github.com/wickidcow/Rebar_VaultWorks/releases/download/dev-build/Rebar_VaultWorks-0.2.0-SNAPSHOT.jar). Place it directly in `plugins/` and restart.

### Build a Vault column

1. Place a **Vault Power Base** first. Its front should face the player/storage aisle; its electrical port is on the **rear service side** so wiring can stay hidden.
2. Stack **1–6 Vault Cells directly above the base**. A seventh placement is rejected.
3. Connect Rebar electricity to the rear of the base. Default demand is **16 W base + 8 W per contiguous Vault**: 24 W for one Vault and 64 W for a full six-high column.
4. When power is available, the base lights and each registered item display inside the connected Vaults brightens/glows. The vanilla Vault block itself stays in its inert state so VaultWorks does not trigger Mojang's trial-key/reward behavior. Without power, Vault storage controls and cargo operations are locked, but stored data remains safe.
5. Open a Vault and hold the item you want to store. Registering consumes exactly **1 item** and permanently keys that Vault to that exact item identity until the count reaches zero and the registration is cleared.
6. Place a **Vault Cargo Node directly behind a Vault Cell**. Connect Rebar cargo to the node's outward-facing side. The front of the Vault wall remains clean.
7. Overflow purge is **OFF by default**. When enabled for a Vault, matching input beyond capacity is intentionally destroyed; when disabled, excess stays at the source.
8. Break a filled Vault Cell normally and the stored state travels with the dropped Vault Cell item. Place that item above another valid Vault Power Base to restore it.

The Power Base uses a lit copper-bulb shell as the safe vanilla fallback for the “powered bedrock / power mat” idea. It remains normally breakable; VaultWorks does not turn the actual world block into unbreakable Bedrock.

### Build a read-only Vault Index

1. Place a **Vault Index**.
2. Connect it to one or more **Vault Power Bases** using **Vault Link Cables**. Adjacent Power Bases also connect directly to one another.
3. Right-click the Index to see connected node count, columns online, Vault count, registered item types, total stored items, capacity and currently accessible stock.
4. Use **Refresh Index** after changing the network. Traversal is bounded by `index.max-network-nodes` (default 4096).
5. The Index follows only loaded Vault Index, Vault Link and Vault Power Base blocks. It never scans the world and never force-loads chunks.

This phase is intentionally **read-only**. The Index owns metadata only; it does not own, withdraw or insert items. Searchable terminal mutation comes after topology and identity rules are proven.

### Browse the Vault Terminal

Craft and connect a **Vault Terminal** anywhere on the same Vault Link topology. The terminal groups items by their full serialized one-item identity, so custom Rebar/Pylon item data stays distinct. It shows 36 item types per page with total stored amount, currently accessible amount and the number of Vault Cells holding that exact item.

The terminal rebuilds its pages only when it is loaded or when **Refresh Terminal** is clicked. There is no per-terminal polling task. Item buttons are intentionally non-interactive in this phase; transactional withdraw/deposit comes after the read path is proven stable.

Rebar cargo is the external machine-I/O layer, not VaultWorks' future internal network protocol. The planned Vault Index and Vault Terminal will perform validated operations directly against attached Vault Cells, while future import/export interfaces will bridge indexed storage to Rebar cargo.

`power.base-watts`, `power.watts-per-vault`, and `cargo.items-per-tick` are configurable; restart after changing them. Rebar's global cargo multiplier also applies. The storage foundation has no world scan, forced chunk loading, or per-Vault polling task.

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

Planned terminal functions:

- search all indexed items;
- insert and withdraw;
- sort/filter;
- show total stored counts;
- show which cells hold an item;
- expose storage health and capacity;
- optionally pin favorites.

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
      +--> Basic Vault Cell
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
