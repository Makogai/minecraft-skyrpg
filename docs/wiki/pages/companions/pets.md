---
title: Pets
category: companions
order: 1
icon: skyrpg_pets:golden_pet_egg
summary: Five pets, one per skill: how to get them, rarities, stats, perks, levelling and the Pets page.
---
Pets are small companions that float at your shoulder and give you stats while they are out. There is one kind of pet for each of five skills, and your pet **levels up as you earn XP** in its skill. At levels 25 and 50 it unlocks a perk. You keep a collection of pets in storage and swap the one that is out whenever you like.

## The pets

Every pet also gives **0.1 Insight per level** in its skill, times its rarity bonus: +5 at level 50 Common, +10 at level 50 Legendary.

| Pet | Skill | Stats per level (Common) | Level 25 perk | Level 50 perk |
| --- | --- | --- | --- | --- |
| {{item:skyrpg_pets:mole_pet\|Mole}} | Mining | +2 Mining Speed, +0.8 Mining Fortune | Nodes you mine regrow 15% faster | A free Echo Sense every minute |
| {{item:skyrpg_pets:bee_pet\|Bumblebee}} | Farming | +1.6 Farming Fortune, +0.2 Speed | 15% more Dewdrops | Crops within 4 blocks of you grow faster |
| {{item:skyrpg_pets:owl_pet\|Owl}} | Foraging | +1.6 Foraging Fortune, +0.2 Speed | Falls 3 blocks further do not hurt | Rare finds from logs 50% more likely |
| {{item:skyrpg_pets:pup_pet\|Wolf Pup}} | Combat | +0.8 Strength, +0.6 Crit Damage | Heals 2% of the melee damage you deal | Critical hits heal 3% of your health |
| {{item:skyrpg_pets:otter_pet\|Otter}} | Fishing | +0.8 Fishing Speed, +0.08% Sea Creature Chance | 5% chance a catch comes up twice | Breathe and swim easily underwater |
| {{item:skyrpg_pets:jack_pet\|Jack o'Lantern}} | Farming | +1.4 Farming Fortune, +0.6 Strength | Rare crop finds 25% more likely | Heals 2% of the melee damage you deal |

Stats grow with level, so a pet is worth more the longer you keep it. Only the pet that is **out** gives stats, perks and earns XP.

## Rarity

A pet's rarity is rolled when its egg hatches and never changes. It multiplies the pet's stats:

| Rarity | Stat multiplier | Chance from an egg | Chance from a Golden Pet Egg |
| --- | --- | --- | --- |
| Common | x1.0 | 60% | Not possible |
| Uncommon | x1.25 | 25% | Not possible |
| Rare | x1.5 | 10% | 66.7% |
| Epic | x1.75 | 4% | 26.7% |
| Legendary | x2.0 | 1% | 6.7% |

At level 50 the stats from the table are 50 times the per-level numbers, then multiplied by the rarity. A Common Mole gives 100 Mining Speed and 40 Mining Fortune, and a Legendary Mole gives 200 and 80.

## Getting a pet

Pets come from **eggs**. Right-click an egg to hatch it straight into your pet storage.

| Egg | Hatches | Where to get it |
| --- | --- | --- |
| {{item:skyrpg_pets:mole_egg}} | Mole | Tamsin Deepholt, 48 Glimmer Crystals, Mining 10 |
| {{item:skyrpg_pets:bee_egg}} | Bumblebee | Mabel Thornfield, 6 Dew Vials, Farming 10 |
| {{item:skyrpg_pets:owl_egg}} | Owl | Oswin Fernhollow, 6 Heartwood Bundles, Foraging 10 |
| {{item:skyrpg_pets:pup_egg}} | Wolf Pup | Kestrel Vane, 64 Valor Marks, Combat 10 |
| {{item:skyrpg_pets:otter_egg}} | Otter | Marina Saltwick, 6 Tidescale Plates, Fishing 10 |
| {{item:skyrpg_pets:golden_pet_egg}} | A random pet, Rare or better | Boss drops and dungeon vaults and reward chests |
| (no egg) | Jack o'Lantern | Only from the Hallow's Crate: Epic (pink tier) or Legendary (gold tier). See [Crates](crates) |

The Golden Pet Egg is a 6% bonus drop from the world bosses (the Hollow King and the Mother of Thorns), and also drops from dungeon bosses' reward loot, and from dungeon vaults and secret chests (see [World bosses](world-bosses) and [Dungeons overview](dungeons-overview)).

## Levelling

A pet's level goes from 1 to 50. The XP it needs grows quickly: 250 times (level minus 1) squared in total, so level 10 is 20,250 XP, level 25 is 144,000 XP and level 50 is 600,250 XP.

- Your active pet earns **25% of the XP you earn in its skill**, and **5% of the XP from other skills**.
- Stored pets keep their level and XP, but do not earn any.
- At level 25 and 50 you get a chat notice with the new perk. At level 50 it shows MAX LEVEL.

> [!TIP]
> A pet earns XP while you play, so equip the pet for what you are about to do (a Mole for mining, an Otter for fishing). Switching is free, and the others keep what they have.

## Pet storage

You keep your pets in **pet storage**, a saved list of 30 pets (the server can change this number).

- **Right-click a pet item** to move it into storage. If no pet is out, it also goes out.
- Eggs hatch straight into storage. A full storage will not hatch an egg, so make room first.
- The pet that is out floats over your shoulder for everyone to see.
- Pet items already in your inventory stay items until you right-click them.

### The Pets page

Open it from the SkyRPG menu (K). It shows a live 3D preview of the selected pet (it turns slowly and leans towards the mouse) with its rarity, name, level and XP bar. On the right you see its skill, stats and perks, then your **pet storage** with a count like `12 / 30`: a grid with the active pet (green mark) and every stored pet, with rarity border, icon, name and level.

Click a pet to select it, then use the buttons:

- **Equip** puts it out (the old pet goes back to its place in storage).
- **Unequip** puts the active pet back in storage.
- **Take out** moves it into your inventory as an item, to trade or give away. It is blocked while your inventory is full.

### Commands

| Command | What it does |
| --- | --- |
| `/pet` | Shows your active pet |
| `/pet away` | Puts it into storage |
| `/pet storage` | Lists your stored pets |
| `/pet give <player> <type> <rarity>` | Operators only: adds a pet to someone's storage |

## What pets look like

The pet is a real 3D model beside your right shoulder, for everyone, in third person and in the inventory and K-menu previews. Each kind has its own look: the Mole wears a miner's helmet with a lamp, the Bumblebee has fuzzy translucent wings, the Owl has big eyes and ear tufts, the Wolf Pup has floppy ears and a wagging tail, and the Otter holds a pebble or a fish.

Rarity changes the look too: Common is plain, Uncommon and Rare change colours and add small accessories (a flower, a bandana, a sprig, a pickaxe, goggles), Epic adds a bigger accessory, and Legendary adds a crown or halo and a glow. A pet also gives off a rarity-coloured aura for nearby players. It trails slightly behind you when you move and leans into the movement.

## See also

- [Mounts](mounts)
- [Skills overview](skills-overview)
- [Mining](mining), [Farming](farming), [Foraging](foraging), [Fishing](fishing)
- [Pet list](pet-list)
- [K menu](k-menu)
