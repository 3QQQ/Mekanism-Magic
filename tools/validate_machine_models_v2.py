import json
import math
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSET_ROOT = ROOT / "src/main/resources/assets/mekanism_magic"
BLOCK_MODELS = ASSET_ROOT / "models/block"

ORDINARY = {
    "source_generator_base": {
        "generator_base_body", "generator_source_column_inner",
        "generator_crown_core",
    },
    "source_converter_base": {
        "converter_base_body", "converter_transmutation_prism",
        "converter_overhead_bus",
    },
    "catalyst_identifier_assembler_base": {
        "identifier_base_body", "identifier_carousel_diamond",
        "identifier_crystal_gold_apex",
    },
    "imbuement_processor_base": {
        "lab_base_monocoque", "true_arcane_magic_circle",
        "overhead_injector_head",
    },
    "enchanting_apparatus_processor_base": {
        "enchanter_base_body", "enchanter_book_spine",
        "enchanter_hover_core",
    },
    "drygmy_simulator_base": {
        "simulator_base_body", "simulator_habitat_scan_plate",
        "simulator_front_left_pillar",
    },
    "spirit_processor_base": {
        "processor_lower_chassis", "bound_spirit_prism",
        "work_cradle",
    },
    "dimension_miner_base": {
        "horizontal_dimensional_aperture", "phase_extraction_nozzle",
        "ore_output_chute",
    },
    "ritual_engine_base": {
        "projected_ritual_surface", "northwest_spirit_projector",
        "ritual_focus_base",
    },
    "mini_ritual_assembler_base": {
        "plotter_crossbeam", "scribing_head",
        "purple_chalk_cartridge",
    },
}

TIERS = (
    "basic", "advanced", "elite", "ultimate",
    "absolute", "supreme", "cosmic", "infinite",
)

FACTORY_LED_KEYS = {
    "basic": "8",
    "advanced": "8",
    "elite": "8",
    "ultimate": "8",
    "absolute": "9",
    "supreme": "9",
    "cosmic": "10",
    "infinite": "11",
}

IMPORTED_ARS = {
    "source_generator_base",
    "source_converter_base",
    "catalyst_identifier_assembler_base",
    "imbuement_processor_base",
    "enchanting_apparatus_processor_base",
    "drygmy_simulator_base",
}

ALLOWED_ROTATIONS = {-45, -22.5, 22.5, 45}


def read(relative):
    return json.loads((BLOCK_MODELS / relative).read_text(encoding="utf-8"))


def positive_overlap(first, second):
    return [
        min(first["to"][axis], second["to"][axis])
        - max(first["from"][axis], second["from"][axis])
        for axis in range(3)
    ]


def rotated_corners(element):
    rotation = element.get("rotation")
    corners = [
        [x, y, z]
        for x in (element["from"][0], element["to"][0])
        for y in (element["from"][1], element["to"][1])
        for z in (element["from"][2], element["to"][2])
    ]
    if rotation is None:
        return corners
    angle = rotation["angle"]
    assert angle in ALLOWED_ROTATIONS, (element["name"], angle)
    radians = math.radians(angle)
    sine = math.sin(radians)
    cosine = math.cos(radians)
    origin = rotation["origin"]
    axis = rotation["axis"]
    rotated = []
    for corner in corners:
        local = [corner[index] - origin[index] for index in range(3)]
        if axis == "x":
            value = [
                local[0],
                local[1] * cosine - local[2] * sine,
                local[1] * sine + local[2] * cosine,
            ]
        elif axis == "y":
            value = [
                local[0] * cosine + local[2] * sine,
                local[1],
                -local[0] * sine + local[2] * cosine,
            ]
        else:
            assert axis == "z", (element["name"], axis)
            value = [
                local[0] * cosine - local[1] * sine,
                local[0] * sine + local[1] * cosine,
                local[2],
            ]
        rotated.append([
            value[index] + origin[index] for index in range(3)
        ])
    return rotated


def validate_elements(elements, label, check_internal=True):
    assert elements, f"{label}: no elements"
    names = [element["name"] for element in elements]
    assert len(names) == len(set(names)), f"{label}: duplicate element names"
    for element in elements:
        assert all(0 <= value <= 16 for value in element["from"]), (label, element["name"], element["from"])
        assert all(0 <= value <= 16 for value in element["to"]), (label, element["name"], element["to"])
        assert all(
            element["from"][axis] <= element["to"][axis]
            for axis in range(3)
        ), (label, element["name"])
        for corner in rotated_corners(element):
            assert all(-0.001 <= value <= 16.001 for value in corner), (
                label, element["name"], corner
            )
    if check_internal:
        for index, first in enumerate(elements):
            for second in elements[index + 1:]:
                overlap = positive_overlap(first, second)
                assert min(overlap) <= 0, (label, first["name"], second["name"], overlap)


