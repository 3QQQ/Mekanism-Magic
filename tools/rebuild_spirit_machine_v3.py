"""Build the v3 Occultism spirit processor and factory models.

The editable Blockbench projects and the runtime Java block models are
generated from the same cube descriptions so the in-editor model cannot drift
away from what Minecraft loads.
"""

from __future__ import annotations

import copy
import json
import uuid
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BLOCKBENCH = ROOT / "assets" / "blockbench"
MODEL_ROOT = (
    ROOT / "src" / "main" / "resources" / "assets"
    / "mekanism_magic" / "models" / "block"
)
FACES = ("north", "east", "south", "west", "up", "down")
TEXTURES = {
    "lab_dark": "mekanism_magic:block/ars_series/lab_dark",
    "lab_shell": "mekanism_magic:block/ars_series/lab_shell",
    "lab_spine": "mekanism_magic:block/ars_series/lab_spine",
    "precision_metal": "mekanism_magic:block/ars_series/precision_metal",
    "gold": "mekanism_magic:block/occult_machine/gold",
    "binding": "mekanism_magic:block/occult_machine/binding",
    "soul": "mekanism_magic:block/occult_machine/soul",
    "spirit": "mekanism_magic:block/occult_machine/spirit",
    "void": "mekanism_magic:block/occult_machine/void",
}
# Structural cubes must sample a quiet material patch rather than stretching
# a complete 16x16 panel (and its border/icon) over every narrow beam.
STRUCTURAL_UVS = {
    "lab_dark": (7, 7, 9, 9),
    "lab_spine": (9, 9, 11, 11),
    "lab_shell": (7, 7, 9, 9),
    "precision_metal": (7, 7, 9, 9),
}
ACCENT_UVS = {
    "gold": (7, 7, 9, 9),
    "binding": (7, 6, 8, 7),
    "soul": (7, 8, 8, 9),
}


@dataclass(frozen=True)
class Cube:
    name: str
    start: tuple[float, float, float]
    end: tuple[float, float, float]
    texture: str
    group: str
    rotation: tuple[str, float, tuple[float, float, float]] | None = None
    uv: tuple[float, float, float, float] | None = None


def cube(name, start, end, texture, group, rotation=None, uv=None):
    return Cube(
        name, tuple(start), tuple(end), texture, group, rotation,
        tuple(uv) if uv is not None else None,
    )


