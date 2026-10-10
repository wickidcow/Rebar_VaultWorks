# Prewrite transaction guard – foundation

**Status: scaffolding only. Not enabled for normal transfers and not a crash-atomicity guarantee.**

VaultWorks 0.3.0-rc.2 already captures independent participant snapshots and handles failed in-memory transfer compensation. Abrupt JVM termination, a lost chunk write, or a player-save failure is a separate problem.

This development branch adds a distinct `prepared` recovery-record state to `VaultRecoveryStore`, along with restart/lock/checksum tests. The player-facing deposit, withdrawal, cargo and wireless paths **still use their existing behavior** and never create PREPARED records.

## Implemented invariants

- `prepare(evidence, endpointIds, root, operation)` creates a durable, checksummed record and locks the named participants before returning. Empty participant sets or missing scope are rejected.
- The record is forced to disk before an atomic rename, using the same audited file format as failure incidents. The prepared state survives server restart.
- During startup, a valid PREPARED record locks its endpoint UUIDs and initiating terminal. A damaged record or incomplete pending file instead fails closed globally.
- `resolved(...)` is reserved for the existing **failed-transfer rollback** path. It rejects PREPARED records rather than accepting an ordinary method return as a safe commit.
- There is intentionally **no automatic unlock, restore, or replay** command.

## Required before enabling writes

1. Identify **every durable participant**: vault endpoint identity/revision, before-and-after custom item identity and quantity, terminal claim slots, player inventory slots, and any external item handoff.
2. Define a stable intent ID and exactly-once state transitions. Save PREPARED before mutation, and reject concurrent operations against its participants while it is pending.
3. Establish a real **cross-save durability boundary**. Returning from `Inventory#setItem` or mutating Rebar block fields does not mean the corresponding playerdata and world/chunk state have reached durable storage.
4. Recover after a kill -9 / forced power-loss scenario using recorded before/after state **without guessing** which side saved. Ambiguous cases stay locked for offline reconciliation.
5. Bound disk work and record size (current recovery evidence limit: 16 MiB), validate save failures and corrupt files, and benchmark fsync overhead on a busy server. No synchronous per-item fsync loop.
6. Include cargo, portable filled-cell drops, cell break/unload, network terminal deposits/withdrawals, claim pickup, and wireless access in the safety model before promising universal crash atomicity.
7. Run two clean restart passes and forced-termination integration tests with real Paper/Rebar, including duplicate IDs, stale endpoint revisions, saturated player inventories, power loss and partial transfer failures.

Until those conditions are met, **retain the release-candidate designation**. This work deliberately does not change existing Vault item IDs, recipes, persistent storage keys, column capacity, power demand, or production JAR behavior.
