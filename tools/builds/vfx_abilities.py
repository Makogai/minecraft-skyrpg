#!/usr/bin/env python3
"""Writes the VFX effects of item abilities, shields and bows (namespace skyrpg_abilities).

Run:  python tools/builds/vfx_abilities.py
Out:  vfx/src/main/resources/data/skyrpg_abilities/skyrpg/vfx/<id>.json   (only this folder is wiped and rewritten)
Format reference: docs/design/vfx.md; what plays when: docs/design/abilities.md.
Sizes: a flat ring of size S is about 1.2 * S blocks in radius (the particle quad is drawn 1.2 x its size).
"""
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "vfx/src/main/resources/data/skyrpg_abilities/skyrpg/vfx"
V = "skyrpg_vfx:"

effects = {}


def effect(name, description, *emitters, range=64):
    effects[name] = {"description": description, "emitters": list(emitters), "range": range}


def ring(color, size, life, delay=0, y=0.08, alpha=(0.9, 0), orient="flat", anchor=None, **kw):
    e = {"particle": V + "ring", "count": 1, "color": color, "size": list(size), "lifetime": life, "alpha": list(alpha),
         "orient": orient, "offset": [0, y, 0], "delay": delay, "fullbright": True}
    if anchor:
        e["anchor"] = anchor
    e.update(kw)
    return e


def glow(color, size, life, count=1, alpha=(0.8, 0), delay=0, shape=None, **kw):
    e = {"particle": V + "glow", "count": count, "color": color, "size": list(size), "lifetime": life, "alpha": list(alpha),
         "delay": delay}
    if shape:
        e["shape"] = shape
    e.update(kw)
    return e


def sparks(color, count, speed, life, size=(0.13, 0.03), gravity=0.3, delay=0, shape=None, **kw):
    e = {"particle": V + "spark", "count": count, "color": color, "size": list(size), "lifetime": life, "speed": speed,
         "gravity": gravity, "alpha": [1, 0], "delay": delay, "shape": shape or "point"}
    e.update(kw)
    return e


def stars(color, count, size, life, speed=0.0, delay=0, shape=None, **kw):
    e = {"particle": V + "star", "count": count, "color": color, "size": list(size), "lifetime": life, "alpha": [1, 0],
         "delay": delay, "shape": shape or "point", "drag": 0.08}
    if speed:
        e["speed"] = speed
    e.update(kw)
    return e


def puff(color, count, size, life, delay=0, speed=0.08, alpha=(0.45, 0), **kw):
    e = {"particle": V + "smoke_puff", "count": count, "color": color, "size": list(size), "lifetime": life, "speed": speed,
         "drag": 0.1, "alpha": list(alpha), "delay": delay}
    e.update(kw)
    return e


def column(color, height, count, life, size=(0.6, 0.15), delay=0, **kw):
    """A pillar of light: glows along a vertical line, standing still."""
    return glow(color, size, life, count=count, alpha=(0.75, 0), delay=delay,
                shape={"type": "line", "from": [0, 0, 0], "to": [0, height, 0], "even": True}, **kw)


def helix(particle, color, count, radius, height, turns, size, life, rise=0.04, delay=0, **kw):
    e = {"particle": V + particle, "count": count, "color": color, "size": list(size), "lifetime": life, "rise": rise,
         "alpha": [1, 0], "delay": delay, "drag": 0.05,
         "shape": {"type": "helix", "radius": radius, "height": height, "turns": turns, "even": True}}
    e.update(kw)
    return e


def rune(color, size, life, delay=0, spin=3, alpha=(0.85, 0), y=0.06):
    """A turning flat star on the ground: the magic circle (the rune sprites are square plates and look like tiles when big)."""
    return {"particle": V + "star", "count": 1, "color": color, "size": list(size), "lifetime": life, "alpha": list(alpha),
            "orient": "flat", "spin": spin, "delay": delay, "offset": [0, y, 0], "fullbright": True}


GOLD = ["#FFF2B0", "#FFE08A", "#FFC46B"]
STAR_WHITE = ["#FFFFFF", "#EAF4FF", "#FFF2B0"]
CYAN = ["#9FE8FF", "#5CD0FF", "#E0F8FF"]
VIOLET = ["#C490FF", "#9B5CE8", "#E6CCFF"]
ELECTRIC = ["#FFFFFF", "#BFE6FF", "#7FC8FF", "#FFF6A0"]

