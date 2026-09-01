import base64
import copy
import json
import shutil
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BLOCKBENCH = ROOT / "assets/blockbench"
MODEL_ROOT = (
    ROOT / "src/main/resources/assets/mekanism_magic/models/block"
)
TEXTURE_ROOT = (
    ROOT / "src/main/resources/assets/mekanism_magic/textures/block"
)
COMMON_TEXTURE_TARGET = TEXTURE_ROOT / "ars_series"
DRYGMY_TEXTURE_TARGET = TEXTURE_ROOT / "drygmy_simulator/model"

COMMON_FILES = {
    "lab_shell": BLOCKBENCH / "series_textures/lab_shell.png",
    "lab_dark": BLOCKBENCH / "series_textures/lab_dark.png",
    "lab_spine": BLOCKBENCH / "series_textures/lab_spine.png",
    "precision_metal": BLOCKBENCH / "series_textures/precision_metal.png",
    "source_violet": BLOCKBENCH / "series_textures/source_violet.png",
    "source_cyan": BLOCKBENCH / "series_textures/source_cyan.png",
    "arcane_gold": BLOCKBENCH / "series_textures/arcane_gold.png",
    "arcane_focus_circle_crisp": (
        BLOCKBENCH / "arcane_focus_circle_crisp.png"
    ),
}

BASE_TEXTURES = {
    "0": "lab_shell",
    "1": "lab_dark",
    "2": "lab_spine",
    "3": "precision_metal",
    "4": "source_violet",
    "5": "source_cyan",
    "6": "arcane_gold",
    "7": "arcane_focus_circle_crisp",
}

ORDINARY_MODELS = {
    "source_generator": {
        "source": "source_generator_series_a.json",
        "textures": {key: BASE_TEXTURES[key] for key in (
            "0", "1", "3", "4", "5", "6"
        )},
    },
    "source_converter": {
        "source": "source_converter_series_a.json",
        "textures": {key: BASE_TEXTURES[key] for key in (
            "0", "1", "3", "4", "5", "6"
        )},
    },
    "imbuement_processor": {
        "source": "source_lattice_synthesizer_prototype_c.json",
        "textures": {
            "0": "lab_shell",
            "1": "lab_dark",
            "2": "lab_spine",
            "3": "precision_metal",
            "4": "source_violet",
            "5": "source_cyan",
            "7": "arcane_glyph",
            "9": "arcane_gold",
            "12": "arcane_focus_circle_crisp",
        },
    },
    "enchanting_apparatus_processor": {
        "source": "enchanting_apparatus_processor_series_a.json",
        "textures": {key: BASE_TEXTURES[key] for key in (
            "0", "1", "3", "4", "5", "6", "7"
        )},
        "full_uv": {"enchanter_arcane_inlay"},
    },
    "catalyst_identifier_assembler": {
        "source": "catalyst_identifier_assembler_series_a.json",
        "textures": {key: BASE_TEXTURES[key] for key in (
            "0", "1", "3", "4", "5", "6", "7"
        )},
        "full_uv": {"identifier_sigillum_plate"},
    },
    "drygmy_simulator": {
        "source": "drygmy_simulator_series_a.json",
        "textures": {
            **{key: BASE_TEXTURES[key] for key in (
                "0", "1", "3", "4", "5", "6", "7"
            )},
            "8": "mekanism_magic:block/drygmy_simulator/model/simulator_glass",
        },
        "exclude_prefixes": (
            "drygmy_entity_",
            "simulator_drygmy_henge",
        ),
        "full_uv": {"simulator_habitat_scan_plate"},
        "runtime_bounds": {
            "simulator_habitat_scan_plate": {
                "from": {1: 5.65}, "to": {1: 6.05},
            },
        },
    },
}

