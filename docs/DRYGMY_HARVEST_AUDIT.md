# Drygmy live-harvest audit

This audit covers Minecraft 1.21.1 and a finite set of locally inspected mod
versions. It is an explicit compatibility catalog, not a claim that every mod's
live products, machines, trades, or breeding recipes can be discovered safely.

Development version `1.0.6.13` ships 35 definitions: 18 for Minecraft, 13 for
EarthMobs, and 4 for Occultism. Of these, 33 are enabled by default; the cat and
villager gift definitions are optional and disabled. The loaded count is lower
when the corresponding optional mods are absent.

The default definitions are in
`src/main/resources/data/mekanism_magic/drygmy_harvest/`. They are loaded from
`data/<namespace>/drygmy_harvest/*.json` on data-pack reload. Each definition names
its entity types explicitly. Neither an entity's name nor a loot-table filename
is used to guess a product. Optional mod rules require the relevant mod to be
loaded and resolve output registry IDs at runtime.

## Simulation policy

One simulated harvest replaces a real collection cycle. The simulator supplies
the virtual habitat and collection step: egg-laying time, grass regrowth,
shearing, a basic flower-and-hive cycle, and the breeding interaction for the
explicitly supported eggs. Players do not supply buckets, bottles, bowls, or
shears. Item rules directly produce the finished item, including stew and
`dragon_breath`. Real fluid rules send only the amount accepted by an online AE
network into that network; they never produce a filled bucket/bottle fallback.
There are no new consumable slots, fluid container slots, or fluid tanks.
The simulator does not tick the captured entities, call
their interaction/shearing methods, place blocks, remove horns, transform mobs,
or consume or edit the captured container. These are addon simulation choices;
the tables below distinguish them from the source game's requirements.

Captured state still matters. Age, chicken-jockey status, wool/dye colors,
mushroom variants, sheared flags, moss/mud variants, remaining goat horns,
prepared stew effects, and familiar upgrades restrict the matching rules.
Froglights require a captured small magma cube in the same machine. Turtle and
sniffer eggs require two adults of the same entity type with distinct UUIDs.
The machine supplies the breeding cycle for those explicit recipes; it does
not expose arbitrary mod breeding, conversion, trading, or production recipes.

Each rule runs once per matching entity type and relevant output variant per
batch, not once per identical container. Explicit `variant_keys` preserve
additional NBT variants when needed. A rule's chance is sampled once for that
variant; its output is then scaled by the operation multiplier, bounded to
1–256. Loot-table probabilities and native count ranges therefore describe a
simulated cycle, not a real-time farm-rate promise.

## Already covered by the existing loot system

Normal entity loot tables remain the source of death materials: meat, leather,
feathers, wool, bones, slime, fish, and other registered material drops, including
modded drops. This audit does not replace those tables with a handwritten list.
The equipment and item-blacklist policy in [DRYGMY_LOOT_POLICY.md](DRYGMY_LOOT_POLICY.md)
continues to apply to supplemental harvest items.

Existing fixed special outputs also remain separate: Nether Star, Wilden
Tribute, the addon-defined Warden Echo Shard, Turtle Scute, and Armadillo Scute.
The two scutes are already live/growth supplements and are not added again here.

When the current death candidate pool already contains an item with the same
components as a live harvest, the live catalog skips that duplicate and keeps
the existing death-loot selection rate. This particularly affects sheep wool
and EarthMobs woolly cows. Different colors, instrument components, and prepared
stew effects remain distinct; sharing an Item ID alone does not merge them.

## Default Minecraft rules

All entity and item IDs in this table use the `minecraft` namespace. Rule files
use the `minecraft_` prefix.

