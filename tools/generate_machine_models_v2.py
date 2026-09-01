import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BLOCK_MODELS = ROOT / "src/main/resources/assets/mekanism_magic/models/block"
ARS_TIERS = (
    "basic", "advanced", "elite", "ultimate",
    "absolute", "supreme", "cosmic", "infinite",
)


def six(texture):
    return {
        direction: {"texture": f"#{texture}"}
        for direction in ("down", "up", "north", "south", "west", "east")
    }


def box(name, start, end, texture, **overrides):
    faces = six(texture)
    for direction, override in overrides.items():
        faces[direction] = {"texture": f"#{override}"}
    return {"name": name, "from": start, "to": end, "faces": faces}


def panel(name, start, end, direction, texture, emissive=False):
    element = {
        "name": name,
        "from": start,
        "to": end,
        "faces": {
            direction: {
                "texture": f"#{texture}",
                "cullface": direction,
            }
        },
    }
    if emissive:
        element["neoforge_data"] = {"block_light": 15, "sky_light": 15}
    return element


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def replace_elements(model_name, credit, elements):
    path = BLOCK_MODELS / f"{model_name}.json"
    model = read(path)
    model["credit"] = credit
    model["elements"] = elements
    write(path, model)


def source_generator():
    return [
        # Layered Mekanism foundation with interrupted perimeter rails.
        box("precision_reactor_foundation", [0, 0, 0], [16, 1, 16], "casing", down="bottom", up="trim"),
        box("recessed_inner_plinth", [1, 1, 1], [15, 2, 15], "casing", up="top"),
        box("northwest_foundation_rail", [0, 1, 0], [5, 2, 1], "trim"),
        box("northeast_foundation_rail", [11, 1, 0], [16, 2, 1], "trim"),
        box("southwest_foundation_rail", [0, 1, 15], [5, 2, 16], "trim"),
        box("southeast_foundation_rail", [11, 1, 15], [16, 2, 16], "trim"),
        box("west_front_foundation_rail", [0, 1, 1], [1, 2, 5], "trim"),
        box("west_rear_foundation_rail", [0, 1, 11], [1, 2, 15], "trim"),
        box("east_front_foundation_rail", [15, 1, 1], [16, 2, 5], "trim"),
        box("east_rear_foundation_rail", [15, 1, 11], [16, 2, 15], "trim"),

        # Dense lower machine body and a physically layered front console.
        box("source_reactor_lower_body", [3, 2, 3], [13, 7.5, 13], "casing", north="front", south="back", west="right_panel", east="left_panel", up="top"),
        box("source_reactor_trim_band", [3, 7.5, 3], [13, 8, 13], "trim", up="sourcestone"),
        box("front_console_body", [4, 2, 0.5], [12, 6.5, 3], "trim", north="front"),
        box("front_display_bezel", [5, 3, 0], [11, 6, 0.5], "front"),
        box("front_console_west_rail", [3.25, 2.5, 1], [4, 6, 3], "casing", north="trim"),
        box("front_console_east_rail", [12, 2.5, 1], [12.75, 6, 3], "casing", north="trim"),
        box("front_console_crown", [4, 6.5, 0.5], [12, 7.5, 3], "sourcestone", north="rune", up="trim"),

        # Split induction banks expose actual depth instead of a flat side box.
        box("west_induction_base", [0, 2, 4], [3, 4, 12], "casing", west="right_panel"),
        box("west_forward_coil_bank", [0, 4, 4], [3, 7, 7.25], "casing", west="coil", up="trim"),
        box("west_rear_coil_bank", [0, 4, 8.75], [3, 7, 12], "casing", west="coil", up="trim"),
        box("west_source_coupler", [0.75, 4.5, 7.25], [2.25, 6.5, 8.75], "conduit"),
        box("west_forward_coil_cap", [0, 7, 4], [3, 8, 7.25], "trim", up="rune"),
        box("west_rear_coil_cap", [0, 7, 8.75], [3, 8, 12], "trim", up="rune"),
        box("east_induction_base", [13, 2, 4], [16, 4, 12], "casing", east="left_panel"),
        box("east_forward_coil_bank", [13, 4, 4], [16, 7, 7.25], "casing", east="coil", up="trim"),
        box("east_rear_coil_bank", [13, 4, 8.75], [16, 7, 12], "casing", east="coil", up="trim"),
        box("east_source_coupler", [13.75, 4.5, 7.25], [15.25, 6.5, 8.75], "conduit"),
        box("east_forward_coil_cap", [13, 7, 4], [16, 8, 7.25], "trim", up="rune"),
        box("east_rear_coil_cap", [13, 7, 8.75], [16, 8, 12], "trim", up="rune"),

        # Rear capacitor group balances the front console without duplicating it.
        box("rear_capacitor_body", [5, 2, 13], [11, 6.5, 16], "casing", south="back"),
        box("rear_capacitor_crown", [4.5, 6.5, 13], [11.5, 8, 16], "trim", south="back", up="sourcestone"),
        box("rear_west_terminal", [3.5, 3, 14], [5, 6, 15.5], "conduit", south="coil"),
        box("rear_east_terminal", [11, 3, 14], [12.5, 6, 15.5], "conduit", south="coil"),

        # Open process deck: four frame segments surround a recessed basin.
        box("north_containment_deck", [3, 8, 3], [13, 10, 5], "trim", up="rune"),
        box("south_containment_deck", [3, 8, 11], [13, 10, 13], "trim", up="rune"),
        box("west_containment_deck", [3, 8, 5], [5, 10, 11], "trim", up="rune"),
        box("east_containment_deck", [11, 8, 5], [13, 10, 11], "trim", up="rune"),
        box("source_basin_floor", [5, 8, 5], [11, 8.75, 11], "sourcestone", up="conduit"),
        box("source_basin_pedestal", [6.25, 8.75, 6.25], [9.75, 10, 9.75], "sourcestone", up="rune"),

        # Four mechanical pylons constrain two separate arcane rings.
        box("northwest_constraint_pylon", [4, 10, 4], [5, 14, 5], "casing", up="trim"),
        box("northeast_constraint_pylon", [11, 10, 4], [12, 14, 5], "casing", up="trim"),
        box("southwest_constraint_pylon", [4, 10, 11], [5, 14, 12], "casing", up="trim"),
        box("southeast_constraint_pylon", [11, 10, 11], [12, 14, 12], "casing", up="trim"),
        box("northwest_source_feed", [5, 10, 4.25], [7, 10.5, 4.75], "conduit"),
        box("northeast_source_feed", [9, 10, 4.25], [11, 10.5, 4.75], "conduit"),
        box("southwest_source_feed", [5, 10, 11.25], [7, 10.5, 11.75], "conduit"),
        box("southeast_source_feed", [9, 10, 11.25], [11, 10.5, 11.75], "conduit"),
        box("west_north_source_feed", [4.25, 10, 5], [4.75, 10.5, 7], "conduit"),
        box("west_south_source_feed", [4.25, 10, 9], [4.75, 10.5, 11], "conduit"),
        box("east_north_source_feed", [11.25, 10, 5], [11.75, 10.5, 7], "conduit"),
        box("east_south_source_feed", [11.25, 10, 9], [11.75, 10.5, 11], "conduit"),
        box("lower_ring_north", [5.75, 10.5, 5.75], [10.25, 11, 6.25], "conduit"),
        box("lower_ring_south", [5.75, 10.5, 9.75], [10.25, 11, 10.25], "conduit"),
        box("lower_ring_west", [5.75, 10.5, 6.25], [6.25, 11, 9.75], "conduit"),
        box("lower_ring_east", [9.75, 10.5, 6.25], [10.25, 11, 9.75], "conduit"),
        box("core_lower_mount", [7, 10, 7], [9, 11, 9], "sourcestone", up="rune"),
        box("upper_ring_north", [5, 13, 5], [11, 13.5, 5.75], "sourcestone", up="rune"),
        box("upper_ring_south", [5, 13, 10.25], [11, 13.5, 11], "sourcestone", up="rune"),
        box("upper_ring_west", [5, 13, 5.75], [5.75, 13.5, 10.25], "sourcestone", up="rune"),
        box("upper_ring_east", [10.25, 13, 5.75], [11, 13.5, 10.25], "sourcestone", up="rune"),
        box("north_rune_focus", [7, 13.5, 5.25], [9, 14, 5.75], "rune"),
        box("south_rune_focus", [7, 13.5, 10.25], [9, 14, 10.75], "rune"),
        box("west_rune_focus", [5.25, 13.5, 7], [5.75, 14, 9], "rune"),
        box("east_rune_focus", [10.25, 13.5, 7], [10.75, 14, 9], "rune"),

        # Multi-piece suspended Source crystal and individually capped pylons.
        box("suspended_source_heart", [7.25, 11.25, 7.25], [8.75, 14.75, 8.75], "core"),
        box("north_source_shard", [7.5, 11.75, 6.5], [8.5, 14.25, 7.25], "core"),
        box("south_source_shard", [7.5, 11.75, 8.75], [8.5, 14.25, 9.5], "core"),
        box("west_source_shard", [6.5, 11.75, 7.5], [7.25, 14.25, 8.5], "core"),
        box("east_source_shard", [8.75, 11.75, 7.5], [9.5, 14.25, 8.5], "core"),
        box("source_heart_crown", [7.5, 14.75, 7.5], [8.5, 16, 8.5], "core"),
        box("northwest_pylon_cap", [3.5, 14, 3.5], [5.5, 15, 5.5], "trim", up="rune"),
        box("northeast_pylon_cap", [10.5, 14, 3.5], [12.5, 15, 5.5], "trim", up="rune"),
        box("southwest_pylon_cap", [3.5, 14, 10.5], [5.5, 15, 12.5], "trim", up="rune"),
        box("southeast_pylon_cap", [10.5, 14, 10.5], [12.5, 15, 12.5], "trim", up="rune"),
        box("northwest_arc_emitter", [4.1, 15, 4.1], [4.9, 16, 4.9], "conduit"),
        box("northeast_arc_emitter", [11.1, 15, 4.1], [11.9, 16, 4.9], "conduit"),
        box("southwest_arc_emitter", [4.1, 15, 11.1], [4.9, 16, 11.9], "conduit"),
        box("southeast_arc_emitter", [11.1, 15, 11.1], [11.9, 16, 11.9], "conduit"),
    ]


