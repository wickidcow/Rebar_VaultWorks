# VaultWorks 0.3.0-rc.1

This candidate completes the first playable storage-network feature set. It targets Paper 26.2, Java 25 and released Rebar 0.44.2-26.2. Request crafting, dedicated import/export interfaces, and durable crash recovery remain separate roadmap work.

## Changes

- Complete searchable wired and wireless terminal deposits and withdrawals. Withdrawals use the persisted five-slot claim buffer.
- Recheck loaded storage, power, endpoint identity, range and bound wireless item before transfers. Full storage leaves excess items with the player; terminal deposits never use overflow purge.
- Preserve main's duplicate-identity locks and portable persistence when reconciling the previous release branch.
- Fix item registration order for current Rebar so blocks have their correct drop items, including filled portable cells.
- Name the tiers **Basic Powered Vault Cell** and **Advanced Powered Vault Cell**. Item IDs and persistent keys stay unchanged.
- Advanced cell Quick Withdraw: left-click **1**, right-click **up to 64**, Shift + left-click **fill inventory**. Item stack limits and remaining inventory room are respected.
- Add five-step content deletion down the rightmost cell-menu column. Each confirmation appears only after the preceding click. Progress is private to one player, bound to the exact cell revision, expires after 30 seconds and resets when the menu closes. The final click permanently clears contents and registration.
- Stack healthy empty cells up to **64** per tier. Matching empty registrations/settings stack together; filled cells, legacy-recovery cells and conflicted cells stay unstackable. Empty stack placement receives a fresh endpoint identity.
- Add `/vaultworks testpower`: an administrator-only **1 MW Vault Test Power Source** with six Rebar electrical ports and no survival recipe.
- Pin the released Rebar API and server dependency. Add real-server regression and clean-restart gates to CI.

## Verification

Local validation uses Paper 26.2 build 129 and the checksum-verified upstream Rebar 0.44.2-26.2 server JAR.

- 32 JUnit tests passed.
- 60 real-server checks passed, including clean restart of cell amounts/UUIDs and terminal claim contents.
- Real item transfer tests cover partial/full inventory, custom item metadata, 16-item stack limits, full claim buffers, overflow retention, power loss and exception compensation.
- Runtime checks cover progressive deletion, revision invalidation and separate player confirmation state.
- Portable break/re-place preserves filled state; duplicated UUIDs lock both cells and remain locked when a copy is removed.
- Empty cell drops stack correctly, while filled drops have a stack limit of one.
- Wireless binding, cancellation, antenna range, loss of power and cross-dimensional access were exercised.
- The temporary test source powered a real Rebar consumer network. `/vaultworks doctor` passed.

The test harness is an opt-in separate JAR and is not bundled in the plugin delivered to players. Its fixtures belong only on a disposable test server.

## Candidate boundary

Normal operations and orderly restart are covered. There is no durable write-ahead journal coordinating every physical cell, claim buffer, chunk and player save. Abrupt JVM termination or power failure is not claimed to be crash-atomic. This remains a release candidate rather than a stable release with a crash-recovery guarantee.