| Source | Added harvest | Captured-state condition and original source |
| --- | --- | --- |
| `chicken` | 1 `egg` | Adult; `IsChickenJockey=false`. Native Chicken periodic egg-laying. |
| `sniffer` | Native digging loot | Adult; `gameplay/sniffer_digging` loot table. Native digging normally needs an eligible ground location and cooldown. |
| `panda` | Native sneeze loot | Baby; `gameplay/panda_sneeze` loot table. Its native probability remains in the table. |
| `goat` | 1 component-bearing `goat_horn` | Adult with a remaining left or right horn. Native `Goat.createHorn()` selects a UUID-based instrument from the regular or screaming goat tag. Real collection normally needs ramming. |
| Red / brown `mooshroom` | 5 `red_mushroom` / `brown_mushroom` | Adult; exact saved `Type`. Native shearing converts the mooshroom to a cow; this simulation returns the material without conversion. |
| `mooshroom` | `mushroom_stew` or prepared `suspicious_stew` | Adult. Valid saved `stew_effects` supply the effects in either red or brown cows; lightning can turn a prepared brown cow red while retaining its effects. The simulator does not invent flower effects. Native collection uses a bowl and consumes prepared effects; simulation directly returns the finished item and preserves the captured effects. |
| `bogged` | Native shearing loot | `sheared=false`; `shearing/bogged` loot table. Lowercase `sheared` is intentional. |
| `bee` | 3 `honeycomb` | Adult. This is the simulator's virtual basic hive cycle: native comb is harvested from a filled hive, not dropped directly by a bee. |
| `ender_dragon` | 1 `dragon_breath` item | Virtual bottle collection of dragon breath. Minecraft has no registered dragon-breath fluid. |
| `frog` + small magma cube | Variant-specific froglight | Adult frog plus a live captured magma cube of size 1. The native magma-cube loot table is evaluated with the frog as attacker and restricted to the three froglights. The simulated prey is not removed. |
| Adult `turtle` pair | 1–4 `turtle_egg` | Two distinct adult UUIDs of the same type. Native breeding, sand, and egg-laying are represented by the machine's breeding cycle. |
| Adult `sniffer` pair | 1 `sniffer_egg` | Two distinct adult UUIDs of the same type. The native breeding interaction is represented by the machine's breeding cycle. |
| `sheep` | 1–3 wool of its saved color | Adult, `Sheared=false`. Normally already represented by death loot; identical death candidates suppress this extra harvest. |
| `cow`, `mooshroom`, `goat` | 1000 mB milk | Adult. Resolve a real fluid from the `milk_bucket` capability, then `c:milk`. AE must accept the fluid. Neither `milk_bucket` nor an empty bucket is produced. |
| `bee` | 250 mB honey | Adult, virtual basic hive cycle. Resolve a real fluid from `honey_bottle`, then `c:honey`. Omit the fluid output if no provider is registered. |

The gift rules are shipped with `enabled: false`:

| Rule file | Why it is optional |
| --- | --- |
| `minecraft_cat_gift.json` | Morning gifts require a tamed cat and the owner's sleep/wake interaction. Enabling this explicitly chooses a virtual morning cycle; the handler still requires a tamed cat not ordered to sit. |
| `minecraft_villager_gift.json` | Hero gifts require a player with Hero of the Village. Enabling this explicitly chooses a virtual gift cycle. Profession-specific loot uses NeoForge's `RAID_HERO_GIFTS` data map; it is not scanned from every loot-table filename. |

Gift eligibility is a player interaction, not an unconditional material stored
inside the captured animal or villager. The machine cannot establish those
player conditions merely by seeing a container, so these rules are off by default.

## EarthMobs 19.3.0

Inspected `earthmobsmod-1.21.1-19.3.0.jar`. The source classes below are inside
`baguchan/earthmobsmod/`; entity and item registry strings were checked against
`registry/ModEntities.class`, `ModItems.class`, and `ModBlocks.class`.
Every corresponding rule has `required_mods: ["earthmobsmod"]`.

