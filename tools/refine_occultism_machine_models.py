"""Apply the restrained visible-detail pass for the Occultism machine line.

The Blockbench exports already contain complex working assemblies.  This
pass improves what actually reads in game: dark recessed chassis, Occultism
dual-line accents on exposed faces, collars on moving/tooling structures and
small symmetric service details.  It is intentionally idempotent so later
Blockbench exports can be refined again without duplicating geometry.
"""

from __future__ import annotations

import json
import uuid
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODELS = (
    ROOT / "src" / "main" / "resources" / "assets"
    / "mekanism_magic" / "models" / "block"
)
FACES = ("north", "south", "east", "west", "up", "down")
BLOCKBENCH = ROOT / "assets" / "blockbench"


def read(name: str) -> dict:
    return json.loads((MODELS / name).read_text(encoding="utf-8"))


def write(name: str, model: dict) -> None:
    (MODELS / name).write_text(
        json.dumps(model, indent=2) + "\n", encoding="utf-8"
    )


def box(name: str, start: list[float], end: list[float],
        texture: str) -> dict:
    return {
        "name": name,
        "from": start,
        "to": end,
        "faces": {
            face: {"uv": [0, 0, 16, 16], "texture": f"#{texture}"}
            for face in FACES
        },
    }


def set_texture(model: dict, names: set[str], texture: str) -> None:
    for element in model["elements"]:
        if element.get("name") in names:
            for face in element.get("faces", {}).values():
                face["texture"] = f"#{texture}"


def add_unique(model: dict, details: list[dict]) -> None:
    # Replace an earlier refinement with the same name as well as adding new
    # geometry. This keeps the pass repeatable while details are tuned.
    replacements = {detail["name"]: detail for detail in details}
    seen: set[str] = set()
    updated: list[dict] = []
    for element in model["elements"]:
        name = element.get("name")
        if name in replacements:
            updated.append(replacements[name])
            seen.add(name)
        else:
            updated.append(element)
    updated.extend(
        detail for detail in details if detail["name"] not in seen
    )
    model["elements"] = updated


def patch_blockbench(project_name: str, dark_body: str,
                     lower_lines: set[str], upper_lines: set[str],
                     details: list[dict]) -> None:
    """Mirror the runtime pass into its editable Blockbench source."""
    path = BLOCKBENCH / project_name
    project = json.loads(path.read_text(encoding="utf-8"))
    texture_ids = {
        texture.get("name", "").removesuffix(".png"): index
        for index, texture in enumerate(project.get("textures", []))
    }

    def apply_texture(element: dict, texture: str) -> None:
        texture_id = texture_ids[texture]
        for face in element.get("faces", {}).values():
            face["texture"] = texture_id

    existing = {element.get("name"): element
                for element in project["elements"]}
    apply_texture(existing[dark_body], "lab_dark")
    for name in lower_lines:
        apply_texture(existing[name], "soul")
    for name in upper_lines:
        apply_texture(existing[name], "binding")

    group_name = "visible_refinement"
    group = next((entry for entry in project.get("groups", [])
                  if entry.get("name") == group_name), None)
    if group is None:
        group_uuid = str(uuid.uuid5(
            uuid.NAMESPACE_URL,
            f"mekanism-magic:{project_name}:{group_name}",
        ))
        group = {
            "name": group_name,
            "uuid": group_uuid,
            "export": True,
            "locked": False,
            "scope": 0,
            "selected": False,
            "origin": [8, 8, 8],
            "rotation": [0, 0, 0],
            "color": 0,
            "children": [],
            "reset": False,
            "shade": False,
            "mirror_uv": False,
            "visibility": True,
            "autouv": 0,
            "isOpen": False,
            "primary_selected": False,
        }
        project.setdefault("groups", []).append(group)
        project.setdefault("outliner", []).append({
            "uuid": group_uuid,
            "isOpen": False,
            "children": [],
        })
    outliner_group = next(
        entry for entry in project["outliner"]
        if isinstance(entry, dict) and entry.get("uuid") == group["uuid"]
    )

    for detail in details:
        name = detail["name"]
        cube_uuid = str(uuid.uuid5(
            uuid.NAMESPACE_URL,
            f"mekanism-magic:{project_name}:{name}",
        ))
        texture = next(iter(detail["faces"].values()))["texture"][1:]
        cube = {
            "name": name,
            "box_uv": False,
            "render_order": "default",
            "rescale": False,
            "locked": False,
            "shade": True,
            "light_emission": 0,
            "export": True,
            "scope": 0,
            "allow_mirror_modeling": True,
            "from": detail["from"],
            "to": detail["to"],
            "autouv": 1,
            "color": 0,
            "origin": [8, 8, 8],
            "faces": {
                face: {
                    "uv": [0, 0, 16, 16],
                    "texture": texture_ids[texture],
                }
                for face in FACES
            },
            "type": "cube",
            "uuid": cube_uuid,
        }
        if name in existing:
            project["elements"][project["elements"].index(
                existing[name])] = cube
        else:
            project["elements"].append(cube)
        if cube_uuid not in outliner_group["children"]:
            outliner_group["children"].append(cube_uuid)

    path.write_text(json.dumps(project, indent=2) + "\n",
                    encoding="utf-8")