# ------------------------------------------------------------------------------------------------------ Starfall
effect("starfall_cast", "Starfall: a star circle opens under the caster and a column of light climbs into the sky",
       ring(GOLD, (0.4, 5), 14), ring(CYAN, (0.3, 3.4), 12, delay=3),
       rune("#FFE9A8", (2.6, 2.6), 34, spin=4), rune("#9FE8FF", (1.8, 1.8), 30, delay=2, spin=-6, alpha=(0.8, 0)),
       ring(["#FFE9A8"], (2.2, 2.2), 30, alpha=(0.7, 0)),
       column(["#BFE6FF", "#FFFFFF"], 13, 48, 12, size=(0.6, 0.2)),
       helix("star", STAR_WHITE, 18, 1.0, 2.6, 2, (0.3, 0.05), 22, rise=0.05, anchor="feet"),
       sparks(GOLD, 16, [0.08, 0.2], [10, 18], gravity=-0.1, shape={"type": "ring", "radius": 0.9}, velocity=[0, 0.25, 0]),
       glow("#FFFFFF", (1.3, 0.3), 6, alpha=(0.5, 0), offset=[0, 1, 0]))
for e in effects["starfall_cast"]["emitters"]:
    e.setdefault("anchor", "feet")

effect("starfall_marker", "Starfall: a target ring closes on the mob the star is about to hit",
       ring(["#FFE9A8"], (1.9, 0.5), 8, alpha=(0.25, 1)),
       ring(["#9FE8FF"], (1.1, 0.35), 8, alpha=(0.2, 0.9), delay=1),
       glow("#FFF2B0", (0.5, 0.9), 8, alpha=(0.2, 0.7), offset=[0, 0.15, 0]))

meteor = [
    stars(["#FFFFFF", "#FFF2B0"], 1, (0.9, 0.7), 8, shape="point", offset=[0, 10, 0], velocity=[0, -1.25, 0], drag=0, roll=0, alpha=[1, 0.9]),
    glow(["#FFF2B0", "#CFEFFF"], (1.0, 0.6), 8, alpha=(0.6, 0.9), offset=[0, 10, 0], velocity=[0, -1.25, 0], drag=0),
]
for e in meteor:
    e.pop("roll", None)
for i in range(8):
    y = 10 - 1.25 * i
    meteor.append(glow(["#BFE6FF", "#FFF2B0"], (0.55, 0.05), 7, count=2, alpha=(0.8, 0), delay=i, offset=[0, y, 0],
                       shape={"type": "sphere", "radius": 0.12}))
    meteor.append(sparks(["#FFFFFF", "#FFE08A", "#9FE8FF"], 3, [0.0, 0.06], [6, 10], size=(0.12, 0.02), gravity=0.0, delay=i,
                         offset=[0, y, 0], velocity=[0, 0.12, 0], shape={"type": "sphere", "radius": 0.15}))
effect("starfall_meteor", "Starfall: a star falls out of the sky in eight ticks, trailing light", *meteor)

effect("starfall_impact", "Starfall: the star lands (flash, rings, a burst of stars, sparks, shards and dust)",
       glow("#FFFFFF", (1.9, 0.4), 5, alpha=(0.85, 0), offset=[0, 0.7, 0]),
       glow(["#FFE9A8", "#BFE6FF"], (1.6, 0.3), 9, alpha=(0.6, 0), offset=[0, 0.5, 0]),
       ring(["#FFF2B0"], (0.3, 3.4), 11, alpha=(0.95, 0)), ring(["#9FE8FF"], (0.2, 2.4), 10, delay=2),
       ring(["#FFFFFF"], (0.15, 1.6), 8, delay=1, orient="vertical", y=0.6, alpha=(0.8, 0)),
       stars(STAR_WHITE, 5, (0.8, 0.1), [10, 16], speed=[0.12, 0.28], shape={"type": "sphere", "radius": 0.2}, offset=[0, 0.6, 0]),
       sparks(["#FFFFFF", "#FFE08A", "#9FE8FF", "#FFC46B"], 30, [0.22, 0.5], [10, 18], size=(0.22, 0.04), gravity=0.5, offset=[0, 0.4, 0], spread=0.05),
       {"particle": V + "shard", "count": 8, "color": ["#CFEFFF", "#FFF2B0"], "size": [0.2, 0.06], "lifetime": [14, 22],
        "speed": [0.15, 0.32], "gravity": 0.9, "spin": [-12, 12], "alpha": [1, 0], "offset": [0, 0.3, 0],
        "shape": {"type": "ring", "radius": 0.2}, "velocity": [0, 0.28, 0]},
       puff(["#8A92B8", "#B8C0DC"], 8, (0.35, 0.9), [16, 26], speed=[0.1, 0.2], alpha=(0.4, 0),
            shape={"type": "ring", "radius": 0.3}, offset=[0, 0.15, 0]),
       column(["#FFFFFF", "#CFEFFF"], 3.5, 16, 8, size=(0.55, 0.15)))

effect("starfall_finale", "Starfall: the last star of a volley lands, a soft bloom over the whole field",
       ring(["#FFE9A8"], (0.5, 5.5), 16, alpha=(0.7, 0)), glow("#FFFFFF", (3.5, 1.5), 8, alpha=(0.4, 0), offset=[0, 1, 0]),
       stars(STAR_WHITE, 10, (0.6, 0.05), [16, 26], speed=[0.05, 0.14], shape={"type": "sphere", "radius": 1.5}, offset=[0, 1.2, 0],
             gravity=-0.05))