| Explicit source ID | Added harvest | Evidence and conditions |
| --- | --- | --- |
| `earthmobsmod:duck` | 1 `earthmobsmod:duck_egg` | `entity/Duck#spawnAtLocation` replaces `Items.EGG`; adult non-jockey. |
| `earthmobsmod:fancy_chicken` | 1 `earthmobsmod:fancy_egg` | `entity/FancyChicken#spawnAtLocation` replaces `Items.EGG`; adult non-jockey. |
| `earthmobsmod:cluck_shroom` | 1 `earthmobsmod:smelly_egg` | `entity/CluckShroom#spawnAtLocation` replaces `Items.EGG`; adult non-jockey. |
| `earthmobsmod:cluck_shroom` | 5 red/brown vanilla mushrooms | Adult; saved `Type` equals `red`/`brown`. `CluckShroom#onSheared` uses the matching mushroom and transforms the real animal to a chicken. Simulation preserves it. |
| `earthmobsmod:wooly_cow` | 1–3 `minecraft:brown_wool` | Adult, `Sheared=false`; `entity/WoolyCow#onSheared`. Death candidates normally already include this wool. |
| `earthmobsmod:umbra_cow` | 1–3 `minecraft:black_wool` | Adult, `Sheared=false`; `entity/UmbraCow#onSheared`. Same duplicate policy. |
| `earthmobsmod:horned_sheep` | 1–3 color-specific wool | Inherits Sheep; adult and unshorn. Its death table delegates to `minecraft:entities/sheep`. |
| `minecraft:sheep`, `earthmobsmod:horned_sheep` | 1 `minecraft:moss_block` | Adult, `Moss=true`, `Sheared=false`. `mixin/SheepMixin#onSheared` emits moss in addition to wool. `CommonEvents#initSpawn` initializes the moss variant in swamps. |
| `minecraft:pig`, `earthmobsmod:teacup_pig` | 1–3 vanilla dye of saved `Color` | Adult, `Muddy=true`, `Sheared=false`. `mixin/PigMixin#onSheared` has an explicit 16-color dye map; the saved `Color` is a byte. These are the default `can_muddy` tag members. |
| `earthmobsmod:moobloom` | 4 `earthmobsmod:buttercup` | Adult. `entity/Moobloom#onSheared` returns flowers and converts the real mob to Cow. This models that shearing material, not the separate bone-meal exchange. |
| `earthmobsmod:moolip` | 4 `earthmobsmod:pink_daisy` | Adult. `Moolip#getFlower` overrides the inherited Moobloom harvest. |
| `earthmobsmod:magma_cow` | 1000 mB `minecraft:lava` | Adult. `entity/MagmaCow#mobInteract` fills an empty bucket with lava; the simulator outputs fluid directly. |

The three chicken subclasses deliberately replace vanilla eggs. Inheritance
alone therefore does not justify adding `minecraft:egg` to them. The catalog
uses their verified item IDs. WoolyCow/UmbraCow inherit Cow rather than Sheep,
so they also need explicit mappings. Moss and muddy variants reuse vanilla
entity IDs; they cannot be represented by entity-ID-only fixed output tags.

`data/earthmobsmod/loot_table/entities/wooly_cow_sheared.json` and
`umbra_cow_sheared.json` contain leather and beef: they are death tables for the
already-shorn state, not shear-loot tables. Their names must not be used to
discover live materials. No world-changing `onSheared()` callback is invoked.

## Occultism 1.222.0 and 1.224.0

Inspected the repository baseline `libs/occultism-1.21.1-neoforge-1.222.0.jar`
and the local pack's `occultism-1.21.1-neoforge-1.224.0.jar`. The relevant methods
and saved upgrade key agree in these two inspected versions; this does not
claim validation of every later version. Classes are under
`com/klikli_dev/occultism/common/entity/`. Rules require `occultism`.

