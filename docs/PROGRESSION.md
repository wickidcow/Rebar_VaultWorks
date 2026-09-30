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

Planned content:

### Basic Vault Cell

Physical item storage.

Design intent:

- durable;
- limited capacity;
- limited type count;
- usable before a full network exists.

### Vault Index

Connects explicitly attached VaultWorks cells into one searchable index.

### Vault Terminal

Search, deposit, and withdraw from the attached indexed storage.

At this tier there is no request crafting.

## Tier 2 — Dense Storage

Goal: larger bases and more useful network interfaces.

### Dense Lattice

An upgraded lattice component.

### Dense Vault Cell

Higher amount/type capacity.

### Import Interface

Accepts items from an external inventory/logistic path and inserts them into VaultWorks.

### Export Interface

Exports a configured item or filtered set from VaultWorks.

### Stock Interface

Exposes a configured item count/capacity state for integrations.

This is a natural optional bridge to GridWorks later.

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

Once a released Rebar dependency exposes the electricity API, advanced VaultWorks devices could consume power.

That should be a normal resource cost, not a VaultWorks-specific power network.