FACTORY_LEDS = {
    "basic": ("8", "mekanism:block/factory/led"),
    "advanced": ("8", "mekanism:block/factory/led"),
    "elite": ("8", "mekanism:block/factory/led"),
    "ultimate": ("8", "mekanism:block/factory/led"),
    "absolute": ("9", "mekanism_extras:block/factory/led"),
    "supreme": ("9", "mekanism_extras:block/factory/led"),
    "cosmic": ("10", "mekanism_extras:block/factory/cosmic_led"),
    "infinite": ("11", "mekanism_extras:block/factory/infinite_led"),
}

ALLOWED_ANGLES = (-45.0, -22.5, 0.0, 22.5, 45.0)

# Minecraft's block-model depth buffer becomes unstable when decorative
# surfaces are separated by only 0.1-0.2 model units. Blockbench keeps the
# authored project untouched; these runtime bounds give the factory overlays
# enough depth separation to avoid shimmering in motion and at a distance.
FACTORY_RUNTIME_BOUNDS = {
    "factory_base_body": {"from": {2: 0.75}},
    "factory_front_recess": {"from": {2: 0.40}, "to": {2: 0.55}},
    "factory_front_status_cyan": {"from": {2: 0.00}, "to": {2: 0.15}},
    "factory_front_status_gold": {"from": {2: 0.00}, "to": {2: 0.15}},
    "factory_front_status_violet": {"from": {2: 0.00}, "to": {2: 0.15}},
    "factory_side_stripe_lower_left": {
        "from": {0: 0.35}, "to": {0: 0.55},
    },
    "factory_side_stripe_upper_left": {
        "from": {0: 0.35}, "to": {0: 0.55},
    },
    "factory_side_stripe_lower_right": {
        "from": {0: 15.45}, "to": {0: 15.65},
    },
    "factory_side_stripe_upper_right": {
        "from": {0: 15.45}, "to": {0: 15.65},
    },
    "factory_lane_1_rune_plate": {
        "from": {1: 6.55}, "to": {1: 6.75},
    },
    "factory_lane_2_rune_plate": {
        "from": {1: 6.55}, "to": {1: 6.75},
    },
    "factory_lane_3_rune_plate": {
        "from": {1: 6.55}, "to": {1: 6.75},
    },
    "factory_lane_1_core": {
        "from": {1: 6.85}, "to": {1: 7.90},
    },
    "factory_lane_2_core": {
        "from": {1: 6.85}, "to": {1: 7.90},
    },
    "factory_lane_3_core": {
        "from": {1: 6.85}, "to": {1: 7.90},
    },
    "factory_lane_1_energy_tip": {
        "from": {1: 7.90}, "to": {1: 8.15},
    },
    "factory_lane_2_energy_tip": {
        "from": {1: 7.90}, "to": {1: 8.15},
    },
    "factory_lane_3_energy_tip": {
        "from": {1: 7.90}, "to": {1: 8.15},
    },
    "factory_rear_bus_channel": {
        "from": {2: 9.20}, "to": {2: 9.45},
    },
    "factory_master_rune": {
        "from": {2: 10.85}, "to": {2: 11.00},
    },
    "factory_rear_crest_bridge": {
        "from": {2: 11.90},
    },
    "factory_ring_top": {
        "from": {0: 7.05}, "to": {0: 8.95},
    },
    "factory_ring_bottom": {
        "from": {0: 7.05}, "to": {0: 8.95},
    },
    "factory_ring_left": {
        "from": {1: 8.30}, "to": {1: 11.30},
    },
    "factory_ring_right": {
        "from": {1: 8.30}, "to": {1: 11.30},
    },
    "factory_front_header_inset": {
        "from": {2: 0.35}, "to": {2: 0.55},
    },
}


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def common_texture(name):
    return f"mekanism_magic:block/ars_series/{name}"


def runtime_texture(name):
    if ":" in name:
        return name
    return common_texture(name)


