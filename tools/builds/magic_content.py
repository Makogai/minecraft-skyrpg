#!/usr/bin/env python3
"""Writes the data of the magic mod: spells, wands, their item definitions, bench recipes, sell prices, Seraphine's scroll shop,
VFX effects and bindings, and the language file.

Run:  python tools/builds/magic_content.py
Out:  magic/src/main/resources/data/skyrpg_magic/skyrpg/{spells,wands,items,recipes,sell_prices,shops,vfx,vfx_bindings}
      magic/src/main/resources/assets/skyrpg_magic/lang/en_us.json
Only magic's own folders are rewritten. Balance lives in the SPELLS / WANDS tables below; docs/design/magic.md explains them.
"""
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "magic/src/main/resources"
DATA = RES / "data/skyrpg_magic/skyrpg"
ASSETS = RES / "assets/skyrpg_magic"
NS = "skyrpg_magic:"
V = "skyrpg_vfx:"

# ------------------------------------------------------------------------------------------------------------ spells
# id: (name, element, rarity, mana, cooldown s, chargeable, description, params)
SPELLS = {
    "firebolt": ("Firebolt", "fire", "common", 8, 0.5, False,
                 "Hurl a fast bolt of fire. What it hits burns for three seconds.",
                 {"damage": 35, "speed": 1.9, "range": 36, "burn_dps": 8, "burn_seconds": 3}),
    "fireball": ("Fireball", "fire", "uncommon", 24, 3.0, True,
                 "Throw a fireball that explodes on impact and burns everything near it. Hold to charge it: a bigger, stronger blast. Breaks no blocks.",
                 {"damage": 70, "radius": 3, "radius_charged": 5.5, "charge_damage": 2.0, "speed": 1.2, "range": 40,
                  "burn_dps": 14, "burn_seconds": 4}),
    "meteor": ("Meteor", "fire", "legendary", 60, 12.0, True,
               "Call a meteor down on the spot you aim at. It lands after a moment and shatters everything around it. Hold to charge it: a wider, deadlier crash.",
               {"damage": 170, "radius": 5, "radius_charged": 8, "charge_damage": 2.0, "delay_ticks": 26, "range": 42,
                "burn_dps": 18, "burn_seconds": 5}),
    "frost_shard": ("Frost Shard", "frost", "common", 7, 0.6, False,
                    "A quick shard of ice that pierces up to three enemies and slows them.",
                    {"damage": 32, "speed": 2.3, "range": 34, "pierce": 3, "slow_level": 2, "slow_seconds": 2.5}),
    "blizzard": ("Blizzard", "frost", "rare", 50, 14.0, False,
                 "Whip up a snowstorm where you aim. For six seconds it hurts and slows everything inside.",
                 {"damage": 24, "radius": 5, "duration": 6, "range": 30}),
    "chain_lightning": ("Chain Lightning", "lightning", "rare", 22, 2.5, False,
                        "A bolt that leaps between up to four enemies, hitting each of them.",
                        {"damage": 52, "targets": 4, "jump_range": 7, "range": 26}),
    "thunderclap": ("Thunderclap", "lightning", "epic", 38, 8.0, False,
                    "Thunder rolls out from you: everything within six blocks is hurt, thrown back and stunned for a moment.",
                    {"damage": 62, "radius": 6, "knockback": 1.3, "stun_seconds": 1.4}),
    "arcane_missiles": ("Arcane Missiles", "arcane", "uncommon", 26, 4.0, False,
                        "Three homing missiles that fan out and seek your enemies.",
                        {"missiles": 3, "damage": 30, "speed": 1.15, "homing": 0.2}),
    "blink": ("Blink", "arcane", "uncommon", 16, 6.0, False,
              "Step through space up to eight blocks forward and land safely on your feet.",
              {"range": 8}),
    "mana_shield": ("Mana Shield", "arcane", "rare", 30, 20.0, False,
                    "For eight seconds, damage you would take is paid for from your mana instead: half a mana per point.",
                    {"duration": 8, "mana_per_damage": 0.5}),
    "healing_pulse": ("Healing Pulse", "nature", "common", 30, 6.0, False,
                      "A wave of green light that heals you and the party members near you.",
                      {"heal": 60, "radius": 8}),
    "entangle": ("Entangle", "nature", "uncommon", 25, 9.0, False,
                 "Vines burst from the ground and root enemies in an area for four seconds, hurting them.",
                 {"damage": 14, "radius": 4, "root_seconds": 4, "range": 26}),
}