| Source ID | Added harvest | Evidence and original requirements |
| --- | --- | --- |
| `occultism:deer_familiar` | 1 `occultism:datura_seeds`, chance 0.25 | `familiar/DeerFamiliarEntity#ate()` directly emits seeds with a 25% roll. Its goals include vanilla EatBlockGoal; the virtual habitat supplies grazing. No upgrade or payment check exists in `ate()`. |
| `occultism:beaver_familiar` | 1 `occultism:beaver_nugget` | `familiar/BeaverFamiliarEntity#mobInteract`; saved `hasBlacksmithUpgrade=true` is mandatory. Native collection is the owner's empty-hand interaction with a 20-tick cooldown. The machine supplies the collection cycle, not the upgrade. |
| `occultism:fairy_familiar` | 1 `minecraft:dragon_breath` | `familiar/FairyFamiliarEntity#mobInteract`; saved `hasBlacksmithUpgrade=true` is mandatory. Native collection requires the owner, a glass bottle, and a 60-tick cooldown. The simulator directly produces the finished item through its collection cycle; it does not grant the upgrade. |
| `occultism:mercy_goat` | Component-bearing vanilla goat horn | `possessed/GoatOfMercyEntity` inherits Goat. The vanilla handler still requires an adult with a remaining horn and preserves its instrument variant. |

`familiar/FamiliarEntity#addAdditionalSaveData/readAdditionalSaveData` stores
`hasBlacksmithUpgrade` as a boolean in both inspected versions. It is not
inferred from the familiar name. `goat_familiar` is a different class and does
not inherit vanilla Goat; it is not assigned goat horns.

## Other inspected mods and intentional exclusions

| Inspected source | Coverage and reason for no additional default material expansion |
| --- | --- |
| Ars Nouveau 5.13.0; local cached 5.13.0.1389 sources | Normal Wilden entity tables and the existing Wilden Tribute supplement remain. Starbuncle shards consume a gold nugget; Drygmy shards consume an accepted Wilden drop; Alakarkinos shards consume a pottery sherd; Whirlisprig shards follow a taming event. These paths remove the original entity. They are not free periodic animal materials. The jar has `dispel_witch` plus three Wilden entity tables, not a general live-harvest table registry. |
| Productive Bees 13.13.5 | Basic real honey fluids can be resolved through the common tag, but advanced bee production is not imported. `productivebees:configurable_bee` stores its actual bee type in NBT `type`; many species share that entity ID. `data/productivebees/productivebees/raw_materials/iron.json` requires the ferric flower tag. `recipe/bee_produce/raw_materials/iron_bee.json` is an `advanced_beehive` recipe producing `configurable_honeycomb` with `productivebees:bee_type="productivebees:iron"`, plus possible pollen. Flowers, hives, components, recipes, and centrifuging cannot be replaced by scanning an entity name for a metal. `gameplay/hero_of_the_village/beekeeper_gifts` is a villager gift, not a bee product. |
| Mowzie's Mobs 1.8.2 | Normal entity drops remain. No unconditional extra ordinary material was identified in the inspected Elokosa, Frostmaw, Grottol, Lantern, Naga, Sculptor, Umvuthana, or Umvuthi classes. Frostmaw's ice crystal is removable stateful equipment; a captured Grottol item is an entity container. `entities/sculptor_test` is a trial reward table containing an Earthrend Gauntlet, not a live-harvest table. Sculptor/Umvuthi payment paths are not imported. |
| Aquaculture 2.7.21 | Normal fish and turtle entity tables remain. `com/teammetallurgy/aquaculture/entity/TurtleLandEntity` extends Animal, not vanilla Turtle, and has no growth-scute override. Arrau/box/starshell turtles are therefore not assigned vanilla scutes by their names. Fishing and treasure-box tables are not live-animal material tables. |
| Nature's Aura 41.9 | The mod's entities are utility/projectile/minecart types, not new animals with independent material production. `recipe/animal_spawner/*` creates mobs through a machine and is not a catalog of their outputs. |

Other excluded interactions include Piglin bartering and villager trades,
Allay delivery or duplication, Dolphin treasure guidance, Fox equipment or
carried items, Cow/Mooshroom bucket or bowl containers as extra empty items,
snow-golem placed snow, and transformations or arbitrary entity inventory
contents. EarthMobs HornedSheep's `horn_flute` is a stateful instrument rather
than an ordinary material; the melon golem's carved head is a one-time removable
part. Neither is added as a default recurring resource. Bone-meal flower
exchanges are not imported in addition to the explicit flower-shearing rule.

