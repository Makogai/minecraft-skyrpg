#!/usr/bin/env python3
"""Writes the SkyRPG VFX effects and bindings as tidy JSON.

Run:  python tools/builds/vfx_content.py
Out:  vfx/src/main/resources/data/skyrpg_vfx/skyrpg/{vfx,vfx_bindings}/<id>.json
Format reference: docs/design/vfx.md. Edit the dicts below and re-run; the output folders are wiped and rewritten.
Tuning rule: ambient effects are bound at a low rate (plays per tick) and each play spawns 1-3 particles.
"""
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "vfx/src/main/resources/data/skyrpg_vfx/skyrpg"
V = "skyrpg_vfx:"

effects = {}
bindings = {}


def effect(name, description, *emitters, range=48):
    d = {"description": description, "emitters": list(emitters)}
    if range != 64:
        d["range"] = range
    effects[name] = d


def bind(name, effect_name, rate, description, range=24, **filters):
    b = {"description": description, "effect": V + effect_name, "rate": rate, "range": range}
    b.update(filters)
    bindings[name] = b


def body(w=0.9, h=1.7):
    """Around a player's body (anchor centre, no fit)."""
    return {"type": "box", "size": [w, h, w]}


def mobbox(w=1.2, h=1.0):
    """Around any mob: sizes scale with the entity's width and height."""
    return {"type": "box", "size": [w, h, w], "fit": True}


# ----------------------------------------------------------------------------------------------- armour sets
effect("aura_cinderplate", "Cinderplate: embers rising off the wearer",
       {"particle": V + "ember", "count": [1, 2], "color": {"gradient": ["#FFE9A8", "#FF8A2A", "#B02000"]},
        "size": [0.07, 0.02], "lifetime": [28, 50], "rise": [0.02, 0.045], "drag": 0.03, "sway": 0.5,
        "alpha": [1, 0], "shape": body(0.8, 1.4), "offset": [0, -0.2, 0]})

effect("aura_rimeguard", "Rimeguard: drifting snowflakes and frost glints",
       {"particle": V + "snowflake", "count": 1, "color": ["#FFFFFF", "#DDF2FF", "#BFE6FF"], "size": [0.1, 0.07],
        "lifetime": [40, 70], "gravity": [0.02, 0.05], "drag": 0.06, "sway": 1.0, "spin": [-3, 3], "alpha": [0.9, 0],
        "fade_in": 6, "shape": body(1.1, 0.4), "offset": [0, 0.9, 0]},
       {"particle": V + "star", "count": 1, "chance": 0.4, "color": ["#E8F8FF", "#9EDCFF"], "size": [0.12, 0.02],
        "lifetime": [10, 18], "drag": 0.1, "alpha": [1, 0], "shape": body(0.9, 1.6)})

effect("aura_tide_regalia", "Tide Regalia: rising bubbles and pearl glints",
       {"particle": V + "bubble", "count": 1, "color": ["#BFEFFF", "#8FDCF5", "#FFFFFF"], "size": [0.08, 0.1],
        "size_jitter": 0.35, "lifetime": [30, 50], "rise": [0.03, 0.06], "drag": 0.04, "sway": 0.8, "alpha": [0.8, 0],
        "shape": body(0.8, 0.8), "offset": [0, -0.3, 0]},
       {"particle": V + "star", "count": 1, "chance": 0.4, "color": ["#FFFFFF", "#F6E9FF", "#CFF6FF"], "size": [0.1, 0.02],
        "lifetime": [10, 16], "drag": 0.1, "alpha": [1, 0], "shape": body(0.9, 1.6)})

effect("aura_runeforged", "Runeforged: cyan runes slowly orbiting the wearer",
       {"particle": V + "rune", "count": 1, "color": ["#4FE8FF", "#7FF3FF", "#2BC4E8"], "size": [0.1, 0.08],
        "lifetime": [50, 80], "fade_in": 10, "alpha": [1, 0], "drag": 0,
        "shape": {"type": "orbit", "radius": 0.85}, "orbit_speed": 2.5, "offset": [0, 0.15, 0]},
       {"particle": V + "glow", "count": 1, "chance": 0.3, "color": "#4FE8FF", "size": [0.18, 0.05],
        "lifetime": [20, 30], "alpha": [0.35, 0], "shape": body(0.8, 1.6)})

