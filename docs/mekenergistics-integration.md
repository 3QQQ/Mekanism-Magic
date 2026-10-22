# Mek Energistics integration hook

Mekanism Magic's core machine classes do not link against AE2 or Mek
Energistics. Optional source-set adapters consume
`com.example.mekanismmagic.api.IMekanismMagicAutomation`, which exposes live
Mekanism slots through immutable lists.

The current contract version is `IMekanismMagicAutomation.API_VERSION == 6`.
Version 6 adds a side-checked, atomic item-batch planning boundary for ordinary
external AE2 Pattern Providers. It does not require an installed Mek Energistics
upgrade; see [external provider setup](EXTERNAL_PATTERN_PROVIDER.md).

An external adapter should:

1. Detect `tile instanceof IMekanismMagicAutomation`.
2. For installed Mek Energistics pattern upgrades, ignore pattern input on machines where
   `mekanismMagicSupportsPatternAutomation()` is false. A stock external AE2
   Pattern Provider may still use `mekanismMagicPlanExternalItemInputs()` for a
   validated one-face batch on such a machine. Native direct-output
   bridges use `mekanismMagicSupportsDirectNetworkOutput()` independently.
   Providers must also re-check `mekanismMagicCanAdvertisePatterns()` whenever
   a persistent context item changes; this is the dynamic safety gate.
   If `mekanismMagicUsesContextualPatternValidation()` is true, every
   advertised and submitted pattern must additionally pass
   `mekanismMagicMatchesPattern(inputs, outputs)`. An adapter that cannot carry
   the complete input/output declaration must fail closed for that machine.
3. Map `mekanismMagicPatternInputs()` to consumed item input ports.
4. Map `mekanismMagicPatternOutputs()` to item output ports.
5. Treat `mekanismMagicPersistentInputs()` as machine setup, not pattern input.
6. Never insert into or encode `mekanismMagicManualOnlySlots()`.
7. Use `mekanismMagicEnergyContainer()` for network-backed energy only when its
   source is enabled; do not add a second independent pull beside native AE
   supply or the installed MekE node. The per-machine native AE supply switch
   also gates this addon's MekE network-energy path, not local FE inputs.
8. When `mekanismMagicGroupParallelItemInputs()` is true, equivalent factory
   lanes may be exposed as one grouped input port. This must not be used for
   heterogeneous ritual ingredient slots.

Mek Energistics 3.0.6 uses a permutation router for unordered multi-key
patterns. The bundled adapter replaces that path for the Ritual Engine and
Mini Ritual Assembler with a bounded transactional router, disables new smart
multiplication batches for those two machines, and safely drains old pending
batches through the same bounded path.

Current slot policy:

- Mini Ritual Assembler: formation materials are pattern inputs; chalk is
  manual-only.
- Ritual Engine: formation materials, activation, and sacrifice are pattern
  inputs; the ritual selector and Dictionary of Spirits are manual-only.
- Arcane Enchanter: the reagent and pedestal materials are
  consumed inputs. Since `1.0.6.4`, the machine enables the existing contextual
  validation contract: advertising and pushing require the complete input and
  output declaration to match the current Ars recipe and machine selection.
  Context-free helper routes are rejected, and old smart batches use the
  existing network refund and recovery-buffer path.
- Spirit Processor and Spirit Factories: recipe items are pattern inputs; the
  spirit source is persistent. Patterns are advertised only while a
  deterministic, non-empty spirit source is installed. The gambler's weighted
  output is intentionally excluded from AE crafting while its actual results
  may still be exported to network storage. Installed patterns are resolved
  again against the current SpiritJob both while advertising and while
  accepting a push. Context-free Data Energistics/passive/smart-queue helper
  routes are disabled for these machines; old smart batches are refunded and
  their recovery buffer is drained in normal pattern mode.
- Dimensional Miner: the miner is persistent and the machine does not accept
  crafting patterns; its 27 output slots support native AE2 output.
- Drygmy Simulator: entity jars are persistent, crafting patterns are
  disabled, and its 27 output slots support native AE2 output.
- Nature Altar Processor and its four factory tiers: the real reagent is the
  only planned material; the persistent catalyst is verified but not consumed.
  A signed virtual recipe context is removed by the AE decoder, and MekE sends
  each multiplied batch through `NatureAltarAutomationHost` as one rollback-
  safe transaction. Native resource supply reuses an installed MekE node;
  otherwise these machines have one native resource node, never both.

