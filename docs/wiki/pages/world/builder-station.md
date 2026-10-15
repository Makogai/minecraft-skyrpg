---
title: Builder Station
category: world
order: 3
icon: skyrpg_builder:builder_station
summary: Load a schematic or a village house, place its hologram, deliver the materials and watch it build itself from the ground up.
---
The **Builder Station** turns a blueprint into a real build. Pick a blueprint (one of Minecraft's village houses, or your own schematic file), place its glowing hologram where you want it, deliver the materials it lists, press **Build**, and it rises from the ground up, layer by layer, in front of you.

## Getting one

Craft a {{item:skyrpg_builder:builder_station}} at the [Artisan Bench](crafting) (General tab). It needs **Foraging 5**:

| Ingredient | Amount |
| --- | --- |
| Crafting Table | 1 |
| Paper | 8 |
| Iron Ingot | 6 |
| Redstone Dust | 8 |
| Amethyst Shard | 4 |

Place it anywhere in the survival world. Its crystal lens faces you, and new blueprints first appear **behind** it. Right-click it to open the builder menu. Stations don't work in Skyhaven.

## Choosing a blueprint

Press **Choose blueprint** (or **Blueprint...** later). The picker has two tabs and two views:

- **Library:** every blueprint on the server. Minecraft's own buildings are always there: the houses and town centres of all five village types (plains, taiga, savanna, desert, snowy), the igloo, the pillager outpost watchtower and the ruined portals. Schematics anyone has uploaded are listed first, with who uploaded them. You can delete your own uploads.
- **My schematics:** the schematic files on your computer, in the `schematics` folder of your game (the same folder Litematica uses; **Open folder** opens it). Clicking one uploads it to the server's library and loads it. Uploaded blueprints are shared with everyone on the server.
- **Cards** shows each blueprint as a card with a turning 3D preview and its size underneath. **List** is a compact list. Both have a search box.

Picking a blueprint closes the menu and goes straight to **placing it in the world** (below).

Supported files: **.litematic** (Litematica, every region), **.schem** (WorldEdit / Sponge, versions 1 to 3) and **.nbt** (vanilla structure block files). Old MCEdit **.schematic** files are not supported: save them as .litematic or .schem first. A blueprint can be at most **128 blocks** on each side, **120,000 blocks** in all, and the file at most **2 MB**. Files from older Minecraft versions are updated as they load, and the card says why a file can't be read before you upload it.

## Placing the hologram

A blueprint is placed **in the world, not in a menu**. Its **hologram** (see-through blocks with a cyan outline) follows where you aim: the side nearest you sits on the block you look at and the build stretches away from you. Walk around and aim until it's where you want it. A strip at the bottom of the screen shows the keys:

| Key | What it does |
| --- | --- |
| Aim | Move it (it follows the block you look at, up to 64 blocks away) |
| Scroll, or R | Turn it 90° (Shift+R: the other way) |
| M | Mirror it |
| ↑ ↓ ← → | Nudge it one block away, towards you, left or right of where you look (Shift: 5 blocks). This locks it in place |
| Page Up / Page Down, or Shift+scroll | Raise or lower it |
| G | Lock it in place, or let it follow your aim again |
| Left-click, or Enter | Place it here |
| Esc, Backspace, or right-click | Cancel (it goes back to where it was) |

While you place, clicks and those keys only move the hologram: you won't break or place blocks by accident. It turns red when it's more than 64 blocks from its station (or would cover the station), and then it can't be placed. To move a placed blueprint later, **sneak + right-click the station** (whatever you're holding), or open it and press **Move in world**. Moving one that's already placed starts **locked where it is**, so the arrow keys fine-tune it; press G to make it follow your aim again. You can place it from anywhere up to 64 blocks from the station.

Once placed, **everyone** nearby sees the hologram. Blocks that are already in place disappear from it, and blocks that are **in the way** glow red. **Hologram on/off** in the menu hides it.

### Looking at the blueprint

The left side of the menu shows the blueprint four ways:

- **3D:** a model that turns by itself; drag (or scroll) to turn it yourself. **Real** shows every block as it looks (torches, flowers, glass, water); **Simple** shows plain coloured cubes.
- **Top:** seen from above, north up, higher blocks brighter.
- **Side:** seen from the south.
- **Layers:** one layer at a time; scroll for the others. Hover a block to see what it is.

