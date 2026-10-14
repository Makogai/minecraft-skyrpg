---
title: Danger Regions
category: world
order: 3
icon: skyrpg_world:dread_crystal
summary: Corrupted patches of the Overworld with much stronger monsters. Where they are, the six types, what lives there and what they drop.
---
A **danger region** is a big patch of the Overworld where the land itself is corrupted and the monsters are many levels stronger than the ground around them. Regions show **red on your map**, with the region's name at the centre. They are the best place to find regional materials (essences) and the rare Dread Crystal, and the best way to get killed if you wander in unprepared.

## How regions are placed

Regions come from the world seed, so every world is different, but the rules are the same:

- The Overworld is cut into **768-block cells**. Each cell has a **24% chance** to hold one region. That works out to roughly one region every 1,600 blocks.
- The centre falls in the middle half of its cell. The region's radius is **150 to 230 blocks**, and its edge wobbles, so every region is a different blob rather than a circle.
- The **biome at the centre** decides the type. If no type wants that biome (oceans, for example), the cell has no region.
- Nothing is placed within **450 blocks** of the world spawn.
- Where two regions overlap, you count as being in the one you are deeper inside.

### Which type you get

| Region | Centre biomes | Danger | Monster levels |
| --- | --- | --- | --- |
| The Blightlands | forests, taiga | 3 skulls | +15 |
| The Ashen Wastes | savanna, badlands, desert | 4 skulls | +18 |
| Frostgrave | snowy plains, snowy taiga, ice spikes, groves, snowy slopes | 4 skulls | +18 |
| The Mire | swamps, mangrove swamps | 2 skulls | +12 |
| The Haunted Moors | plains, sunflower plains, meadows | 3 skulls | +16 |
| The Shattered Highlands | stony, jagged and frozen peaks, windswept hills, gravelly hills and forest | 4 skulls | +20 |

A snowy taiga can be either Blightlands or Frostgrave (Blightlands is slightly more likely).

## Finding and entering one

- Regions are drawn on the **world map and minimap** as a red wash, strongest at the border, with a marker at the centre. Hover the marker to see its danger.
- `/region` lists the regions near you, with distance, in the form "Frostgrave ☠☠☠☠ at x, z (r 190, 640 blocks away)". Anyone can use it. Ops have `/region list` (3,000 blocks) and `/region nearest <type>` (30,000 blocks).
- When you cross the border, you get a **red title** with the region's name, its skulls and "Monsters +N levels", and a low sound. A message tells you when you leave.
- Inside, particles drift around you (crimson spores, ash, snowflakes, spores or clouds) and the **fog** changes colour.

> [!WARNING]
> The monster bonus is added **on top of** ordinary distance levels, and it is not capped at 50. Far from spawn, a Shattered Highlands monster is level 50, and a Blood Moon adds 10 more.

## What changes inside

- **The land.** Chunks generated after the update are rewritten: ground, trees and plants change to the region's own blocks, from 40 blocks above the ground to 4 below, and the edge frays over 12 blocks. Chunks that existed before keep their old look, but the danger still applies.
- **The monsters.** Every monster that spawns inside gets the region's level bonus, natural or not.
- **Roaming groups.** Every 3 seconds, near each survival player inside, the region may spawn a group from its own list 18 to 36 blocks away, up to a cap of 8 or 9 of its monsters within 48 blocks. These can despawn normally.
- **Drops.** Monsters killed by a player inside roll the region's drops: its **essence** (35%, 1 to 2) and a **Dread Crystal** (0.8%). Farmed mobs do not drop them.
- **A heart.** The centre holds a shrine and a boss. See [Region Hearts](region-hearts).

### Essences and Dread Crystals

{{items:skyrpg_world:blight_essence, skyrpg_world:cinder_essence, skyrpg_world:frost_essence, skyrpg_world:mire_essence, skyrpg_world:ectoplasm, skyrpg_world:gale_essence, skyrpg_world:dread_crystal}}

The Haunted Moors drop **Ectoplasm** and the Shattered Highlands drop **Gale Essence**. The Dread Crystal drops glowing on the ground, with a chime, and is the same in every region. All essences sell for 60 coins and a Dread Crystal for 2,500.

## The six regions

### The Blightlands

*Forests and taiga. 3 skulls, +15 levels. Fog dark red, density 0.3.*

A rot spreads from something buried deep. The grass becomes blightgrass, trees turn into dead pale oak, dirt becomes coarse dirt, flowers become wither roses. Scattered blight crystals, bone spikes, soul soil and the odd skull.