def source_converter():
    return [
        box("converter_floor", [0, 0, 0], [16, 2, 16], "casing", down="bottom", up="trim"),
        box("cyan_conversion_tower", [1, 2, 2], [6, 13, 14], "casing", west="left", up="accent"),
        box("violet_conversion_tower", [10, 2, 2], [15, 11, 14], "casing", east="right", up="accent_b"),
        box("central_exchange_body", [6, 2, 4], [10, 8, 14], "casing", south="back", up="top"),
        box("left_front_terminal", [2, 3, 0], [6, 8, 2], "trim", north="front"),
        box("center_front_terminal", [6, 3, 0], [10, 8, 4], "trim", north="front"),
        box("right_front_terminal", [10, 3, 0], [14, 8, 2], "trim", north="front"),
        box("exchange_bridge", [6, 8, 6], [10, 10, 12], "sourcestone", up="accent_b"),
        box("phase_transducer", [7, 10, 7], [9, 14, 11], "gold", up="accent"),
        box("cyan_tower_cap", [0, 13, 1], [6.5, 14.5, 15], "trim", up="accent"),
        box("cyan_source_cell", [2, 14.5, 4], [5.5, 16, 8], "accent", up="gold"),
        box("violet_tower_cap", [9.5, 11, 1], [16, 12.5, 15], "trim", up="accent_b"),
        box("violet_source_cell", [10.5, 12.5, 8], [14, 15, 12], "accent_b", up="gold"),
        box("conversion_coupler", [6.5, 14, 6], [9.5, 15, 12], "sourcestone", up="accent_b"),
        box("balanced_source_prism", [7, 15, 8], [9, 16, 10], "gold", up="accent"),
    ]


