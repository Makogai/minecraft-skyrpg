# The end game (Adventure Plan v2, phase 5)

The last stretch: two big dungeons in the Overworld and a reworked End with a final boss. Decided 2026-10-13 (design calls are
delegated; see `docs/design/progression.md` "Decisions (made)").

## Ruined Citadel (Overworld, mountains, rare)

A huge three-floor castle ruin on mountain peaks, built like the v2 dungeons (`tools/builds/dungeon_kit.py`). Level about 56.
- **Floor 1, Outer Ward:** a gatehouse, courtyards, barracks and stables.
- **Floor 2, the Keep:** a library, a great hall and a chapel.
- **Floor 3, Throne Room:** knights and archers (armoured skeletons and zombies), mounted knights, banners, a portcullis puzzle.
- **Champion:** the Knight-Captain.
- **Boss:** **the Fallen Knight**, a giant cursed knight in black plate (a scaled wither skeleton with netherite gear and a banner).
- **Trophy:** the Fallen Crest.

## The Catacombs (deep underground, very rare)

A labyrinth under the Magma Rifts (y -60 to -40), reached through a sealed stair found in the rifts. Level about 60.
- **Content:** everything at once: puzzles (levers, pressure plates), traps, three mini-bosses (Bone Wardens), and a **key hunt**. Three Ossuary Keys open the inner tomb.
- **Boss:** **the Bonelord**, a towering skeletal king (the Bone Giant model scaled up, with a crown).
- **Trophy:** the Bonelord's Phylactery.

## The End rework

New End biomes are added with Fabric's `TheEndBiomes`, appearing in newly generated outer-End chunks:
- **Void Gardens:** purple chorus-like trees, glowing flora (the Void Stalker only exists as a slayer boss, so no nests: vanilla endermen).
- **Shattered Isles:** floating rubble islands with parkour gaps and amethyst.
- **Astral Fields:** end stone with "stars in the ground" (glowing specks) and astral crystals.

**Astral Observatory** is a dungeon on the outer islands (level about 64). Its champion is **the Astral Sentinel** and its boss is **the Star Warden**.

## The Ender Wyrm (final boss)

After the dragon dies, an **Ender Wyrm Altar** can be built from Astral Crystals plus each dungeon trophy (Warden's Sigil,
Arachne's Eye, Tide Pearl, Thane's Rune, Tyrant's Core, Fallen Crest, Bonelord's Phylactery). It summons **the Ender Wyrm**:
- It's a long serpentine dragon with its own segmented 3D model.
- It flies in arcs and dives.
- It breathes void fire.
- It summons Void Echoes, and goes through three phases.

Its drops are the best gear in the pack: the **Wyrmscale** armour set and the **Voidreaver** sword.

## Order

1. End biomes and the Astral Observatory.
2. Ruined Citadel and Catacombs (dungeon data).
3. The Ender Wyrm entity and model, then the altar.
4. Wyrmscale and Voidreaver gear.

## Ender Wyrm: summoning, rewards and flight fixes (2026-10-14)

- **Void Sigil** (`skyrpg_combat:void_sigil`, `VoidSigilItem`): used in the End, only when the dragon was killed before (`EnderDragonFight.hasPreviouslyKilledDragon`)
  and is not alive, and no Wyrm or ritual runs in that dimension. Bench recipe (Combat 40): 16 Astral Shards and one of each of the eight trophies give **3** Sigils.
  It replaces the "altar block": no block entity, nothing to build or protect, and the arena is wherever the user stands.
- **Ritual** (`EnderWyrms`): 130 ticks of closing ring, two rising spirals, shakes and flashes (`Vfx`), two visual bolts, then `EnderWyrms.summon`. Fizzles (Sigil
  returned) if everyone has left; the Wyrm itself withdraws after 30 s with nobody alive in its arena (`EnderWyrmEntity.withdraw`) and returns the Sigil to the summoner.
- **Credit:** damage dealers plus anyone within 64 blocks of the centre for 15+ seconds. Each gets 12-18 Wyrm Scales, 1 (35%: 2) Void Hearts, 10,000 coins, 8,000 XP,
  Combat XP, a roll of the loot table (the magic mod's scrolls), and `BossKills.KILLED` (id `skyrpg_combat:ender_wyrm`, `WORLD_BOSS`, SkyRPG XP first time; the quest
  mod counts it for "kill" objectives). Items go to the inventory, never to the floor over the void.