Occultism Cthulhu consumes lapis for prismarine; Devil consumes a golden apple
for an enchanted golden apple and requires an upgrade. Those input-material
conversions, blacksmith upgrade payments, spirit processing/trading jobs,
familiar soul-shard returns, and collected or stolen inventory contents remain
outside this catalog. Finished bottle items are modeled explicitly above;
consuming a non-container ingredient is not automatically waived for mod recipes.

## Real fluid providers

Fluid output first inspects the declared filled item's fluid capability. This
is a lookup using a temporary item; it does not consume an item or put one in a
machine slot. If that cannot resolve a fluid, the rule uses its explicit fluid
ID, or its common fluid tag when no explicit fluid ID is declared. Explicit-ID
and tag-based flowing variants are normalized to their source fluid; a stable
registry-ID ordering chooses among multiple tag providers. A missing provider
omits that fluid output instead of inventing a new fluid registry ID.

The inspected local pack contains both of these real honey providers:

| JAR | Registered source / flowing IDs | Evidence |
| --- | --- | --- |
| `productivebees-1.21.1-13.13.5.jar` | `productivebees:honey`, `productivebees:flowing_honey` | `cy/jdkdigital/productivebees/init/ModFluids.class`; both listed in `data/c/tags/fluid/honey.json`. |
| `create-1.21.1-6.0.10.jar` | `create:honey`, `create:flowing_honey` | `com/simibubi/create/AllFluids.class`; both listed in `data/c/tags/fluid/honey.json`. |

Neither mod is a hard dependency of the honey rule. Vanilla `honey_bottle` is
an item, not proof that a `minecraft:honey` fluid exists. Milk likewise uses the
registered milk-container capability/common tag; lava uses the real vanilla
`minecraft:lava`. Dragon breath stays an item, because vanilla supplies no
dragon-breath fluid registry entry.

## Batch and AE delivery

1. Read supported captured entities into memory and apply the existing entity
   blacklist. Their containers and saved entity state remain unchanged.
2. Sample and filter normal death-loot candidates, extract the existing fixed
   rewards, then evaluate explicit live-harvest rules. Live items already in the
   ordinary pool with identical components are suppressed. The catalog's
   per-rule entity-type/variant key prevents repeated identical containers from
   adding another live harvest.
3. Freeze the chosen outputs, operation multiplier, and resource cost for the
   pending batch. Save/load preserves the pending selection and progress when
   the restored container signature matches. Changing stack upgrades mid-cycle
   applies to the next batch, not a second scaling of the prepared output.
4. After the normal Source/energy processing cycle, send items through the
   existing item output path: AE batching while connected, or visible output
   slots and item transport while offline. No collection consumable is required.
5. Complete real fluid outputs through the direct AE fluid path. AE must be
   online, powered, and able to accept that fluid. Missing AE, missing fluid
   providers, full storage, or incompatible storage produces no corresponding
   stored fluid and no bucket/bottle substitute. Partial acceptance keeps only
   the accepted amount; the rejected remainder is not stored for later recovery.

Only batches containing no item outputs require a positive AE fluid acceptance
preview before processing can advance. This preview reserves no capacity; final
insertion still uses the capacity available at completion. Batches with item
outputs can continue even if their fluid output cannot be accepted.

The shared AE execution budget may defer the first insertion attempt for an
already paid fluid output while the network remains online. That pending
delivery is persisted and retried without charging or generating the batch
again. An actual capacity rejection, partial acceptance, or offline result ends
the fluid output. This bookkeeping is not a fluid tank, exposes no fluid slot
or pipe capability, and does not accumulate fluid rejected by storage.

## Extending or disabling a rule

Override the same resource path in a higher-priority data pack to change a
default, or set `enabled: false` to disable it. New definitions may use another
namespace. Reload the data pack to apply changes; an already prepared batch
retains its saved output selection. No source-code changes are needed for
supported kinds.