# ------------------------------------------------------------------------------------------------------ Thunderclap
effect("thunder_cast", "Thunderclap: the air cracks, a blue ring races out of the caster",
       ring(ELECTRIC, (0.3, 4.2), 8, alpha=(1, 0)), ring(["#FFF6A0"], (0.2, 3.0), 8, delay=2),
       glow("#BFE6FF", (1.4, 0.3), 5, alpha=(0.6, 0), offset=[0, 1, 0], anchor="feet"),
       sparks(ELECTRIC, 26, [0.3, 0.55], [6, 12], gravity=0.0, size=(0.16, 0.03), shape={"type": "ring", "radius": 0.4}, offset=[0, 0.9, 0],
              anchor="feet"),
       helix("spark", ELECTRIC, 14, 0.8, 2.2, 1.5, (0.14, 0.03), 12, rise=0.08, anchor="feet", velocity=[0, 0.2, 0]))
for e in effects["thunder_cast"]["emitters"]:
    e.setdefault("anchor", "feet")

effect("thunder_strike", "Thunderclap: a bolt lands (white flash, sparks, scorch smoke)",
       glow("#FFFFFF", (2.0, 0.4), 5, alpha=(0.95, 0), offset=[0, 0.8, 0]),
       glow("#9FD8FF", (1.2, 0.3), 9, alpha=(0.6, 0), offset=[0, 1.0, 0]),
       ring(ELECTRIC, (0.3, 3.0), 8), ring(["#FFF6A0"], (0.2, 1.8), 8, delay=1),
       sparks(ELECTRIC, 24, [0.2, 0.55], [7, 14], gravity=0.2, size=(0.16, 0.03), offset=[0, 0.9, 0], spread=0.06),
       sparks(["#FFFFFF", "#9FD8FF"], 10, [0.25, 0.45], [6, 10], gravity=0.0, size=(0.2, 0.03), shape={"type": "ring", "radius": 0.2}, offset=[0, 0.3, 0]),
       column(["#FFFFFF", "#BFE6FF"], 5, 22, 5, size=(0.5, 0.25)),
       puff(["#3A3F4A", "#5A606C"], 6, (0.3, 0.7), [14, 22], speed=[0.04, 0.1], alpha=(0.4, 0), offset=[0, 0.2, 0], rise=[0.02, 0.05]))

# ------------------------------------------------------------------------------------------------------ Riftstep
effect("rift_depart", "Riftstep: a violet tear opens where you stood and swallows light",
       column(VIOLET, 2.4, 14, 10, size=(0.7, 0.1)),
       ring(VIOLET, (0.15, 1.8), 9, orient="vertical", y=1.0, alpha=(0.95, 0)), ring(["#E6CCFF"], (0.3, 2.6), 10),
       glow("#E6CCFF", (1.8, 0.2), 6, alpha=(0.6, 0), offset=[0, 1, 0]),
       sparks(VIOLET, 18, [0.1, 0.3], [8, 14], gravity=-0.1, offset=[0, 1, 0]),
       puff(["#2A1048", "#4A2088"], 6, (0.4, 0.9), [16, 24], alpha=(0.5, 0), offset=[0, 0.8, 0]))

effect("rift_arrive", "Riftstep: you tear out the other side in a violet burst",
       glow("#FFFFFF", (1.6, 0.3), 5, alpha=(0.8, 0), offset=[0, 1, 0]),
       ring(["#E6CCFF"], (0.2, 4.0), 11), ring(VIOLET, (0.3, 2.8), 9, delay=2),
       ring(VIOLET, (0.15, 2.2), 9, orient="vertical", y=1.0),
       sparks(VIOLET + ["#FFFFFF"], 30, [0.2, 0.5], [10, 18], gravity=0.2, offset=[0, 1, 0]),
       {"particle": V + "shard", "count": 8, "color": ["#C490FF", "#E6CCFF"], "size": [0.2, 0.05], "lifetime": [14, 22],
        "speed": [0.1, 0.3], "gravity": 0.3, "spin": [-10, 10], "alpha": [1, 0], "offset": [0, 1, 0], "shape": "sphere"},
       stars(["#FFFFFF", "#E6CCFF"], 4, (0.6, 0.05), [10, 16], speed=[0.1, 0.2], offset=[0, 1, 0]))