# ------------------------------------------------------------------------------------------------------------- wands
# id: (name, rarity, slots, intelligence, bonus dict for wands/*.json, perks, description, level, ingredients, sell, held particles)
WANDS = {
    "apprentice_wand": ("Apprentice Wand", "common", 2, 20, {}, [],
                        "Birch with a lapis bead at the tip. Every mage starts with one.", 3,
                        {"minecraft:stick": 8, "minecraft:lapis_lazuli": 12, "skyrpg_enchants:runic_dust": 2}, 400,
                        [{"particle": "minecraft:enchant", "chance": 0.25, "rise": 0.01}]),
    "gilded_wand": ("Gilded Wand", "uncommon", 3, 40, {}, [],
                    "A slim wand banded in gold, with a glimmer crystal that hums when you hold it.", 10,
                    {"skyrpg_magic:apprentice_wand": 1, "minecraft:gold_ingot": 12, "skyrpg_mining:glimmer_crystal": 8,
                     "skyrpg_enchants:runic_dust": 4}, 2500,
                    [{"particle": "minecraft:wax_on", "chance": 0.3, "rise": 0.01}]),
    "skyiron_rod": ("Skyiron Rod", "rare", 3, 60, {"mana_discount": 0.1}, ["Spells cost 10% less mana"],
                    "A rod of folded skyiron. The metal bends the mana around it, so less of it is wasted.", 18,
                    {"skyrpg_magic:gilded_wand": 1, "skyrpg_mining:skyiron_ingot": 12, "skyrpg_combat:wisp_essence": 8,
                     "skyrpg_enchants:runic_dust": 8}, 9000,
                    [{"particle": "minecraft:electric_spark", "chance": 0.2, "rise": 0.01}]),
    "emberheart_staff": ("Emberheart Staff", "epic", 4, 90, {"element_power": {"fire": 0.25}}, ["Fire spells deal 25% more damage"],
                         "An emberwood staff around a heart of living flame. It is always warm.", 26,
                         {"skyrpg_magic:skyiron_rod": 1, "skyrpg_mining:emberite_ingot": 12, "skyrpg_combat:ember_core": 16,
                          "skyrpg_foraging:emberwood_log": 12, "skyrpg_enchants:runic_dust": 14}, 30000,
                         [{"particle": "minecraft:flame", "chance": 0.4, "rise": 0.02}, {"particle": "minecraft:smoke", "chance": 0.2, "rise": 0.02}]),
    "frostspire_staff": ("Frostspire Staff", "epic", 4, 90, {"element_power": {"frost": 0.25}, "slow_bonus": 0.5},
                         ["Frost spells deal 25% more damage", "Frost slows last 50% longer"],
                         "A staff of blue ice that never melts. Frost gathers on whatever it points at.", 26,
                         {"skyrpg_magic:skyiron_rod": 1, "skyrpg_mining:duskquartz_shard": 12, "skyrpg_combat:rime_shard": 16,
                          "skyrpg_world:frost_essence": 8, "skyrpg_enchants:runic_dust": 14}, 32000,
                         [{"particle": "minecraft:snowflake", "chance": 0.45, "rise": 0.0}]),
    "tempest_scepter": ("Tempest Scepter", "legendary", 5, 130, {"chain_bonus": 1}, ["Chain Lightning jumps one more time"],
                        "A scepter that crackles. The storm that made it is still inside.", 32,
                        {"skyrpg_magic:skyiron_rod": 1, "skyrpg_world:stormfeather": 12, "skyrpg_world:gale_essence": 8,
                         "skyrpg_mining:prism_fragment": 6, "skyrpg_combat:nightmare_core": 1, "skyrpg_enchants:runic_dust": 24}, 110000,
                        [{"particle": "minecraft:electric_spark", "chance": 0.6, "rise": 0.0}, {"particle": "minecraft:end_rod", "chance": 0.15, "rise": 0.01}]),
    "starweaver_staff": ("Starweaver Staff", "legendary", 5, 170, {"cooldown_discount": 0.2}, ["Spell cooldowns are 20% shorter"],
                         "Woven from starlight and astral crystal. Its spells are ready almost before you finish them.", 45,
                         {"skyrpg_magic:tempest_scepter": 1, "skyrpg_world:astral_crystal": 16, "skyrpg_mining:prism": 2,
                          "skyrpg_combat:nightmare_core": 2, "skyrpg_world:star_warden_core": 1, "skyrpg_enchants:runic_dust": 40}, 260000,
                         [{"particle": "minecraft:end_rod", "chance": 0.5, "rise": 0.01}, {"particle": "minecraft:glow", "chance": 0.15, "rise": 0.0}]),
}