- **Gear:** Wyrmscale set (Health 460, Defense 400, Strength 130, Crit Damage 60; bonus "Void Scales": 12% dodge, 3% life steal, 12% Crit Chance, 40% Crit Damage,
  50 Intelligence) and the **Voidreaver** (damage 230, Strength 150, Intelligence 60, Crit Chance 15%, Crit Damage 100%, 5% life steal, Void Rend: 80 mana, 25 s,
  pulls every hostile mob within 8 blocks into a rift then collapses it for 150 damage and Wither II; `WeaponAbilities.voidRend`). Upgrade recipes at Combat 50.
- **Flight fixes:** (1) the client's body path started with the head's end-of-tick position, ahead of the interpolated head, so the body doubled back at the front every
  frame (flicker/jitter); (2) the trail was empty for a newly tracked Wyrm, so the body started as a straight line along its yaw; (3) dives overshot into the ground with a
  third of their speed left and the Wyrm has no collisions (head and body went several blocks into terrain): the head now keeps 2.2 blocks above the highest block
  below it, the dive levels off near its mark, and the airspace floor is real velocity, not a 0.05 nudge.

## Status (2026-10-13)

**Built: End biomes and the Astral Observatory (step 1).** The Ruined Citadel, Catacombs, Ender Wyrm and the gear are not done yet.

- **Biomes** (`EndRealm`, data in `world/.../worldgen/biome/{void_gardens,shattered_isles,astral_fields}.json`), added with Fabric's `TheEndBiomes`
  so existing worlds get them in newly generated outer-End chunks (the main island is untouched):
  - `skyrpg_world:void_gardens`: an alternative to the End Highlands (weight 0.8 against vanilla's 1.0). Purple void trees (`VoidTreeFeature`: purpur pillar
    trunk and branches, a canopy of glowing Void Cap), patches of glowing Void Bloom, vanilla chorus plants, endermen.
  - `skyrpg_world:shattered_isles`: an alternative to the small outer islands (0.8). Keeps vanilla's end islands and adds floating rubble islets
    (`ShatteredIslandFeature`: end stone, bricks, purpur and amethyst, with amethyst clusters and loose stepping stones, three tries per chunk). Endermen, endermites.
  - `skyrpg_world:astral_fields`: an alternative to the End Midlands (0.9, under the highlands and the Void Gardens). `AstralSurfaceFeature` turns about one
    end stone surface block in six into Starstone and raises Astral Crystal clusters (about one column in ninety). Endermen, endermites.
  - Each biome has fog and sky colours, ambient particles (warped spores, white ash, end rods), ambient sounds (loop, mood and additions) and `music.end`.
    No Void Stalker-like mobs are added to the spawn lists: the combat mod only has the Void Stalker as a slayer boss, with no normal variant.
- **Blocks** (`WorldContent`, textures from `tools/art/generate_world.py`, add-only): `starstone` (light 6), `astral_crystal` (light 11, drops 2-4 Astral Shards,
  Silk Touch keeps the block), `void_bloom` (light 8), `void_cap` (light 7). **Items:** `astral_shard` (rare material, for the altar), `observatory_key`,
  `star_warden_core` (epic trophy, sells for 11,000).
- **The Astral Observatory** (`skyrpg_world:astral_observatory`, `tools/builds/dungeons/astral_observatory.py`, see `docs/design/dungeons.md`):
  Lv 60-65, two floors (the Lower Halls, then 20 up the Star Deck), 42 templates. Champion **the Astral Sentinel** (an enderman scaled 1.8, 24,000 HP);
  boss **the Star Warden** (an enderman scaled 2.5, 60,000 HP): blink, starlight volley, slam, a pull and a summon below 66%, a ward below 50%, a shockwave and
  enrage below 33%. Drops 1-2 Star Warden's Cores, the key, 8-14 Astral Shards, a Reward Chest.
- **Not done / for later:** the Ender Wyrm altar recipe (Astral Crystals plus trophies; the Astral Shard is the material this adds) and the Wyrm itself.
