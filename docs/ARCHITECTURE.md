# VaultWorks Architecture

## Design goal

VaultWorks is a distributed storage and request-crafting addon for Rebar.

The central architectural rule is:

> **The index may know about items, but it never owns them.**

Physical storage endpoints remain authoritative. Everything else—search, totals, reservations, crafting plans, terminal pages—is derived state that can be rebuilt or reconciled.

This keeps the system understandable during restart, chunk unload, partial network availability, and plugin upgrades.

## System layers

### Storage endpoint layer

A VaultWorks storage endpoint is a physical block with durable inventory ownership.

Examples:

- Vault Cell
- Dense Vault Cell
- Singularity Vault Cell
- external-storage bridge, if added later

Every endpoint needs:

- a stable persistent endpoint UUID;
- durable item inventory;
- a monotonic local revision or equivalent change marker;
- a compact inventory summary for the index;
- explicit loaded/unloaded lifecycle;
- no hidden ownership in a central controller.

### Network/index layer

The Vault Index tracks loaded and explicitly attached storage endpoints.

The index stores **metadata**, such as:

- endpoint UUID;
- endpoint availability;
- item type -> endpoint references;
- quantity summaries;
- free/used capacity;
- endpoint revision;
- reservation availability.

The index must never be the only copy of an item.

If all index state were deleted, the network should be recoverable from the physical cells.

### Access layer

Terminals and interfaces query the index and then perform operations against the authoritative endpoint.

Examples:

- Vault Terminal
- Crafting Terminal
- Import Interface
- Export Interface
- Stock Interface

An access device may cache display/search results, but a withdrawal must always validate against current endpoint state before committing.

### Fabrication layer

Request crafting is a job system.

Planned components:

- Pattern Encoder
- Encoded Pattern
- Fabrication Core
- Fabrication Processor
- Crafting Terminal

A crafting request creates a dependency plan rather than directly mutating inventory.

## Topology

VaultWorks should use explicit topology.

A network only contains endpoints connected through VaultWorks network components.

The first implemented topology foundation uses:

- Vault Index — read-only metadata/query root;
- Vault Link Cable — passive explicit connection;
- Vault Power Base — column endpoint representing its contiguous Vault Cells;
- direct adjacency between Power Bases as an optional compact wall connection.

Traversal is iterative, loaded-chunk-only, and bounded by a configurable maximum node count. It does not traverse cargo ducts or electrical wires, so storage topology remains independent from transport and power topology.

Important rules:

- no world scan;
- no scanning every chest in loaded chunks;
- no force-loading a missing endpoint;
- unloaded endpoints become unavailable, not zero-count;
- topology changes trigger local reindex/reconciliation only.

A future implementation may use Rebar-native connection APIs if they provide the right lifecycle guarantees. The storage model should not depend on a particular graph implementation.

## Item identity

Item identity must preserve all gameplay-relevant item data.

Do not index only by Material.

A storage key should distinguish items when their meaningful serialized identity differs, including custom Rebar/Pylon items.

The key format must be:

- deterministic;
- stable across restart;
- safe to hash;
- versioned if serialization rules ever change.

Where practical, actual item serialization should use public Rebar/Paper APIs rather than custom reflection.

## Capacity model

VaultWorks should support a progression that can scale without one block becoming an unbounded map.

Two viable capacity dimensions can coexist:

- **item amount capacity** — total number of stored items;
- **type capacity** — maximum distinct item keys.

This creates meaningful tier tradeoffs.

For example, a bulk cell could hold enormous quantities of a few types, while a general cell could hold more distinct types.

Exact numbers should be balanced later.

## Implemented endpoint identity safety

Vault Cells now persist an endpoint UUID and revision in both world state and their portable dropped item. The revision advances when authoritative storage/control state changes.

Loaded endpoint UUIDs are registered in-memory. A duplicate UUID is treated as a hard safety fault rather than being auto-renamed: all loaded copies are marked conflicted and storage mutation is disabled. The conflict bit is persisted so simply unloading one copy cannot restore mutation access. This deliberately favors preserved/recoverable data over trying to guess which copy is legitimate.

An empty conflicted Vault with no recovery stacks may be explicitly re-keyed from its GUI because there are no stored contents to legitimize. Filled conflicted Vaults must never be silently re-keyed because doing so would legitimize duplicated contents. A later Doctor/recovery workflow should provide administrator diagnostics for those filled conflicts.