def validate_textures(model, label):
    available = set(model.get("textures", {}))
    referenced = {
        face["texture"].removeprefix("#")
        for element in model["elements"]
        for face in element.get("faces", {}).values()
        if face.get("texture", "").startswith("#")
    }
    assert referenced <= available, (label, referenced - available)


def validate_factory_uvs(model, label, preserved_texture):
    for element in model["elements"]:
        for face_name, face in element.get("faces", {}).items():
            uv = face.get("uv")
            assert uv is not None and len(uv) == 4, (
                label, element["name"], face_name, uv
            )
            assert all(0 <= value <= 16 for value in uv), (
                label, element["name"], face_name, uv
            )
            texture_key = face.get("texture", "").removeprefix("#")
            if texture_key != preserved_texture:
                assert set((uv[0], uv[2])) == {0, 16} and set(
                    (uv[1], uv[3])
                ) == {0, 16}, (
                    label, element["name"], face_name, uv
                )


def validate_cross(first_group, second_group, label):
    for first in first_group:
        for second in second_group:
            overlap = positive_overlap(first, second)
            assert min(overlap) <= 0, (label, first["name"], second["name"], overlap)


def validate_surface_separation(elements, label, minimum=0.25):
    face_axes = {
        "west": (0, 0, -1),
        "east": (0, 1, 1),
        "down": (1, 0, -1),
        "up": (1, 1, 1),
        "north": (2, 0, -1),
        "south": (2, 1, 1),
    }
    faces = []
    for element in elements:
        if element.get("rotation") is not None:
            continue
        for face_name in element.get("faces", {}):
            axis, endpoint, normal = face_axes[face_name]
            other_axes = [value for value in range(3) if value != axis]
            faces.append({
                "name": element["name"],
                "axis": axis,
                "plane": element[("from", "to")[endpoint]][axis],
                "normal": normal,
                "u": (
                    element["from"][other_axes[0]],
                    element["to"][other_axes[0]],
                ),
                "v": (
                    element["from"][other_axes[1]],
                    element["to"][other_axes[1]],
                ),
            })
    risks = []
    for index, first in enumerate(faces):
        for second in faces[index + 1:]:
            if (
                first["name"] == second["name"]
                or first["axis"] != second["axis"]
                or first["normal"] != second["normal"]
            ):
                continue
            distance = abs(first["plane"] - second["plane"])
            if distance >= minimum:
                continue
            overlap_u = min(first["u"][1], second["u"][1]) - max(
                first["u"][0], second["u"][0]
            )
            overlap_v = min(first["v"][1], second["v"][1]) - max(
                first["v"][0], second["v"][0]
            )
            if overlap_u > 0 and overlap_v > 0:
                risks.append((
                    first["name"], second["name"], distance,
                    overlap_u * overlap_v,
                ))
    assert not risks, (label, risks)


def validate_rotated_surface_separation(elements, label, minimum=0.25):
    face_corners = {
        "west": (0, 1, 3, 2),
        "east": (4, 6, 7, 5),
        "down": (0, 4, 5, 1),
        "up": (2, 3, 7, 6),
        "north": (0, 2, 6, 4),
        "south": (1, 5, 7, 3),
    }

    def subtract(first, second):
        return [first[index] - second[index] for index in range(3)]

    def dot(first, second):
        return sum(first[index] * second[index] for index in range(3))

    def cross(first, second):
        return [
            first[1] * second[2] - first[2] * second[1],
            first[2] * second[0] - first[0] * second[2],
            first[0] * second[1] - first[1] * second[0],
        ]

    def normalize(vector):
        length = math.sqrt(dot(vector, vector))
        return [value / length for value in vector]

    def polygons_overlap(first, second, normal):
        for polygon in (first, second):
            for index in range(4):
                edge = subtract(polygon[(index + 1) % 4], polygon[index])
                axis = cross(normal, edge)
                if dot(axis, axis) < 1e-12:
                    continue
                axis = normalize(axis)
                first_values = [dot(point, axis) for point in first]
                second_values = [dot(point, axis) for point in second]
                overlap = min(max(first_values), max(second_values)) - max(
                    min(first_values), min(second_values)
                )
                if overlap <= 1e-5:
                    return False
        return True

    faces = []
    for element in elements:
        corners = rotated_corners(element)
        for face_name in element.get("faces", {}):
            polygon = [corners[index] for index in face_corners[face_name]]
            normal = normalize(cross(
                subtract(polygon[1], polygon[0]),
                subtract(polygon[2], polygon[0]),
            ))
            faces.append((element["name"], face_name, polygon, normal))

    risks = []
    for index, first in enumerate(faces):
        for second in faces[index + 1:]:
            if first[0] == second[0] or dot(first[3], second[3]) < 0.99999:
                continue
            distance = abs(dot(subtract(second[2][0], first[2][0]), first[3]))
            if (
                distance < minimum
                and polygons_overlap(first[2], second[2], first[3])
            ):
                risks.append((
                    first[0], first[1], second[0], second[1], distance,
                ))
    assert not risks, (label, risks)


