---
title: Chat
category: interface
order: 2
icon: minecraft:writable_book
summary: The themed chat: level badges, name cards, emotes, stickers, pictures, showcase tokens, whispers, mentions and chat settings.
---
SkyRPG restyles chat. Every message shows the sender's level and a hover card, you can show off your gear in a message, and you can post stickers and pictures that appear right in the chat window. It also removes the "Not Secure" marks and report buttons from player chat. This page covers everything you can type and see.

## Reading a message

A player's message looks like this:

`[badge] Name: message`

- **The level badge** is a star and a number in brackets, such as `(* 42)`. The number is the sender's **SkyRPG Level** (see [Collections](collections)). The colour changes every 40 levels: grey, white, yellow, green, dark green, aqua, dark aqua, blue, pink, purple, gold, red, and dark red from level 480. Hover a badge to see what the next colour is.
- **Titles.** An administrator can give a player a coloured title, shown as a pill by their name.
- **The name** has a hover card with the player's level, skills, purse, slayers, pet and held item. **Click a name** to start a `/msg` to them.
- **A stripe** beside each message shows its kind: player chat, whispers, joins, deaths, SkyRPG notices and more. Hover a stripe for the time.
- Joins, leaves, deaths and advancements have their own themed lines, with icons. A new player gets a welcome that tells them to press K.

Game messages about coins, skills, events and bank have an icon, a short bold tag in an accent colour, then the text, for example `SKILL`, `COINS` and `EVENT`.

## Showing off gear

Type one of these tokens anywhere in a message. It turns into a hoverable link that shows the real item tooltip.

| Token | Shows |
| --- | --- |
| `[item]` or `[hand]` | What you hold in your main hand |
| `[offhand]` | What you hold in your offhand |
| `[armor]` or `[armour]` | Every piece of armour you wear |
| `[pet]` | Your active pet |

An item shows its name in its rarity colour, with a rarity gem and, for stacks, `x16`. Empty hands show a grey `[nothing]`.

## Emotes

Type a code and it turns into an icon or symbol. `/emotes` lists them all, and you can click one to use it. Among the codes:

| Group | Codes |
| --- | --- |
| Classic | `:heart:` `<3` `:star:` `:yes:` `:no:` `:skull:` `o/` `h/` `:shrug:` `:tableflip:` `:arrow:` `:java:` `:totem:` `:typing:` `:maths:` `:snail:` `:thinking:` `:gimme:` `:wizard:` `:pvp:` `:peace:` `:oof:` `:puffer:` `:sloth:` `:dog:` `:cat:` `:cute:` `:dj:` `:yey:` `:dab:` `:snow:` |
| SkyRPG | `:coin:` `:gg:` `:rare:` `:sparkle:` `:sword:` `:pick:` `:fish:` `:gem:` `:mana:` `:defense:` `:strength:` `:crit:` `:luck:` `:boss:` `:sky:` `:sun:` `:moon:` `:music:` `:flower:` `:claude:` |
| Pixel icons | `:fire:` `:crown:` `:shield:` `:pickaxe:` `:axe:` `:hoe:` `:rod:` `:potion:` `:trophy:` `:lightning:` `:wave:` `:combat:` `:farming:` `:fishing:` `:foraging:` `:mining:` `:mail:` `:common:` `:uncommon:` `:epic:` `:legendary:` `:mythic:` |

The pixel icons draw as small sprites, and each skill has one. The rarity ones (`:common:` to `:mythic:`) show the same gems used on items.

## Stickers

Stickers are small pixel-art pictures. Type `[s:name]` (or `[sticker:name]`) and it shows as a picture in chat, such as `gg [s:gg]`. `/stickers` lists them, and a click puts a token in your chat box.

| Name | Looks like |
| --- | --- |
| `gg` | big GG |
| `wow` | surprised face |
| `lol` | laughing face |
| `rip` | gravestone |
| `hype` | HYPE starburst |
| `thumbsup` | thumbs up |
| `hearteyes` | face with heart eyes |
| `angry` | red angry face |
| `sleepy` | sleeping face |
| `coins` | coins raining onto a pile |
| `crit` | CRIT starburst |
| `chest` | dungeon chest |
| `owl` | pet owl |
| `creeper` | creeper face |
| `trophy` | gold trophy |

An unknown name stays as plain text.

## Pictures in chat

### Screenshots

Run `/share screenshot` to show your newest screenshot to everyone. `/share screenshot <file>` shows a named one. The picture is scaled to at most 640 pixels and sent to every player with the mod. You can share one picture every 30 seconds. Players without the mod see the line without the picture.

### Links

Paste a link to a PNG, JPEG or GIF and it shows as a picture. Known sites load by themselves: imgur, Discord, Reddit, Tenor, Giphy and GitHub. Pictures from other sites show a "click to load" line if you chose **Any site** in the settings. GIFs show only their first frame. Pictures are capped at 4 MB.

> [!NOTE]
> Loading a picture from a link tells that site your internet address. Set **Images from links** to **Off** in [Settings](settings) if you do not want that.

### Looking closer

Click a picture in the open chat to see it full size. **Esc** or a click closes it. A message can hold up to three pictures. While one downloads you see `[loading image...]`, and `[image unavailable]` if it fails.

## Mentions

Your name, or `@yourname`, in someone's message is highlighted and gives a soft ding. Whispers have their own softer ding. Both can be turned down in Settings.

## Whispers

| Command | What it does |
| --- | --- |
| `/msg`, `/tell` or `/w <player> <text>` | Send a private message |
| `/r <text>` or `/reply <text>` | Reply to the last person who whispered you |
| `/me <text>` | Act: `* Name waves` |

Whispers have an envelope icon, a "From" or "To" label in their own pink-violet colour, and appear in the Whispers tab.

## Chat tabs and settings

On the open chat window you get four tabs: **All**, **Chat**, **Whispers** and **SkyRPG**. A tab is just a view of the same history. Closing the chat goes back to All.

All these options live in **K, Settings, Chat**. See [Settings](settings).

| Setting | Default | What it does |
| --- | --- | --- |
| Themed chat box | On | SkyRPG colours and a framed panel instead of plain black |
| Message stripes | On | A coloured stripe by message kind |
| Timestamps | Off | Off, `12:34` or `12:34:56` before each message |
| Compact repeats | On | The same message in a row shows once, with a grey `(x3)` |
| Highlight mentions | On | Highlight and ding for your name or `@name` |
| Ping volume | 70% | How loud mentions and whispers ding. 0% is silent |
| Ding on whispers | On | A softer ding on whispers |
| Chat tabs | On | The All, Chat, Whispers and SkyRPG tabs |
| Show images | On | Pictures inline in chat. Off shows only text |
| Images from links | Known sites | Known sites, Any site (click to load), or Off |
| Max image height | 7 lines | 3 to 12 chat lines |
| Show stickers | On | Stickers as pictures. Off leaves the token as you typed it |

> [!TIP]
> Turn **Timestamps** on while you play with friends across timezones or when you are checking when something happened. Hover a stripe to see the time without them.

## See also

- [Controls and keys](controls-and-keys)
- [Settings](settings)
- [Collections](collections)
- [K menu](k-menu)