def catalyst_identifier():
    return [
        box("analyzer_floor", [0, 0, 0], [16, 2, 16], "casing", down="bottom", up="trim"),
        box("analyzer_body", [2, 2, 2], [14, 9, 16], "casing", south="back", up="top"),
        box("sample_console", [3, 3, 0], [13, 8, 2], "trim", north="front"),
        box("west_data_bank", [0, 2, 4], [2, 10, 14], "casing", west="left"),
        box("east_data_bank", [14, 2, 4], [16, 10, 14], "casing", east="right"),
        box("scan_bed", [4, 9, 5], [12, 10, 14], "accent_b", up="accent"),
        box("rear_analysis_tower", [5, 10, 12], [11, 15, 16], "trim", south="back", up="gold"),
        box("west_arch_support", [2, 10, 3], [5, 15, 5], "trim", up="gold"),
        box("east_arch_support", [11, 10, 3], [14, 15, 5], "trim", up="gold"),
        box("scanner_arch", [2, 15, 3], [14, 16, 6], "trim", down="accent_b", north="accent"),
        box("scan_emitter", [6, 13, 6], [10, 15, 9], "gold", down="accent"),
        box("amber_scan_prism", [7, 10, 9], [9, 13, 12], "accent", up="gold"),
        box("west_sample_cache", [0, 10, 6], [2, 12, 12], "accent_b"),
        box("east_sample_cache", [14, 10, 6], [16, 12, 12], "accent_b"),
    ]


