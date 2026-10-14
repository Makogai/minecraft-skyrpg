# Events, announcements and boards

The `skyrpg_events` mod runs timed events with a live leaderboard (top right of the HUD), warnings,
prizes and a winner board with a podium and confetti when they end. It also has `/announce` for
banners and `/board` for a custom sidebar. Everything here works from chat, RCON (`python tools/mc.py
cmd "..."`) or the MCP `run_commands` tool. The JSON is lenient, so keys don't need quotes and
strings can use single quotes.

| Command | What it does |
| --- | --- |
| `/event run <json>` | Start an event described in JSON (op) |
| `/event start <id> [duration]` | Start one from `data/<ns>/skyrpg/events/<id>.json`, e.g. `timber_rush 2m` (op) |
| `/event end [id]` | Finish now and hand out prizes (op) |
| `/event stop [id]` | Cancel: no prizes (op) |
| `/event time <set\|add> <duration> [id]` | Change the time left (op) |
| `/event points <players> <add\|set> <n> [id]` | Change scores; joins opt-in events (op) |
| `/event list` | What's running (and, for ops, what's defined) |
| `/event status [id]` | JSON for scripts: phase `lobby`/`running`/`none`, time, players, scores |
| `/event join [id]`, `/event leave [id]` | Opt in or out; `.join` / `.leave` in chat work too |
| `/announce [to <players>] <json>` | A banner at the top of the screen (op) |
| `/board show [to <players>] <json>`, `/board hide [players]` | The custom sidebar (op) |

`[id]` can be left out when only one event is running. Durations are seconds or text like `"5m"`,
`"90s"`, `"1m30s"`. Ties share a place (two firsts, then a third), and only players with at least
one point can place. Players in creative mode don't score from blocks or mobs unless the event says
`creative: true`.

Shipped events: `timber_rush`, `monster_hunt`, `sky_rings` (in `events/src/main/resources/data/skyrpg_events/skyrpg/events/`).
Start from one and change a few fields with `from`:

```
/event run {from:'timber_rush', duration:'2m', prizes:[{place:1, coins:500}]}
```

## Wood chopping

```
/event run {name:'Timber Rush', description:'Chop the most logs!', color:'#C8913C', icon:'minecraft:oak_log', duration:'5m', unit:'logs', scoring:[{type:'break_blocks', blocks:['#minecraft:logs'], points:1}], prizes:[{place:1, coins:100}, {place:2, coins:50}, {place:3, coins:25}]}
```

`python tools/events/gather_rush.py --minutes 5 --prize 100` sends this for you.

## Mob slaying

```
/event run {name:'Monster Hunt', color:'#E04848', icon:'minecraft:iron_sword', duration:'10m', scoring:[{type:'kill_mobs', entities:['minecraft:zombie','minecraft:spider'], points:1}, {type:'kill_mobs', entities:['#minecraft:skeletons','minecraft:creeper'], points:2}], prizes:[{place:1, coins:250, commands:['give {player} minecraft:diamond 2'], label:'250 coins + 2 diamonds'}, {place:'all', commands:['give {player} minecraft:cooked_beef 4'], label:'4 steak'}]}
```

A prize's `commands` run as the server with `{player}`, `{place}`, `{score}` and `{event}` filled in;
`coins: N` is short for `coins give {player} N` labelled "N coins". `place: 'all'` is for everyone who
scored.

## A custom points event

Anything you can't detect with a rule: ask a question, run a build contest, judge a race. Scores come
from commands (or `SkyRpgEvents.addPoints` in Java).

```
/event run {id:'trivia', name:'Trivia Night', description:'First to answer in chat gets a point!', color:'aqua', icon:'minecraft:book', duration:'15m', participation:'opt_in', scoring:[{type:'custom'}], prizes:[{place:1, coins:300}]}
/event points Drogashh add 1 trivia
/event end trivia
```

With `participation:'opt_in'` only players who type `.join` (or `/event join`) take part. Add
`lobby:'30s'` for a join window before the clock starts.

## Timed announcements inside an event

`announcements` takes numbers (a "N seconds left" warning in chat and the action bar) and banners at
`left` seconds before the end or `after` seconds from the start:

```
announcements:[60, 10, {after:30, label:'TWIST', title:'&6Golden logs count double!', icon:'minecraft:gold_ingot', color:'gold'}]
```

## Arena events (Sky Rings)

These fields make an event take players somewhere and give them gear. Each player's whole inventory is
saved on the server (`<world>/skyrpg_events/stash/<uuid>.dat`) before it's cleared, and given back
when the event ends or they leave it, or on their next join after a crash or disconnect.

```
/event run {name:'Sky Rings', participation:'opt_in', lobby:'20s', duration:'60s', unit:'rings', stash_inventory:true, kit:['minecraft:elytra[minecraft:unbreakable={}] 1 chest', 'minecraft:firework_rocket 64 hotbar.0'], auto_glide:true, start_at:'skyrpg_hub:hub 0.5 160 30.5 180 15', return:true, scoring:[{type:'custom'}], prizes:[{place:1, coins:100}]}
```

`python tools/events/sky_rings.py` starts the shipped `sky_rings` event, builds the barrier arena and
rings above the hub, and gives a point per ring flown through. `auto_glide` starts gliding as soon as a
player is in the air. Scripts can turn that on for anyone with `/tag <player> add skyrpg.glide`.

## Announcements

```
/announce {title:'Double drops!', subtitle:'For the next 10 minutes', label:'SERVER EVENT', color:'#2ECC71', icon:'minecraft:emerald', sound:'minecraft:ui.toast.challenge_complete'}
/announce to Drogashh {title:'Happy birthday, {player}!', color:'light_purple', icon:'minecraft:cake'}
```

Only `title` is needed. `sound:'none'` is silent and `chat:false` skips the chat copy. Text can use
`&6` colour codes or a full text component like `{text:'Hi', color:'gold', bold:true}`.

## Boards

```
/board show {title:'Build Battle', color:'aqua', lines:['&7Theme: &fCastles', '', ['Round', '2/3'], {label:'Time left', value:'4:00', color:'gold'}], footer:'skyrpg', duration:'5m'}
/board hide
```

A line is a string (`''` for a gap), a `[label, value]` pair, or `{label, value, color}`. Up to 15
lines. `{player}` becomes each viewer's name. `/board show to <players>` shows it to some players only,
on top of the board for everyone. The board replaces the vanilla scoreboard sidebar while it's up.
Players can move or hide it in the HUD editor, and switch the SkyRPG look for vanilla's sidebar off in
Settings.