# Scroll recipes at the bench (Enchanting skill). Common scrolls are Seraphine's; epic and legendary ones only drop. id: (level, ingredients)
SCROLL_RECIPES = {
    "fireball": (8, {"minecraft:paper": 8, "skyrpg_combat:ember_core": 6, "minecraft:blaze_powder": 8, "skyrpg_enchants:runic_dust": 3}),
    "blink": (8, {"minecraft:paper": 8, "minecraft:ender_pearl": 4, "minecraft:amethyst_shard": 6, "skyrpg_enchants:runic_dust": 3}),
    "arcane_missiles": (10, {"minecraft:paper": 8, "minecraft:amethyst_shard": 12, "skyrpg_combat:wisp_essence": 6, "skyrpg_enchants:runic_dust": 4}),
    "entangle": (10, {"minecraft:paper": 8, "minecraft:vine": 16, "skyrpg_farming:dew_vial": 4, "skyrpg_enchants:runic_dust": 3}),
    "chain_lightning": (20, {"minecraft:paper": 8, "skyrpg_world:stormfeather": 4, "skyrpg_mining:skyiron_ingot": 4, "skyrpg_enchants:runic_dust": 8}),
    "blizzard": (20, {"minecraft:paper": 8, "skyrpg_combat:rime_shard": 10, "skyrpg_world:frost_essence": 4, "skyrpg_enchants:runic_dust": 8}),
    "mana_shield": (22, {"minecraft:paper": 8, "skyrpg_mining:glimmer_crystal": 12, "skyrpg_combat:wisp_essence": 10, "skyrpg_enchants:runic_dust": 8}),
}

SCROLL_SELL = {"firebolt": 150, "frost_shard": 150, "healing_pulse": 200, "fireball": 800, "blink": 700, "arcane_missiles": 900,
               "entangle": 700, "chain_lightning": 4000, "blizzard": 5000, "mana_shield": 4500, "thunderclap": 18000, "meteor": 60000}
# What Seraphine charges: the common scrolls, at a few times what they sell for.
SERAPHINE = {"firebolt": 500, "frost_shard": 500, "healing_pulse": 700}

ELEMENT_NAMES = {"fire": "Fire", "frost": "Frost", "lightning": "Lightning", "arcane": "Arcane", "nature": "Nature"}