**Monsters:** Blighted Dead (armoured zombies), Blight Archers, Gravecallers, Hollow Wisps, Blight Witches. **Drops:** {{item:skyrpg_world:blight_essence}}. **Boss:** The Blight Lord.

### The Ashen Wastes

*Savanna, badlands and desert. 4 skulls, +18 levels. Fog ash grey, density 0.35.*

Burnt flat by a fire that never went out. Ashen soil replaces sand and grass, trunks are stripped and bare, and the ground is dotted with magma patches, basalt pillars and smooth basalt.

**Monsters:** Ember Imps, Ash Walkers (husks), Cinder Wraiths (blazes), Charred Revenants (wither skeletons). **Drops:** {{item:skyrpg_world:cinder_essence}}. **Boss:** The Ash Colossus.

### Frostgrave

*Snowy biomes, ice spikes, groves. 4 skulls, +18 levels. Fog white, density 0.3.*

A graveyard under the snow. Snow and grass become cursed ice and packed ice, tall packed and blue ice spikes rise from the ground, and patches of powder snow hide traps.

**Monsters:** Frost Revenants, Frostbound Archers (strays), Frozen Dead (zombies in ice helmets). **Drops:** {{item:skyrpg_world:frost_essence}}. **Boss:** The Frost Revenant King.

### The Mire

*Swamps and mangroves. 2 skulls, +12 levels. Fog green, density 0.4. The mildest region.*

Grass becomes thick mire mud, and mushrooms replace short grass and flowers. Mud-brick spikes, moss and brown mushrooms are scattered about.

**Monsters:** Bog Hags, Mire Witches, slimes, Mire Lurkers (drowned). **Drops:** {{item:skyrpg_world:mire_essence}}. **Boss:** The Bog Mother.

### The Haunted Moors

*Plains, sunflower plains, meadows. 3 skulls, +16 levels. Fog grey, density 0.55 (the thickest).*

Grey fog over dead grass, crooked headstones and open graves. Grass becomes podzol and dry grass, leaves turn pale. At night, soul-fire will-o'-wisps float above the ground.

**Monsters:** Moor Ghosts (vexes), Gravecallers, Hollow Wisps, Restless Dead (zombies that dig themselves out of the ground). **Drops:** {{item:skyrpg_world:ectoplasm}}. **Boss:** The Lantern Wraith.

> [!TIP]
> Moor Ghosts take only a quarter of the damage from any weapon **without the Hallowed Edge enchantment**. If your hits do almost nothing, that is why. See [Enchanting](enchanting).

### The Shattered Highlands

*Stony, jagged and frozen peaks, windswept hills. 4 skulls, +20 levels. Fog pale blue, density 0.25. The highest level bonus.*

The mountains split and drifted apart. Gravel, tuff and calcite replace the ground, and **floating rock islets** hang 12 to 40 blocks above, some with veins of Skyiron ore. Griffin nests sit on high ledges, with wild griffins circling.

**Monsters:** Windborn (breezes), Cliff Stalkers (fast spiders, 1.35 times speed), Gale Harpies (phantoms that fly by day). **Drops:** {{item:skyrpg_world:gale_essence}}. **Boss:** The Storm Griffin.

## Region fog

Inside a region, your client blends the fog towards the region's colour and pulls it closer by the density, easing in over a few seconds and fading out over the last 30 blocks of the edge. It is purely visual, but a thick fog in the Moors makes it hard to see what is coming.

## Region gear

Each of four regions has a weapon and an accessory, crafted at the Artisan Bench in the **region** category. Each needs essence **and one Dread Crystal**.

| Region | Weapon | Accessory | Combat level |
| --- | --- | --- | --- |
| The Blightlands | {{item:skyrpg_items:blightreaver}} | {{item:skyrpg_items:blightheart_amulet}} | 16 |
| The Ashen Wastes | {{item:skyrpg_items:cinderbrand}} | {{item:skyrpg_items:ashen_bracers}} | 20 |
| Frostgrave | {{item:skyrpg_items:rimecleaver}} | {{item:skyrpg_items:frostwoven_cloak}} | 20 |
| The Mire | {{item:skyrpg_items:mirefang}} | {{item:skyrpg_items:bog_belt}} | 14 |

Weapons use 24 essence and accessories 16. The Haunted Moors and the Shattered Highlands have no region gear yet.

## See also

- [Region Hearts](region-hearts)
- [Blood Moon](blood-moon)
- [The Survival World](survival-world)
- [Region List](region-list)
- [Combat Basics](combat-basics)
- [Enchanting](enchanting)