signatures = set()
for model_name, required_names in ORDINARY.items():
    model = read(f"{model_name}.json")
    elements = model["elements"]
    validate_elements(
        elements,
        model_name,
        check_internal=model_name not in IMPORTED_ARS,
    )
    validate_textures(model, model_name)
    names = {element["name"] for element in elements}
    assert required_names <= names, (model_name, required_names - names)
    maximum_y = max(
        corner[1]
        for element in elements
        for corner in rotated_corners(element)
    )
    if model_name == "drygmy_simulator_base":
        assert not any(name.startswith("drygmy_entity_") for name in names), (
            "drygmy_simulator_base: animated Drygmy display must not have "
            "a static model placeholder"
        )
        assert "simulator_drygmy_henge" not in names, (
            "drygmy_simulator_base: original stone pedestal must not overlap "
            "the simulator scan base"
        )
        validate_rotated_surface_separation(
            elements, "drygmy_simulator_base depth layers"
        )
        assert 13 <= maximum_y <= 16, model_name
    elif model_name == "spirit_processor_base":
        # The v3 processor deliberately uses a lower, compact containment
        # crown instead of the old near-full-height gantry.
        assert 11.5 <= maximum_y <= 15, (model_name, maximum_y)
        validate_rotated_surface_separation(
            elements, "spirit_processor_base depth layers"
        )
    elif model_name in IMPORTED_ARS:
        assert 9 <= maximum_y <= 16, (model_name, maximum_y)
    else:
        assert 9 <= maximum_y <= 16, (model_name, maximum_y)
    signature = tuple(
        (tuple(element["from"]), tuple(element["to"]))
        for element in elements
    )
    assert signature not in signatures, f"{model_name}: geometry duplicates another machine"
    signatures.add(signature)

spirit_factory = read("spirit_factory/base.json")
spirit_chamber = read("spirit_factory/binding_rig.json")
validate_elements(spirit_factory["elements"], "spirit_factory/base")
validate_elements(spirit_chamber["elements"], "spirit_factory/binding_rig")
validate_textures(spirit_factory, "spirit_factory/base")
validate_textures(spirit_chamber, "spirit_factory/binding_rig")
validate_cross(spirit_factory["elements"], spirit_chamber["elements"], "Spirit factory composite")
validate_surface_separation(
    spirit_factory["elements"] + spirit_chamber["elements"],
    "Spirit factory depth layers",
)
assert {"factory_bound_spirit", "manifold_crown", "rear_distribution_bus"} <= {
    element["name"] for element in spirit_chamber["elements"]
}
embedded_front_led = {
    "name": "embedded_original_factory_tier_indicator",
    "from": [4.98, 2.9, 0.01],
    "to": [11.02, 3.5, 0.12],
}
for element in spirit_factory["elements"]:
    overlap = positive_overlap(element, embedded_front_led)
    assert min(overlap) <= 0, (
        "Spirit factory chassis occludes embedded tier indicator",
        element["name"],
        overlap,
    )
assert {
    "factory_lower_chassis",
    "tier_indicator_backplate",
    "tier_indicator_west_bezel",
    "tier_indicator_east_bezel",
    "tier_indicator_lower_bezel",
    "tier_indicator_upper_bezel",
    "west_binding_plate",
    "center_binding_plate",
    "east_binding_plate",
} <= {element["name"] for element in spirit_factory["elements"]}

# Long chassis beams must never receive an entire icon-bearing texture.  That
# stretches a small center glyph across the front and recreates the malformed
# Spirit Processor base seen in-game.
spirit_processor = read("spirit_processor_base.json")
processor_elements = {
    element["name"]: element for element in spirit_processor["elements"]
}
expected_quiet_uvs = {
    "processor_foot": [7, 7, 9, 9],
    "processor_lower_chassis": [7, 7, 9, 9],
    "processor_front_recess": [9, 9, 11, 11],
    "processor_front_soul_line": [7, 8, 8, 9],
    "processor_front_binding_line": [7, 6, 8, 7],
    "processor_deck": [9, 9, 11, 11],
}
for element_name, expected_uv in expected_quiet_uvs.items():
    assert all(
        face["uv"] == expected_uv
        for face in processor_elements[element_name]["faces"].values()
    ), (element_name, "stretched decorative texture returned")

