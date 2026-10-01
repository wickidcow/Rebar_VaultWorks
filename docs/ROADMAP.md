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

Still to add before Phase 1 is considered complete:

- stable endpoint UUID;
- duplicate-ID detection;
- explicit inventory revision/change events;
- restart and crash-window regression tests.

No indexed network yet.

Primary goal: prove storage cannot dupe or lose items across restart or physical relocation.

## Phase 2 — Indexed network

Implement:

- explicit topology;
- Vault Index;
- endpoint registration;
- change-driven summaries;
- network totals;
- unloaded endpoint state;
- network split/merge handling.

Primary goal: the index can be deleted/rebuilt without losing items.

## Phase 3 — Vault Terminal

Implement:

- paged search;
- item count;
- deposit;
- withdrawal;
- validation against physical endpoint state;
- namespace/addon filters;
- capacity/network health display.

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