| Field | Meaning and bounds |
| --- | --- |
| `enabled` | Optional boolean, default `true`. |
| `required_mods` | Optional array of mod IDs; every named mod must be loaded. |
| `entities` | Required nonempty array of explicit entity registry IDs. Entity tags and name-based discovery are not supported here. |
| `kind` | One of the supported kinds listed below. |
| `adult`, `baby` | Optional booleans, both default `false`; they cannot both be `true`. |
| `nbt` | Optional JSON object or SNBT compound string matched as a subset of saved entity NBT. Names and numeric NBT types must match. |
| `variant_keys` | Optional array of saved NBT paths, including dotted compound paths. Add these values to the per-rule, per-entity-type output-variant key. |
| `chance` | Optional finite number from 0 through 1, default 1. Roll once per matching type/variant before operation scaling. |
| `count` | Optional integer or `{"min": 1, "max": 3}`, with `1 <= min <= max <= 64`. Sets item-kind counts and overrides native-kind stack counts. A generic `loot_table` kind retains the table's counts; this field does not override them. |
| `item` | Required registered output item ID for `kind: "item"`. Produces the default item stack; arbitrary item components are not a schema field. |
| `loot_table` | Required table resource ID for `kind: "loot_table"`. Uses only supported native context parameters; incompatible contexts are skipped with a warning. |
| `fluid_container`, `fluid`, `fluid_tag` | At least one is required for `kind: "fluid"`. Lookup order is filled-item capability, then explicit fluid ID if supplied, otherwise the fluid tag. Tag values omit `#`. |
| `amount` | Fluid mB per unscaled operation, default 1000, allowed range 1–16000. |

The simple kinds are `item`, `loot_table`, and `fluid`. The explicit native
handlers are `goat_horn`, `mooshroom_stew`, `frog_lights`, `turtle_egg`,
`sniffer_egg`, `colored_wool`, `colored_dye`, `cat_gift`, and `villager_gift`.
They preserve their supported state/component semantics and enforce the native
handler's eligibility checks in addition to the rule predicates. Selecting a
kind does not discover other recipes or make an arbitrary entity compatible.

For example, the shipped chicken rule is equivalent to:

```json
{
  "entities": ["minecraft:chicken"],
  "kind": "item",
  "item": "minecraft:egg",
  "count": 1,
  "adult": true,
  "nbt": {"IsChickenJockey": false}
}
```

A pack that wants only cyan muddy-pig dye can override the shipped
`mekanism_magic:earthmobs_muddy_pig_dye` definition with:

```json
{
  "required_mods": ["earthmobsmod"],
  "entities": ["minecraft:pig", "earthmobsmod:teacup_pig"],
  "kind": "colored_dye",
  "count": {"min": 1, "max": 3},
  "adult": true,
  "nbt": "{Muddy:true,Sheared:false,Color:9b}",
  "variant_keys": ["Color"]
}
```

The `b` in `Color:9b` preserves the byte type used by EarthMobs. JSON numeric
`9` becomes an NBT integer and would not match this byte field. Boolean JSON
predicates such as `{"Muddy": true, "Sheared": false}` are supported directly.
These are read-only state predicates, not requests to modify the animal.

The milk definition demonstrates fluid lookup without generating a milk bucket:

```json
{
  "entities": ["minecraft:cow", "minecraft:mooshroom", "minecraft:goat"],
  "kind": "fluid",
  "fluid_container": "minecraft:milk_bucket",
  "fluid_tag": "c:milk",
  "amount": 1000,
  "adult": true
}
```

At most 256 enabled, applicable definitions are loaded in resource-ID order.
The operation multiplier is bounded to 1–256. Table/native stacks without a
native count override are capped at 64 items each before that scaling;
configured counts and fluid amounts have the bounds above. Malformed rules and
unsupported runtime contexts are skipped with a warning instead of executing
arbitrary interaction code. Distinct rule IDs are independent, so pack authors
must avoid adding overlapping copies when they mean to override a default.

NBT names and types must be verified against the owning mod. A new table should
be added only after confirming its intended context and acquisition requirements;
a path containing `entities`, `gift`, or `sheared` does not establish that it is
a free live material source. Recipe-driven bee production, trades, and paid
ingredient conversions remain outside the supplied catalog.
