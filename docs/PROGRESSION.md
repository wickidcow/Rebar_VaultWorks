# VaultWorks Progression

## Progression philosophy

VaultWorks should feel earned.

The player should move from a small local digital vault to a distributed indexed storage system and finally to request-based fabrication.

The progression should add **capability**, not only larger numbers.

## Tier 0 — Components

Early components establish the technology language.

### Encoded Circuit

The basic control component used by VaultWorks devices.

Possible vanilla ingredients:

- copper;
- redstone;
- quartz.

### Memory Wafer

Stores item/index metadata.

Used by:

- cells;
- terminals;
- index devices;
- patterns.

### Storage Lattice

The structural memory component of basic cells.

Built from Memory Wafers and durable materials.

## Tier 1 — Local Vault

Goal: solve one-room storage without autocrafting.

Implemented content:

### Vault Power Base

Physical power mat/pedestal for one Vault column.

Design intent:

- one rear-facing Rebar electrical service connection;
- powers a contiguous vertical stack of up to six Vault Cells;
- makes power state visually obvious without exposing wire ports on every cell.

### Basic Powered Vault Cell

Single-item bulk physical storage.

Design intent:

- one exact registered item identity;
- high but bounded item count;
- one item consumed to register the Vault;
- portable stored state when broken and replaced;
- manual controls and automation available only while its column is powered.

### Vault Cargo Node

Rear service adapter for one Vault Cell.

Design intent:

- hides cargo behind a clean storage wall;
- exposes one outward Rebar cargo connection;
- stops moving items when the associated Vault column is offline.

### Vault Index

Connects explicitly attached VaultWorks cells into one searchable index.

### Vault Terminal

Search, deposit, and withdraw from the attached indexed storage.

At this tier there is no request crafting.

## Tier 2 — Dense Storage and Wireless Access

Goal: larger bases plus practical access away from the storage room.

### Dense Lattice

An upgraded lattice component.

### Advanced Powered Vault Cell

Higher bulk capacity using the same one-item physical ownership model and the advanced ominous-Vault visual. It still uses the shared Vault Power Base and rear Cargo Node layout.

### Vault Transmitter

Powered access point attached to normal Vault Link topology.

It deliberately reuses the normal Vault Terminal inventory path rather than creating a second wireless-storage implementation.

### Wireless Vault Terminal

Portable player terminal.

Sneak-right-click a Vault Transmitter to bind it, then right-click the portable terminal to use the same search/deposit/withdraw interface remotely.

### Vault Antenna

Adjacent transmitter upgrade for longer same-world range.

The source transmitter must remain loaded and powered. Wireless access never chunk-loads it.

### Future interfaces

Import, export, and stock interfaces remain useful future additions. Machine I/O is already available per cell through the Vault Cargo Node, so these interfaces do not block the 0.3 storage-network release.

A Stock Interface is a natural optional bridge to GridWorks later.

## Tier 3 — Pattern Crafting

Goal: player-requested crafting.

### Blank Pattern

A craftable medium before encoding.

### Pattern Encoder

Encodes a supported recipe onto a Blank Pattern.

### Encoded Pattern

Portable recipe definition.

### Crafting Terminal

Extends the Vault Terminal with craftable/requestable results.

At first, recipes may be visible but require a Fabrication Core to execute automatically.

## Tier 4 — Fabrication

Goal: actual asynchronous request crafting.

### Fabrication Matrix

Advanced crafting component.

### Fabrication Core

Owns the job queue, dependency plan, reservation journal, and processor coordination for one network.

### Fabrication Processor

Executes crafting steps assigned by the Fabrication Core.

Multiple processors increase concurrency.

### Co-Processor / Parallel Processor

Potential later tier that allows more simultaneous substeps without simply multiplying recipe speed.

## Tier 5 — Endgame Vault

Goal: huge technical bases without turning one block into infinite opaque storage.

### Singularity Lattice

Endgame storage/fabrication component.

### Singularity Vault Cell

Very high physical cell capacity while preserving the same cell/index architecture.

### High-Density Fabrication Processor

More concurrency or larger request queue.

### Dimensional Vault Antenna

Endgame wireless upgrade that permits a bound Wireless Vault Terminal to reach its loaded, powered transmitter from another world/dimension.

No world name is hard-coded, so newly added worlds can use the same rule without changing existing Vault data.

### Advanced Terminal

Potential quality-of-life features:

- favorites;
- saved searches;
- job history;
- network health summary;
- pinned stock targets.

## Capacity balancing

Exact capacity numbers are intentionally deferred.

A good balance should consider both:

### Amount capacity

How many total items a cell can hold.

### Type capacity

How many distinct item identities a cell can hold.

Possible specialization later:

- General Cell — balanced types/amount.
- Bulk Cell — huge amount, few types.
- Diverse Cell — many types, lower total amount.

This creates choices beyond “always craft the biggest cell.”

## Upgrade philosophy

Upgrading a cell should never require deleting the old storage object in an unsafe way.

Preferred patterns:

- crafting upgrade component applied to an existing empty cell; or
- migration operation that validates destination capacity before committing.

Never silently truncate stored contents to fit a smaller/different tier.

## Recipe philosophy

Higher tiers should reuse earlier VaultWorks parts.

Example progression:

```text
vanilla materials
    |
Encoded Circuit
    |
Memory Wafer
    |
Storage Lattice
    |
Basic Cell / Index / Terminal
    |
Transmitter / Wireless Terminal / Antenna
    |
Dense Lattice
    |
Dense Cell / Interfaces
    |
Patterns / Crafting Terminal
    |
Fabrication Matrix
    |
Fabrication Core / Processors
    |
Singularity Lattice
    |
Endgame cells + fabrication
```

This creates a coherent crafting tree similar in spirit to large Slimefun progression addons without copying their machines or item IDs.

## Integration progression

VaultWorks should function alone.

Optional integrations can unlock extra capability without becoming hard requirements.

### GridWorks

Potential integration:

- stock telemetry;
- free-capacity telemetry;
- crafting queue status;
- requested deficit;
- import/export activity.

### Rebar electricity

Vault storage already consumes Rebar electricity through the shared Vault Power Base. Later index, terminal and fabrication devices may add their own normal Rebar power costs.

That remains a resource cost, not a VaultWorks-specific power network.
