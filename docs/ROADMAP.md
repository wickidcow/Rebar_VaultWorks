# VaultWorks Roadmap

VaultWorks 0.2.0-SNAPSHOT has a playable **portable bulk-storage and powered-column foundation**.

Implemented: single-item Basic and Powered Vault Cells, portable stored state on the dropped cell item, floating registered-item displays, a six-high Vault Power Base column, rear Vault Cargo Nodes, power-gated controls/cargo, safe overflow-purge toggles, and recovery of the earlier 54-slot prototype data. Distributed indexing, reservations and request crafting remain future work and require the transaction/recovery contracts below.

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

Implemented endpoint invariants:

- stable endpoint UUID persisted through break/re-place;
- monotonic endpoint revision across storage/control mutations;
- loaded duplicate-ID detection;
- persisted hard lock for duplicate identities;
- conflicted endpoints excluded from accessible network stock.

Still to add before Phase 1 is considered complete:

- administrator Doctor diagnostics/recovery workflow for filled identity conflicts;
- broader restart and crash-window regression tests.

Empty conflicted Vaults with no recovery stacks can already be safely re-keyed from their GUI.

No indexed network yet.

Primary goal: prove storage cannot dupe or lose items across restart or physical relocation.

## Phase 2 — Indexed network

Implemented foundation:

- explicit loaded-block topology through Vault Link Cables and Vault Power Bases;
- bounded iterative traversal with no world scan or force-loading;
- read-only Vault Index network totals;
- online/offline column counts;
- physical stored/capacity totals and distinct item-type counts.

Still to implement:

- stable endpoint registration/UUIDs;
- change-driven cached summaries;
- explicit unloaded-endpoint metadata;
- network split/merge reconciliation;
- searchable item index used by the future terminal.

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

Still to implement:

- transactional deposit;
- transactional withdrawal;
- validation/reservation against physical endpoint state at commit time;
- concurrent-use and double-click anti-overdraw tests.

Primary goal: reliable player access under concurrent use.

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

The first playable release should prefer fewer features with strong invariants over a broad but unsafe storage network.
