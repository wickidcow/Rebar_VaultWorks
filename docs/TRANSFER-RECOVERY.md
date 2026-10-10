# Failed-transfer recovery

VaultWorks 0.3.0-rc.2 adds protection for a specific failure: an inventory operation throws after some items have moved, and restoring that inventory or a participating cell also fails.

## Normal behavior

The transfer captures independent copies of inventory slots and physical cell state before changing either. If an operation throws, VaultWorks first writes recovery evidence, then attempts every restore independently. Inventory failure cannot skip cell compensation; cell failure cannot skip the remaining cells. Restored inventory contents and cell state are checked against the snapshots.

When all participants restore successfully, the recovery record is marked resolved and retained for audit and normal access resumes. A failed verification or restoration leaves the record in place. The source endpoint UUIDs and initiating terminal/location stay locked across restart. No items are automatically replayed from an incident, because the external inventory may already contain some of them.

Normal successful transfers create no recovery files. Cargo is blocked from locked cells, but cargo transfers themselves are not covered by this player-transfer compensation mechanism.

## Administrator diagnostics

- `/vaultworks doctor` fails when recovery incidents or store faults remain.
- `/vaultworks recovery` lists incident IDs, OPEN/PREPARED state, operation names, affected-cell counts, and the initiating world UUID and block coordinates.
- `/vaultworks recovery inspect <UUID>` checks a specific incident checksum again and reports only safe metadata: creation time, state, player, participant count, and inventory/cell snapshot counts. It never prints serialized item bytes, restores contents, or unlocks storage. If validation fails, storage fails closed globally.
- Affected cells show `LOCKED / RECOVERY`; terminals refuse access and claim-slot removal. Normal breaking and empty-cell re-keying cannot clear these locks.
- Unaffected storage remains available. A corrupt record, incomplete `.pending` file, or `store.fault` marker locks all storage until reviewed, since the scope of the failure may be unknown.

## Evidence and review

Stop the server before editing any recovery or storage files. Preserve a copy of the complete world, player data, Rebar data, VaultWorks data, and server log from the same stopped state.

Records live in `plugins/VaultWorks/recovery/<incident UUID>.incident`. The first line contains a format version and SHA-256 checksum of the remaining UTF-8 Java Properties payload. A file must pass that checksum before it is interpreted. Each record contains:

- source endpoint UUIDs and block locations;
- each cell's registered item, count, revision, overflow setting, and legacy contents before the transfer;
- cell state observed when the operation failed;
- inventory slots before the operation and observed at failure;
- player UUID when the inventory exposes its holder;
- initiating world name, plugin/server versions, operation, timestamp, and original error.

Items are Base64-encoded Paper `ItemStack.serializeAsBytes()` values, retaining custom item metadata; `empty` represents an empty slot. These are evidence snapshots, not extra claimable stock. The observed contents were captured **before compensation**; the current saved state may reflect some successful restoration attempts. Review the suppressed exceptions in the server log as well.

Reconcile the affected inventories and physical cells together against the evidence and your stopped-state backups. Do not add the snapshot amounts on top of existing stock. An administrator should archive an incident out of the active recovery directory only after the corresponding saved state has been reconciled. For a global fault, review every incomplete/corrupt record and the store fault before archiving its marker. Keep the evidence outside the active directory for audit. Restart and rerun the diagnostics afterward.

VaultWorks intentionally provides no automatic `unlock`, `replay`, or `give contents` command for ambiguous incidents.

## Limits

This protects exception compensation. It does not coordinate every Rebar chunk save, player save, item entity, or cargo transfer with a durable write-ahead journal. Abrupt JVM termination during a normal successful transfer is still outside the candidate's atomicity guarantee.

The evidence file is forced to disk before its atomic move; the directory is also forced on platforms that expose directory channels. A completely unavailable disk can prevent evidence or a fault marker from being saved. In that case, the running instance locks all storage and logs the recording failure; restart must not be treated as successful recovery.