def dump(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


# ------------------------------------------------------------------------------------------------------------ VFX
effects = {}
bindings = {}


def effect(name, description, *emitters, range=48):
    effects[name] = {"description": description, "emitters": list(emitters), "range": range}


def pal(*c):
    return {"gradient": list(c)}


FIRE = pal("#FFE9A8", "#FF8A2A", "#B02000")
FROST = pal("#FFFFFF", "#BFEFFF", "#5FB8FF")
BOLT = pal("#FFFFFF", "#FFF3A0", "#B9D4FF")
ARCANE = pal("#FFFFFF", "#E0B8FF", "#9A4DFF")
NATURE = pal("#E8FFC0", "#7BE36B", "#2F9E44")


def glow(color, size, life=6, count=1, alpha=(0.8, 0), **kw):
    e = {"particle": V + "glow", "count": count, "color": color, "size": list(size), "lifetime": life, "alpha": list(alpha)}
    e.update(kw)
    return e


def burst(particle, color, count, speed, size, life, **kw):
    e = {"particle": V + particle, "count": count, "color": color, "speed": speed, "size": size, "lifetime": life,
         "alpha": [1, 0], "shape": "sphere"}
    e.update(kw)
    return e


def ring(color, size, life=10, delay=0, y=0.08, alpha=(0.8, 0), orient="flat"):
    e = {"particle": V + "ring", "count": 1, "color": color, "size": list(size), "lifetime": life, "alpha": list(alpha),
         "orient": orient, "offset": [0, y, 0]}
    if delay:
        e["delay"] = delay
    return e


def trail(name, desc, color, size, sparks, smoke=None, spark_particle="ember", extra=()):
    ems = [glow(color if not isinstance(color, str) else color, (size, size * 0.25), life=6, alpha=(0.9, 0)),
           {"particle": V + spark_particle, "count": sparks, "color": color, "size": [size * 0.3, 0.02], "lifetime": [10, 22],
            "speed": [0.0, 0.04], "drag": 0.08, "alpha": [1, 0], "shape": {"type": "sphere", "radius": size * 0.3}}]
    if smoke:
        ems.append({"particle": V + "smoke_puff", "count": 1, "color": smoke, "size": [size * 0.6, size * 1.4], "lifetime": [10, 18],
                    "alpha": [0.4, 0], "rise": [0.01, 0.03]})
    ems.extend(extra)
    effect(name, desc, *ems)


# Fire
trail("firebolt_trail", "Firebolt in flight", FIRE, 0.42, 2, smoke=["#2A2220", "#4A3A34"])
effect("firebolt_hit", "Firebolt impact",
       glow("#FFB04A", (0.9, 2.2), life=6),
       burst("ember", FIRE, 16, [0.08, 0.26], [0.12, 0.03], [14, 26], gravity=0.12),
       ring("#FF7A2A", (0.3, 2.2), life=7))
trail("fireball_trail", "Fireball in flight", FIRE, 0.9, 4, smoke=["#2A2220", "#4A3A34"],
      extra=[{"particle": V + "glow", "count": 1, "color": "#FFD08A", "size": [0.5, 0.2], "lifetime": 5, "alpha": [0.6, 0]}])
effect("fireball_explosion", "Fireball blast",
       glow("#FFB04A", (1.5, 5.5), life=8),
       burst("ember", FIRE, 36, [0.12, 0.4], [0.16, 0.04], [18, 34], gravity=0.1),
       {"particle": V + "smoke_puff", "count": 12, "color": ["#2A2220", "#4A3A34"], "size": [0.5, 1.6], "lifetime": [24, 40],
        "speed": [0.03, 0.12], "rise": [0.02, 0.06], "alpha": [0.55, 0], "shape": {"type": "sphere", "radius": 0.5}},
       ring("#FF6A1A", (0.5, 7), life=10), ring("#FFC46B", (0.3, 5), life=8, delay=2))
effect("fireball_explosion_big", "Charged fireball blast",
       glow("#FFD08A", (2.5, 9), life=10),
       burst("ember", FIRE, 70, [0.15, 0.6], [0.2, 0.05], [22, 44], gravity=0.1),
       {"particle": V + "smoke_puff", "count": 22, "color": ["#2A2220", "#4A3A34"], "size": [0.8, 2.4], "lifetime": [30, 50],
        "speed": [0.04, 0.16], "rise": [0.02, 0.07], "alpha": [0.6, 0], "shape": {"type": "sphere", "radius": 0.8}},
       ring("#FF6A1A", (0.6, 12), life=12), ring("#FFC46B", (0.4, 9), life=10, delay=2), ring("#FFFFFF", (0.3, 6), life=8, delay=4))
effect("meteor_marker", "Where the meteor will land",
       ring("#FF4A1A", (6, 10), life=6, alpha=(0.85, 0.2)), ring("#FF9A3A", (3, 5), life=6, alpha=(0.7, 0.1)),
       {"particle": V + "rune", "count": 6, "color": "#FF7A2A", "size": [0.3, 0.2], "lifetime": 10, "alpha": [0.9, 0],
        "shape": {"type": "ring", "radius": 3.6}, "offset": [0, 0.1, 0]}, range=64)
effect("meteor_trail", "A meteor falling",
       glow("#FFB04A", (1.8, 0.5), life=5),
       burst("ember", FIRE, 5, [0.0, 0.08], [0.4, 0.05], [14, 28], gravity=0.06),
       {"particle": V + "smoke_puff", "count": 2, "color": ["#2A2220", "#4A3A34"], "size": [1.0, 2.2], "lifetime": [16, 28],
        "alpha": [0.5, 0], "shape": {"type": "sphere", "radius": 0.6}}, range=96)
effect("meteor_impact", "Meteor crash",
       glow("#FFD08A", (4, 14), life=12),
       burst("ember", FIRE, 110, [0.2, 0.8], [0.26, 0.06], [26, 52], gravity=0.12),
       {"particle": V + "smoke_puff", "count": 34, "color": ["#2A2220", "#4A3A34", "#6A5A50"], "size": [1.2, 3.2], "lifetime": [34, 64],
        "speed": [0.05, 0.22], "rise": [0.03, 0.09], "alpha": [0.65, 0], "shape": {"type": "sphere", "radius": 1.0}},
       {"particle": V + "shard", "count": 30, "color": ["#3A2A22", "#5A4636", "#FF8A2A"], "size": [0.2, 0.1], "lifetime": [20, 40],
        "speed": [0.2, 0.7], "gravity": 0.5, "alpha": [1, 0], "shape": "sphere"},
       ring("#FF4A1A", (1, 18), life=14), ring("#FF9A3A", (0.6, 13), life=12, delay=2), ring("#FFFFFF", (0.4, 8), life=10, delay=4), range=96)

# Frost
trail("frost_trail", "Frost Shard in flight", FROST, 0.34, 2, spark_particle="star", extra=[
    {"particle": V + "snowflake", "count": 1, "color": ["#FFFFFF", "#CFEFFF"], "size": [0.14, 0.06], "lifetime": [14, 24], "gravity": 0.02,
     "sway": 0.8, "alpha": [1, 0], "shape": {"type": "sphere", "radius": 0.2}}])
effect("frost_hit", "Frost Shard impact",
       glow("#BFEFFF", (0.8, 1.8), life=6),
       burst("shard", ["#FFFFFF", "#BFEFFF", "#5FB8FF"], 14, [0.1, 0.3], [0.14, 0.04], [12, 22], gravity=0.12),
       burst("snowflake", ["#FFFFFF", "#DDF2FF"], 10, [0.04, 0.14], [0.14, 0.08], [20, 34], gravity=0.03),
       ring("#9EDCFF", (0.3, 2.4), life=7))
effect("blizzard_burst", "A blizzard starts",
       glow("#DDF4FF", (2, 7), life=10),
       burst("snowflake", ["#FFFFFF", "#DDF2FF", "#BFE6FF"], 50, [0.1, 0.4], [0.2, 0.08], [24, 46], gravity=0.02, sway=0.8),
       ring("#9EDCFF", (0.5, 11), life=12), ring("#FFFFFF", (0.3, 8), life=10, delay=3))
effect("blizzard_field", "The snowstorm",
       {"particle": V + "snowflake", "count": [5, 8], "color": ["#FFFFFF", "#DDF2FF", "#BFE6FF"], "size": [0.18, 0.1], "lifetime": [26, 44],
        "gravity": [0.04, 0.09], "sway": 1.2, "spin": [-6, 6], "alpha": [0.95, 0], "shape": {"type": "disc", "radius": 5}, "offset": [0, 4.5, 0],
        "velocity": [0.12, -0.02, 0.06]},
       {"particle": V + "wisp", "count": 2, "color": ["#EAF6FF", "#BFE6FF"], "size": [0.8, 1.6], "lifetime": [16, 26], "alpha": [0.3, 0],
        "shape": {"type": "disc", "radius": 4.5}, "offset": [0, 0.6, 0], "velocity": [0.1, 0, 0.05]},
       ring("#9EDCFF", (10, 10.4), life=4, alpha=(0.55, 0.3)))

# Lightning
effect("lightning_hit", "Lightning strikes a target",
       glow("#FFF6B0", (1.2, 3.2), life=4),
       burst("spark", BOLT, 18, [0.15, 0.45], [0.18, 0.03], [8, 16], drag=0.1),
       burst("star", ["#FFFFFF", "#FFF3A0"], 5, [0.05, 0.2], [0.3, 0.05], [8, 14]),
       ring("#FFF3A0", (0.3, 2.6), life=6))
effect("thunderclap", "The shockwave of a Thunderclap",
       glow("#FFF6B0", (2, 8), life=8),
       ring("#FFF3A0", (0.5, 13), life=9), ring("#FFFFFF", (0.3, 9), life=8, delay=1), ring("#9ABEFF", (0.2, 6), life=7, delay=3),
       burst("spark", BOLT, 46, [0.25, 0.9], [0.2, 0.03], [10, 20], drag=0.08),
       {"particle": V + "smoke_puff", "count": 10, "color": ["#EEF4FF", "#C7D6EE"], "size": [0.6, 1.8], "lifetime": [18, 30],
        "speed": [0.06, 0.24], "alpha": [0.45, 0], "shape": {"type": "ring", "radius": 1.5}, "offset": [0, 0.3, 0]})
effect("stun_stars", "Stars over a stunned head",
       {"particle": V + "star", "count": 1, "every": 2, "duration": 24, "color": ["#FFF3A0", "#FFFFFF"], "size": [0.22, 0.12], "lifetime": 14,
        "alpha": [1, 0], "shape": {"type": "orbit", "radius": 0.45}})

# Arcane
trail("arcane_trail", "Arcane Missile in flight", ARCANE, 0.4, 2, spark_particle="star", extra=[
    {"particle": V + "wisp", "count": 1, "color": ["#C79BFF", "#7A3DFF"], "size": [0.3, 0.1], "lifetime": [10, 18], "alpha": [0.6, 0]}])
effect("arcane_hit", "Arcane Missile impact",
       glow("#E0B8FF", (0.8, 2.0), life=6),
       burst("star", ARCANE, 12, [0.06, 0.24], [0.2, 0.04], [12, 22]),
       ring("#C79BFF", (0.3, 2.4), life=7))
effect("blink_flash", "A Blink, at either end",
       glow("#E9D2FF", (1.6, 3.4), life=6),
       burst("star", ARCANE, 26, [0.08, 0.34], [0.22, 0.04], [14, 26]),
       ring("#C79BFF", (0.4, 3.4), life=8, orient="vertical"), ring("#FFFFFF", (0.3, 2.4), life=7, delay=1, orient="vertical"))
effect("mana_shield_cast", "Mana Shield goes up",
       ring("#8FA8FF", (0.5, 5), life=10), ring("#C79BFF", (0.4, 4), life=10, delay=2),
       {"particle": V + "star", "count": 14, "color": ARCANE, "size": [0.2, 0.05], "lifetime": [16, 28], "rise": [0.02, 0.07],
        "alpha": [1, 0], "shape": {"type": "helix", "radius": 1.0, "height": 2.2, "turns": 2, "even": True}})
effect("mana_shield_aura", "The shield around you",
       {"particle": V + "glow", "count": 4, "color": ["#8FA8FF", "#C79BFF", "#7ACBFF"], "size": [0.4, 0.1], "lifetime": [14, 22],
        "alpha": [0.55, 0], "shape": {"type": "orbit", "radius": 0.95}, "orbit_speed": 9},
       {"particle": V + "star", "count": 1, "color": "#FFFFFF", "size": [0.18, 0.04], "lifetime": [10, 16], "alpha": [1, 0],
        "shape": {"type": "sphere", "radius": 0.9, "surface": True}}, range=32)
effect("shield_absorb", "The shield soaks a hit",
       glow("#A8B8FF", (1.2, 2.6), life=5), burst("star", ARCANE, 10, [0.1, 0.3], [0.2, 0.04], [10, 18]),
       ring("#8FA8FF", (0.4, 3), life=6, orient="vertical"))
effect("shield_break", "The shield shatters",
       burst("shard", ["#8FA8FF", "#C79BFF", "#FFFFFF"], 34, [0.15, 0.5], [0.2, 0.05], [16, 30], gravity=0.12),
       glow("#C79BFF", (1.6, 4), life=7), ring("#8FA8FF", (0.4, 4), life=9))

# Nature
effect("healing_pulse", "A Healing Pulse spreads out",
       glow("#B8FFB0", (1.4, 4), life=8),
       ring("#7BE36B", (0.5, 17), life=14), ring("#E8FFC0", (0.3, 11), life=12, delay=2),
       {"particle": V + "leaf", "count": 26, "color": NATURE, "size": [0.2, 0.1], "lifetime": [24, 44], "speed": [0.06, 0.2],
        "rise": [0.04, 0.1], "sway": 0.6, "spin": [-6, 6], "alpha": [1, 0], "shape": {"type": "disc", "radius": 4}},
       {"particle": V + "star", "count": 12, "color": ["#E8FFC0", "#FFFFFF"], "size": [0.2, 0.04], "lifetime": [16, 30],
        "rise": [0.05, 0.1], "alpha": [1, 0], "shape": {"type": "disc", "radius": 3}})
effect("heal_target", "Someone is healed",
       {"particle": V + "star", "count": 14, "color": NATURE, "size": [0.2, 0.04], "lifetime": [18, 30], "rise": [0.03, 0.08],
        "alpha": [1, 0], "shape": {"type": "helix", "radius": 0.6, "height": 1.8, "turns": 2, "even": True}},
       glow("#B8FFB0", (0.8, 2.2), life=6))
effect("entangle_burst", "Vines burst from the ground",
       glow("#9BEA8A", (1.2, 4), life=8),
       {"particle": V + "leaf", "count": 40, "color": NATURE, "size": [0.24, 0.1], "lifetime": [20, 40], "speed": [0.08, 0.3],
        "rise": [0.06, 0.16], "gravity": 0.06, "spin": [-8, 8], "alpha": [1, 0], "shape": {"type": "disc", "radius": 3.2}},
       ring("#4FB85A", (0.5, 9), life=12), ring("#9BEA8A", (0.3, 6), life=10, delay=2),
       {"particle": V + "shard", "count": 24, "color": ["#2F7A33", "#4FB85A"], "size": [0.2, 0.08], "lifetime": [14, 26],
        "rise": [0.1, 0.22], "alpha": [1, 0], "shape": {"type": "disc", "radius": 3.4}})
effect("entangle_roots", "Roots hold a target",
       {"particle": V + "shard", "count": 10, "color": ["#2F7A33", "#4FB85A", "#7BE36B"], "size": [0.22, 0.1], "lifetime": [12, 20],
        "rise": [0.06, 0.14], "alpha": [1, 0], "shape": {"type": "ring", "radius": 0.5, "even": True}},
       {"particle": V + "leaf", "count": 4, "color": NATURE, "size": [0.2, 0.1], "lifetime": [16, 26], "rise": [0.03, 0.07],
        "alpha": [1, 0], "shape": {"type": "ring", "radius": 0.6}})
effect("entangle_field", "The grasping ground",
       {"particle": V + "leaf", "count": [2, 3], "color": NATURE, "size": [0.2, 0.1], "lifetime": [20, 34], "rise": [0.03, 0.08],
        "sway": 0.7, "alpha": [0.9, 0], "shape": {"type": "disc", "radius": 4}},
       ring("#4FB85A", (7.6, 8), life=6, alpha=(0.5, 0.2)))

# Casting flashes at the wand's tip, one per element (anchored on the caster's hand)
def cast_flash(name, color, spark, hexcolor):
    effect(name, "Flash at the wand when a spell goes off",
           {"particle": V + "glow", "count": 1, "color": hexcolor, "size": [0.7, 1.5], "lifetime": 5, "alpha": [0.9, 0],
            "anchor": "right_hand", "offset": [0, 0.15, 0.55]},
           {"particle": V + spark, "count": 10, "color": color, "size": [0.14, 0.03], "lifetime": [8, 16], "speed": [0.05, 0.2],
            "anchor": "right_hand", "offset": [0, 0.15, 0.55], "alpha": [1, 0], "shape": "sphere"}, range=40)


cast_flash("cast_fire", FIRE, "ember", "#FFB04A")
cast_flash("cast_frost", FROST, "star", "#BFEFFF")
cast_flash("cast_lightning", BOLT, "spark", "#FFF6B0")
cast_flash("cast_arcane", ARCANE, "star", "#E0B8FF")
cast_flash("cast_nature", NATURE, "leaf", "#B8FFB0")


# Held glows at the tip of each wand family (bindings)
def wand_glow(name, color, hexcolor, spark):
    effect(name, "A wand's tip glows and sheds sparks",
           {"particle": V + "glow", "count": 1, "color": hexcolor, "size": [0.34, 0.12], "lifetime": 8, "alpha": [0.55, 0],
            "anchor": "hand", "offset": [0.0, 0.25, 0.55]},
           {"particle": V + spark, "count": 1, "chance": 0.5, "color": color, "size": [0.1, 0.02], "lifetime": [14, 26],
            "rise": [0.0, 0.02], "drag": 0.05, "alpha": [1, 0], "anchor": "hand", "offset": [0.0, 0.25, 0.55],
            "shape": {"type": "sphere", "radius": 0.12}}, range=32)


wand_glow("wand_arcane", ARCANE, "#C79BFF", "star")
wand_glow("wand_fire", FIRE, "#FF8A2A", "ember")
wand_glow("wand_frost", FROST, "#9EDCFF", "snowflake")
wand_glow("wand_lightning", BOLT, "#FFF3A0", "spark")
wand_glow("wand_star", pal("#FFFFFF", "#FFF3C0", "#FFD76B"), "#FFE9A8", "star")


def bind(name, effect_name, items, rate=0.7):
    bindings[name] = {"description": "Wand tip glow", "effect": NS + effect_name, "rate": rate, "range": 24, "self": True,
                      "held": {"items": [NS + i for i in items], "hand": "main"}}


bind("held_arcane", "wand_arcane", ["apprentice_wand", "gilded_wand", "skyiron_rod"])
bind("held_fire", "wand_fire", ["emberheart_staff"])
bind("held_frost", "wand_frost", ["frostspire_staff"])
bind("held_lightning", "wand_lightning", ["tempest_scepter"], rate=1.0)
bind("held_star", "wand_star", ["starweaver_staff"])


# ------------------------------------------------------------------------------------------------------------ write
def main():
    for sub in ("spells", "wands", "items", "recipes", "sell_prices", "shops", "vfx", "vfx_bindings"):
        shutil.rmtree(DATA / sub, ignore_errors=True)
    lang = {"itemGroup.skyrpg_magic": "SkyRPG Magic", "key.skyrpg_magic.wheel": "Spell wheel (hold)",
            "key.skyrpg_magic.wheel_toggle": "Spell wheel (toggle)"}
    for sid, (name, element, rarity, mana, cooldown, chargeable, desc, params) in SPELLS.items():
        d = {"name": name, "element": element, "rarity": rarity, "mana": mana, "cooldown": cooldown, "description": desc, "params": params}
        if chargeable:
            d["chargeable"] = True
            d["charge_seconds"] = 1.5
        dump(DATA / "spells" / f"{sid}.json", d)
        item = {"name": f"{name} Scroll", "rarity": rarity, "type": "Spell Scroll", "description": desc}
        if rarity in ("epic", "legendary"):
            item["glint"] = True
        dump(DATA / "items" / f"{sid}_scroll.json", item)
        lang[f"item.skyrpg_magic.{sid}_scroll"] = f"{name} Scroll"
    for wid, (name, rarity, slots, intelligence, bonus, perks, desc, level, ingredients, sell, held) in WANDS.items():
        w = {"slots": slots}
        w.update(bonus)
        dump(DATA / "wands" / f"{wid}.json", w)
        item = {"name": name, "rarity": rarity, "type": "Wand", "stats": {"intelligence": intelligence},
                "perks": [f"{slots} spell slots"] + perks, "description": desc, "held_particles": held}
        if rarity in ("epic", "legendary"):
            item["glint"] = True
        dump(DATA / "items" / f"{wid}.json", item)
        recipe = {"category": "enchanting", "result": f"skyrpg_magic:{wid}",
                  "ingredients": ingredients, "requires": {"skill": "skyrpg_skills:enchanting", "level": level}}
        dump(DATA / "recipes" / f"{wid}.json", recipe)
        lang[f"item.skyrpg_magic.{wid}"] = name
    for sid, (level, ingredients) in SCROLL_RECIPES.items():
        dump(DATA / "recipes" / f"{sid}_scroll.json", {"category": "enchanting", "result": f"skyrpg_magic:{sid}_scroll",
                                                       "ingredients": ingredients, "requires": {"skill": "skyrpg_skills:enchanting", "level": level}})
    prices = {f"skyrpg_magic:{w}": v[9] for w, v in WANDS.items()}
    prices.update({f"skyrpg_magic:{s}_scroll": p for s, p in SCROLL_SELL.items()})
    dump(DATA / "sell_prices" / "magic.json", prices)
    dump(DATA / "shops" / "arcane_scrolls.json", {
        "title": "Seraphine's Scrolls", "keeper": "Seraphine Vael", "accent": "#B98CFF", "icon": "skyrpg_magic:firebolt_scroll",
        "offers": [{"item": f"skyrpg_magic:{s}_scroll", "price": p} for s, p in SERAPHINE.items()],
        "buys": [f"skyrpg_magic:{s}_scroll" for s in SPELLS], "buys_hint": "Spell scrolls"})
    for name, e in effects.items():
        dump(DATA / "vfx" / f"{name}.json", e)
    for name, b in bindings.items():
        dump(DATA / "vfx_bindings" / f"{name}.json", b)
    dump(ASSETS / "lang" / "en_us.json", lang)
    print(f"{len(SPELLS)} spells, {len(WANDS)} wands, {len(effects)} effects, {len(bindings)} bindings")


if __name__ == "__main__":
    main()
