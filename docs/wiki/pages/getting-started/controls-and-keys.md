---
title: Controls and keys
category: getting-started
order: 3
icon: minecraft:comparator
summary: Every key, mouse action and chat command a player uses in SkyRPG.
---
SkyRPG adds a handful of keys on top of vanilla Minecraft, a lot of mouse actions inside its screens, and a set of chat commands. This page lists all of them in one place. Keys can be changed in Minecraft's Controls screen, under the **SkyRPG** category.

## Keys

| Default key | Action | What it does |
| --- | --- | --- |
| **K** | Open SkyRPG Menu | The menu with your Profile, Skills, Journal and more. Press again to close. See [K menu](k-menu). |
| **M** | Open World Map | Everything you have explored in this dimension. Press again to close. See [HUD and minimap](hud-and-minimap). |
| **=** | Minimap Zoom In | Zooms the minimap in, up to 4 pixels per block. |
| **-** | Minimap Zoom Out | Zooms the minimap out, down to a quarter pixel per block. |
| **B** | Open Backpack | Opens the backpack in your first backpack slot. See [Backpacks](backpacks). |
| **H** | Look up in Wiki | Hover an item in any inventory and press this to open its wiki entry. |
| **Right click** (hold, with a wand) | Spell wheel (hold) | Opens the spell wheel while you hold it; release over a spell to select it. Rebind it under **SkyRPG**. See [Wands](wands). |
| **G** | Spell wheel (toggle) | Opens a wheel that stays open until you click a spell. |
| **Tab** (hold) | Player list | Shows the SkyRPG player list with heads, ping and info panels. Can be switched off in [Settings](settings). |

Vanilla keys keep their normal job. The sneak key, jump key and sprint key also steer a griffin, see below.

## Inside the K menu

| Input | Action |
| --- | --- |
| Up and Down arrows | Move to the previous or next page |
| **1** to **9** | Jump to the first nine pages in the sidebar |
| **K** or **Esc** | Close the menu. Esc first steps back inside a page, for example clearing a search. |
| Scroll wheel | Scrolls the page |

Pages with a text field (Settings search, Pay, the bank) take digits and letters first, so numbers type instead of switching pages.

## In shops

| Input | Action |
| --- | --- |
| Click an offer | Buy one |
| Shift-click an offer | Buy a full stack |
| Right-click an offer | Choose how many: type a number, use the arrows, or pick a preset (1, 8, 16, 32, 64), then Enter |
| Click an item in the sell panel | Sell all of that item |
| **Sell all** | Sell everything this keeper buys |
| **Buy back** | Take back your last sale to this keeper for the same coins |

## At the bank and on the Pay page

| Input | Action |
| --- | --- |
| Type an amount, **Enter** | Deposit (at the bank) or send (on the Pay page) |
| **Shift+Enter** at the bank | Withdraw |
| **All** and **Half** buttons | Move all or half of the coins |

Amounts accept `k`, `m` and `b`, so `2.5k` is 2,500 and `1m` is 1,000,000. See [Coins and the bank](coins-and-bank).

## In conversations

| Input | Action |
| --- | --- |
| Click, **Space** or **Enter** | Show all of the text, then go on |
| Click a choice, or press its number | Pick that answer |
| **Esc** | Close the conversation |

## On the world map and HUD editor

| Input | Action |
| --- | --- |
| Drag | Move the map |
| Scroll | Zoom towards the mouse |
| **C** | Follow yourself |
| Right-click the map | Drop a waypoint |
| Click or right-click an icon | Track, share or remove it |
| Drag a box (HUD editor) | Move it |
| Scroll over a box (HUD editor) | Resize it, 0.5x to 2x |
| Right-click a box (HUD editor) | Hide or show it |
| **R** (HUD editor) | Reset the box under the mouse, or every box if the mouse is on none |

See [Waypoints](waypoints) and [HUD and minimap](hud-and-minimap).

## In the world

- **Right-click an NPC** to talk, shop, bank, enchant or start slayer quests. See [NPCs](npcs).
- **Right-click a weapon, tool or pickaxe** to use its ability, such as Skyward Burst or Treefeller.
- **Right-click an accessory** to put it on. **Right-click a backpack** in its slot to open it. Backpack and accessory slots sit in strips beside your inventory and hide while the recipe book is open.
- **Right-click a pet item** to move it into your pet storage and equip it. **Right-click a Griffin Whistle** to add it to your stable and call it.
- **Right-click a waystone** to attune it. Right-click again to travel to another one.
- **Right-click the Artisan Bench** to craft.
- **Sneak and hit** a node, planter or stump with an empty hand to pick it up.

