# Project working rules

## IntelliJ IDEA workflow

- Before working on this project, check that IntelliJ IDEA has the current workspace/project directory open (currently `E:\IdeaProjects\Mekanism Magic`) and connect to its existing local MCP server when available. Pass the actual working directory as the MCP project path, including when working in a worktree.
- If IDEA is not running, automatically launch the already-installed IDEA and open this project. Reuse an existing project window; do not start duplicate IDE instances or close unrelated projects.
- Prefer background MCP/project APIs for navigation, inspection and build feedback; avoid taking over the user's mouse or keyboard. Do not disable IDE security/approval checks to make the connection work.
- Known installation at the time of this instruction: `E:\JetBrains\IntelliJ IDEA 2026.2.0.1\bin\idea64.exe`; verify it still exists before launch. Read the current `mcp_servers.idea` endpoint from the local Codex configuration rather than assuming a port forever.
- This is a work-session startup preference, not a recurring desktop monitor. If the connection is unavailable, report it accurately and continue safe project work where possible.

## Machine names and GUI titles

- Name new machines using the source mod's established terminology and a concise, distinctive device name appropriate to the function. Check the supplied mod's language/book resources first; do not present newly coined names as official translations.
- Keep registry IDs, recipe IDs, save keys and player-assigned custom names stable when renaming display text.
- In the same change, provide matching `block.mekanism_magic.<id>` and `container.mekanism_magic.<id>` titles in both `zh_cn` and `en_us`, and update related JEI category labels. The default machine GUI title must match the item/block name; untranslated container keys and generic placeholder titles are not acceptable.
- Ordinary machines inherit the shared header from `NativeMagicMachineScreen`. Native and Extras factory screens use `MagicThemedScreen.drawMachineHeader`; use the shared title position and reserve space for the status label, with scrolling for long titles.
- Run `validateMachineContracts` and the machine-title tests before handing off new machines or name/title changes. Missing or inconsistent titles must fail validation, not be left for the user to discover in game.

## Versioning

- Follow `docs/VERSIONING.md`. `gradle.properties` → `mod_version` is the single source of truth for JAR names and mod metadata.
- No mod runtime-code change means no version bump. This is a strict rule: a new task, inspection, compilation, retry, test, relaunch or repeated packaging does not by itself justify changing `mod_version`.
- Unpublished runtime work uses `A.B.C.D`: preserve the latest published `A.B.C` and increment `D` only once after completing and verifying a new substantive change to the mod's runtime code. Start at `.1` after a release. Do not increment per file, tool call or build within the same request; comments, formatting, build/validation tools and documentation are not substantive runtime-code changes.
- Independent resource packs, textures, models, Blockbench projects and previews use their own artifact/design versions (for example resource-pack 1.2 or model V5), never the mod's development counter. Do not rebuild the mod JAR for a resource-pack-only change. Resource-, recipe- or configuration-only changes with unchanged mod runtime code must not automatically advance `mod_version`.
- Before changing `mod_version`, identify the actual new runtime-code changes since the last valid code baseline. Differences limited to version metadata or separately delivered resources do not justify another mod version. Explicit user-requested releases/version corrections are handled separately and never justify an additional automatic bump.
- If resource-only changes require a different mod JAR under an already-used version, ask the user how to handle numbering instead of inventing a new version or overwriting a historical JAR. Build only using the checked-in version; do not use descriptive `-dev-...` overrides or rename an older JAR. A version-only correction does not require rebuilding identical code. Keep historical JARs unchanged, including artifacts with subsequently withdrawn numbers.
- Only when the user requests a release, advance the published version (`1.0.6.x` → `1.0.7`) and remove the development component. Subsequent unpublished work starts at `1.0.7.1`.
- A version change is not permission to publish, create remote tags, upload artifacts or change unrelated branches. Keep historical releases and JARs unchanged.

## Retired code

- When a machine or feature is canceled, delete its obsolete implementation, tests that exercise that implementation, runtime assets, recipes, and dead build exclusions. Preserve negative regression guards that prove the canceled feature stays absent. Do not move executable source or runtime resource copies into `docs/history` or another archive for possible reuse.
- Independent design materials such as Blockbench models and preview images are not runtime code. Keep or delete them according to the user's explicit direction; when kept, do not treat them as authority to restore a canceled machine.
- Historical release notes may record that the feature existed and was removed, but current design documentation must not describe retired implementation as active or planned. Do not revive removed code from history or an old JAR unless the user explicitly requests it.
- Preserve unrelated user work and historical release JARs. A cleanup of already inactive code or documentation does not itself advance `mod_version`.