def detail_elements(model_name: str, names: set[str]) -> list[dict]:
    model = read(model_name)
    lookup = {element["name"]: element for element in model["elements"]}
    return [lookup[name] for name in names]


def refine_ordinary(name: str, dark_body: str,
                    lower_lines: set[str], upper_lines: set[str],
                    details: list[dict]) -> int:
    model = read(name)
    set_texture(model, {dark_body}, "lab_dark")
    set_texture(model, lower_lines, "soul")
    set_texture(model, upper_lines, "binding")
    before = len(model["elements"])
    add_unique(model, details)
    write(name, model)
    return len(model["elements"]) - before


# The processor and its factory were rebuilt as the v3 binding-cell family.
# They are intentionally owned by rebuild_spirit_machine_v3.py so running this
# legacy refinement pass cannot restore the old worker-station silhouettes.
processor_added = 0


miner_added = refine_ordinary(
    "dimension_miner_base.json",
    "phase_bore_base_body",
    {"miner_west_side_line_lower", "miner_east_side_line_lower",
     "miner_front_void_line_lower"},
    {"miner_west_side_line_upper", "miner_east_side_line_upper",
     "miner_front_binding_line_upper"},
    [
        box("west_phase_bore_tower_binding_cap",
            [1.95, 12.15, 6.0], [3.55, 12.75, 6.5], "binding"),
        box("east_phase_bore_tower_binding_cap",
            [12.45, 12.15, 6.0], [14.05, 12.75, 6.5], "binding"),
    ],
)


ritual_added = refine_ordinary(
    "ritual_engine_base.json",
    "ritual_base_body",
    {"ritual_west_side_line_lower", "ritual_east_side_line_lower",
     "ritual_front_soul_line_lower"},
    {"ritual_west_side_line_upper", "ritual_east_side_line_upper",
     "ritual_front_binding_line_upper"},
    [
        box("ritual_conductor_north",
            [7.4, 6.15, 3.4], [8.6, 6.4, 5], "binding"),
        box("ritual_conductor_south",
            [7.4, 6.15, 11], [8.6, 6.4, 12.6], "binding"),
        box("ritual_conductor_west",
            [3.4, 6.15, 7.4], [5, 6.4, 8.6], "binding"),
        box("ritual_conductor_east",
            [11, 6.15, 7.4], [12.6, 6.4, 8.6], "binding"),
        box("northwest_projector_binding_collar",
            [2.9, 7.8, 2.7], [4.1, 8.25, 3.1], "gold"),
        box("northeast_projector_binding_collar",
            [11.9, 7.8, 2.7], [13.1, 8.25, 3.1], "gold"),
        box("southwest_projector_binding_collar",
            [2.9, 7.8, 12.9], [4.1, 8.25, 13.3], "gold"),
        box("southeast_projector_binding_collar",
            [11.9, 7.8, 12.9], [13.1, 8.25, 13.3], "gold"),
    ],
)