def imbuement_processor():
    return [
        box("infuser_floor", [0, 0, 0], [16, 2, 16], "casing", down="bottom", up="trim"),
        box("infuser_body", [2, 2, 2], [14, 10, 14], "casing", up="top"),
        box("infuser_console", [3, 3, 0], [13, 9, 2], "trim", north="front"),
        box("west_source_pump", [0, 3, 4], [2, 9, 12], "casing", west="left", up="accent_b"),
        box("east_source_pump", [14, 3, 4], [16, 9, 12], "casing", east="right", up="accent_b"),
        box("rear_source_reservoir", [5, 2, 14], [11, 8, 16], "casing", south="back"),
        box("west_manifold_housing", [2, 10, 3], [5, 14, 13], "trim", up="rune"),
        box("east_manifold_housing", [11, 10, 3], [14, 14, 13], "trim", up="rune"),
        box("front_manifold_bridge", [5, 10, 2], [11, 12, 5], "sourcestone", up="rune"),
        box("rear_manifold_bridge", [5, 10, 11], [11, 12, 14], "sourcestone", up="rune"),
        box("infusion_crucible", [5, 10, 5], [11, 11, 11], "basin"),
        box("north_source_channel", [7, 11, 5], [9, 11.4, 7], "conduit"),
        box("south_source_channel", [7, 11, 9], [9, 11.4, 11], "conduit"),
        box("west_source_channel", [5, 11, 7], [7, 11.4, 9], "conduit"),
        box("east_source_channel", [9, 11, 7], [11, 11.4, 9], "conduit"),
        box("infusion_core_mount", [6, 11.4, 6], [10, 13, 10], "sourcestone", up="rune"),
        box("north_rune_lintel", [5, 14, 4], [11, 15, 5], "rune"),
        box("south_rune_lintel", [5, 14, 11], [11, 15, 12], "rune"),
        box("west_rune_lintel", [4, 14, 5], [5, 15, 11], "rune"),
        box("east_rune_lintel", [11, 14, 5], [12, 15, 11], "rune"),
        box("source_heart", [7, 13, 7], [9, 16, 9], "core"),
    ]


def enchanting_processor():
    return [
        box("enchanter_floor", [0, 0, 0], [16, 2, 16], "casing", down="bottom", up="trim"),
        box("west_library_stack", [2, 2, 5], [6, 12, 16], "casing", west="left", south="back"),
        box("east_library_stack", [10, 2, 5], [14, 12, 16], "casing", east="right", south="back"),
        box("rear_arcane_spine", [6, 2, 12], [10, 12, 16], "casing", south="back"),
        box("enchanter_console", [3, 3, 0], [13, 9, 5], "trim", north="front"),
        box("apparatus_altar", [6, 9, 5], [10, 11, 10], "sourcestone", up="accent_b"),
        box("book_cradle", [6.5, 11, 6], [9.5, 12, 9], "gold", up="accent"),
        box("west_arc_tower", [2, 12, 5], [5, 15, 9], "trim", up="gold"),
        box("east_arc_tower", [11, 12, 5], [14, 15, 9], "trim", up="gold"),
        box("rear_arc_tower", [5, 12, 12], [11, 15, 16], "trim", up="accent_b"),
        box("front_arc_bridge", [5, 14, 4], [11, 15, 6], "gold", north="accent"),
        box("apparatus_focus", [7, 12, 7], [9, 16, 9], "accent", up="gold"),
        box("west_arc_cap", [1, 15, 4], [5, 16, 10], "gold"),
        box("east_arc_cap", [11, 15, 4], [15, 16, 10], "gold"),
        box("rear_arc_cap", [5, 15, 12], [11, 16, 16], "gold"),
    ]