## Index updates

The index should be change-driven.

Preferred flow:

1. endpoint inventory changes;
2. endpoint increments its revision;
3. attached index receives a compact delta or fresh endpoint summary;
4. index updates only the affected item keys;
5. terminal views invalidate only affected cached results.

If an endpoint cannot provide precise change events, use a shared conservative sampler for loaded endpoints rather than one repeating task per cell.

## Implemented read-only browser identity

The first Vault Terminal browser groups registered items by Paper's serialized one-item stack representation. This includes gameplay-relevant custom item data and avoids collapsing custom Rebar/Pylon items that merely share a vanilla material.

This serialized identity is currently an **ephemeral read model** rebuilt from physical Vault Cells. It is not yet persisted as the long-term index key. A future persisted/searchable index schema must version its identity format before it becomes durable state.

Terminal rendering is separately bounded from topology traversal so a large valid network cannot cause one GUI open to materialize an unbounded number of button objects.

## Search

Search should operate on the index, not the physical inventories.

The index should maintain normalized searchable metadata:

- display name;
- namespace/addon;
- item key;
- optional lore/tags where safe and useful.

Search results can be paged and ranked without touching every stored stack.

Withdrawal still validates against live physical storage.

## Implemented withdrawal planning boundary

VaultWorks now has a revision-aware withdrawal planner, but it still does **not** commit Terminal item movement.

A plan is built from the currently connected and operational physical Vault Cells. Each source entry records:

- stable endpoint UUID;
- expected endpoint revision;
- amount planned from that endpoint.

Before a future commit, VaultWorks re-walks the current explicit topology and resolves each source through the loaded endpoint registry. The plan is rejected if a source is no longer connected/accessible, if its revision changed, or if its stored amount is now too low.

This means a Terminal page or search snapshot is never authorization to mutate storage. Cargo movement, player access, power loss, block movement, identity conflicts, or any other storage mutation can invalidate the old plan.

The next commit layer should deliver into a persisted Terminal-owned claim buffer rather than directly into a player's inventory. That keeps the authoritative storage-to-storage handoff recoverable before external player inventory delivery is attempted.

## Transaction model

Any operation moving items across VaultWorks must be transactional enough to survive partial failure.

### Basic withdrawal

A safe withdrawal flow:

1. resolve candidate endpoint(s);
2. lock/reserve the required quantity logically;
3. revalidate available amount;
4. remove from physical storage;
5. deliver to destination;
6. commit;
7. release reservation.

If delivery fails after removal, the system must have a deterministic compensation path.

### Basic insertion

A safe insertion flow:

1. resolve eligible endpoint(s);
2. calculate accepted amount;
3. insert into physical storage;
4. update endpoint revision;
5. update/reconcile index;
6. return remainder to caller.

Never report more accepted than was actually persisted.

## Reservations

Reservations are the foundation of safe request crafting.

A reservation identifies:

- job UUID;
- item key;
- quantity;
- source endpoint UUID;
- reservation state.

Reserved items still physically exist in their cell until the crafting step consumes them, but other VaultWorks jobs treat the reserved quantity as unavailable.

This prevents:

- two jobs using the same input;
- terminal withdrawal stealing an already committed ingredient;
- parallel processors oversubscribing one stack.

Reservations must be bounded and persisted with the crafting job or in an equally durable journal.

## Crafting plans

The Crafting Terminal should create a dependency graph.

Example:

```text
Request: 1 Machine Frame
        |
        +--> 4 Steel Plates
        |      |
        |      +--> 4 Steel Ingots
        |
        +--> 2 Circuits
               |
               +--> Copper Wire
               +--> Quartz Components
```

The planner should:

- consume existing stock first;
- expand only missing amounts;
- detect recipe cycles;
- reject ambiguous or unsupported patterns cleanly;
- bound maximum graph depth/node count;
- calculate reservations before starting work.

## Pattern model

An Encoded Pattern represents one supported recipe.

It should store stable recipe identity and enough validated metadata to detect when the underlying recipe changes incompatibly.

Patterns should not blindly deserialize arbitrary executable logic.

Potential pattern state:

- recipe key;
- input descriptors;
- output descriptor;
- pattern schema version;
- optional machine/fabricator class requirement.