assembler_added = refine_ordinary(
    "mini_ritual_assembler_base.json",
    "assembler_base_body",
    {"assembler_west_side_line_lower", "assembler_east_side_line_lower",
     "assembler_front_soul_line_lower"},
    {"assembler_west_side_line_upper", "assembler_east_side_line_upper",
     "assembler_front_binding_line_upper"},
    [
        box("west_gantry_binding_cap",
            [2.7, 9.5, 6.7], [4, 10.15, 9.3], "binding"),
        box("east_gantry_binding_cap",
            [12, 9.5, 6.7], [13.3, 10.15, 9.3], "binding"),
        box("plotter_crossbeam_soul_line_lower",
            [5, 9.72, 6.75], [11, 10.08, 7], "soul"),
        box("plotter_crossbeam_binding_line_upper",
            [6.25, 10.15, 6.75], [9.75, 10.45, 7], "binding"),
        box("scribing_head_collar_north",
            [7, 7.75, 6.95], [9, 8.25, 7.25], "gold"),
        box("scribing_head_collar_south",
            [7, 7.75, 8.75], [9, 8.25, 9.05], "gold"),
        box("scribing_head_collar_west",
            [6.95, 7.75, 7.25], [7.25, 8.25, 8.75], "gold"),
        box("scribing_head_collar_east",
            [8.75, 7.75, 7.25], [9.05, 8.25, 8.75], "gold"),
        box("white_chalk_cartridge_clamp",
            [2.85, 4.45, 1.55], [4.15, 5.05, 1.75], "gold"),
        box("red_chalk_cartridge_clamp",
            [5.85, 4.45, 1.55], [7.15, 5.05, 1.75], "gold"),
        box("purple_chalk_cartridge_clamp",
            [8.85, 4.45, 1.55], [10.15, 5.05, 1.75], "gold"),
        box("gold_chalk_cartridge_clamp",
            [11.85, 4.45, 1.55], [13.15, 5.05, 1.75], "gold"),
        box("scribing_bed_socket_northwest",
            [4.3, 5.8, 4.3], [5, 6.1, 5], "binding"),
        box("scribing_bed_socket_northeast",
            [11, 5.8, 4.3], [11.7, 6.1, 5], "binding"),
        box("scribing_bed_socket_southwest",
            [4.3, 5.8, 11], [5, 6.1, 11.7], "binding"),
        box("scribing_bed_socket_southeast",
            [11, 5.8, 11], [11.7, 6.1, 11.7], "binding"),
    ],
)


factory_added = 0


patch_blockbench(
    "dimension_miner_phase_bore_v2.bbmodel",
    "phase_bore_base_body",
    {"miner_west_side_line_lower", "miner_east_side_line_lower",
     "miner_front_void_line_lower"},
    {"miner_west_side_line_upper", "miner_east_side_line_upper",
     "miner_front_binding_line_upper"},
    detail_elements("dimension_miner_base.json", {
        "west_phase_bore_tower_binding_cap",
        "east_phase_bore_tower_binding_cap",
    }),
)
patch_blockbench(
    "ritual_engine_series_techmagic.bbmodel",
    "ritual_base_body",
    {"ritual_west_side_line_lower", "ritual_east_side_line_lower",
     "ritual_front_soul_line_lower"},
    {"ritual_west_side_line_upper", "ritual_east_side_line_upper",
     "ritual_front_binding_line_upper"},
    detail_elements("ritual_engine_base.json", {
        "ritual_conductor_north", "ritual_conductor_south",
        "ritual_conductor_west", "ritual_conductor_east",
        "northwest_projector_binding_collar",
        "northeast_projector_binding_collar",
        "southwest_projector_binding_collar",
        "southeast_projector_binding_collar",
    }),
)
patch_blockbench(
    "mini_ritual_assembler_series_techmagic.bbmodel",
    "assembler_base_body",
    {"assembler_west_side_line_lower", "assembler_east_side_line_lower",
     "assembler_front_soul_line_lower"},
    {"assembler_west_side_line_upper", "assembler_east_side_line_upper",
     "assembler_front_binding_line_upper"},
    detail_elements("mini_ritual_assembler_base.json", {
        "west_gantry_binding_cap", "east_gantry_binding_cap",
        "plotter_crossbeam_soul_line_lower",
        "plotter_crossbeam_binding_line_upper",
        "scribing_head_collar_north", "scribing_head_collar_south",
        "scribing_head_collar_west", "scribing_head_collar_east",
        "white_chalk_cartridge_clamp", "red_chalk_cartridge_clamp",
        "purple_chalk_cartridge_clamp", "gold_chalk_cartridge_clamp",
        "scribing_bed_socket_northwest",
        "scribing_bed_socket_northeast",
        "scribing_bed_socket_southwest",
        "scribing_bed_socket_southeast",
    }),
)


print(
    "Refined Occultism models: "
    f"processor +{processor_added}, miner +{miner_added}, "
    f"ritual +{ritual_added}, assembler +{assembler_added}, "
    f"factory +{factory_added}."
)