effect("rift_trail", "Riftstep: a lingering cut in the air along the path (played every block of it)",
       glow(VIOLET, (0.8, 0.1), 16, alpha=(0.6, 0), offset=[0, 1, 0], shape={"type": "sphere", "radius": 0.3}),
       {"particle": V + "wisp", "count": 2, "color": VIOLET, "size": [0.3, 0.1], "lifetime": [14, 22], "alpha": [0.7, 0],
        "rise": [0.01, 0.03], "drag": 0.05, "offset": [0, 1, 0], "shape": {"type": "sphere", "radius": 0.4}},
       sparks(["#FFFFFF", "#E6CCFF"], 3, [0.0, 0.08], [6, 12], gravity=-0.05, offset=[0, 1, 0], shape={"type": "sphere", "radius": 0.5}))

effect("rift_cut", "Riftstep: the rift's edge slices through a mob",
       glow("#FFFFFF", (1.6, 0.3), 5, alpha=(0.8, 0), offset=[0, 1, 0]),
       stars(["#FFFFFF", "#E6CCFF"], 3, (0.8, 0.1), 8, offset=[0, 1, 0]),
       sparks(["#FFFFFF", "#C490FF", "#E6CCFF"], 18, [0.2, 0.45], [6, 12], gravity=0.15, offset=[0, 1, 0]))

# ------------------------------------------------------------------------------------------------------ Mend
GREEN = ["#9BFFB0", "#5CE08A", "#E6FFB0", "#FFF2B0"]
effect("mend_cast", "Mend: green-gold light spirals up the caster, a soft ring and hearts",
       ring(["#9BFFB0"], (0.3, 2.2), 14, alpha=(0.8, 0)), ring(["#FFF2B0"], (0.2, 1.5), 12, delay=3, alpha=(0.7, 0)),
       helix("star", GREEN, 20, 0.7, 2.2, 2.5, (0.26, 0.05), 24, rise=0.04, anchor="feet"),
       helix("glow", ["#9BFFB0", "#E6FFB0"], 14, 0.55, 2.0, 2, (0.2, 0.05), 20, rise=0.045, delay=2, anchor="feet", alpha=[0.7, 0]),
       glow("#E6FFB0", (1.7, 0.4), 8, alpha=(0.5, 0), offset=[0, 1, 0], anchor="feet"),
       # head-anchored so the caster doesn't get hearts in the face in first person (others still see them)
       {"particle": "minecraft:heart", "count": 4, "delay": 3, "lifetime": 24, "offset": [0, -0.3, 0], "anchor": "head",
        "shape": {"type": "ring", "radius": 0.85, "even": True}, "rise": 0.03})
for e in effects["mend_cast"]["emitters"]:
    e.setdefault("anchor", "feet")

# ------------------------------------------------------------------------------------------------------ Boost abilities
def boost(name, desc, ring_color, main, accent, *extra, particle="star"):
    effect(name, desc,
           ring(ring_color, (0.3, 2.8), 12, alpha=(0.85, 0), anchor="feet"),
           ring(accent[:1], (0.2, 1.8), 10, delay=3, alpha=(0.7, 0), anchor="feet"),
           helix(particle, main, 20, 0.85, 2.3, 2, (0.24, 0.04), 24, rise=0.04, anchor="feet"),
           sparks(accent, 16, [0.12, 0.3], [10, 18], gravity=-0.05, anchor="feet", shape={"type": "ring", "radius": 0.6},
                  velocity=[0, 0.22, 0], offset=[0, 0.2, 0]),
           glow(accent[:1] + main[:1], (1.7, 0.4), 7, alpha=(0.5, 0), offset=[0, 1, 0], anchor="feet"),
           *extra)


boost("boost_gust_warm", "Bountiful Gust (warm): a hot wind of golden leaves and grain spins around the harvester",
      ["#FFD27A"], ["#FFE9A8", "#FFC46B", "#FF9A3A"], ["#FFE9A8", "#FFB03A"],
      {"particle": V + "leaf", "count": 14, "color": ["#E8B84A", "#D88A2A", "#F0D060"], "size": [0.22, 0.12], "lifetime": [24, 34],
       "alpha": [1, 0], "sway": 1.0, "gravity": 0.05, "spin": [-8, 8], "anchor": "feet", "shape": {"type": "orbit", "radius": 0.9},
       "orbit_speed": 9, "offset": [0, 0.3, 0], "rise": 0.04}, particle="spark")
boost("boost_gust_cold", "Bountiful Gust (cold): a frosty wind of blue leaves and snow around the harvester",
      ["#BFE6FF"], ["#E8F8FF", "#9EDCFF", "#FFFFFF"], ["#E8F8FF", "#7FC8FF"],
      {"particle": V + "leaf", "count": 12, "color": ["#9EDCFF", "#CFEFFF", "#7FC8FF"], "size": [0.22, 0.12], "lifetime": [24, 34],
       "alpha": [1, 0], "sway": 1.0, "gravity": 0.05, "spin": [-8, 8], "anchor": "feet", "shape": {"type": "orbit", "radius": 0.9},
       "orbit_speed": 9, "offset": [0, 0.3, 0], "rise": 0.04},
      {"particle": V + "snowflake", "count": 10, "color": ["#FFFFFF", "#DDF2FF"], "size": [0.12, 0.08], "lifetime": [20, 34],
       "alpha": [1, 0], "sway": 1.0, "gravity": 0.02, "anchor": "feet", "shape": {"type": "sphere", "radius": 1.0}, "offset": [0, 1, 0]},
      particle="star")