effect("aura_wardens_shroud", "Warden's Shroud: soul wisps curling up",
       {"particle": V + "wisp", "count": 1, "color": ["#5EE8E0", "#2FB8C0", "#A8FFF4"], "size": [0.14, 0.2],
        "lifetime": [34, 54], "rise": [0.015, 0.035], "drag": 0.04, "sway": 0.9, "alpha": [0.75, 0], "fade_in": 6,
        "shape": body(0.9, 1.2), "offset": [0, -0.1, 0]})

effect("aura_blightforged", "Blightforged: violet spores drifting off",
       {"particle": V + "glow", "count": [1, 2], "color": ["#9B4DE0", "#C38BFF", "#6A2AA8"], "size": [0.08, 0.04],
        "size_jitter": 0.4, "lifetime": [36, 60], "drag": 0.05, "sway": 1.2, "rise": [0.004, 0.015], "alpha": [0.8, 0],
        "fade_in": 6, "shape": body(1.0, 1.6)})

effect("aura_arachne_carapace", "Arachne Carapace: faint red eye-glints",
       {"particle": V + "star", "count": 1, "color": ["#FF2A2A", "#FF5A3A"], "size": [0.09, 0.03], "lifetime": [8, 14],
        "fade_in": 2, "alpha": [0.95, 0], "drag": 0.2, "shape": body(0.85, 1.6)},
       {"particle": V + "glow", "count": 1, "chance": 0.25, "color": "#B01010", "size": [0.12, 0.04], "lifetime": [14, 22],
        "alpha": [0.4, 0], "shape": body(0.8, 1.4)})

effect("aura_hellforged", "Hellforged: lava drips, embers and heat shimmer",
       {"particle": V + "ember", "count": 1, "color": {"gradient": ["#FFD27A", "#FF5A1A", "#8A1000"]}, "size": [0.06, 0.02],
        "lifetime": [20, 36], "rise": [0.02, 0.04], "alpha": [1, 0], "sway": 0.4, "shape": body(0.8, 1.5)},
       {"particle": V + "glow", "chance": 0.35, "count": 1, "color": ["#FF8A2A", "#FFC46B"], "size": [0.07, 0.05],
        "lifetime": [20, 30], "gravity": 0.5, "drag": 0.01, "alpha": [0.9, 0], "shape": body(0.7, 0.5),
        "offset": [0, -0.1, 0]},
       {"particle": V + "smoke_puff", "chance": 0.5, "count": 1, "color": ["#3A2A28", "#5A4038"], "size": [0.14, 0.3],
        "lifetime": [24, 40], "rise": [0.02, 0.04], "alpha": [0.3, 0], "shape": body(0.8, 0.6), "offset": [0, 0.4, 0]})

effect("aura_gravewarden", "Gravewarden: plague-green motes rising off rotting plate",
       {"particle": V + "glow", "count": 1, "color": ["#7FBF5F", "#B4E890", "#3A6A26"], "size": [0.1, 0.04], "lifetime": [30, 50],
        "rise": [0.01, 0.03], "drag": 0.04, "sway": 0.6, "alpha": [0.8, 0], "fade_in": 6, "shape": body(0.9, 1.6)},
       {"particle": V + "smoke_puff", "chance": 0.35, "count": 1, "color": ["#2A3A22", "#4A5A38"], "size": [0.14, 0.3],
        "lifetime": [26, 42], "rise": [0.015, 0.03], "alpha": [0.25, 0], "shape": body(0.8, 0.6), "offset": [0, 0.3, 0]})

effect("aura_silkstalker", "Silkstalker: venom-green glints and drifting silk",
       {"particle": V + "star", "count": 1, "color": ["#8EE03C", "#C4F878", "#58A424"], "size": [0.09, 0.03], "lifetime": [10, 16],
        "fade_in": 2, "alpha": [0.95, 0], "drag": 0.2, "shape": body(0.85, 1.7)},
       {"particle": V + "wisp", "chance": 0.4, "count": 1, "color": ["#E6E6F0", "#C4C4D2"], "size": [0.12, 0.18],
        "lifetime": [34, 54], "rise": [0.006, 0.02], "drag": 0.04, "sway": 1.0, "alpha": [0.55, 0], "fade_in": 6,
        "shape": body(0.9, 1.3)})

