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