## Fabrication processors

Fabrication Processors execute work assigned by the Fabrication Core.

Scaling should primarily increase **parallelism**, not multiply recipe speed without bound.

That creates useful progression:

- one processor = one active crafting step;
- more processors = more independent steps/jobs;
- higher-tier processors may improve queueing/concurrency efficiency.

This is more interesting and safer than a single “64x crafting speed” block.

## Crafting job lifecycle

A durable job may move through:

```text
PLANNING
  -> WAITING_FOR_STOCK
  -> RESERVED
  -> READY
  -> CRAFTING
  -> OUTPUT_PENDING
  -> COMPLETE

or

  -> PAUSED
  -> CANCELLED
  -> FAILED
```

Jobs need enough persisted state that restart does not manufacture a second output.

### Critical rule

A crafting step must have one durable commit point.

The system must be able to answer after restart:

- inputs not consumed yet;
- inputs consumed, output not committed;
- output committed.

Never infer completion only from a transient GUI/process timer.

## Restart recovery

On enable:

1. load physical cell inventories;
2. restore network topology for loaded components;
3. rebuild/reconcile index summaries;
4. load persisted jobs;
5. validate reservations;
6. reconcile any in-flight crafting commit markers;
7. resume only work whose state is unambiguous.

If a state is ambiguous, pause the job for safe recovery rather than guess.

## Chunk lifecycle

Unloaded chunks are normal.

When a storage cell unloads:

- keep its persistent identity;
- mark it unavailable in the live network;
- do not treat its contents as deleted;
- do not force-load it to satisfy a terminal request.

A terminal may display unavailable capacity/stock separately from currently accessible stock if that proves useful.

## Failure domains

VaultWorks should explicitly distinguish:

- endpoint unavailable;
- network partitioned;
- output full;
- recipe unavailable;
- reservation conflict;
- job blocked;
- persistence failure;
- corrupt endpoint metadata.

These should not collapse into a generic “0 items” state.

## Anti-dupe invariants

The project should eventually have regression tests proving at least:

1. one physical item cannot be reserved by two jobs;
2. cancelling a reservation never creates an item;
3. restart between input-consume and output-commit cannot produce duplicate output;
4. network split/merge cannot duplicate endpoint registration;
5. duplicated block PDC/UUID is detected instead of merging identities;
6. import/export retries are idempotent;
7. terminal double-click/concurrent requests cannot over-withdraw;
8. an unloaded cell cannot be treated as empty and overwritten.

## Persistence

Persistent data should have explicit schema versions.

Prefer small durable records:

- cell metadata;
- cell inventory;
- network/endpoints if topology requires persistence;
- crafting jobs;
- reservations;
- commit journal.

Derived search indexes should be rebuildable.

## Performance rules

- no world scans;
- no force-loaded chunks;
- no per-item repeating task;
- no per-cell ticker when an event or shared sampler works;
- no rebuilding the entire index for one changed slot;
- paginate terminal results;
- bound crafting graph expansion;
- bound concurrent planning work;
- publish/update only changed summaries.

## Public integration API

VaultWorks should expose public contracts rather than require other addons to inspect internals.

Potential services:

### StorageQueryService

Read-only queries:

- count item;
- free capacity;
- total capacity;
- network availability.

### CraftingRequestService

Optional controlled API for:

- request craft;
- inspect job;
- cancel job.

### Telemetry service

Allows an addon such as GridWorks to observe:

- stock count;
- capacity ratio;
- job queue depth;
- crafting active/blocked;
- requested deficits.

GridWorks should never receive direct mutation access to cell internals.

## GridWorks boundary

VaultWorks owns:

- item storage;
- indexing;
- reservations;
- crafting requests;
- fabrication execution.

GridWorks owns:

- production decisions;
- automation routing;
- alarms;
- sequences;
- stock-control policy.

Optional integration should therefore look like:

```text
VaultWorks telemetry --> GridWorks decision --> external factory command
```

not:

```text
GridWorks directly edits VaultWorks cells
```

## Electricity boundary

VaultWorks should not implement an electricity system.

If crafting/storage devices eventually consume Rebar electricity, that should use the released Rebar electricity API after it is available in the project's chosen dependency line.

Energy consumption is a cost/requirement of VaultWorks devices, not ownership of electrical simulation.