boost("boost_harvest_moon", "Harvest Moon: a pale moon-glow with silver stars rising over the field",
      ["#E8F0FF"], ["#FFFFFF", "#DDE8FF", "#FFF2B0"], ["#FFF2B0", "#CFE0FF"],
      {"particle": V + "glow", "count": 1, "color": "#F4F8FF", "size": [1.3, 1.6], "lifetime": 24, "alpha": [0.9, 0],
       "fade_in": 6, "offset": [0, 3.4, 0], "anchor": "feet", "drag": 0},
      particle="star")
boost("boost_molten", "Molten Core: a heat flash, embers and a ring of lava-light around the miner",
      ["#FF8A2A"], ["#FFD27A", "#FF8A2A", "#FF5A1A"], ["#FFD27A", "#FF6A2A"],
      {"particle": V + "ember", "count": 22, "color": {"gradient": ["#FFE9A8", "#FF8A2A", "#B02000"]}, "size": [0.12, 0.03],
       "lifetime": [22, 40], "rise": [0.03, 0.07], "alpha": [1, 0], "sway": 0.5, "anchor": "feet",
       "shape": {"type": "sphere", "radius": 0.9}, "offset": [0, 1, 0]},
      puff(["#3A2A28", "#5A4038"], 5, (0.3, 0.7), [20, 30], alpha=(0.35, 0), anchor="feet", offset=[0, 1.5, 0], rise=0.04),
      particle="ember")
boost("boost_lumen", "Lumen Pulse: cold white-cyan light pulses out of the miner and runs back in motes",
      ["#C8FAFF"], ["#FFFFFF", "#C8FAFF", "#9FE8FF"], ["#FFFFFF", "#9FE8FF"],
      ring(["#FFFFFF"], (0.2, 4.0), 14, delay=2, anchor="feet", alpha=(0.6, 0)),
      glow("#C8FAFF", (1.8, 0.4), 6, alpha=(0.6, 0), offset=[0, 1, 0], anchor="feet"),
      particle="glow")
boost("boost_skyward", "Skyward Burst: wind streaks and blue-white sparks shoot upward from the miner",
      ["#9FD8FF"], ["#FFFFFF", "#BFE6FF", "#9FD8FF"], ["#FFFFFF", "#9FD8FF"],
      sparks(["#FFFFFF", "#BFE6FF"], 18, [0.0, 0.05], [8, 14], size=(0.2, 0.03), gravity=-0.2, anchor="feet",
             shape={"type": "ring", "radius": 0.7}, velocity=[0, 0.5, 0], offset=[0, 0.1, 0]),
      column(["#BFE6FF", "#FFFFFF"], 6, 24, 10, size=(0.5, 0.15), anchor="feet"),
      particle="spark")
boost("boost_hellfire", "Hellfire Vein: red flame, embers and black smoke curl up around the miner",
      ["#FF3A1A"], ["#FF9A3A", "#FF3A1A", "#8A1000"], ["#FFD27A", "#FF3A1A"],
      {"particle": "minecraft:flame", "count": 14, "lifetime": 24, "anchor": "feet", "shape": {"type": "ring", "radius": 0.9},
       "rise": 0.05, "offset": [0, 0.1, 0], "speed": 0.01},
      puff(["#1A1010", "#3A2020"], 6, (0.3, 0.8), [20, 32], alpha=(0.4, 0), anchor="feet", offset=[0, 1.2, 0], rise=0.05),
      particle="ember")

# Treefeller
effect("treefeller_arm", "Treefeller: wood chips and green leaves whirl around the axe-bearer",
       ring(["#8FD870"], (0.3, 2.2), 12, anchor="feet", alpha=(0.8, 0)),
       {"particle": V + "leaf", "count": 14, "color": ["#5CA83A", "#8FD870", "#3E8A2A"], "size": [0.2, 0.12], "lifetime": [24, 36],
        "alpha": [1, 0], "sway": 0.8, "spin": [-8, 8], "anchor": "feet", "shape": {"type": "orbit", "radius": 0.9}, "orbit_speed": 10,
        "offset": [0, 0.4, 0], "rise": 0.04},
       {"particle": V + "shard", "count": 8, "color": ["#B07A44", "#8A5A2A", "#D2A06A"], "size": [0.14, 0.05], "lifetime": [18, 28],
        "alpha": [1, 0], "spin": [-10, 10], "gravity": 0.1, "anchor": "feet", "shape": {"type": "orbit", "radius": 0.7}, "orbit_speed": 12,
        "offset": [0, 1.0, 0]},
       sparks(["#E8FFC0", "#B8F090"], 10, [0.1, 0.25], [8, 14], anchor="feet", offset=[0, 1.1, 0], gravity=0.0))