def drygmy_simulator():
    return [
        box("habitat_floor", [0, 0, 0], [16, 2, 16], "casing", down="bottom", up="trim"),
        box("habitat_cabinet", [2, 2, 2], [14, 8, 14], "casing", up="top"),
        box("feeding_console", [3, 3, 0], [13, 7, 2], "trim", north="front"),
        box("west_life_support", [0, 3, 4], [2, 7, 12], "casing", west="left", up="accent"),
        box("east_life_support", [14, 3, 4], [16, 7, 12], "casing", east="right", up="accent_b"),
        box("terrarium_base", [1, 8, 1], [15, 9, 15], "trim", up="accent"),
        box("habitat_bed", [2, 9, 2], [14, 10, 14], "accent", up="accent_b"),
        box("west_glass_wall", [1, 9, 1], [2, 14, 15], "glass"),
        box("east_glass_wall", [14, 9, 1], [15, 14, 15], "glass"),
        box("north_glass_wall", [2, 9, 1], [14, 14, 2], "glass"),
        box("south_glass_wall", [2, 9, 14], [14, 14, 15], "glass"),
        box("drygmy_grove", [5, 10, 5], [8, 13, 8], "accent", up="gold"),
        box("habitat_focus", [9, 10, 8], [12, 12, 11], "accent_b", up="gold"),
        box("west_canopy", [1, 14, 1], [6, 16, 15], "trim", up="top"),
        box("east_canopy", [10, 14, 1], [15, 16, 15], "trim", up="top"),
        box("front_canopy_bridge", [6, 14, 1], [10, 15, 4], "trim", up="top"),
        box("rear_canopy_bridge", [6, 14, 12], [10, 15, 15], "trim", up="top"),
    ]


def spirit_processor():
    return [
        box("binding_floor", [0, 0, 0], [16, 2, 16], "casing", down="bottom", up="binding"),
        box("front_binding_altar", [2, 2, 0], [14, 9, 4], "trim", north="front", up="binding"),
        box("rear_spirit_engine", [2, 2, 12], [14, 10, 16], "casing", south="back", up="otherstone"),
        box("west_binding_column", [0, 2, 4], [4, 12, 12], "casing", west="side", up="otherstone"),
        box("east_binding_column", [12, 2, 4], [16, 12, 12], "casing", east="side", up="otherstone"),
        box("chamber_floor", [4, 2, 4], [12, 4, 12], "gold", up="binding"),
        box("spirit_well", [5, 4, 5], [11, 5, 11], "gold", up="spirit"),
        box("cage_north", [5, 5, 5], [11, 12, 5.5], "glass"),
        box("cage_south", [5, 5, 10.5], [11, 12, 11], "glass"),
        box("cage_west", [5, 5, 5.5], [5.5, 12, 10.5], "glass"),
        box("cage_east", [10.5, 5, 5.5], [11, 12, 10.5], "glass"),
        box("bound_spirit", [6, 5, 6], [10, 11, 10], "spirit"),
        box("west_binding_rotor", [4, 12, 6], [7, 13, 10], "binding"),
        box("east_binding_rotor", [9, 12, 6], [12, 13, 10], "binding"),
        box("north_binding_rotor", [7, 12, 5], [9, 13, 7], "binding"),
        box("south_binding_rotor", [7, 12, 9], [9, 13, 11], "binding"),
        box("spirit_seal", [6, 13, 6], [10, 15, 10], "otherstone", up="soul"),
        box("soul_socket", [7, 15, 7], [9, 16, 9], "soul"),
    ]


def dimension_miner():
    return [
        box("miner_floor", [0, 0, 0], [16, 2, 16], "casing", down="bottom", up="trim"),
        box("west_miner_foot", [0, 2, 2], [5, 7, 16], "casing", west="side", south="back"),
        box("east_miner_foot", [11, 2, 2], [16, 7, 16], "casing", east="side", south="back"),
        box("rear_extraction_engine", [5, 2, 12], [11, 9, 16], "casing", south="back", up="accent_b"),
        box("left_output_console", [4, 2, 0], [7, 7, 2], "trim", north="front"),
        box("ore_output_chute", [7, 3, 0], [9, 6, 2], "accent", north="accent"),
        box("right_output_console", [9, 2, 0], [12, 7, 2], "trim", north="front"),
        box("aperture_foundation", [5, 2, 2], [11, 3, 12], "gold", up="void"),
        box("west_aperture_rim", [4, 7, 3], [6, 15, 12], "otherstone", east="accent"),
        box("east_aperture_rim", [10, 7, 3], [12, 15, 12], "otherstone", west="accent_b"),
        box("aperture_crown", [4, 15, 3], [12, 16, 12], "otherstone", down="void"),
        box("vertical_dimensional_aperture", [6, 9, 4], [10, 15, 5], "void"),
        box("extraction_nozzle", [6, 3, 5], [10, 7, 10], "gold", up="void"),
        box("rear_phase_conduit", [7, 9, 12], [9, 14, 16], "accent_b"),
    ]