effect("aura_marshals_bonemail", "Marshal's Bonemail: red embers and bone dust off the plate",
       {"particle": V + "ember", "count": 1, "color": ["#E04A44", "#FF8A7A", "#B82028"], "size": [0.06, 0.02], "lifetime": [18, 30],
        "rise": [0.01, 0.03], "alpha": [1, 0], "sway": 0.3, "shape": body(0.8, 1.5)},
       {"particle": V + "glow", "chance": 0.3, "count": 1, "color": ["#E8E0C4", "#C8BC9C"], "size": [0.08, 0.03], "lifetime": [20, 34],
        "gravity": 0.2, "drag": 0.03, "alpha": [0.6, 0], "shape": body(0.9, 1.2)})

effect("aura_voidwalker", "Voidwalker: void shards slowly orbiting, violet motes and a faint rift glow",
       {"particle": V + "rune", "count": 1, "color": ["#B06AE8", "#8A4AD0", "#E0C8FF"], "size": [0.12, 0.1], "lifetime": [60, 90],
        "fade_in": 12, "alpha": [1, 0], "drag": 0, "shape": {"type": "orbit", "radius": 0.95}, "orbit_speed": 1.2,
        "offset": [0, 0.2, 0]},
       {"particle": V + "star", "chance": 1, "count": 1, "color": ["#C490FF", "#E6CCFF"], "size": [0.1, 0.03], "lifetime": [30, 50],
        "fade_in": 6, "alpha": [0.9, 0], "drag": 0.04, "sway": 0.5, "rise": [0.004, 0.015], "shape": body(1.0, 1.7)},
       {"particle": V + "glow", "chance": 0.8, "count": 1, "color": "#4F2584", "size": [0.2, 0.06], "lifetime": [20, 34],
        "alpha": [0.4, 0], "shape": body(0.8, 1.6)})

effect("aura_wyrmscale", "Wyrmscale: a slow ring of void light, violet motes and ember-bright scale glints",
       {"particle": V + "star", "count": 1, "color": ["#E0A8FF", "#B86CFF", "#FBE8FF"], "size": [0.1, 0.03], "lifetime": [40, 70],
        "fade_in": 8, "alpha": [1, 0], "drag": 0, "shape": {"type": "orbit", "radius": 1.05}, "orbit_speed": 1.6,
        "offset": [0, 0.3, 0]},
       {"particle": V + "wisp", "chance": 0.7, "count": 1, "color": ["#52308A", "#311858"], "size": [0.16, 0.22], "lifetime": [24, 40],
        "rise": [0.01, 0.03], "drag": 0.05, "sway": 0.6, "alpha": [0.55, 0], "fade_in": 5, "shape": body(0.9, 1.5)},
       {"particle": V + "glow", "chance": 0.6, "count": 1, "color": "#9A48E8", "size": [0.18, 0.05], "lifetime": [16, 28],
        "alpha": [0.45, 0], "shape": body(0.8, 1.6)})

SETS = {
    "cinderplate": 0.35,
    "rimeguard": 0.3,
    "tide_regalia": 0.3,
    "runeforged": 0.06,
    "wardens_shroud": 0.22,
    "blightforged": 0.3,
    "arachne_carapace": 0.2,
    "hellforged": 0.4,
    "gravewarden": 0.3,
    "silkstalker": 0.25,
    "marshals_bonemail": 0.3,
    "voidwalker": 0.07,
    "wyrmscale": 0.1,
}
for key, rate in SETS.items():
    bind("armor_" + key, "aura_" + key, rate, "Full " + key.replace("_", " ") + " set worn",
         armor={"asset": "skyrpg_items:" + key, "pieces": 4})

# ----------------------------------------------------------------------------------------------- held items
effect("held_thanes_hammer", "Thane's Hammer: embers and forge sparks from the head",
       {"particle": V + "ember", "count": 1, "color": {"gradient": ["#FFE9A8", "#FF8A2A", "#B02000"]}, "size": [0.06, 0.02],
        "lifetime": [16, 28], "rise": [0.015, 0.035], "alpha": [1, 0], "sway": 0.3, "anchor": "hand",
        "shape": {"type": "sphere", "radius": 0.18}, "offset": [0, 0.35, 0.25]},
       {"particle": V + "spark", "chance": 0.3, "count": 1, "color": ["#FFF2B0", "#FFC46B"], "size": [0.1, 0.04],
        "lifetime": [6, 10], "speed": [0.08, 0.16], "gravity": 0.3, "alpha": [1, 0], "anchor": "hand",
        "shape": {"type": "sphere", "radius": 0.1}, "offset": [0, 0.35, 0.25]}, range=32)