effect("treefeller_armed", "Treefeller: a few leaves circle the axe-bearer while it waits for a log",
       {"particle": V + "leaf", "count": 3, "color": ["#5CA83A", "#8FD870", "#3E8A2A"], "size": [0.18, 0.1], "lifetime": [16, 24],
        "alpha": [1, 0], "sway": 0.8, "spin": [-8, 8], "anchor": "feet", "shape": {"type": "orbit", "radius": 0.8}, "orbit_speed": 11,
        "offset": [0, 0.9, 0], "rise": 0.02},
       sparks(["#E8FFC0", "#B8F090"], 2, [0.02, 0.06], [8, 12], anchor="feet", offset=[0, 1.0, 0], gravity=0.0, shape={"type": "sphere", "radius": 0.7}))

effect("treefeller_fell", "Treefeller: the tree comes down (a thump ring, bark, leaves and dust at its base)",
       ring(["#B8F090"], (0.4, 4.0), 12), ring(["#D2A06A"], (0.3, 2.6), 10, delay=2),
       {"particle": V + "leaf", "count": 36, "color": ["#5CA83A", "#8FD870", "#3E8A2A", "#C8E060"], "size": [0.24, 0.16],
        "lifetime": [30, 50], "speed": [0.12, 0.35], "gravity": 0.12, "sway": 1.0, "spin": [-10, 10], "alpha": [1, 0],
        "shape": {"type": "ring", "radius": 0.7}, "offset": [0, 0.6, 0], "velocity": [0, 0.25, 0]},
       {"particle": V + "shard", "count": 14, "color": ["#B07A44", "#8A5A2A", "#D2A06A"], "size": [0.2, 0.08], "lifetime": [18, 30],
        "speed": [0.12, 0.3], "gravity": 0.7, "spin": [-14, 14], "alpha": [1, 0], "shape": {"type": "ring", "radius": 0.4},
        "offset": [0, 0.3, 0], "velocity": [0, 0.25, 0]},
       puff(["#8A7A60", "#B0A080"], 10, (0.4, 1.0), [18, 28], speed=[0.1, 0.2], alpha=(0.45, 0), shape={"type": "ring", "radius": 0.5},
            offset=[0, 0.15, 0]))

# Echo Sense
effect("echo_pulse", "Echo Sense: sonar rings sweep out of the miner across the stone",
       ring(["#9FE8FF"], (0.4, 10), 22, alpha=(0.85, 0), anchor="feet"), ring(["#FFD27A"], (0.3, 8.5), 20, delay=4, alpha=(0.7, 0), anchor="feet"),
       ring(["#FFFFFF"], (0.2, 6), 16, delay=8, alpha=(0.5, 0), anchor="feet"),
       glow("#9FE8FF", (1.6, 0.3), 6, alpha=(0.5, 0), offset=[0, 1, 0], anchor="feet"),
       sparks(["#9FE8FF", "#FFD27A", "#FFFFFF"], 24, [0.3, 0.55], [14, 22], gravity=0.0, anchor="feet", offset=[0, 0.5, 0],
              shape={"type": "ring", "radius": 0.5}, size=(0.1, 0.03)))

effect("echo_ping", "Echo Sense: an ore answers the pulse with a flare",
       glow("#FFFFFF", (1.5, 0.3), 12, alpha=(0.7, 0), offset=[0, 0.5, 0]),
       ring(["#FFFFFF"], (0.15, 1.1), 12, orient="vertical", y=0.5), ring(["#9FE8FF"], (0.1, 1.4), 12, delay=2, y=0.5, orient="vertical"),
       stars(["#FFFFFF", "#9FE8FF"], 2, (0.5, 0.05), 12, offset=[0, 0.5, 0]))

# ------------------------------------------------------------------------------------------------------ Shields
def block_fx(name, desc, main, accent, *extra, sizes=(0.15, 1.5)):
    effect(name, desc,
           ring(main[:1], sizes, 8, orient="vertical", y=0, alpha=(0.95, 0)),
           ring(accent[:1], (0.05, sizes[1] * 0.6), 6, orient="vertical", y=0, delay=1, alpha=(0.8, 0)),
           glow("#FFFFFF", (1.1, 0.3), 5, alpha=(0.7, 0)),
           sparks(main + accent, 14, [0.12, 0.34], [7, 13], size=(0.2, 0.04), gravity=0.4, spread=0.04),
           stars(main[:2], 2, (0.75, 0.1), 8),
           *extra)


