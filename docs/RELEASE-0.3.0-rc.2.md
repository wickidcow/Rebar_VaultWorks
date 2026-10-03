# VaultWorks 0.3.0-rc.2

This candidate hardens failed transfers while preserving the rc.1 storage features, item IDs, recipes, and saved-data keys.

- Always attempt every participant's rollback, even when an inventory restore throws.
- Clone registration, quick-deposit, quick-withdrawal, legacy-recovery, and terminal-transfer snapshots.
- Verify restored contents rather than assuming a setter succeeded.
- Preserve checksummed, forced-to-disk incident evidence before compensation; mark it resolved only after verified restoration.
- Keep unresolved source cells and the initiating terminal locked across restart, including cargo, wireless claims, normal block breaking, and re-key controls.
- Add read-only `/vaultworks recovery` diagnostics and make `/vaultworks doctor` report unresolved recovery.
- Fail closed on corrupt/incomplete evidence and recovery-store faults.
- Remove deprecated Gradle source-set/task registration syntax from the runtime harness setup.

Validation targets Paper 26.2, Java 25, and Rebar 0.44.2-26.2. The test suite passed 40 unit/content tests and 87 live checks on each final restart pass, including live failure injection for partial inventory writes, failed compensation, independent multi-cell restoration, manual transfer recovery, and persistent locks after restart. CI repeats the server run to verify persistence.

Successful transfers do not write these incident records. This is failure-compensation protection, not a normal-transfer write-ahead journal or an abrupt-crash atomicity guarantee. The build remains a release candidate. See [the recovery procedure](TRANSFER-RECOVERY.md) for the exact behavior and limits.