bind("held_thanes_hammer", "held_thanes_hammer", 0.35, "Holding the Thane's Hammer", range=24,
     held={"item": "skyrpg_items:thanes_hammer", "hand": "either"})

effect("held_legendary", "Legendary weapon: a subtle gold sparkle",
       {"particle": V + "star", "count": 1, "color": ["#FFD76A", "#FFF2B0", "#FFC23A"], "size": [0.09, 0.02],
        "lifetime": [10, 16], "alpha": [1, 0], "drag": 0.1, "anchor": "hand",
        "shape": {"type": "sphere", "radius": 0.3}, "offset": [0, 0.25, 0.3]}, range=24)
bind("held_legendary", "held_legendary", 0.18, "Holding anything legendary", range=16,
     held={"rarity": "legendary", "hand": "main", "except": ["skyrpg_items:thanes_hammer"]})

# ----------------------------------------------------------------------------------------------- mobs
effect("mob_fire_beetle", "Fire Beetle: embers",
       {"particle": V + "ember", "count": 1, "color": {"gradient": ["#FFD27A", "#FF6A1A", "#A01800"]}, "size": [0.06, 0.02],
        "lifetime": [20, 34], "rise": [0.02, 0.04], "alpha": [1, 0], "shape": mobbox(1.1, 0.8)})
bind("mob_fire_beetle", "mob_fire_beetle", 0.3, "Fire beetles glow with embers", entity="skyrpg_combat:fire_beetle")

effect("mob_sporeling", "Sporeling: drifting spores",
       {"particle": V + "glow", "count": 1, "color": ["#B8E86A", "#8FD04A", "#E4FF9A"], "size": [0.07, 0.03],
        "lifetime": [36, 60], "sway": 1.0, "drag": 0.05, "rise": [0.003, 0.012], "alpha": [0.8, 0], "fade_in": 6,
        "shape": mobbox(1.3, 1.1)})
bind("mob_sporeling", "mob_sporeling", 0.3, "Sporelings shed spores", entity="skyrpg_combat:sporeling")

effect("mob_dwarven_construct", "Dwarven Construct: rune-core glints",
       {"particle": V + "star", "count": 1, "color": ["#4FE8FF", "#9FF6FF"], "size": [0.12, 0.03], "lifetime": [10, 16],
        "alpha": [1, 0], "drag": 0.1, "shape": {"type": "sphere", "radius": 0.18}, "offset": [0, 0.1, 0.25]},
       {"particle": V + "rune", "chance": 0.25, "count": 1, "color": "#4FE8FF", "size": [0.1, 0.08], "lifetime": [30, 44],
        "rise": 0.015, "alpha": [0.9, 0], "shape": mobbox(1.0, 0.8)})
bind("mob_dwarven_construct", "mob_dwarven_construct", 0.25, "Dwarven constructs have a glowing rune core",
     entity="skyrpg_combat:dwarven_construct")

effect("mob_iron_thane", "Iron Thane: forge sparks and a crown glow",
       {"particle": V + "spark", "count": 1, "color": ["#FFF2B0", "#FFC46B", "#FF8A2A"], "size": [0.12, 0.04], "lifetime": [6, 12],
        "speed": [0.06, 0.14], "gravity": 0.3, "alpha": [1, 0], "shape": mobbox(1.2, 1.0)},
       {"particle": V + "glow", "count": 1, "chance": 0.5, "color": ["#FFC23A", "#FFE08A"], "size": [0.2, 0.08], "lifetime": [14, 22],
        "alpha": [0.45, 0], "anchor": "head", "shape": {"type": "ring", "radius": 0.3}, "offset": [0, 0.35, 0]})
bind("mob_iron_thane", "mob_iron_thane", 0.5, "Iron Thane forges sparks", range=32, entity="skyrpg_combat:iron_thane")

effect("mob_bone_giant", "Bone Giant: soul wisps",
       {"particle": V + "wisp", "count": 1, "color": ["#8FF4E8", "#5EE8E0", "#CFFFF8"], "size": [0.18, 0.26], "lifetime": [34, 54],
        "rise": [0.015, 0.035], "sway": 0.8, "drag": 0.04, "alpha": [0.7, 0], "fade_in": 6, "shape": mobbox(1.1, 1.0)})