block_fx("shield_block", "A shield takes a hit: a flash, a ring and sparks at its face", ["#FFFFFF", "#DDE8F4"], ["#FFE08A"])
block_fx("shield_block_ember", "Emberforged Aegis takes a hit: embers and flame burst off the shield", ["#FFC46B", "#FF8A2A"], ["#FFE9A8"],
         {"particle": V + "ember", "count": 10, "color": {"gradient": ["#FFE9A8", "#FF8A2A", "#B02000"]}, "size": [0.12, 0.03],
          "lifetime": [14, 24], "speed": [0.05, 0.14], "rise": 0.03, "alpha": [1, 0]})
block_fx("shield_block_frost", "Frostward Barrier takes a hit: ice shards and snow", ["#E8F8FF", "#9EDCFF"], ["#FFFFFF"],
         {"particle": V + "snowflake", "count": 8, "color": ["#FFFFFF", "#DDF2FF"], "size": [0.14, 0.08], "lifetime": [14, 22],
          "speed": [0.06, 0.16], "alpha": [1, 0], "gravity": 0.1},
         {"particle": V + "shard", "count": 5, "color": ["#CFEFFF", "#9EDCFF"], "size": [0.18, 0.05], "lifetime": [10, 16],
          "speed": [0.1, 0.25], "gravity": 0.5, "spin": [-10, 10], "alpha": [1, 0]})
block_fx("shield_block_storm", "Thunderguard takes a hit: electricity crawls over the shield", ["#FFFFFF", "#BFE6FF"], ["#FFF6A0", "#7FC8FF"],
         sparks(ELECTRIC, 12, [0.2, 0.5], [5, 9], gravity=0.0, size=(0.18, 0.03)))
block_fx("shield_block_astral", "Astral Aegis takes a hit: violet stars scatter", ["#E6CCFF", "#C490FF"], ["#FFFFFF"],
         stars(["#FFFFFF", "#E6CCFF", "#C490FF"], 5, (0.5, 0.06), [10, 18], speed=[0.05, 0.16]))

effect("shield_bash", "Shield bash: a shove ring and dust where the attacker was hit",
       ring(["#FFFFFF"], (0.3, 2.0), 8), glow("#FFFFFF", (1.3, 0.3), 4, alpha=(0.6, 0), offset=[0, 0.8, 0]),
       sparks(["#FFFFFF", "#FFE08A"], 12, [0.15, 0.4], [6, 11], gravity=0.3, offset=[0, 0.8, 0]),
       puff(["#B8A890", "#8A7A68"], 5, (0.3, 0.7), [14, 20], shape={"type": "ring", "radius": 0.3}, offset=[0, 0.1, 0], speed=[0.1, 0.2]))

effect("shield_charge", "Thunderguard: a block charges the shield (a spark helix up the body)",
       helix("spark", ELECTRIC, 12, 0.6, 1.8, 2, (0.16, 0.03), 10, rise=0.06, anchor="feet"),
       glow("#BFE6FF", (1.4, 0.3), 5, alpha=(0.5, 0), offset=[0, 1, 0], anchor="feet"))


def shock(name, size):
    effect(name, "Thunderguard: the stored charges leave as a ring of lightning",
           ring(ELECTRIC, (0.4, size), 10, anchor="feet"), ring(["#FFF6A0"], (0.3, size * 0.75), 10, delay=2, anchor="feet"),
           glow("#FFFFFF", (2.4, 0.5), 5, alpha=(0.7, 0), offset=[0, 1, 0], anchor="feet"),
           sparks(ELECTRIC, 30, [0.3, 0.6], [8, 14], gravity=0.0, anchor="feet", offset=[0, 0.6, 0],
                  shape={"type": "ring", "radius": 0.5}, size=(0.16, 0.03)),
           helix("spark", ELECTRIC, 16, 0.9, 2.4, 2, (0.15, 0.03), 14, rise=0.08, anchor="feet"))


shock("shield_shockwave_s", 3.6)
shock("shield_shockwave_m", 5.2)
shock("shield_shockwave_l", 7.0)

effect("shield_reflect", "Astral Aegis: a projectile is turned round in a violet flash",
       glow("#FFFFFF", (1.3, 0.3), 5, alpha=(0.8, 0)), ring(["#E6CCFF"], (0.1, 1.8), 8, orient="vertical", y=0),
       ring(["#FFFFFF"], (0.1, 1.0), 6, delay=1, orient="vertical", y=0),
       stars(["#FFFFFF", "#E6CCFF", "#C490FF"], 6, (0.55, 0.06), [10, 18], speed=[0.08, 0.22]),
       sparks(VIOLET, 14, [0.15, 0.4], [6, 12], gravity=0.0))

# ------------------------------------------------------------------------------------------------------ Bows
effect("bow_ready", "A bow reaches full draw: a glint at the nocked arrow",
       stars(["#FFFFFF", "#FFF2B0"], 1, (0.5, 0.08), 8, anchor="main_hand", first_person=True, offset=[0, 0, 0.3]),
       glow("#FFF2B0", (0.7, 0.1), 6, alpha=(0.6, 0), anchor="main_hand", first_person=True, offset=[0, 0, 0.3]),
       sparks(["#FFFFFF", "#FFE08A"], 5, [0.04, 0.1], [5, 8], gravity=0.0, anchor="main_hand", first_person=True, offset=[0, 0, 0.3]))