def full_face_uv(face):
    uv = face.get("uv", [0, 0, 16, 16])
    flip_u = len(uv) == 4 and uv[2] < uv[0]
    flip_v = len(uv) == 4 and uv[3] < uv[1]
    return [
        16 if flip_u else 0,
        16 if flip_v else 0,
        0 if flip_u else 16,
        0 if flip_v else 16,
    ]


def normalize_factory_sources(tier, led_key):
    json_path = BLOCKBENCH / f"imbuement_factory_series_a_{tier}.json"
    source = read_json(json_path)
    for element in source["elements"]:
        for face in element.get("faces", {}).values():
            if face.get("texture") != f"#{led_key}":
                face["uv"] = full_face_uv(face)
    write_json(json_path, source)

    project_path = BLOCKBENCH / f"imbuement_factory_series_a_{tier}.bbmodel"
    project = read_json(project_path)
    for element in project["elements"]:
        for face in element.get("faces", {}).values():
            if str(face.get("texture")) != led_key:
                face["uv"] = full_face_uv(face)
    write_json(project_path, project)
    return source


def clean_elements(
        source, exclude_prefixes=(), full_uv=(), active_led=None,
        runtime_bounds=None, normalize_uv=False, preserve_uv_textures=()):
    elements = []
    adjusted_angles = []
    for source_element in source["elements"]:
        name = source_element["name"]
        if any(name.startswith(prefix) for prefix in exclude_prefixes):
            continue
        element = copy.deepcopy(source_element)
        bounds = (runtime_bounds or {}).get(name, {})
        for endpoint in ("from", "to"):
            for axis, value in bounds.get(endpoint, {}).items():
                element[endpoint][axis] = value
        rotation = element.get("rotation")
        if rotation is not None:
            angle = float(rotation.get("angle", 0))
            if angle == 0:
                element.pop("rotation")
            elif angle not in ALLOWED_ANGLES:
                compatible = min(
                    ALLOWED_ANGLES,
                    key=lambda candidate: abs(candidate - angle),
                )
                rotation["angle"] = compatible
                adjusted_angles.append((name, angle, compatible))
        if name in full_uv:
            for face in element.get("faces", {}).values():
                face["uv"] = [0, 0, 16, 16]
        for face in element.get("faces", {}).values():
            if face.get("texture") == "#missing":
                face["texture"] = "#0"
            texture_key = face.get("texture", "").removeprefix("#")
            if normalize_uv and texture_key not in preserve_uv_textures:
                # Blockbench's automatic UV export collapses many sub-pixel
                # factory faces to zero-width/zero-height rectangles and lets
                # others run past the 16x16 Java-block UV domain. The editor
                # can still preview those faces, but Minecraft samples atlas
                # seams and produces the noisy, fragmented result seen in game.
                # Factory chassis textures are reusable material swatches, so
                # a full-face projection is stable and matches their purpose.
                face["uv"] = full_face_uv(face)
        if active_led == name:
            element["neoforge_data"] = {
                "block_light": 15,
                "sky_light": 15,
            }
        elements.append(element)
    return elements, adjusted_angles


def model_json(credit, elements, textures):
    resolved = {
        key: runtime_texture(value)
        for key, value in textures.items()
    }
    resolved["particle"] = resolved["0"]
    return {
        "credit": credit,
        "ambientocclusion": True,
        "gui_light": "side",
        "render_type": "minecraft:cutout",
        "textures": resolved,
        "elements": elements,
    }


def export_ordinary_models():
    counts = {}
    adjustments = []
    for machine, config in ORDINARY_MODELS.items():
        source = read_json(BLOCKBENCH / config["source"])
        elements, model_adjustments = clean_elements(
            source,
            config.get("exclude_prefixes", ()),
            config.get("full_uv", ()),
            runtime_bounds=config.get("runtime_bounds"),
        )
        write_json(
            MODEL_ROOT / f"{machine}_base.json",
            model_json(
                f"Mekanism Magic {machine} Blockbench model",
                elements,
                config["textures"],
            ),
        )
        parent = {"parent": f"mekanism_magic:block/{machine}_base"}
        write_json(MODEL_ROOT / f"{machine}.json", parent)
        write_json(MODEL_ROOT / f"{machine}_active.json", parent)
        counts[machine] = len(elements)
        adjustments.extend(
            (machine, name, before, after)
            for name, before, after in model_adjustments
        )
    return counts, adjustments