All three MekE upgrade types (`PATTERN_PROVIDER`, `PASSIVE_CRAFTING`, and
`OUTPUT_INTERFACE`) now share the ME factory installer's eligibility gate.
Unsupported machines reject the installer, direct container API and native
Mekanism upgrade-slot/direct-add paths. This does not restrict an ordinary
external Pattern Provider's item capability. Imbuement catalogue/AE context
is routing metadata only: recipes with pedestal ingredients require a real,
currently signed catalyst identifier in the machine's retained library.

`MachineAeResourceHooks` is a separate dependency-free control/status bridge.
Native AE supply can refill local energy and magic storage without a MekE
upgrade. It reuses existing miner/Drygmy/imbuement nodes and yields ownership
to an installed MekE node even while that node is offline. Resource supply
does not add automatic item exports to other machines. Native AE energy
budgets use the larger of capacity/40, four times upgraded total lane demand,
and 2,048 J/t, capped at the deficit and 20,000,000 J/t after runtime AE/FE/J
conversion. Aura budgets use capacity/20, capped at the deficit and 250,000
Aura/t, still subject to the local capability's same-type input-side limits.
Unlinked Source consumers pull from AE each tick up to their native transfer
rate; explicit jar links retain priority and their existing 20-tick cadence.
These are bounded transfers, not unlimited power or full-speed guarantees.
The control now lives inside the native side-configuration window rather
than in an extra column of main-screen tabs.

Aura generators may select a fixed Overworld/Nether/End type only while both
their tank and paid fuel quota are empty. Their existing AE node exports that
exact type and provides optional native-empty-bottle + 20,000 typed Aura
crafting recipes. Manual bottling uses AE's native held-container action:
hold a Bottle and Cork and left-click the stored typed Aura (Shift-left-click
fills a held stack), consuming 20,000 Aura per bottle and returning the filled
bottle to the cursor or player inventory. Right-clicking with a filled Aura
bottle returns its exact type to AE and gives back the empty bottle; Shift-right-
click repeats for a held stack. An empty cursor cannot bottle Aura. Automatic
crafting separately draws its empty bottles from network storage and returns
the finished bottles to that network.

Native AE2 output on the Dimensional Miner and Drygmy Simulator coalesces
identical keys in a persistent long buffer and submits one batch every 20
server ticks. A rejected batch applies backpressure; an offline grid falls
back to visible slots and third-party item transports.

## Occultism ritual item identity in 1.0.6.3

The Ritual Engine uses the same miner stack preparation for real outputs,
JEI outputs, and miner ingredients in subsequent rituals. Newly produced
native Occultism miners use the item's default spirit name. Occultism's
native getter supplies missing mining-time and rolls-per-operation values;
existing values, damage, enchantments, custom names, and other components
remain intact. Only the cosmetic miner name is standardized. Preservation
applies to the stack being normalized; this revision does not change which
components native miner tier recipes inherit from their consumed miners.

For nine audited native `craft_with_spirit_name` outputs, the machine copies
the recipe result, including its default and explicit components, instead of
adding the activation book's random spirit name. The allowlist is limited to
`occultism:knowledge_tablet`, `occultism:ender_satchel`,
`occultism:infused_pickaxe`, `occultism:dimensional_matrix`,
`occultism:satchel`, `occultism:ritual_satchel_t1`,
`occultism:ritual_satchel_t2`, `occultism:storage_remote`, and
`occultism:iesnium_butcher_knife`. Occultism's own rituals and book-binding
recipes keep their original behavior. Other named outputs, entity ownership,
and functional bindings are outside this normalization.

The machine JEI recipes for these miners and named items show unbound books
as activation inputs. This path requires a Dictionary of Spirits in the
Ritual Engine's dictionary slot. The dictionary is persistent, manual setup;
do not encode it as a consumed pattern input. Corresponding bound books
remain valid for manual insertion. This uses the existing dictionary module
and avoids a random bound-book identity in the automated crafting chain.