While it builds, the parts not built yet are faded (in 3D they're cyan hologram), and the Layers view follows the layer being built.

## Materials

The right side lists everything the build needs, how much you've delivered and a bar for each. Blocks already standing in the right place don't need materials.

- **Deliver all** takes everything the build still needs from your inventory and backpacks (never more than it needs). Click one material to deliver just that one.
- **List** or **Grid**: a list with names and bars, or a grid of bigger icons that fits many more at once.
- Each material shows how many **you carry** (green: enough for what's missing, gold: some). Hover one for the details; Deliver all's tooltip says how much you could deliver now.
- **Shift-click** a row, or press **Take back**, to get delivered materials back (not while it is building).
- In creative mode delivering is free.
- **Anyone** can open the station and deliver, so friends can chip in. The menu shows who delivered how much ("Delivered by").

What things cost:

- Most blocks cost their own item. Doors, beds and tall plants are paid for once; double slabs cost two slabs; candles, sea pickles, turtle eggs, petals and snow layers cost one item per piece.
- Crops cost their seeds (wheat seeds, carrots, potatoes...), and a potted plant costs a flower pot plus the plant.
- **Grass blocks, dirt paths, farmland and mycelium** take plain **dirt**.
- **Water** and **lava** sources cost a full bucket; the empty bucket comes back in the leftovers.
- Some blocks are never built and are left out of the cost ("skipped" in the menu): spawners, bedrock, barriers, portals and portal frames, command and structure blocks, budding amethyst, infested blocks, player heads, the dragon egg, and blocks from other mods.
- Chests, barrels and the like are built empty. Signs keep their text, banners their patterns and heads their owner.

## Building

When everything is delivered the status turns **READY TO BUILD**. Press **Build** (or Enter). The menu closes so you can watch.

The station's lens lights up and the build goes up **layer by layer from the ground**, sweeping out from the station, with a beam from the lens to each new block and a glowing plane at the layer being built. Each layer is cleared first, then its solid blocks go in, then the things that hang on them (torches, doors, carpets). It takes its time so you can watch it: about 12 seconds plus a little per block and per layer, with a short rest after each layer. A small village house takes about 45 seconds, bigger builds a few minutes (3 at most).

Everyone within 64 blocks gets a **build panel** in the bottom-right corner: a big 3D model of the build (built part in colour, the rest as hologram), its name, a progress bar, the layer, blocks placed and about how long is left. It says **FINISHED!** when it's done.

### Preview settings

The 3D previews (blueprint cards, the station, the build panel) have two settings under **K menu → Settings → Builder**:

- **Preview style:** Real (default) or Simple. The Real / Simple button in the station and the blueprint picker switches it too.
- **Preview quality:** Low, Medium or High (default). Lower is lighter on slower computers.

- **Stand clear:** the build waits for anyone standing where a block goes.
- If it runs out of a material (someone took a block away, for example), it **pauses** and says what it's waiting for. Deliver more and it carries on.
- **Stop** pauses the build. Press **Build** again later and it carries on where it left off.
- When it's done you get a burst of fireworks and a message. **Build again** plans the same blueprint again (move it first); **New blueprint** picks another.

### Clearing the site

With **Clear site** on (the default), blocks that are in the way are removed as the layers go up, so the build comes out exactly like the blueprint, air included. What they drop goes to the station's **leftovers**, but only blocks you could break by hand drop anything: dirt, sand, plants, logs and planks come back, while **stone and ores just vanish**. Items inside containers that are cleared away go to the leftovers too. Bedrock and other unbreakable blocks are never touched.

With **Clear site** off, nothing that's already there is touched: blocks in the way are simply left out of the build.

Press **Collect leftovers** to take them.

## Breaking the station

Break the station with an axe to move it. Everything delivered to it, and its leftovers, drop on the spot. A build in progress stops.

## Tips

- Put a schematic you made with Litematica (or downloaded) in your `schematics` folder and it shows up under **My schematics**, with a preview, right away.
- Use **Turn** before moving: it turns about the middle, so the build stays roughly where you put it.
- Turn **Clear site** off when building into an existing build, so nothing of yours is removed.
