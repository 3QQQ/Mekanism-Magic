# Drygmy Loot Policy

The Drygmy Ecosystem Simulator builds a candidate pool from each captured
entity's normal loot table before selecting its configured number of items.
Empty stacks, damageable items, `c:tools`, `c:armors`, and entries in
`mekanism_magic:drygmy_loot_blacklist` are removed before that selection.
This keeps generated equipment out without restricting ordinary material
drops by namespace.

`mekanism_magic:drygmy_allow_damageable_loot` opts an item back into the pool
when it is intentionally used as a damageable material. The explicit
blacklist has final precedence if an item is present in both tags.
The built-in blacklist also removes saddles and vanilla animal armor, which
are non-damageable equipment and would otherwise bypass the generic gear
check. A data pack may replace or extend either policy tag.

Some mob rewards are emitted outside loot tables by native code; others are
explicit supplemental rewards defined by this addon. The simulator emits
these as fixed outputs, removing any matching entry
that a data pack or global loot modifier already put in the ordinary pool.
Each matching entity type contributes one fixed item per simulated operation;
the stack upgrade therefore scales it only by its operation multiplier, up to
256, and never by the ordinary `targetItems` random-selection count.

| Entity-type tag | Fixed output |
| --- | --- |
| `mekanism_magic:drygmy_special_loot/nether_star` | Nether Star |
| `mekanism_magic:drygmy_special_loot/wilden_tribute` | Wilden Tribute |
| `mekanism_magic:drygmy_special_loot/echo_shard` | Echo Shard (Warden) |
| `mekanism_magic:drygmy_special_loot/turtle_scute` | Turtle Scute (Turtle) |
| `mekanism_magic:drygmy_special_loot/armadillo_scute` | Armadillo Scute (Armadillo) |

The Warden-to-Echo-Shard rule is an addon-defined renewable source, not a
vanilla death drop: vanilla 1.21.1 Wardens drop Sculk Catalysts, while Echo
Shards come from Ancient City chests. Turtle scutes normally come from
growth, and armadillo scutes from live shedding/brushing. These three fixed
rewards intentionally make those materials available through the simulator.
Normal loot tables remain in use, including Warden Sculk Catalysts.

The Ars Nouveau Drygmy entity blacklist still applies before all rewards,
and the explicit item blacklist can suppress any of these fixed materials.

Data packs can add compatible third-party entity types to these entity tags,
and can extend either item policy tag without a code integration.

Experience gems are calculated independently of material candidates, so a
captured entity with an empty or fully filtered loot table can still produce
its normal Drygmy experience reward.

## Live-harvest supplements in 1.0.6.13

The simulator also evaluates the explicit data-pack catalog at
`data/<namespace>/drygmy_harvest/*.json`. The 35 shipped definitions include
33 enabled rules for Minecraft, EarthMobs, and Occultism, plus disabled cat and
villager gifts. Optional mod rules load only when their required mods are
present. The complete source audit, state conditions, schema, and exclusions
are in [DRYGMY_HARVEST_AUDIT.md](DRYGMY_HARVEST_AUDIT.md).

Live item outputs pass the same equipment and blacklist filter as ordinary
loot. Unlike the fixed-reward extraction above, a live item already present in
the current ordinary candidate pool with identical components is skipped, so
that death-loot candidate keeps its existing selection rate. Different wool
colors, horn instruments, and stew effects stay distinct. Each rule evaluates
once per matching entity type and relevant variant per batch, rolls its chance
once, and scales the resulting harvest only by the operation multiplier, up to
256. It does not enter the ordinary `targetItems` repeated-selection loop.

Eggs, mushrooms, honeycomb, prepared stew, and dragon breath are direct item
outputs. No bucket, bottle, bowl, or shears input is required, and there is no
new consumable slot. Milk, resolved honey fluid, and EarthMobs magma-cow lava
use real registered fluids and can only enter an online, powered AE network
with capacity for that fluid. They do not become filled bucket/bottle items
when AE is absent, offline, or full. Partial acceptance produces only the
accepted amount; no fluid tank or fluid container slot stores the remainder.

Only an AE execution-budget delay may retain an already paid fluid delivery
for its first attempt; an actual storage rejection or offline result does not
create a recoverable fluid reserve. Item outputs retain their existing AE and
visible-slot delivery behavior. Pending batch state is persisted so a reload
does not reroll or apply the stack multiplier twice.

All calculations use the captured state without consuming, replacing, or
editing the entity container. The catalog does not tick the entity, shear it
in-world, remove its horn, clear prepared stew effects, consume simulated prey,
or transform the animal. Explicit breeding-pair and upgrade requirements still
apply. The default rules do not import arbitrary production recipes, trades,
or conversions that consume non-container materials.