bind("mob_bone_giant", "mob_bone_giant", 0.4, "Bone giants trail soul wisps", range=32, entity="skyrpg_combat:bone_giant")

effect("mob_magma_tyrant", "Magma Tyrant: lava drips and a flame crown",
       {"particle": V + "glow", "count": 1, "color": ["#FF8A2A", "#FFC46B", "#FF5A1A"], "size": [0.09, 0.06], "lifetime": [20, 30],
        "gravity": 0.5, "drag": 0.01, "alpha": [0.9, 0], "shape": mobbox(1.1, 0.7)},
       {"particle": V + "wisp", "count": 1, "color": ["#FFB03A", "#FF6A1A"], "size": [0.2, 0.3], "lifetime": [14, 22], "rise": 0.03,
        "alpha": [0.9, 0], "anchor": "head", "shape": {"type": "ring", "radius": 0.35}, "offset": [0, 0.3, 0]},
       {"particle": V + "ember", "chance": 0.6, "count": 1, "color": {"gradient": ["#FFD27A", "#FF5A1A", "#8A1000"]}, "size": [0.07, 0.02],
        "lifetime": [24, 40], "rise": [0.02, 0.05], "alpha": [1, 0], "shape": mobbox(1.3, 1.0)})
bind("mob_magma_tyrant", "mob_magma_tyrant", 0.6, "Magma tyrants drip lava and wear flames", range=40,
     entity="skyrpg_combat:magma_tyrant")

# Slayer bosses: each its own ominous colour
BOSSES = {
    "grave_tyrant": ("#7A9A90", "#C9F0E4"),
    "broodmother": ("#8A1A1A", "#FF4A4A"),
    "bone_marshal": ("#D8D2BC", "#FFF6D6"),
    "void_stalker": ("#7A2FD0", "#D9A8FF"),
}
for boss, (dark, light) in BOSSES.items():
    effect("boss_" + boss, boss.replace("_", " ").title() + ": an ominous aura",
           {"particle": V + "wisp", "count": 1, "color": [dark, light], "size": [0.2, 0.3], "lifetime": [36, 56], "rise": [0.01, 0.03],
            "sway": 0.8, "drag": 0.04, "alpha": [0.65, 0], "fade_in": 8, "shape": mobbox(1.2, 1.0)},
           {"particle": V + "smoke_puff", "chance": 0.5, "count": 1, "color": [dark], "size": [0.2, 0.5], "lifetime": [30, 50],
            "rise": 0.02, "alpha": [0.35, 0], "anchor": "feet", "shape": {"type": "disc", "radius": 0.6, "fit": True}},
           {"particle": V + "star", "chance": 0.3, "count": 1, "color": [light], "size": [0.12, 0.03], "lifetime": [10, 16],
            "alpha": [1, 0], "shape": mobbox(1.2, 1.0)}, range=48)
    bind("boss_" + boss, "boss_" + boss, 0.6, boss.replace("_", " ").title() + " slayer boss", range=48,
         entity="skyrpg_combat:" + boss)

# ----------------------------------------------------------------------------------------------- pet auras
# Played by the pets mod every ~4 ticks at the pet's position (a plain position, no entity: shapes are in blocks).
effect("pet_aura_common", "Pet aura: almost nothing",
       {"particle": V + "glow", "chance": 0.12, "count": 1, "color": "#FFFFFF", "size": [0.05, 0.02], "lifetime": [16, 24],
        "alpha": [0.5, 0], "rise": 0.01, "shape": {"type": "sphere", "radius": 0.3}}, range=24)
effect("pet_aura_uncommon", "Pet aura: a rare green mote",
       {"particle": V + "glow", "chance": 0.18, "count": 1, "color": ["#7DFF7D", "#55FF55"], "size": [0.06, 0.02], "lifetime": [16, 26],
        "alpha": [0.6, 0], "rise": 0.012, "shape": {"type": "sphere", "radius": 0.3}}, range=24)