After upgrading, re-encode affected processing patterns from the machine's
JEI category, including miner inputs in higher-tier patterns, and replace the
old patterns. Cancel and resubmit old AE crafting requests after accounting
for items already produced or returned: an existing request still expects
the components encoded before the update. Stored items and machine
inventories are not automatically rewritten. AE item keys and MekE output
validation still compare complete components; damage, enchantments,
container contents, and bindings are never discarded to make a pattern match.

Version `1.0.6.3` remains unpublished. The combined server passed all 59
GameTests, including four new regressions: five consecutive real CPU orders
covering repeated lower-tier crafting, upgrades using stored miners, and a
recursive two-tier craft; identical actual outputs from different bound-book
names; distinct identities for functional component differences; and matching
actual, JEI, and declared outputs for all nine native named items. Gradle
`check`, all 47 machine resource contracts, and packaged integration checks
also passed.

Standalone CPU orders are verified through the selected CPU becoming idle,
the captured link remaining uncanceled, the requested amount reaching zero,
and the exact output key and quantity returning to network storage. AE2
19.2.17 does not set a standalone link's `isDone()` flag: such links have no
nexus for `markDone()` to notify. This validation follows the real terminal
order lifecycle; non-standalone links still require `isDone()`.

## Exact dynamic results in 1.0.6.4

The Ritual Computation Core's JEI category now computes results from the displayed
inputs through `OccultismRecipeBridge.resolveRitualJeiOutput`. Its complete
input list contains materials, activation and sacrifice, excluding the
selector and Dictionary of Spirits. The resolver accepts one recipe's full
consumption and returns the machine planner's exact result. Multi-copy
automation batches are also rejected if their results have different item
components or quantities.

Repair and resurrection entries expose no OUTPUT without an actual
activation INPUT focus. Their icons are RENDER_ONLY explanations. Hover the
actual tool or soul shard containing entity data and open JEI usages
(default `U`) in the machine category to obtain the exact result. Repair
retains components other than restored damage; resurrection retains entity
data. Upgrade entries keep the displayed primary input paired with its
component-preserving result, including the destination item's maximum damage.

Where a summoning, worker-capsule or upgrade ritual requires a binding book,
its default JEI activation is the unbound counterpart with a persistent
Dictionary of Spirits. A named bound-book INPUT
focus instead retains that book's name and the calling-book data embedded in
the resulting capsule. Deferred spawning still initializes jobs, ownership
and calling-book bindings; these functional fields are not normalized away.

The Arcane Enchanter has its own JEI category of the same name. Ars' original
physical-apparatus categories are unchanged. The machine category validates
and assembles concrete inputs with Ars' recipe methods, so enchantments,
armor tiers and scroll destinations remain part of the result. Default
reagents are offered only when they produce a valid machine result. An
invalid actual reagent focus does not fall back to a different default.
Recipes needing unavailable player or physical-apparatus context expose
only a reference when the machine cannot calculate their output.

The Arcane Enchanter resolves unordered same-item inputs by preferring a
reagent that carries components, such as a bound scroll over a blank scroll.
Two bound scrolls producing different destinations are ambiguous and are
rejected. Slot order must not select a different destination silently.

Both machine categories register a dedicated AE transfer handler. It reads
only visible consumed-input slots and a visible slot named `output` whose
role is OUTPUT, resolves the current recipe again, and compares the complete
output components and count. Ingredients with identical AE keys are grouped;
each encoded input has a single exact candidate. Persistent catalysts and
reference icons are excluded. Missing entity data, insufficient terminal
slots or a stale result after recipe reload produce a transfer error before
encoding. This uses the existing automation API version 5; no wildcard NBT
comparison or new context-free submission path is introduced.

Re-encode and replace affected patterns from the machine categories. Cancel
old stalled orders and resubmit after accounting for produced or returned
items. Stored components are not rewritten. One processing pattern describes
one exact input/output contract; accepting every component variant would
discard valid enchantments, contents or bindings.

Version `1.0.6.4` is unpublished. All 67 combined GameTests passed, including
eight new regressions. Three new tests complete real AE CPU orders for
repair, resurrection and an Ars-enchanted sword. Gradle `check` and all 47
machine resource contracts also passed. The server log is
[`combined-server.log`](../build/reports/dynamic-recipe-identity/combined-server.log).
Final artifact and hash information will be added to
[`RELEASE_NOTES_1.0.6.4.md`](RELEASE_NOTES_1.0.6.4.md).
