---
title: Waystones and Travel
category: world
order: 2
icon: skyrpg_world:waystone
summary: Where waystones and Wayshrines are, how to attune and travel, what it costs, and the other ways to get around.
---
A **waystone** is a stone you can attune to and later travel back to. Once you have attuned to a few, you can jump between them, or home to Skyhaven, for a small number of XP levels. They are the backbone of long trips in the survival world, so it pays to attune to every one you walk past. You can also **pick them up and move them**, **rename them** and **craft new ones** to build a network of your own.

## Where to find them

Waystones stand in three kinds of place:

- **Wayshrines:** small ruined shrines of mossy stone brick with a waystone on a dais, candles, broken pillars and a chest. They generate in forests, taiga, savanna, plains, sunflower plains, meadows, snowy plains, deserts, birch forests and cherry groves.
- **Villages:** the first time a village's bell area loads, a waystone is placed two to four blocks from the bell.
- **Dungeons:** every dungeon entrance has one, including the Infernal Citadel in the Nether and the Astral Observatory in the End.

Skyhaven has one as well, just left of the spawn point.

Wayshrine and village waystones can be picked up and moved (see [Moving and building your own](#moving-and-building-your-own)). The waystone in Skyhaven and the ones at **dungeon entrances** are fixed: they anchor the dungeon, so they cannot be taken away.

### Wayshrines

Wayshrines are common: the world is cut into 24-chunk (384-block) squares and each can hold one, at least 8 chunks from the next. The **Shrine Chest** in each holds 2 to 4 stacks from bread, apples, cooked beef, carrots, torches, arrows, string, sticks and iron nuggets, plus 1 to 2 {{item:skyrpg_enchants:runic_dust}} and a map. Like dungeon chests, it is rolled per player, so everyone who visits gets their own loot.

## Attuning

1. Walk up to a waystone and **right-click** it.
2. The first time, it lights up (a lit waystone glows brighter), you get a **WAYSTONE ATTUNED** banner with its name, and it is added to your list and to your map.
3. Right-click it again to open the travel menu.

Waystones get an automatic name: the dungeon it guards, `<Name> Village`, or a made-up place name followed by the biome, for example "Ravenwatch · Taiga".

A waystone's name is shown in the travel list, on the world map and in the banner when you attune. See [Renaming](#renaming) to change it.

## Moving and building your own

### Picking one up

Mine a waystone with **any pickaxe**. It takes a few seconds (a wooden pickaxe is slow, an iron or better one is quick) and it comes off as a **Waystone** item named after the stone, which remembers exactly which waystone it was. Nothing else will do: without a pickaxe the break is refused.

- **Place it again** anywhere and it is *the same waystone*, in its new spot. Everyone who was attuned to it stays attuned, and from then on their travel list, map and costs point at the new place. It keeps its name and its glow.
- While you carry it, nobody can travel to it (it is greyed out of their list, and it comes back on its own once it is placed). Players stay attuned the whole time.
- The item will not despawn or burn up, but if it falls into the void or you lose it the waystone is lost with it. A stack can never hold two: each carried waystone is its own item.
- Placing a waystone attunes you to it straight away.
- You cannot copy a waystone: if the waystone an item remembers already stands somewhere, a second placement makes a brand-new, separate waystone instead.

What cannot be picked up:

- **Skyhaven's waystone**, and every **dungeon entrance waystone**. Swinging at one says so on the action bar.
- Anything in Skyhaven itself, where nothing can be broken.

What cannot destroy a waystone: explosions (creepers, TNT, beds in the Nether and so on) leave it standing, pistons will not push it, and the Wither and the Ender Dragon cannot break it. If anything else does take one away, such as a command, it drops as its item rather than vanishing.

### Making a new one

Craft a **Waystone** at the [Artisan Bench](crafting) (General tab). It needs **Mining 8**:

| Ingredient | Amount |
| --- | --- |
| Stone Bricks | 8 |
| {{item:skyrpg_mining:glimmer_crystal}} | 16 |
| Iron Ingot | 4 |
| Ender Pearl | 1 |

Place it where you need it: it gets an automatic name (a made-up place and the biome), you are attuned to it and you are listed as its owner. Travel between your own waystones costs the same XP as any other.

## Renaming

Open the waystone's menu and click **Rename** at the top right, type a name of up to **24 characters** and press Enter. The new name shows to everyone, in the travel list and on the map.

- The player who **placed** a waystone (or crafted and placed it) is its owner and the only one who can rename it.
- **Generated** waystones (wayshrines, villages, dungeon entrances) can be renamed by anyone who is attuned to them.
- **Skyhaven's** waystone keeps its name.
- A carried waystone can be renamed in an **anvil**; the new name is used when you place it.
- A name tag with a name on it, used on the waystone, also renames it (and is used up), under the same rules.

## Travelling

The menu lists **Skyhaven** first (outside the hub) and then every waystone you have attuned to, with its world, distance and coordinates. Click one to go.

| Trip | Cost |
| --- | --- |
| Back to Skyhaven | Free |
| From Skyhaven to any attuned waystone | 2 XP levels |
| Waystone to waystone, same dimension | 1 XP level per 1,000 blocks of distance, plus 1 (maximum 5) |
| Waystone to waystone, between dimensions | 4 XP levels |

That means anything under 1,000 blocks costs 1 level, 1,000 to 1,999 costs 2, and so on up to the 5-level cap at 4,000 blocks. Creative players travel for free.

You land on a free spot next to the destination waystone, facing it. A few rules:

- You must still be within 6 blocks of the waystone when you click, and you need enough XP levels.
- If a waystone is being carried, it is left out of the list until somebody places it again. If it no longer exists, you get "That waystone is gone" and it drops off your list.
- Destinations are saved on you, so your list follows you between worlds and dimensions.

### Warm-ups

Choosing a destination does not take you there at once:

- **In combat** the trip is refused (see [Combat basics](combat-basics#combat-tag)).
- Otherwise you **stand still for 5 seconds** with a countdown in the action bar, a ring of sparkles and a tick each second. Moving more than about a third of a block, taking damage or getting into a fight cancels it, and you keep your XP.
- The XP levels are paid when you arrive, not when you click.
- Creative players skip the wait. Going back to Skyhaven has the same one 5 second wait, not two.

> [!TIP]
> Attune to a waystone before you head into a [danger region](danger-regions) or dungeon. When your health is low, the trip home costs at most 5 levels, which is far cheaper than a death run back through monsters.

> [!TIP]
> Waystone icons on the world map can be clicked for **Track** and **Share in chat**, which makes it easy to tell a friend where to meet you. See [Waypoints](waypoints).

## Other ways to get around

| Way | Notes |
| --- | --- |
| `/hub` and `/survival` | Free, with the same 5 second warm-up and no use in combat. `/hub` goes to Skyhaven from anywhere, `/survival` returns you to where you left. See [The Survival World](survival-world). |
| Griffin mounts | Tame a griffin in the Shattered Highlands and ride it. Flight uses stamina that refills on the ground. See [Mounts](mounts). |
| Waypoints | Not a teleport, but they point the way. Track a waypoint and a marker, a ribbon and an arrow guide you. See [Waypoints](waypoints). |
| Vanilla routes | Boats, minecarts (Abandoned Mine Camps leave rails behind), ender pearls and Nether portals all still work. |
| Dungeon maps | Treasure maps from bosses and chests point to the next dungeon. |

## See also

- [Artisan Bench](crafting)
- [The Survival World](survival-world)
- [Waypoints](waypoints)
- [Mounts](mounts)
- [HUD and Minimap](hud-and-minimap)
- [Danger Regions](danger-regions)
- [Dungeons Overview](dungeons-overview)