effect("pet_aura_rare", "Pet aura: blue glints",
       {"particle": V + "star", "chance": 0.35, "count": 1, "color": ["#7FA8FF", "#B8D0FF"], "size": [0.09, 0.02], "lifetime": [10, 16],
        "alpha": [1, 0], "drag": 0.1, "shape": {"type": "sphere", "radius": 0.35}},
       {"particle": V + "glow", "chance": 0.2, "count": 1, "color": "#5588FF", "size": [0.06, 0.02], "lifetime": [16, 24],
        "alpha": [0.5, 0], "rise": 0.012, "shape": {"type": "sphere", "radius": 0.3}}, range=24)
effect("pet_aura_epic", "Pet aura: purple motes and glints",
       {"particle": V + "glow", "chance": 0.6, "count": 1, "color": ["#C05AFF", "#E0A0FF", "#8A2AD0"], "size": [0.08, 0.02], "lifetime": [18, 30],
        "alpha": [0.8, 0], "rise": 0.015, "sway": 0.5, "shape": {"type": "sphere", "radius": 0.35}},
       {"particle": V + "star", "chance": 0.3, "count": 1, "color": ["#F0C8FF", "#D080FF"], "size": [0.1, 0.02], "lifetime": [10, 16],
        "alpha": [1, 0], "drag": 0.1, "shape": {"type": "sphere", "radius": 0.4}}, range=28)
effect("pet_aura_legendary", "Pet aura: a gold sparkle trail",
       {"particle": V + "star", "count": [1, 2], "color": ["#FFD76A", "#FFF2B0", "#FFC23A"], "size": [0.11, 0.02], "lifetime": [12, 20],
        "alpha": [1, 0], "drag": 0.08, "spin": [-6, 6], "shape": {"type": "sphere", "radius": 0.4}},
       {"particle": V + "glow", "chance": 0.7, "count": 1, "color": ["#FFC23A", "#FFE08A"], "size": [0.09, 0.02], "lifetime": [20, 34],
        "alpha": [0.8, 0], "rise": 0.012, "sway": 0.5, "shape": {"type": "sphere", "radius": 0.35}},
       {"particle": V + "spark", "chance": 0.25, "count": 1, "color": "#FFF2B0", "size": [0.12, 0.04], "lifetime": [6, 10],
        "speed": [0.04, 0.09], "alpha": [1, 0], "shape": {"type": "sphere", "radius": 0.3}}, range=32)

# ----------------------------------------------------------------------------------------------- one-shots
effect("level_up", "Level up: a gold ring, rising sparkles and a flash",
       {"particle": V + "ring", "count": 1, "color": "#FFD76A", "size": [0.4, 2.4], "lifetime": 16, "alpha": [0.9, 0], "orient": "flat",
        "anchor": "feet", "offset": [0, 0.05, 0]},
       {"particle": V + "star", "count": 22, "color": ["#FFD76A", "#FFF2B0", "#FFC23A"], "size": [0.16, 0.03], "lifetime": [24, 40],
        "rise": [0.05, 0.1], "drag": 0.05, "alpha": [1, 0], "anchor": "feet", "shape": {"type": "ring", "radius": 0.7}},
       {"particle": V + "glow", "count": 1, "color": "#FFF2B0", "size": [0.8, 2.0], "lifetime": 10, "alpha": [0.5, 0], "anchor": "center"},
       range=48)

effect("rare_drop", "Rare drop: a flash and glints",
       {"particle": V + "glow", "count": 1, "color": "#FFFFFF", "size": [0.5, 1.2], "lifetime": 8, "alpha": [0.6, 0]},
       {"particle": V + "star", "count": 12, "color": ["#FFFFFF", "#FFE08A", "#9FE8FF"], "size": [0.14, 0.02], "lifetime": [14, 24],
        "speed": [0.04, 0.12], "drag": 0.08, "alpha": [1, 0], "shape": "sphere"},
       {"particle": V + "spark", "count": 6, "color": ["#FFF2B0"], "size": [0.12, 0.04], "lifetime": [8, 14], "speed": [0.1, 0.2],
        "gravity": 0.2, "alpha": [1, 0], "shape": "point"}, range=40)