PROCESSOR = [
    cube("processor_foot", [.5, .5, .5], [15.5, 1.25, 15.5],
         "lab_dark", "processor_chassis"),
    cube("processor_lower_chassis", [1, 1.25, 1], [15, 4.1, 15],
         "lab_dark", "processor_chassis"),
    cube("processor_front_recess", [2.3, 2.05, .55], [13.7, 3.75, 1],
         "lab_spine", "processor_chassis"),
    cube("processor_front_soul_line", [3, 2.4, .2], [13, 2.76, .35],
         "soul", "processor_chassis", uv=ACCENT_UVS["soul"]),
    cube("processor_front_binding_line", [5, 3.08, .2], [11, 3.44, .35],
         "binding", "processor_chassis", uv=ACCENT_UVS["binding"]),
    cube("processor_west_service_recess", [.55, 2.05, 4.6], [1, 3.75, 11.8],
         "precision_metal", "processor_chassis"),
    cube("processor_east_service_recess", [15, 2.05, 4.6], [15.45, 3.75, 11.8],
         "precision_metal", "processor_chassis"),
    cube("processor_deck", [1.5, 4.1, 1.5], [14.5, 5.15, 14.5],
         "lab_spine", "processor_chassis"),
    cube("processor_deck_inset", [3.3, 5.15, 2.2], [12.7, 5.45, 10.25],
         "lab_dark", "work_cradle"),
    cube("work_cradle", [4.2, 5.45, 2.8], [11.8, 5.75, 10],
         "lab_spine", "work_cradle"),
    cube("work_cradle_west_rail", [3.8, 5.45, 2.6], [4.15, 5.88, 9.8],
         "precision_metal", "work_cradle"),
    cube("work_cradle_east_rail", [11.85, 5.45, 2.6], [12.2, 5.88, 9.8],
         "precision_metal", "work_cradle"),
    cube("work_cradle_input", [5.5, 5.75, 2.35], [10.5, 6.08, 2.75],
         "precision_metal", "work_cradle"),
    cube("work_cradle_output", [5.5, 5.75, 9.8], [10.5, 6.08, 10.2],
         "precision_metal", "work_cradle"),
    cube("work_binding_plate", [5.2, 5.75, 5.2], [10.8, 6.05, 8.8],
         "binding", "work_cradle"),
    cube("work_binding_socket", [6.6, 6.05, 6.25], [9.4, 6.3, 7.75],
         "gold", "work_cradle"),
    cube("west_injector_pylon", [2.1, 5.15, 4.8], [3.3, 9.35, 7],
         "lab_spine", "precision_injectors"),
    cube("east_injector_pylon", [12.7, 5.15, 4.8], [13.9, 9.35, 7],
         "lab_spine", "precision_injectors"),
    cube("west_pylon_soul_key", [3.3, 8.1, 5.35], [3.6, 9, 6.6],
         "soul", "precision_injectors"),
    cube("east_pylon_soul_key", [12.4, 8.1, 5.35], [12.7, 9, 6.6],
         "soul", "precision_injectors"),
    cube("west_precision_injector", [3.55, 7.3, 5.2], [5.35, 7.8, 6.45],
         "precision_metal", "precision_injectors",
         ("z", -22.5, (3.55, 7.55, 5.825))),
    cube("east_precision_injector", [10.65, 7.3, 5.2], [12.45, 7.8, 6.45],
         "precision_metal", "precision_injectors",
         ("z", 22.5, (12.45, 7.55, 5.825))),
    cube("west_binding_nozzle", [5, 6.42, 5.55], [5.6, 7.05, 6.15],
         "binding", "precision_injectors"),
    cube("east_binding_nozzle", [10.4, 6.42, 5.55], [11, 7.05, 6.15],
         "binding", "precision_injectors"),
    # Match the larger visual weight of the factory manifold while retaining
    # the processor's single-cell vault, collars, and precision injectors.
    cube("spirit_vault_foundation", [4.76, 5.15, 10.3], [11.24, 6.27, 14],
         "lab_dark", "spirit_vault"),
    cube("spirit_vault_socket", [5.84, 6.27, 11], [10.16, 6.8, 13.4],
         "gold", "spirit_vault"),
    cube("spirit_vault_west_restraint", [4.28, 6.27, 10.7], [5.24, 12.58, 13.8],
         "lab_spine", "spirit_vault"),
    cube("spirit_vault_east_restraint", [10.76, 6.27, 10.7], [11.72, 12.58, 13.8],
         "lab_spine", "spirit_vault"),
    cube("spirit_vault_west_inner_trim", [5.24, 7.33, 11.1], [5.6, 11.99, 13.4],
         "lab_shell", "spirit_vault"),
    cube("spirit_vault_east_inner_trim", [10.4, 7.33, 11.1], [10.76, 11.99, 13.4],
         "lab_shell", "spirit_vault"),
    cube("spirit_vault_crown", [5.24, 12.58, 10.8], [10.76, 13.29, 13.7],
         "lab_spine", "spirit_vault"),
    cube("west_restraint_collar_lower", [3.86, 7.86, 10.45], [5.24, 8.45, 10.7],
         "gold", "spirit_vault"),
    cube("east_restraint_collar_lower", [10.76, 7.86, 10.45], [12.14, 8.45, 10.7],
         "gold", "spirit_vault"),
    cube("west_restraint_collar_upper", [3.86, 10.64, 10.45], [5.24, 11.23, 10.7],
         "binding", "spirit_vault"),
    cube("east_restraint_collar_upper", [10.76, 10.64, 10.45], [12.14, 11.23, 10.7],
         "binding", "spirit_vault"),
    cube("bound_spirit_prism", [7.04, 6.92, 11.55], [8.96, 9.93, 13.05],
         "spirit", "bound_spirit"),
    cube("bound_spirit_focus", [7.4, 9.93, 11.75], [8.6, 11.17, 12.85],
         "soul", "bound_spirit"),
    cube("spirit_binding_bus", [5.6, 10.58, 10.25], [10.4, 10.99, 10.5],
         "binding", "bound_spirit"),
]


