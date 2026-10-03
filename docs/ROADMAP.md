# VaultWorks Roadmap

VaultWorks 0.3.0-rc.1 is the **storage-network release line**.

Implemented: portable Basic Powered and Advanced Powered Vault Cells, persistent endpoint UUID/revision tracking, six-high powered columns, rear cargo, bounded explicit indexing, searchable transactional Vault Terminals, loaded-only powered transmitters, same-world antennas, Dimensional Vault Antennas, and bindable Wireless Vault Terminals. Request crafting remains future work and does not block the storage-network release.

## Phase 0 — Architecture contract

Before gameplay code:

- define item identity;
- define physical cell persistence;
- define endpoint UUID rules;
- define capacity model;
- define index data model;
- define reservation semantics;
- define crafting job commit points;
- define chunk unload behavior;
- define corruption/recovery behavior.

Deliverable: architecture tests and immutable core data models.

## Phase 1 — Physical cells

Implemented foundation:

- one registered item identity per Vault Cell;
- long-count bulk capacity enforcement;
- portable save/break/replace state;
- six-high powered physical columns;
- rear Rebar cargo adapters;
- overflow handling that is safe-by-default;
- legacy prototype recovery.

Phase 1 release work now includes:

- stable portable endpoint UUIDs;
- duplicate loaded-ID detection with persistent mutation locks; only empty conflicted cells may be explicitly re-keyed;
- monotonic storage revisions for manual, terminal and cargo mutations;
- portable break/replace state;
- legacy prototype recovery.

A durable cross-storage journal and abrupt-termination recovery tests remain required before advertising crash-atomic transfers. Version 0.3.0-rc.1 is a candidate for normal-operation and orderly-restart testing.

Primary goal: prove storage cannot dupe or lose items across restart or physical relocation.

## Phase 2 — Indexed network

Implemented foundation:

- explicit loaded-block topology through Vault Link Cables and Vault Power Bases;
- bounded iterative traversal with no world scan or force-loading;
- read-only Vault Index network totals;
- online/offline column counts;
- physical stored/capacity totals and distinct item-type counts.

Implemented for the storage release:

- loaded endpoint registration/UUIDs;
- bounded loaded topology traversal;
- exact-item summaries used by the terminal;
- explicit online/accessibility accounting;
- network split/merge rebuilding from physical storage.

Future optimization can add change-driven cached summaries and richer unloaded-endpoint metadata without changing physical ownership.

Primary goal: the index can be deleted/rebuilt without losing items.

## Phase 3 — Vault Terminal

Implemented foundation:

- paged exact-item browser;
- full custom-item identity grouping;
- total vs currently accessible item counts;
- per-item Vault Cell count;
- manual refresh with no background polling;
- read-only network summary in the Terminal.

Implemented search:

- live anvil-backed name search;
- `@namespace` addon/namespace filter;
- `#online` / `#offline` availability filters;
- one captured topology snapshot per search session, with no per-keystroke rescans.

Implemented:

- transactional withdrawal commit into the claim buffer;
- transactional deposit;
- transactional withdrawal;
- live endpoint revalidation at commit time;
- truncated-network mutation rejection;
- deterministic inventory/storage compensation on shortfall;
- one-scan Deposit Inventory behavior;
- no automatic empty-cell registration and no terminal overflow purge.

Primary goal: reliable player access while physical Vault Cells remain authoritative.

## Phase 3.5 — Wireless access

Implemented for 0.3.x:

- powered Vault Transmitter that reuses the normal Terminal GUI/transaction path;
- bound portable Wireless Vault Terminal;
- configurable same-world base range;
- adjacent Vault Antenna range upgrade;
- adjacent Dimensional Vault Antenna for cross-world access;
- no hard-coded world names;
- loaded-only transmitter registry with no chunk loading;
- duplicate transmitter-id detection and re-keying.

Future quality-of-life work may add favorites, named transmitter labels, or more antenna tiers without changing the storage transaction model.

## Phase 4 — Interfaces

Implement:

- Import Interface;
- Export Interface;
- filters;
- bounded transfer rates;
- blocked-output handling;
- idempotent retry behavior.

Potential optional telemetry API begins here.

## Phase 5 — Storage progression

Implement:

- Dense Lattice;
- Dense Vault Cell;
- additional cell specialization if useful;
- safe cell upgrade/migration.

Balance capacity only after real server testing.

## Phase 6 — Pattern system

Implement:

- Blank Pattern;
- Pattern Encoder;
- Encoded Pattern;
- recipe validation;
- pattern schema/version;
- Crafting Terminal display of craftable items.

Still no automated execution until planner/reservations are ready.

## Phase 7 — Crafting planner

Implement:

- dependency graph;
- stock-first planning;
- recursive missing-ingredient expansion;
- cycle detection;
- graph bounds;
- reservation plan;
- clear “missing ingredient” reporting.

Planner should be independently testable without a Minecraft world.

## Phase 8 — Fabrication Core

Implement:

- persisted crafting jobs;
- reservation journal;
- job states;
- cancel/pause/resume;
- one durable commit point per step;
- restart reconciliation.

This is the highest-risk anti-dupe phase and should receive extensive tests before public use.

## Phase 9 — Fabrication Processors

Implement:

- processor registration;
- bounded work queue;
- parallel substeps;
- output-pending state;
- processor unload/reload;
- multiple processor coordination.

Scaling should increase concurrency more than raw speed.

## Phase 10 — Endgame progression

Implement only after the core system proves stable:

- Singularity Lattice;
- high-capacity cells;
- advanced fabrication processors;
- richer terminal UI;
- job history;
- optional storage specialization.

## Phase 11 — Integrations

Optional, soft integrations:

### GridWorks

Expose storage/crafting telemetry through a public service or adapter.

Possible signals:

- item count;
- capacity used/free;
- crafting active;
- queue depth;
- requested deficit;
- blocked output.

### Rebar electricity

The current storage foundation uses Rebar's electricity API through the Vault Power Base. Future terminals, index devices and fabrication hardware may consume power through the same public API.

VaultWorks should never own power simulation.

## Non-goals

Do not add merely because another storage mod/plugin has it:

- world-wide chest scanning;
- force-loaded remote storage;
- infinite storage from one cheap block;
- instant recursive autocrafting with no reservations;
- hidden central inventory ownership;
- generic factory automation already better suited to GridWorks;
- a custom electricity system.

## Release gates

A serious public build should eventually require automated tests for:

- capacity enforcement;
- item identity;
- persistence round-trip;
- concurrent withdrawal;
- reservation conflict;
- cancel/release;
- network split/merge;
- duplicate endpoint ID;
- chunk unload;
- restart during crafting;
- output-blocked recovery;
- no duplicate output after crash-window replay.

The 0.3 storage-network release should ship only after its branch is green, main passes the live Paper/Rebar smoke gate and `/vaultworks doctor`, and the raw release JAR is byte-for-byte tied to that tested build. Fabrication will remain separate until its reservation/crash-window invariants are equally strong.