def ritual_engine():
    return [
        box("ritual_floor", [0, 0, 0], [16, 2, 16], "casing", down="bottom", up="trim"),
        box("front_ritual_bank", [1, 2, 0], [15, 8, 4], "casing", north="front"),
        box("west_ritual_bank", [0, 2, 4], [4, 9, 16], "casing", west="side", south="back"),
        box("east_ritual_bank", [12, 2, 4], [16, 9, 16], "casing", east="side", south="back"),
        box("rear_ritual_bank", [4, 2, 12], [12, 9, 16], "casing", south="back"),
        box("sunken_ritual_core", [4, 2, 4], [12, 5, 12], "trim", up="binding"),
        box("lower_ritual_dais", [3, 9, 3], [13, 10, 13], "otherstone", up="chalk"),
        box("upper_ritual_dais", [4, 10, 4], [12, 11, 12], "gold", up="binding"),
        box("north_chalk_lane", [4, 11, 4], [12, 11.5, 5], "chalk"),
        box("south_chalk_lane", [4, 11, 11], [12, 11.5, 12], "chalk"),
        box("west_chalk_lane", [4, 11, 5], [5, 11.5, 11], "chalk"),
        box("east_chalk_lane", [11, 11, 5], [12, 11.5, 11], "chalk"),
        box("west_spirit_obelisk", [2, 10, 4], [4, 15, 7], "otherstone", up="soul"),
        box("east_spirit_obelisk", [12, 10, 9], [14, 15, 12], "otherstone", up="soul"),
        box("ritual_focus_mount", [6, 11, 6], [10, 13, 10], "gold", up="binding"),
        box("spirit_bridge", [4, 13, 6], [12, 14, 10], "binding"),
        box("ritual_focus", [7, 14, 7], [9, 16, 9], "spirit", up="soul"),
    ]


def mini_ritual_assembler():
    return [
        box("assembler_floor", [0, 0, 0], [16, 2, 16], "casing", down="bottom", up="trim"),
        box("rear_pattern_cabinet", [1, 2, 8], [15, 10, 16], "casing", south="back"),
        box("front_material_drawer", [2, 2, 0], [14, 7, 8], "casing", north="front"),
        box("scribing_table", [2, 10, 3], [14, 11, 14], "otherstone", up="chalk"),
        box("west_ink_rack", [0, 7, 2], [2, 13, 8], "trim", west="side"),
        box("east_ink_rack", [14, 7, 2], [16, 13, 8], "trim", east="side"),
        box("north_scribing_track", [3, 11, 3], [13, 12, 5], "gold"),
        box("south_scribing_track", [3, 11, 12], [13, 12, 14], "gold"),
        box("west_plotter_rail", [2, 13, 4], [4, 15, 12], "trim", up="otherstone"),
        box("east_plotter_rail", [12, 13, 4], [14, 15, 12], "trim", up="otherstone"),
        box("plotter_crossbeam", [4, 14, 6], [12, 15, 10], "binding"),
        box("scribing_head", [7, 12, 6], [9, 14, 10], "otherstone", down="spirit", north="gold"),
        box("plotter_head_cap", [6, 15, 5], [10, 16, 11], "gold", up="soul"),
        box("white_chalk_well", [3, 12, 3], [5, 13, 5], "chalk"),
        box("red_chalk_well", [11, 12, 3], [13, 13, 5], "accent"),
        box("purple_chalk_well", [3, 12, 11], [5, 13, 13], "accent_b"),
        box("gold_chalk_well", [11, 12, 11], [13, 13, 13], "soul"),
    ]