FACTORY = [
    cube("factory_foot", [.5, .5, .5], [15.5, 1.25, 15.5],
         "lab_dark", "factory_chassis"),
    cube("factory_lower_chassis", [1, 1.25, 1], [15, 4.1, 15],
         "lab_dark", "factory_chassis"),
    cube("factory_front_recess_west", [2, 2.05, .55], [5.9, 3.8, 1],
         "lab_spine", "factory_chassis"),
    cube("factory_front_recess_center", [6.4, 2.05, .55], [9.6, 3.8, 1],
         "lab_spine", "factory_chassis"),
    cube("factory_front_recess_east", [10.1, 2.05, .55], [14, 3.8, 1],
         "lab_spine", "factory_chassis"),
    # The factory tier indicator is part of the lower control fascia.  Keeping
    # the Mekanism colour strip here makes it readable from the front without
    # leaving a lone lamp floating above the three work lanes.
    cube("tier_indicator_backplate", [4.55, 2.62, .3], [11.45, 3.78, .54],
         "lab_dark", "tier_indicator_dock"),
    cube("tier_indicator_west_bezel", [4.3, 2.62, 0], [4.78, 3.78, .29],
         "gold", "tier_indicator_dock", uv=ACCENT_UVS["gold"]),
    cube("tier_indicator_east_bezel", [11.22, 2.62, 0], [11.7, 3.78, .29],
         "gold", "tier_indicator_dock", uv=ACCENT_UVS["gold"]),
    cube("tier_indicator_lower_bezel", [4.78, 2.62, 0], [11.22, 2.86, .29],
         "lab_shell", "tier_indicator_dock"),
    cube("tier_indicator_upper_bezel", [4.78, 3.54, 0], [11.22, 3.78, .29],
         "lab_shell", "tier_indicator_dock"),
    cube("factory_front_soul_line_west", [2.8, 2.48, .2], [4.3, 2.8, .35],
         "soul", "factory_chassis", uv=ACCENT_UVS["soul"]),
    cube("factory_front_soul_line_east", [11.7, 2.48, .2], [13.2, 2.8, .35],
         "soul", "factory_chassis", uv=ACCENT_UVS["soul"]),
    cube("factory_front_binding_line_west", [3.45, 3.16, .2], [4.3, 3.46, .35],
         "binding", "factory_chassis", uv=ACCENT_UVS["binding"]),
    cube("factory_front_binding_line_east", [11.7, 3.16, .2], [12.55, 3.46, .35],
         "binding", "factory_chassis", uv=ACCENT_UVS["binding"]),
    cube("factory_west_service_recess", [.55, 2.05, 4.6], [1, 3.8, 12.5],
         "lab_spine", "factory_chassis"),
    cube("factory_east_service_recess", [15, 2.05, 4.6], [15.45, 3.8, 12.5],
         "lab_spine", "factory_chassis"),
    cube("factory_deck", [1.5, 4.1, 1.5], [14.5, 5.1, 14.5],
         "lab_spine", "factory_chassis"),
]

for lane_name, center in (("west", 3.5), ("center", 8), ("east", 12.5)):
    FACTORY.extend([
        cube(f"{lane_name}_lane_base", [center - 1.55, 5.1, 2.55],
             [center + 1.55, 5.48, 9.45], "lab_dark", "factory_lanes"),
        cube(f"{lane_name}_lane_inlay", [center - 1.12, 5.48, 3.05],
             [center + 1.12, 5.78, 9], "void", "factory_lanes"),
        cube(f"{lane_name}_lane_west_rail", [center - 1.48, 5.48, 2.9],
             [center - 1.18, 5.82, 9.25], "lab_shell", "factory_lanes"),
        cube(f"{lane_name}_lane_east_rail", [center + 1.18, 5.48, 2.9],
             [center + 1.48, 5.82, 9.25], "lab_shell", "factory_lanes"),
        cube(f"{lane_name}_lane_input", [center - .72, 5.7, 2.55],
             [center + .72, 5.98, 3.05], "precision_metal", "factory_lanes"),
        cube(f"{lane_name}_binding_plate", [center - .92, 5.78, 5.15],
             [center + .92, 6.08, 8.05], "binding", "factory_lanes"),
        cube(f"{lane_name}_binding_socket", [center - .32, 6.08, 6.05],
             [center + .32, 6.38, 7.15], "gold", "factory_lanes"),
    ])

