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

Two mob rewards are emitted outside loot tables by their native death code.
The simulator promotes these to fixed outputs, removing any matching entry
that a data pack or global loot modifier already put in the ordinary pool.
Each matching entity type contributes one fixed item per simulated operation;
the stack upgrade therefore scales it only by its operation multiplier, up to
256, and never by the ordinary `targetItems` random-selection count.

| Entity-type tag | Fixed output |
| --- | --- |
| `mekanism_magic:drygmy_special_loot/nether_star` | Nether Star |
| `mekanism_magic:drygmy_special_loot/wilden_tribute` | Wilden Tribute |

Data packs can add compatible third-party entity types to these entity tags,
and can extend either item policy tag without a code integration.

Experience gems are calculated independently of material candidates, so a
captured entity with an empty or fully filtered loot table can still produce
its normal Drygmy experience reward.
