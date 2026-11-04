# VaultWorks 0.3.0-rc.3 — HARD Storage Recipes

## Gameplay balance

VaultWorks storage cells now default to HARD Pylon crafting progression. The 1,000,000-item Basic Cell requires Pylon Steel Sheets and Cargo Controllers; its 4,000,000-item Advanced upgrade requires Igneous Composite and 1 kJ Capacitors. Earlier steps now consume Pylon Copper/Bronze Sheets and related industrial components. Supporting Power Bases, Cargo Nodes and Indexes are also more expensive.

Use `recipes.difficulty: HARD` (default), `BALANCED`, or `CLASSIC` in `plugins/VaultWorks/config.yml`. Changing this option requires a full restart. Both HARD and BALANCED need the Pylon addon; CLASSIC retains the original crafting recipes and works with only Rebar.

## Safety and compatibility

- Existing Vault item IDs, storage capacities, cell registrations, portable cell contents, saved networks and recovery records are unchanged.
- All custom ingredients match exact Rebar/Pylon item identities, not lookalike vanilla materials.
- If HARD/BALANCED requirements are missing, VaultWorks remains enabled so existing storage stays accessible, but its crafting recipes are unavailable until Pylon is restored or CLASSIC is selected. It never silently falls back to cheaper crafting.
- Filled and recovery-locked cells are not valid Basic Cell ingredients for the Advanced upgrade.

## Build and validation

GitHub CI must compile/tests on Java 25 and perform real Paper 26.2/Rebar integration and clean-restart testing with a pinned Pylon 0.42.2-26.2 runtime JAR. Merge and rolling development-JAR publication are gated on passing checks.