FACTORY.extend([
    # The rear binding manifold is deliberately broader and taller than the
    # three lane carriages so it reads as the factory's shared processing
    # chamber instead of a processor-sized attachment. Keep its depth intact
    # so the whole assembly remains inside one block and clear of recipe items.
    cube("manifold_foundation", [4.76, 5.1, 10], [11.24, 6.14, 14.15],
         "lab_dark", "binding_manifold"),
    cube("manifold_socket", [5.78, 6.14, 10.8], [10.22, 6.75, 13.55],
         "gold", "binding_manifold"),
    cube("manifold_west_restraint", [4.28, 6.14, 10.65], [5.3, 12.53, 13.8],
         "lab_spine", "binding_manifold"),
    cube("manifold_east_restraint", [10.7, 6.14, 10.65], [11.72, 12.53, 13.8],
         "lab_spine", "binding_manifold"),
    cube("manifold_west_inner_trim", [5.3, 7.22, 11.05], [5.66, 12.0, 13.4],
         "lab_shell", "binding_manifold"),
    cube("manifold_east_inner_trim", [10.34, 7.22, 11.05], [10.7, 12.0, 13.4],
         "lab_shell", "binding_manifold"),
    cube("manifold_crown", [5.3, 12.53, 10.8], [10.7, 13.3, 13.65],
         "lab_spine", "binding_manifold"),
    cube("manifold_west_binding_key", [3.86, 8.52, 10.4], [5.3, 9.09, 10.65],
         "binding", "binding_manifold"),
    cube("manifold_east_binding_key", [10.7, 8.52, 10.4], [12.14, 9.09, 10.65],
         "binding", "binding_manifold"),
    cube("factory_bound_spirit", [7.04, 6.81, 11.5], [8.96, 9.82, 13.05],
         "spirit", "shared_spirit_core"),
    cube("factory_spirit_focus", [7.4, 9.82, 11.75], [8.6, 11.06, 12.85],
         "soul", "shared_spirit_core"),
    cube("west_lane_feed", [3.2, 6.18, 9.25], [6.6, 6.48, 10.35],
         "lab_spine", "spirit_distribution"),
    cube("center_lane_feed", [7.58, 6.18, 9.25], [8.42, 6.48, 10.35],
         "lab_spine", "spirit_distribution"),
    cube("east_lane_feed", [9.4, 6.18, 9.25], [12.8, 6.48, 10.35],
         "lab_spine", "spirit_distribution"),
    cube("west_lane_emitter", [3.05, 6.05, 8.72], [3.95, 6.55, 9.25],
         "gold", "spirit_distribution"),
    cube("center_lane_emitter", [7.55, 6.05, 8.72], [8.45, 6.55, 9.25],
         "gold", "spirit_distribution"),
    cube("east_lane_emitter", [12.05, 6.05, 8.72], [12.95, 6.55, 9.25],
         "gold", "spirit_distribution"),
    cube("rear_distribution_bus", [5.3, 11.3, 10.35], [10.7, 11.68, 10.65],
         "lab_spine", "spirit_distribution"),
    cube("rear_distribution_binding_line", [5.9, 11.68, 10.38], [10.1, 12.01, 10.62],
         "binding", "spirit_distribution"),
])


