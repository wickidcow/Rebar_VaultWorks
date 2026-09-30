# Rebar VaultWorks

**Distributed digital storage, indexed inventory, and request crafting for Pylon/Rebar.**

VaultWorks is an early design-stage Rebar addon intended to solve a problem large technical Minecraft bases eventually hit: **storage stops being about chest capacity and becomes an information problem**.

The goal is not to clone Slimefun Networks, Applied Energistics, Refined Storage, or Infinity Expansion. VaultWorks should borrow the lessons that made those systems useful while giving Rebar its own storage model:

> **Items remain owned by physical storage cells. An index knows where they are. Terminals request them. Fabricators reserve and craft them.**

There is no magic global inventory object and no world-wide storage scan.

## Project status

**0.1.0-SNAPSHOT: powered storage foundation implemented.**

Available now: a 54-slot Basic Vault Cell and a 54-slot Powered Vault Cell with native Rebar electricity and cargo. The network index, remote terminal and request crafting below remain planned; this first build does not claim those systems are finished.

Requires Paper 26.2, Java 25, and the electricity-enabled Rebar development server JAR from upstream commit `5e34938f044dc63c103213e80b07484bf4994639`. The build pins API snapshot `1.0.0-20260929.193904-140`. Rebar's stable 0.43.0-26.2 server JAR cannot load this build; the Maven API artifact is not the server plugin.

### Use powered storage

1. Open `/rebar guide` → VaultWorks and craft a Powered Vault Cell.
2. Right-click to insert items directly. The cell owns and saves its 54 slots through Rebar; breaking it drops the contents.
3. Connect a compatible Rebar wire from your electricity content addon to the **top power port**. The default load is **32 W**.
4. Connect cargo input on the **WEST** face and cargo output on the **EAST** face. Compass directions are fixed and do not depend on the placement direction.
5. With adequate power, Rebar handles cargo transfers. Without power, both automated insertion and extraction are blocked; manual access remains available. Electricity updates take effect on Rebar's next electricity tick.

For GridWorks control, wire supply through a Power Coupler to the cell; aim a Smart Breaker and optional Power Limiter at the coupler. The addons do not depend on each other. GridWorks Inventory Sensors can already read the cell using Rebar's public virtual-inventory interface.

`power.watts` and `cargo.items-per-tick` are configurable; restart after changing them. Rebar's global cargo multiplier also applies. This foundation has no per-cell polling task, world scan, forced chunk load, separate item database or queued crafting job. Power loss never clears the inventory.

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