def imbuement_factory_base():
    return {
        "credit": "Mekanism Magic original Source Manifold Factory chassis",
        "render_type": "minecraft:cutout",
        "textures": {
            "particle": "mekanism_magic:block/imbuement_factory/front",
            "front": "mekanism_magic:block/imbuement_factory/front",
            "top": "mekanism:block/factory/infusing/infusing_factory_top",
            "side": "mekanism:block/factory/infusing/infusing_factory_side",
            "back": "mekanism:block/factory/infusing/infusing_factory_back",
            "bottom": "mekanism:block/factory/infusing/infusing_factory_bottom",
            "ports": "mekanism:block/models/ports",
            "ports_led": "mekanism:block/models/ports_led",
            "back_panel": "mekanism:block/factory/factory_front_back",
        },
        "elements": [
            box("factory_floor", [0, 0, 0], [16, 3, 16], "side", north="front", south="back", up="top", down="bottom"),
            box("factory_front_crown", [0, 3, 0], [16, 16, 3], "side", north="front", south="back_panel", up="top", down="bottom"),
            box("west_drive_bank", [0, 3, 3], [3, 13, 16], "side", south="back", up="top"),
            box("east_drive_bank", [13, 3, 3], [16, 13, 16], "side", south="back", up="top"),
            box("rear_drive_bank", [3, 3, 13], [13, 13, 16], "back", up="top"),
            box("central_process_floor", [3, 3, 3], [13, 5, 13], "bottom", up="top"),
            box("west_roof_cartridge", [0, 13, 3], [5, 16, 16], "side", up="top", south="back"),
            box("east_roof_cartridge", [11, 13, 3], [16, 16, 16], "side", up="top", south="back"),
            box("rear_roof_bridge", [5, 13, 13], [11, 16, 16], "back", up="top"),
            panel("factory_output_port", [4, 5, 16], [12, 12, 16], "south", "ports"),
            panel("factory_output_port_led", [4, 5, 16], [12, 12, 16], "south", "ports_led", emissive=True),
        ],
    }


def imbuement_factory_manifold():
    return {
        "credit": "Mekanism Magic original multi-lane Source Manifold",
        "render_type": "minecraft:cutout",
        "textures": {
            "particle": "mekanism_magic:block/source_generator/sourcestone",
            "rail": "mekanism_magic:block/source_generator/sourcestone",
            "basin": "mekanism_magic:block/imbuement_processor/basin",
            "channel": "mekanism_magic:block/imbuement_processor/conduit",
            "rune": "mekanism_magic:block/imbuement_processor/rune",
            "core": "mekanism_magic:block/imbuement_processor/core",
        },
        "elements": [
            box("west_manifold_support", [5, 5, 5], [6, 10, 12], "rail"),
            box("east_manifold_support", [10, 5, 5], [11, 10, 12], "rail"),
            box("factory_infuser_crucible", [5, 10, 4], [11, 11, 13], "basin"),
            box("left_production_prong", [5, 11, 4], [6, 13, 6], "channel"),
            box("center_production_prong", [7.5, 11, 4], [8.5, 13, 6], "channel"),
            box("right_production_prong", [10, 11, 4], [11, 13, 6], "channel"),
            box("north_factory_channel", [7, 11, 6], [9, 11.4, 8], "channel"),
            box("south_factory_channel", [7, 11, 10], [9, 11.4, 12], "channel"),
            box("west_factory_channel", [5, 11, 8], [7, 11.4, 10], "channel"),
            box("east_factory_channel", [9, 11, 8], [11, 11.4, 10], "channel"),
            box("factory_core_mount", [6, 11.4, 7], [10, 12.5, 11], "rail", up="rune"),
            box("north_factory_rune", [6, 14, 5], [10, 15, 6], "rune"),
            box("south_factory_rune", [6, 14, 11], [10, 15, 12], "rune"),
            box("west_factory_rune", [5, 14, 6], [6, 15, 11], "rune"),
            box("east_factory_rune", [10, 14, 6], [11, 15, 11], "rune"),
            box("factory_source_heart", [7, 12.5, 8], [9, 16, 10], "core"),
        ],
    }


write(BLOCK_MODELS / "source_generator_base.json", {
    "credit": "Mekanism Magic v3 Precision Source Containment Reactor",
    "ambientocclusion": True,
    "gui_light": "side",
    "render_type": "minecraft:cutout",
    "textures": {
        "particle": "mekanism_magic:block/source_generator/sourcestone",
        "casing": "mekanism_magic:block/source_generator/casing",
        "trim": "mekanism_magic:block/source_generator/trim",
        "front": "mekanism_magic:block/source_generator/front_panel",
        "left_panel": "mekanism_magic:block/source_generator/left_panel",
        "right_panel": "mekanism_magic:block/source_generator/right_panel",
        "back": "mekanism_magic:block/source_generator/back_panel",
        "top": "mekanism_magic:block/source_generator/top_panel",
        "bottom": "mekanism_magic:block/source_generator/bottom_panel",
        "coil": "mekanism_magic:block/source_generator/coil",
        "conduit": "mekanism_magic:block/source_generator/conduit",
        "core": "mekanism_magic:block/source_generator/core",
        "sourcestone": "mekanism_magic:block/source_generator/sourcestone",
        "rune": "mekanism_magic:block/source_generator/rune",
    },
    "elements": source_generator(),
})