def java_element(part: Cube) -> dict:
    uv = list(part.uv or STRUCTURAL_UVS.get(
        part.texture, (0, 0, 16, 16)
    ))
    element = {
        "name": part.name,
        "from": list(part.start),
        "to": list(part.end),
        "faces": {
            face: {"uv": uv, "texture": f"#{part.texture}"}
            for face in FACES
        },
    }
    if part.rotation:
        axis, angle, origin = part.rotation
        element["rotation"] = {
            "angle": angle,
            "axis": axis,
            "origin": list(origin),
            "rescale": False,
        }
    return element


def runtime_model(credit: str, parts: list[Cube]) -> dict:
    return {
        "credit": credit,
        "ambientocclusion": True,
        "gui_light": "side",
        "render_type": "minecraft:cutout",
        "textures": {"particle": TEXTURES["lab_dark"], **TEXTURES},
        "elements": [java_element(part) for part in parts],
    }


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


TIER_INDICATORS = {
    "basic": ("mekanism:block/factory/led", 0),
    "advanced": ("mekanism:block/factory/led", 1),
    "elite": ("mekanism:block/factory/led", 2),
    "ultimate": ("mekanism:block/factory/led", 3),
    "absolute": ("mekanism_extras:block/factory/led", 0),
    "supreme": ("mekanism_extras:block/factory/led", 1),
    "cosmic": ("mekanism_extras:block/factory/cosmic_led", 2),
    "infinite": ("mekanism_extras:block/factory/infinite_led", 3),
}


def tier_indicator_model(tier: str, active: bool) -> dict:
    """Preserve the original Mek/Mek Extras colour rows in a lower fascia."""
    texture, uv_row = TIER_INDICATORS[tier]
    element = {
        "name": f"embedded_tier_indicator_{tier}",
        "from": [4.98, 2.9, .01],
        "to": [11.02, 3.5, .12],
        "faces": {
            "north": {
                "uv": [0, uv_row, 6, uv_row + 1],
                "texture": "#led",
                "cullface": "north",
            },
            # The thin upper lip keeps the original physical-light-strip look,
            # but is intentionally not cull-faced as it is no longer on y=16.
            "up": {
                "uv": [0, uv_row, 6, uv_row + 1],
                "rotation": 180,
                "texture": "#led",
            },
        },
    }
    if active:
        element["neoforge_data"] = {"block_light": 15, "sky_light": 15}
    return {
        "credit": "Mekanism Magic embedded original factory tier indicator",
        "ambientocclusion": False,
        "render_type": "minecraft:cutout",
        "textures": {"particle": texture, "led": texture},
        "elements": [element],
    }


def rebuild_tier_indicator_children() -> None:
    for tier in TIER_INDICATORS:
        write_json(
            MODEL_ROOT / "spirit_factory" / "front_led" / f"{tier}.json",
            tier_indicator_model(tier, False),
        )
        write_json(
            MODEL_ROOT / "spirit_factory" / "front_led" / "active" / f"{tier}.json",
            tier_indicator_model(tier, True),
        )
        for active in (False, True):
            composite_path = (
                MODEL_ROOT / "spirit_factory" / "active" / f"{tier}.json"
                if active else MODEL_ROOT / f"{tier}_spirit_factory.json"
            )
            composite = json.loads(composite_path.read_text(encoding="utf-8"))
            active_path = "active/" if active else ""
            composite["children"]["front_led"]["parent"] = (
                "mekanism_magic:block/spirit_factory/front_led/"
                f"{active_path}{tier}"
            )
            write_json(composite_path, composite)