spirit_factory_idle_textures = {
    "particle": "mekanism_magic:block/spirit_factory/front",
    "front": "mekanism_magic:block/spirit_factory/front",
    "top": "mekanism_magic:block/spirit_factory/top",
    "back_panel": "mekanism_magic:block/spirit_factory/back",
    "side": "mekanism_magic:block/spirit_factory/side",
    "bottom": "mekanism_magic:block/spirit_factory/bottom",
}
# Factory motion is supplied by the block-entity renderer. Keeping the chassis
# on static textures prevents animated atlas frames from flashing across a row
# of factories while preserving the original tier LED state.
spirit_factory_active_textures = spirit_factory_idle_textures

spirit_factory_active_base = read("spirit_factory/base_active.json")
assert spirit_factory_active_base["textures"]["front"] == (
    spirit_factory_idle_textures["front"]
)
spirit_factory_active_rig = read("spirit_factory/binding_rig_active.json")
assert all(
    "_active" not in texture
    for texture in spirit_factory_active_rig["textures"].values()
), "Spirit factory active rig must not use animated atlas textures"

for tier_index, tier in enumerate(TIERS):
    namespace = "mekanism" if tier_index < 4 else "mekanism_extras"
    for active in (False, True):
        active_path = "active/" if active else ""
        suffix = "_active" if active else ""

        ars_label = f"{tier}_imbuement_factory{suffix}"
        ars = read(f"{ars_label}.json")
        validate_elements(
            ars["elements"], ars_label, check_internal=False
        )
        validate_textures(ars, ars_label)
        validate_factory_uvs(ars, ars_label, FACTORY_LED_KEYS[tier])
        validate_surface_separation(
            ars["elements"], f"{ars_label} depth layers"
        )
        validate_rotated_surface_separation(
            ars["elements"], f"{ars_label} rotated depth layers"
        )
        ars_elements = {
            element["name"]: element for element in ars["elements"]
        }
        tier_led = ars_elements[f"factory_tier_led_{tier}"]
        assert {
            "factory_base_body", "factory_top_relief_ridge",
            "factory_master_rune", f"factory_tier_led_{tier}",
        } <= set(ars_elements)
        if active:
            assert tier_led.get("neoforge_data") == {
                "block_light": 15, "sky_light": 15,
            }
        else:
            assert "neoforge_data" not in tier_led

        expected_led_texture = (
            f"{namespace}:block/factory/led"
            if tier not in {"cosmic", "infinite"}
            else f"mekanism_extras:block/factory/{tier}_led"
        )
        led_keys = {
            face["texture"].removeprefix("#")
            for face in tier_led["faces"].values()
            if face.get("texture", "").startswith("#")
            and face["texture"] != "#1"
        }
        assert len(led_keys) == 1, (ars_label, led_keys)
        led_key = led_keys.pop()
        assert ars["textures"][led_key] == expected_led_texture, (
            ars_label, ars["textures"][led_key]
        )

        spirit_path = f"spirit_factory/active/{tier}.json" if active else f"{tier}_spirit_factory.json"
        spirit = read(spirit_path)
        assert "opaque_core" not in spirit["children"]
        expected_spirit_textures = (
            spirit_factory_active_textures if active else spirit_factory_idle_textures
        )
        assert spirit["textures"]["particle"] == expected_spirit_textures["particle"]
        assert spirit["children"]["base"]["textures"] == expected_spirit_textures
        expected_indicator_parent = (
            "mekanism_magic:block/spirit_factory/front_led/"
            f"{active_path}{tier}"
        )
        assert spirit["children"]["front_led"]["parent"] == expected_indicator_parent
        indicator_path = f"spirit_factory/front_led/{active_path}{tier}.json"
        indicator = read(indicator_path)
        validate_elements(indicator["elements"], indicator_path)
        validate_textures(indicator, indicator_path)
        assert indicator["textures"]["led"] == expected_led_texture
        indicator_element = indicator["elements"][0]
        assert indicator_element["from"] == embedded_front_led["from"]
        assert indicator_element["to"] == embedded_front_led["to"]
        assert indicator_element["faces"]["north"]["uv"] == [
            0, tier_index % 4, 6, tier_index % 4 + 1,
        ]
        if active:
            assert indicator_element.get("neoforge_data") == {
                "block_light": 15, "sky_light": 15,
            }
        else:
            assert "neoforge_data" not in indicator_element
        assert spirit["children"]["binding_rig"]["parent"] == (
            "mekanism_magic:block/spirit_factory/binding_rig_active"
            if active else "mekanism_magic:block/spirit_factory/binding_rig"
        )

print("Validated all ten ordinary-machine geometries inside one block, including rotations.")
print("Validated eight Blockbench Ars factory tiers and the Occultism factory chassis.")
print("Validated all sixteen factory states against original Mekanism/Mekanism Extras LED colours.")
print("Validated factory decorative surfaces against near-coplanar depth fighting.")
print("Validated neutral Spirit Factory chassis textures so tier identity comes from the same LED layer as Ars.")
print("Validated an unobstructed lower-fascia slot for the Spirit Factory tier indicator.")