def export_factories():
    counts = {}
    adjustments = []
    for tier, (led_key, led_texture) in FACTORY_LEDS.items():
        source = normalize_factory_sources(tier, led_key)
        textures = {
            **{key: BASE_TEXTURES[key] for key in (
                "0", "1", "2", "3", "4", "5", "6", "7"
            )},
            led_key: led_texture,
        }
        led_name = f"factory_tier_led_{tier}"
        idle_elements, idle_adjustments = clean_elements(
            source,
            runtime_bounds=FACTORY_RUNTIME_BOUNDS,
            normalize_uv=True,
            preserve_uv_textures=(led_key,),
        )
        active_elements, _ = clean_elements(
            source,
            active_led=led_name,
            runtime_bounds=FACTORY_RUNTIME_BOUNDS,
            normalize_uv=True,
            preserve_uv_textures=(led_key,),
        )
        write_json(
            MODEL_ROOT / f"{tier}_imbuement_factory.json",
            model_json(
                f"Mekanism Magic {tier} Imbuement Factory Series A",
                idle_elements,
                textures,
            ),
        )
        write_json(
            MODEL_ROOT / f"{tier}_imbuement_factory_active.json",
            model_json(
                f"Mekanism Magic active {tier} Imbuement Factory Series A",
                active_elements,
                textures,
            ),
        )
        counts[tier] = len(idle_elements)
        adjustments.extend(
            (tier, name, before, after)
            for name, before, after in idle_adjustments
        )
    return counts, adjustments


def export_textures():
    COMMON_TEXTURE_TARGET.mkdir(parents=True, exist_ok=True)
    for name, source in COMMON_FILES.items():
        shutil.copyfile(source, COMMON_TEXTURE_TARGET / f"{name}.png")

    lattice = read_json(
        BLOCKBENCH / "source_lattice_synthesizer_prototype_c.bbmodel"
    )
    glyph = next(
        texture
        for texture in lattice["textures"]
        if texture["name"] == "arcane_glyph"
    )
    write_embedded_png(glyph, COMMON_TEXTURE_TARGET / "arcane_glyph.png")

    drygmy = read_json(BLOCKBENCH / "drygmy_simulator_series_a.bbmodel")
    glass = next(
        texture
        for texture in drygmy["textures"]
        if texture["name"] == "simulator_glass.png"
    )
    DRYGMY_TEXTURE_TARGET.mkdir(parents=True, exist_ok=True)
    write_embedded_png(
        glass, DRYGMY_TEXTURE_TARGET / "simulator_glass.png"
    )


def write_embedded_png(texture, target):
    prefix = "data:image/png;base64,"
    encoded = texture["source"]
    if not encoded.startswith(prefix):
        raise ValueError(f"{texture['name']} is not an embedded PNG")
    target.write_bytes(base64.b64decode(encoded.removeprefix(prefix)))


ordinary_counts, ordinary_adjustments = export_ordinary_models()
factory_counts, factory_adjustments = export_factories()
export_textures()

print(
    "Synced ordinary Ars models: "
    + ", ".join(
        f"{machine}={count}" for machine, count in ordinary_counts.items()
    )
)
print(
    "Synced Imbuement Factory tiers: "
    + ", ".join(
        f"{tier}={count}" for tier, count in factory_counts.items()
    )
)
adjustments = ordinary_adjustments + factory_adjustments
print(f"Normalized {len(adjustments)} runtime rotations for Minecraft 1.21.1.")
for model, element, before, after in adjustments:
    print(f"  {model}: {element} {before:g} -> {after:g}")