### Casting with a wand

With a [wand](wands) in your main hand the mouse is for spells (anything that has a right-click use of its own, like a chest or an NPC, keeps it):

| Input | Action |
| --- | --- |
| **Left click** | Cast the selected [spell](spells). No melee swing, no block breaking |
| **Hold left click**, release | Charge a chargeable spell (Fireball, Meteor) and cast it |
| **Hold right click** | Open the spell wheel; move the mouse to a spell and release to select it |
| **Sneak + right click** | Open the wand's spell slots to install or remove scrolls |
| **Sneak + scroll wheel** | Select the next or previous spell |

### Riding a griffin

| Input | On the ground | In the air |
| --- | --- | --- |
| **W A S D** | Walk | Fly where you face |
| **Jump** | Take off | Climb |
| **Sneak** | Dismount | Descend |
| **Sprint** | | Glide faster, 1.75 times |

See [Mounts](mounts).

## Chat commands

Commands start with `/` and are typed in chat. Anyone can use these.

### Money

| Command | What it does |
| --- | --- |
| `/coins [player]` | Show your purse and bank, or another player's |
| `/pay <player> <amount>` | Send coins from your purse |
| `/sell` or `/sell hand` | Sell the stack in your hand |
| `/sell all` | Sell every stack of the item in your hand |
| `/sell inventory` | Sell everything NPCs will buy |
| `/worth` | Show what the item in your hand sells for |

### Travel

| Command | What it does |
| --- | --- |
| `/hub` | Go to Skyhaven after a 5 second stand-still warm-up (refused in combat; run it again to cancel) |
| `/survival` | Go to the survival world, back to where you left, with the same warm-up |
| `/waypoint ...` or `/wp ...` | Make and use waypoints. See [Waypoints](waypoints) |

### Progress and info

| Command | What it does |
| --- | --- |
| `/skills [player]` | Show skills |
| `/stats [player]` | Show stats |
| `/quest` or `/quest list` | List your quests |
| `/quest track [id]` | Track a quest |
| `/quest abandon <id>` | Give up an open side quest |
| `/title`, `/title locked` | List your earned or locked titles. See [Titles](titles) |
| `/title set <id>` and `/title clear` | Wear an earned title, or take it off |
| `/contracts` | List today's contracts |
| `/event list` | Show running events |
| `/event join [id]` and `/event leave [id]` | Join or leave an opt-in event. Typing `.join` or `.leave` in chat works too |
| `/region` | Describe nearby danger regions |
| `/layer` | Say which underground layer you are in |
| `/items list` | List SkyRPG items |
| `/pet`, `/pet away`, `/pet storage` | Show your pet, put it away, list your pet storage |
| `/mounts`, `/mounts call`, `/mounts away` | List your stable, call your mount, put it away |
| `/wiki [page]` | Open the in-game wiki |
| `@skyhelper <question>` in chat, or `/ask <question>` | Ask SkyHelper, the assistant. `/ask` answers only you. See [SkyHelper](skyhelper) |
| `/party ...` or `/p ...` | Make and manage a party. See [Party](party) |

### Chat

| Command | What it does |
| --- | --- |
| `/msg`, `/tell`, `/w <player> <text>` | Whisper |
| `/r <text>` or `/reply <text>` | Reply to the last person who whispered you |
| `/me <text>` | Describe an action |
| `/pc <message>` | Send a message to your party |
| `/party chat` | Switch your chat to party chat, and back |
| `/emotes` | List the emotes. Click one to use it |
| `/stickers` | List the stickers. Click one to put it in your message |
| `/share screenshot [file]` | Show your newest screenshot, or a named one, to everyone |
| `/chat level [player]` | Show your SkyRPG chat level, or another player's |

See [Chat](chat).

> [!NOTE]
> Operators have more commands, such as `/coins give`, `/slayer`, `/event start`, `/bench` and `/hubadmin`. They are not covered here.

## See also

- [K menu](k-menu)
- [Settings](settings)
- [Chat](chat)
- [HUD and minimap](hud-and-minimap)
