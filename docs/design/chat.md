# Chat (`chat`, mod id `skyrpg_chat`)

A themed chat. The server side works through system messages, so it looks right with a vanilla client; the client side adds the chat box look and quality-of-life. Depends on `skyrpg_core` (reads levels, purse and skills through core's API; the pet is read from the pets mod's synced attachment by id, so chat doesn't need `pets`).

## Decisions

- **Player chat is replaced by a styled system message.** `ServerChat` cancels the vanilla broadcast (late phase of `ALLOW_CHAT_MESSAGE`, so the events mod's `.join` filter still runs first) and sends a system message to everyone whose chat setting is "full". System messages carry no signature, so "Not Secure" markers and report buttons are gone. If formatting throws, the message falls back to vanilla.
- **The server log keeps vanilla's line.** `ServerChat.logLikeVanilla` calls `MinecraftServer.logChatMessage`, vanilla's own logger, so the log still has `[Not Secure] <Name> message` (or `<Name> message` when signed). The Claude tools depend on this: `read_chat` in `tools/mcp_server.py` uses `CHAT_LINE` (`... (Minecraft) (?:[Not Secure] )?<Name> text`), and the "@claude" monitor greps the log. The logged text is the raw message (no emotes or showcase expansion), so `@claude` always matches. No change to `mcp_server.py` was needed.
- **Console `say` / `tellraw` stay vanilla.** Only `ChatType.CHAT` and a player's `/me` are restyled; `tellraw` never goes through those events, so Claude's purple `[Claude]` posts are untouched (the client tags them as kind CLAUDE).
- **Every styled line carries hidden metadata** (kind, sender) in `ChatLines`, which the client reads for stripes, tabs and mentions.
- Image icons come from a custom bitmap font (see **Icons** below); every unicode emote keeps its plain-text `text` as a fallback in its data file, and emotes without an `icon` still use it.
- Join/leave lines, whispers and showcase gems use the icon font too. Server-log lines are untouched (only chat the players see changes).

## Features

| Feature | Notes |
|---|---|
| Level badge | `(star 42)` (dim bracket icons, a star icon and the level, tinted by tier), coloured by tier (`ChatColors.tier`); hover shows the next tier. Level = skill levels + slayer weight. |
| Name hover card | level, skills, purse, slayers, pet, held item. Click suggests `/msg Name `. |
| Showcase | `[item]`/`[hand]`, `[offhand]`, `[armor]`/`[armour]`, `[pet]` become hoverable links with the real tooltip. |
| Emotes | data-driven: `data/<ns>/skyrpg/chat_emotes/*.json` (`:heart:` and `<3` give a red heart, `:star:`, `:skull:`, `:yes:`, `:no:`, `o/`, `:shrug:`, more). `/emotes` lists them; click one to use it. |
| Whispers | `/msg`, `/tell`, `/w` styled (mixin on vanilla's MsgCommand), `/r` and `/reply` to the last whisperer, `/me` as `✱ Name waves`. |
| System lines | `» Name joined`, `« Name left`, a first-join welcome, `☠` death lines, advancement lines. |
| Titles | `/chat title <player> <color> <text>` (op) or `clear`; shown as the text between two pill chevron icons, in the title colour; `/chat level [player]`. |
| Client look | themed chat panel, per-kind stripe (hover for the time), optional timestamps, compact repeats `(x3)`, All/Chat/Whispers/SkyRPG tabs. |
| Mentions | your name or `@name` is highlighted with a ding (client). |

## Settings

SkyRPG Settings, category **Chat** (`ChatSettings`):

- Look: Themed chat box (on), Message stripes (on), Timestamps (Off / 12:34 / 12:34:56), Compact repeats (on).
- Mentions: Highlight mentions (on), Ping volume (slider, 70%), Ding on whispers (on).
- Tabs: Chat tabs (on).
- Images: Show images (on), Images from links (Known sites / Any site (click to load) / Off; default Known sites), Max image height (3-12 chat lines, default 7), Show stickers (on).

## Images in chat

Chat can show real pictures inline: shared screenshots, built-in stickers and links to pictures. It is all client side rendering of **text**, so the server log, `read_chat` and console `tellraw` keep working: a player's message is logged as `<Name> [s:gg]` or `<Name> https://i.imgur.com/x.png`, exactly as typed.

### How it renders

- Any chat message (player chat, whispers, `tellraw`, system lines) is scanned for picture references (`ImageRefs`). A message may carry up to 3 pictures.
- The tokens `[s:...]` / `[img:...]` are taken out of the displayed text (`ChatImageRender.decorate`, called from `ChatLook.decorate`; links stay visible and clickable). The raw text is untouched everywhere else.
- Room: `GuiMessageMixin` appends blank placeholder lines (`ImageLine`) after the message's text lines, one per chat line of picture height: the picture is scaled to fit the chat width and the *Max image height* setting (aspect kept, never enlarged), so it takes roughly 4-8 lines. Because they are real chat lines, the picture scrolls with the chat, the stripe runs beside it, and long histories drop it like any other line.
- Drawing: `ChatImageGraphicsMixin` hooks `handleMessage` of the closed-chat and open-chat drawing helpers; for an `ImageLine` it blits that line's horizontal slice of the texture (UV rows rounded at shared edges, so no seams) with the chat's own per-line opacity (fade and Chat Text Opacity). Pictures show in the closed chat too (while messages are fresh) and in the open chat.
- Click: slices drawn in the open chat record their screen rectangles; a left click on one opens `ImageViewerScreen` (fit to window, up to 2x; stickers up to 6x). Esc or a click closes it and returns to the chat.
- Loading state: while a link downloads the message shows `[loading image...]`; when the picture is ready the chat re-lays out (scroll position kept) and the lines appear. A failure shows `[image unavailable]` (hover for the reason).

### Sources

- **Links.** `http(s)` links to a PNG/JPEG/GIF (by `.png/.jpg/.jpeg/.gif` path, or any path on a host that only serves pictures). Known hosts load automatically when *Images from links* is *Known sites*: `i.imgur.com`, `imgur.com`, `cdn.discordapp.com`, `media.discordapp.net`, `i.redd.it`, `media.tenor.com`, `c.tenor.com`, `media1.tenor.com`, `media.giphy.com`, `raw/user-images/avatars/private-user-images.githubusercontent.com`. Any other host is **never** loaded by itself: with *Any site (click to load)* the line gets `[image from <host> - click to load]` (runs `/skyrpgimg load <url>`, which approves that link for the session); with *Known sites* nothing extra shows; *Off* loads nothing from links. The download runs on a worker thread: 5 s connect / 8 s read timeouts, 4 MB cap, redirects followed by hand (max 4) and re-checked against the host rule, loopback/private addresses refused, image content types only. Decoded with ImageIO (GIF: first frame), scaled to at most 512 px on the long side, kept in memory (entries forever, textures LRU of 32) and on disk as PNG in `<game folder>/cache/skyrpg_chat/images` (trimmed at startup to 96 MB / 30 days).
- **Screenshots.** `/share screenshot` shares your newest file in `screenshots/`, `/share screenshot <file>` a named one (tab completes). The client scales it to at most 640 px wide (and 640 tall), JPEG under about 300 KB, and sends it in chunks. The server (`ChatImages`) checks: PNG/JPEG magic, at most 320 KB and 16 chunks, header dimensions at most 1280x1280 (header read only, no decode), one share per 30 s per player. It then relays the bytes to every player that has the mod and broadcasts the line `✱ Name shared a screenshot [img:1a2b3c4d]` (players without the mod get the same line without the token). The server only keeps the last 4 pictures in memory; nothing is written to disk. Clients cache the received picture in memory and in the disk cache (not re-requestable from the server, so a picture that never arrived shows `[image unavailable]` after 25 s).
- **Stickers.** 15 built-in 48x48 pixel-art pictures in `assets/skyrpg_chat/textures/sticker/`, drawn by `python tools/art/generate_stickers.py [--preview DIR]` (also writes `assets/skyrpg_chat/stickers.txt`, the list the mod reads). Type `[s:name]` (or `[sticker:name]`), for example `gg [s:gg]`. `/stickers` lists them (client command; click one to put its token in your chat box). Unknown names stay as plain text; with *Show stickers* off the token stays as typed.

| Name | Looks like |
|---|---|
| `gg` | big GG |
| `wow` | surprised face |
| `lol` | laughing face |
| `rip` | gravestone |
| `hype` | HYPE starburst |
| `thumbsup` | thumbs up |
| `hearteyes` | face with heart eyes |
| `angry` | red angry face |
| `sleepy` | sleeping face, Zzz |
| `coins` | coins raining onto a pile |
| `crit` | CRIT starburst |
| `chest` | dungeon chest |
| `owl` | pet owl |
| `creeper` | creeper face |
| `trophy` | gold trophy |

### Claude posting a picture with tellraw (or any text)

The client recognises these anywhere in a message's plain text, so one `text` component is enough:

```
tellraw @a [{"text":"[Claude] ","color":"#D97757"},{"text":"nice build! [sticker:gg]"}]
tellraw @a [{"text":"[Claude] ","color":"#D97757"},{"text":"here is the map: https://i.imgur.com/AbCdEf1.png"}]
```

- Sticker: `[sticker:<name>]` or `[s:<name>]` with a name from the table above.
- Picture link: a full `https://` URL to a PNG/JPEG/GIF on a known host (list above). For a host that is not on the list the players only see a click-to-load link, and only if they chose *Any site*.
- Shared screenshot: `[img:<8 hex id>]` only works for ids the server relayed (`ChatImages`), so it isn't useful from the console.
- Players without the mod (or with *Show images* / *Show stickers* off) see the token or link as plain text. Keep the message readable without the picture.
- Put the token in a `text` that has no hover/click you need to keep; styles are preserved when the token is cut out.

## Files

Pictures: server `ChatImages` (payloads `skyrpg_chat:share_upload` C2S and `skyrpg_chat:image_chunk` S2C, 20,000 bytes per chunk), `Stickers`; client `ImageRefs`, `ImageCache`, `ChatImageRender`, `ImageViewerScreen`, `ImageNet`, `ImageCommands`, mixins `ChatImageGraphicsMixin` and `GuiMessageMixin`.

Server: `ServerChat`, `ChatBody` (emotes, showcase, mentions in the body), `Profiles`, `Showcase`, `Emotes`, `Whispers`, `SystemLines`, `Titles`, `ChatCommands`, `ChatLines`, `ChatColors`, mixin `MsgCommandMixin` (`skyrpg_chat.mixins.json`). Client: `ChatHooks`, `ChatLook`, `ChatTabs`, `Mentions`, `ChatSettings`, mixins registered in `skyrpg_chat.client.mixins.json`.

## Icons

Pixel-art sprites in the bitmap font `skyrpg_chat:icons` (`assets/skyrpg_chat/font/icons.json`, atlas `textures/font/icons.png`, 10px cells, ascent 8 so they sit on the baseline). Both files are generated by `python tools/art/generate_chat_icons.py [--preview DIR]`, which also prints this table. Server code uses `ChatIcons` (`ChatIcons.icon(glyph)`, `ChatIcons.tinted(glyph, rgb)`, `ChatIcons.lead(glyph)`); emote data files take an optional `"icon": ""` (the `text` stays as fallback). `data/skyrpg_chat/skyrpg/chat_emotes/icons.json` maps the icon emote codes (`:heart:`, `<3`, `:skull:`, `:fire:`, `:coin:`, `:gg:`, `:claude:`, `:mining:`, `:legendary:`, ...).

Use one from `tellraw` (console, Claude): `{"text":"","font":"skyrpg_chat:icons"}`. Full-colour icons should have colour white (or none). The grey-scale frame pieces (E027-E02D) take their colour from the text colour: `{"text":"","font":"skyrpg_chat:icons","color":"gold"}`. Every glyph advances 1px past its pixels; the font also has space glyphs: U+E0F0 = -1px, U+E0F1..E0F4 = +1..+4px.

Example `[Claude]` line: `tellraw @a [{"text":"[Claude] ","color":"#D97757"},{"text":"","font":"skyrpg_chat:icons"},{"text":" hello"}]` (the client still tags it as Claude: leading icons are ignored when it looks for the `[Claude]` prefix).

| Codepoint | Name | Used for |
|---|---|---|
| U+E000 | heart | `:heart:` `<3` |
| U+E001 | star | `:star:` |
| U+E002 | skull | `:skull:`, death lines |
| U+E003 | fire | `:fire:` |
| U+E004 | coin | `:coin:` |
| U+E005 | gem | `:gem:` |
| U+E006 | crown | `:crown:` |
| U+E007 | sword | `:sword:` |
| U+E008 | shield | `:shield:` |
| U+E009 | pickaxe | `:pickaxe:` `:pick:` |
| U+E00A | axe | `:axe:` |
| U+E00B | hoe | `:hoe:` |
| U+E00C | fishing rod | `:rod:` |
| U+E00D | potion | `:potion:` |
| U+E00E | trophy | `:trophy:` |
| U+E00F | lightning | `:lightning:` |
| U+E010 | moon | `:moon:` |
| U+E011 | sun | `:sun:` |
| U+E012 | check | `:yes:` |
| U+E013 | cross | `:no:` |
| U+E014 | wave (hand) | `:wave:` |
| U+E015 | music note | `:music:` |
| U+E016 | gg | `:gg:` |
| U+E017 | envelope | whispers (`:mail:`) |
| U+E018 | robot | `:claude:`, `[Claude]` tellraw |
| U+E019 | join arrow | join line |
| U+E01A | leave arrow | leave line |
| U+E01B | welcome sparkle | first-join line |
| U+E01C-E020 | skill icons: combat, farming, fishing, foraging, mining | `:combat:` ... `:mining:` (`ChatIcons.skill(path)`) |
| U+E021-E026 | rarity gems: common, uncommon, rare, epic, legendary, mythic | item showcase (picked from the name's colour: white/grey, green, blue/aqua, purple/pink, gold/yellow, red); `:common:` ... `:mythic:` |
| U+E027 / E028 / E029 | badge bracket left / fill / bracket right (tinted) | level badge (left and right) |
| U+E02A / E02B / E02C | pill chevron left / fill / chevron right (tinted) | chat title (left and right) |
| U+E02D | level star (tinted) | level badge |

The fill pieces (E028, E02B) are solid blocks for hand-made tellraw pills; nothing in the mod uses them (text can't be reliably layered over a glyph).

## Tab list width

`SkyRpgTab` (core, client) sizes the players column from its widest row: face + name + the title pill (team prefix, measured bold as it is drawn) + ping bars, clamped to 110-320px. The whole list grows from 470px up to 640px on big screens. On a small screen the column keeps its full content and the panels drop to one column; only when even that cannot fit does a row get cut: the pill gives up width first and ends in an ellipsis, then the name shrinks. The title shown is the player's scoreboard-team prefix; chat's `/chat title` is an attachment that is not synced to the client, so it only appears in chat.

## Game notices

System lines about the game (level-ups, coins, finds, events) go through `com.skyrpg.core.text.Notice` so chat stays scannable. A notice is a pixel icon, a short bold tag in the accent colour, then the body (soft grey by default; coloured parts of the body keep their own colour):

`[icon] SKILL  Mining 12 ➜ 13`

Core does not depend on the chat mod: `Notice` names the font `skyrpg_chat:icons` by id and holds the glyphs as codepoint constants (same table as above).

| Kind | Icon | Tag | Colour |
| --- | --- | --- | --- |
| SKILL | star E001 (level-ups use the skill icon via `Notice.skillIcon`) | SKILL | cyan 55E6E6 |
| COINS | coin E004 | COINS | gold FFC940 |
| MINING / FARMING / FORAGING / FISHING / COMBAT | skill icons E020 / E01D / E01F / E01E / E01C | the skill name | steel 8FB4D9 / green 7BD35A / forest 3FB56B / blue 4FB8F0 / red FF5555 |
| SLAYER | skull E002 | SLAYER | crimson C02A3A |
| DUNGEON | shield E008 | DUNGEON | violet B06AE8 |
| DROP | rarity gem E021-E026 (picked from the item colour) | RARE DROP! | orange-gold FFAA00 |
| PET | heart E000 | PET | pink FF8AC8 |
| ENCHANT | gem E005 (tomes and the codex use the rarity gem) | ENCHANT | purple C77DFF |
| EVENT | trophy E00E | EVENT | yellow FFE14D (events override with their own colour) |
| CONTRACT | envelope E017 | CONTRACT | amber F0A040 |
| BANK / SHOP | crown E006 / coin E004 | BANK / SHOP | green 6BE08A |
| ABILITY | fire E003 | the ability name | orange FF9A3C |
| ITEM | sword E007 | ITEMS | sand E8D4A0 |
| SUCCESS / INFO / WARN / ERROR | check E012 / sparkle E01B / lightning E00F / cross E013 | DONE / INFO / WARNING / ERROR | green 55DD77 / grey-blue 9FB3D1 / yellow FFD23F / red FF5555 |

Usage:

```java
Notice.send(player, Notice.Kind.COINS, Msg.text("+50 coins", ChatFormatting.GOLD));
Component line = Notice.build(Notice.Kind.INFO, "Plain text body");        // for broadcasting yourself

// Override the icon, tag or colour; add a hover and a click on the icon and tag.
Notice.of(Notice.Kind.SKILL).icon(Notice.skillIcon("mining")).tag("LEVEL UP")
        .hover(Component.literal("Open your skills")).click("/skills")   // or .suggest("/pay ")
        .send(player, body);

// Rare drops: gold tag, rarity gem from the item colour (an int RGB, or Notice.rgb(ChatFormatting.AQUA)).
Notice.rareDrop(player, body, Notice.rgb(ChatFormatting.AQUA));
Notice.rareDrop("RARE FIND!", body, rgb);                                // custom tag
```

Only chat lines use it. Item lore, tooltips, action-bar and overlay messages are not notices. Conversational lines (NPC greetings) stay plain.