effect("slam_shockwave", "Slam: a flat shockwave with dust and sparks",
       {"particle": V + "ring", "count": 1, "color": ["#FFE8C0"], "size": [0.5, 5], "lifetime": 12, "alpha": [0.8, 0], "orient": "flat",
        "offset": [0, 0.08, 0]},
       {"particle": V + "ring", "count": 1, "delay": 3, "color": "#FFB060", "size": [0.3, 3.5], "lifetime": 10, "alpha": [0.6, 0],
        "orient": "flat", "offset": [0, 0.08, 0]},
       {"particle": V + "smoke_puff", "count": 14, "color": ["#B8A890", "#8A7A68"], "size": [0.3, 0.8], "lifetime": [16, 26],
        "speed": [0.12, 0.25], "drag": 0.12, "alpha": [0.5, 0], "shape": {"type": "ring", "radius": 0.4}, "offset": [0, 0.1, 0]},
       {"particle": V + "spark", "count": 12, "color": ["#FFF2B0", "#FFC46B"], "size": [0.14, 0.04], "lifetime": [8, 14],
        "speed": [0.15, 0.3], "gravity": 0.4, "alpha": [1, 0], "shape": {"type": "ring", "radius": 0.3}, "offset": [0, 0.15, 0]},
       range=48)

effect("molten_burst", "Molten burst: a fiery eruption of lava, embers and smoke",
       {"particle": V + "glow", "count": 1, "color": "#FF8A2A", "size": [0.8, 2.6], "lifetime": 8, "alpha": [0.7, 0]},
       {"particle": V + "ember", "count": 26, "color": {"gradient": ["#FFE9A8", "#FF8A2A", "#B02000"]}, "size": [0.12, 0.03],
        "lifetime": [20, 38], "speed": [0.1, 0.28], "gravity": 0.15, "alpha": [1, 0], "shape": "sphere"},
       {"particle": V + "glow", "count": 10, "color": ["#FF8A2A", "#FFC46B"], "size": [0.12, 0.06], "lifetime": [20, 32],
        "speed": [0.08, 0.2], "rise": [0.05, 0.12], "gravity": 0.6, "alpha": [0.9, 0], "shape": "point"},
       {"particle": V + "smoke_puff", "count": 8, "color": ["#2A2220", "#4A3A34"], "size": [0.3, 0.9], "lifetime": [24, 40],
        "speed": [0.03, 0.08], "rise": [0.03, 0.06], "alpha": [0.5, 0], "shape": {"type": "sphere", "radius": 0.3}},
       {"particle": V + "ring", "count": 1, "color": "#FF6A1A", "size": [0.4, 3], "lifetime": 10, "alpha": [0.7, 0], "orient": "flat",
        "offset": [0, 0.08, 0]}, range=48)

effect("teleport_flash", "Teleport flash: a violet-white flash and a swirl",
       {"particle": V + "glow", "count": 1, "color": "#E8D8FF", "size": [0.6, 2.2], "lifetime": 8, "alpha": [0.8, 0], "anchor": "center"},
       {"particle": V + "star", "count": 16, "color": ["#FFFFFF", "#C8A0FF", "#9A6AFF"], "size": [0.14, 0.02], "lifetime": [14, 24],
        "speed": [0.05, 0.14], "drag": 0.08, "alpha": [1, 0], "anchor": "center", "shape": "sphere"},
       {"particle": V + "glow", "count": 14, "color": ["#B890FF", "#E0C8FF"], "size": [0.1, 0.03], "lifetime": [14, 22],
        "rise": [0.04, 0.09], "alpha": [0.8, 0], "anchor": "feet",
        "shape": {"type": "helix", "radius": 0.5, "height": 1.8, "turns": 1.5, "even": True}},
       range=40)

effect("crit_spark", "Critical hit: a quick burst of sparks and a star",
       {"particle": V + "star", "count": 1, "color": "#FFF2B0", "size": [0.35, 0.08], "lifetime": 8, "alpha": [1, 0]},
       {"particle": V + "spark", "count": 9, "color": ["#FFFFFF", "#FFE08A", "#FFB03A"], "size": [0.13, 0.04], "lifetime": [6, 11],
        "speed": [0.12, 0.28], "gravity": 0.2, "alpha": [1, 0], "shape": "point"}, range=32)


# ----------------------------------------------------------------------------------------------- write
def write(folder, items):
    target = OUT / folder
    if target.exists():
        shutil.rmtree(target)
    target.mkdir(parents=True)
    for name, data in items.items():
        (target / (name + ".json")).write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    write("vfx", effects)
    write("vfx_bindings", bindings)
    print(f"wrote {len(effects)} effects and {len(bindings)} bindings to {OUT}")


if __name__ == "__main__":
    main()