def blockbench_project(template_name: str, output_name: str,
                       project_name: str, parts: list[Cube]) -> None:
    template = json.loads((BLOCKBENCH / template_name).read_text(encoding="utf-8"))
    project = copy.deepcopy(template)
    project["name"] = project_name
    texture_ids = {
        texture["name"].removesuffix(".png"): index
        for index, texture in enumerate(project["textures"])
    }
    group_names = list(dict.fromkeys(part.group for part in parts))
    group_ids = {
        name: str(uuid.uuid5(uuid.NAMESPACE_URL,
                             f"mekanism-magic:{project_name}:group:{name}"))
        for name in group_names
    }
    groups = []
    outliner = []
    elements = []
    children = {name: [] for name in group_names}
    for part in parts:
        part_id = str(uuid.uuid5(uuid.NAMESPACE_URL,
                                 f"mekanism-magic:{project_name}:cube:{part.name}"))
        rotation = [0, 0, 0]
        origin = [8, 8, 8]
        if part.rotation:
            axis, angle, pivot = part.rotation
            rotation[("x", "y", "z").index(axis)] = angle
            origin = list(pivot)
        elements.append({
            "name": part.name,
            "box_uv": False,
            "render_order": "default",
            "rescale": False,
            "locked": False,
            "shade": True,
            "light_emission": 0,
            "export": True,
            "scope": 0,
            "allow_mirror_modeling": True,
            "from": list(part.start),
            "to": list(part.end),
            "autouv": 1,
            "color": 0,
            "origin": origin,
            "rotation": rotation,
            "faces": {
                face: {
                    "uv": list(part.uv or STRUCTURAL_UVS.get(
                        part.texture, (0, 0, 16, 16)
                    )),
                    "texture": texture_ids[part.texture],
                }
                for face in FACES
            },
            "type": "cube",
            "uuid": part_id,
        })
        children[part.group].append(part_id)
    for group_name in group_names:
        group_id = group_ids[group_name]
        groups.append({
            "name": group_name,
            "uuid": group_id,
            "export": True,
            "locked": False,
            "scope": 0,
            "selected": False,
            "origin": [8, 8, 8],
            "rotation": [0, 0, 0],
            "color": 0,
            "children": [],
            "reset": False,
            "shade": True,
            "mirror_uv": False,
            "visibility": True,
            "autouv": 1,
            "isOpen": False,
            "primary_selected": False,
        })
        outliner.append({
            "uuid": group_id,
            "isOpen": False,
            "children": children[group_name],
        })
    project["elements"] = elements
    project["groups"] = groups
    project["outliner"] = outliner
    write_json(BLOCKBENCH / output_name, project)

    exported = {
        "format_version": "1.21.0",
        "credit": f"Mekanism Magic {project_name}",
        "textures": {
            str(index): TEXTURES[name]
            for name, index in texture_ids.items()
            if name in TEXTURES
        },
        "elements": [
            {
                **java_element(part),
                "faces": {
                    face: {
                        "uv": list(part.uv or STRUCTURAL_UVS.get(
                            part.texture, (0, 0, 16, 16)
                        )),
                        "texture": f"#{texture_ids[part.texture]}",
                    }
                    for face in FACES
                },
            }
            for part in parts
        ],
        "groups": groups,
    }
    exported["textures"]["particle"] = TEXTURES["lab_dark"]
    write_json(BLOCKBENCH / output_name.replace(".bbmodel", ".json"), exported)


write_json(
    MODEL_ROOT / "spirit_processor_base.json",
    runtime_model("Mekanism Magic v3 precision spirit binding cell", PROCESSOR),
)

factory_base_groups = {"factory_chassis", "factory_lanes", "tier_indicator_dock"}
factory_base = [part for part in FACTORY if part.group in factory_base_groups]
factory_rig = [part for part in FACTORY if part.group not in factory_base_groups]
write_json(
    MODEL_ROOT / "spirit_factory" / "base.json",
    runtime_model("Mekanism Magic v3 tri-lane spirit factory chassis", factory_base),
)
write_json(
    MODEL_ROOT / "spirit_factory" / "binding_rig.json",
    runtime_model("Mekanism Magic v3 shared spirit distribution manifold", factory_rig),
)
rebuild_tier_indicator_children()

blockbench_project(
    "spirit_processor_worker_station_v2.bbmodel",
    "spirit_processor_binding_cell_v3.bbmodel",
    "spirit_processor_binding_cell_v3",
    PROCESSOR,
)
blockbench_project(
    "spirit_factory_worker_line_v2.bbmodel",
    "spirit_factory_binding_line_v3.bbmodel",
    "spirit_factory_binding_line_v3",
    FACTORY,
)

print(
    f"Rebuilt spirit v3 models: processor={len(PROCESSOR)}, "
    f"factory={len(FACTORY)} (base={len(factory_base)}, rig={len(factory_rig)})."
)