def arrow_hit(name, desc, colors, *extra):
    effect(name, desc,
           glow("#FFFFFF", (1.0, 0.2), 4, alpha=(0.7, 0), offset=[0, 0.5, 0]),
           ring(colors[:1], (0.15, 1.7), 8, orient="vertical", y=0.5),
           sparks(colors + ["#FFFFFF"], 14, [0.12, 0.34], [7, 12], size=(0.2, 0.04), gravity=0.3, offset=[0, 0.5, 0], spread=0.03),
           stars(colors[:2], 2, (0.7, 0.08), 8, offset=[0, 0.5, 0]),
           *extra)


arrow_hit("arrow_hit", "An arrow lands: a quick flash and sparks", ["#FFE08A", "#FFC46B"])
arrow_hit("arrow_hit_pierce", "A piercing arrow punches through: a clean white streak burst", ["#FFFFFF", "#CFEFFF"],
          sparks(["#FFFFFF"], 8, [0.3, 0.55], [5, 9], gravity=0.0, offset=[0, 0.5, 0], size=(0.2, 0.03)))
arrow_hit("arrow_hit_star", "A Starfall Bow arrow lands: golden stars burst out", ["#FFF2B0", "#BFE6FF"],
          stars(STAR_WHITE, 4, (0.6, 0.06), [10, 16], speed=[0.08, 0.2], offset=[0, 0.5, 0]))
arrow_hit("arrow_hit_frost", "A Frostbite arrow lands: ice crystals shatter", ["#E8F8FF", "#9EDCFF"],
          {"particle": V + "shard", "count": 8, "color": ["#CFEFFF", "#9EDCFF"], "size": [0.18, 0.05], "lifetime": [12, 20],
           "speed": [0.1, 0.26], "gravity": 0.5, "spin": [-10, 10], "alpha": [1, 0], "offset": [0, 0.5, 0]},
          {"particle": V + "snowflake", "count": 6, "color": ["#FFFFFF"], "size": [0.14, 0.08], "lifetime": [14, 22],
           "speed": [0.05, 0.14], "alpha": [1, 0], "offset": [0, 0.5, 0]})
effect("arrow_burst", "An Ember Bow arrow explodes: fireball flash, shockwave ring, embers and smoke",
       glow("#FFFFFF", (1.5, 0.3), 4, alpha=(0.9, 0), offset=[0, 0.6, 0]),
       glow(["#FFC46B", "#FF8A2A"], (1.2, 0.3), 10, alpha=(0.7, 0), offset=[0, 0.6, 0]),
       ring(["#FFB060"], (0.3, 3.0), 10), ring(["#FFE9A8"], (0.2, 2.0), 8, delay=1),
       {"particle": V + "ember", "count": 24, "color": {"gradient": ["#FFE9A8", "#FF8A2A", "#B02000"]}, "size": [0.14, 0.03],
        "lifetime": [14, 28], "speed": [0.1, 0.32], "gravity": 0.3, "alpha": [1, 0], "offset": [0, 0.6, 0]},
       puff(["#3A2A28", "#5A4038"], 7, (0.4, 1.0), [18, 28], alpha=(0.45, 0), offset=[0, 0.6, 0]),
       sparks(["#FFE08A", "#FFC46B"], 14, [0.2, 0.5], [8, 14], gravity=0.5, offset=[0, 0.6, 0]))
effect("arrow_volley_shot", "Starfall Bow: three stars flare out of the bow as the volley leaves",
       stars(["#FFFFFF", "#FFF2B0", "#BFE6FF"], 4, (0.6, 0.06), 10, speed=[0.06, 0.16], anchor="main_hand", first_person=True,
             offset=[0, 0, 0.5]),
       glow("#FFF2B0", (1.0, 0.1), 5, alpha=(0.6, 0), anchor="main_hand", first_person=True, offset=[0, 0, 0.5]))
effect("arrow_lightning_shot", "Stormcaller Bow: a charged shot crackles as it leaves the bow",
       sparks(ELECTRIC, 10, [0.1, 0.3], [5, 9], gravity=0.0, anchor="main_hand", first_person=True, offset=[0, 0, 0.5], size=(0.16, 0.03)),
       glow("#BFE6FF", (1.0, 0.1), 5, alpha=(0.6, 0), anchor="main_hand", first_person=True, offset=[0, 0, 0.5]))


def main():
    shutil.rmtree(OUT, ignore_errors=True)
    OUT.mkdir(parents=True)
    for name, data in effects.items():
        (OUT / (name + ".json")).write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {len(effects)} effects to {OUT}")


if __name__ == "__main__":
    main()