for name, credit, builder in (
    ("source_converter_base", "Mekanism Magic v2 Asymmetric Source Converter", source_converter),
    ("catalyst_identifier_assembler_base", "Mekanism Magic v2 Catalyst Rotary Analyzer", catalyst_identifier),
    ("imbuement_processor_base", "Mekanism Magic v2 Source Manifold Infuser", imbuement_processor),
    ("enchanting_apparatus_processor_base", "Mekanism Magic v2 Arcane Library Enchanter", enchanting_processor),
    ("drygmy_simulator_base", "Mekanism Magic v2 Drygmy Habitat Module", drygmy_simulator),
    ("spirit_processor_base", "Mekanism Magic v2 Binding Rotor Processor", spirit_processor),
    ("dimension_miner_base", "Mekanism Magic v2 Vertical Aperture Miner", dimension_miner),
    ("ritual_engine_base", "Mekanism Magic v2 Asymmetric Ritual Engine", ritual_engine),
    ("mini_ritual_assembler_base", "Mekanism Magic v2 Ritual Plotter", mini_ritual_assembler),
):
    replace_elements(name, credit, builder())

write(BLOCK_MODELS / "imbuement_factory_base.json", imbuement_factory_base())
write(BLOCK_MODELS / "imbuement_factory_top_relief.json", imbuement_factory_manifold())
# Spirit Processor/Factory v3 are owned by rebuild_spirit_machine_v3.py.
# Do not emit the retired top-mounted LED chassis from this v2 generator: doing
# so after a v3 rebuild would silently restore the old floating tier lamp.

spirit_factory_idle_textures = {
    "particle": "mekanism_magic:block/spirit_factory/front",
    "front": "mekanism_magic:block/spirit_factory/front",
    "top": "mekanism_magic:block/spirit_factory/top",
    "back_panel": "mekanism_magic:block/spirit_factory/back",
    "side": "mekanism_magic:block/spirit_factory/side",
    "bottom": "mekanism_magic:block/spirit_factory/bottom",
}
# The active chassis remains static. Work motion comes from the dedicated
# renderer, which avoids rapid atlas-frame flashing across factory rows.
spirit_factory_active_textures = spirit_factory_idle_textures

for tier in ARS_TIERS:
    for active in (False, True):
        suffix = "_active" if active else ""
        path = BLOCK_MODELS / f"{tier}_imbuement_factory{suffix}.json"
        model = read(path)
        model["children"]["base"]["parent"] = "mekanism_magic:block/imbuement_factory_base"
        write(path, model)

    idle_path = BLOCK_MODELS / f"{tier}_spirit_factory.json"
    idle = read(idle_path)
    idle["children"].pop("opaque_core", None)
    idle["textures"]["particle"] = spirit_factory_idle_textures["particle"]
    idle["children"]["base"]["parent"] = "mekanism_magic:block/spirit_factory/base"
    idle["children"]["base"]["textures"] = spirit_factory_idle_textures
    idle["children"]["binding_rig"] = {
        "parent": "mekanism_magic:block/spirit_factory/binding_rig"
    }
    write(idle_path, idle)

    active_path = BLOCK_MODELS / f"spirit_factory/active/{tier}.json"
    active = read(active_path)
    active["children"].pop("opaque_core", None)
    active["textures"]["particle"] = spirit_factory_active_textures["particle"]
    active["children"]["base"]["parent"] = "mekanism_magic:block/spirit_factory/base_active"
    active["children"]["base"]["textures"] = spirit_factory_active_textures
    active["children"]["binding_rig"] = {
        "parent": "mekanism_magic:block/spirit_factory/binding_rig_active"
    }
    write(active_path, active)

print("Generated v2 machine geometry; Spirit Factory v3 geometry remains owned by its dedicated rebuild tool.")
