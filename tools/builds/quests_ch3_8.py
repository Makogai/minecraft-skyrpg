"""Chapters 3 to 8 of the Skyhaven Chronicle: dungeons, slayers, enchanting, the Deep, the Nether, ruins and the End."""
from quests_dsl import *  # noqa: F401,F403

CH3 = ("03_into_the_dark", "Into the Dark", "minecraft:skeleton_skull",
       "Your first dungeon, your first slayer hunt and your first enchantment. Light a torch.", [
           [Q("a_door_in_the_woods", "A Door in the Woods", "weaponsmith",
              "Find the Forgotten Crypt.",
              [found("forgotten_crypt")], 400, 60, [("minecraft:torch", 32)],
              hint="The Track button leads you to the crypt.", shop=True,
              offer=[
                  ("serious", "There's old stonework under the forest floor. Nobody I trust has come back to say what's in it, and that tells you something."),
                  "The wild is full of &6dungeons&r: crypts, hollows, drowned temples. Each has an entrance with a &6waystone&r beside it, so you can always travel back. Walking up to one adds it to your map.",
                  "Dungeons also train a skill of their own, &6Delving&r. Find the crypt first. Press &6Track&r in the Journal and the compass will take you there.",
              ],
              complete=[
                  "You found it, and you're still breathing. Good. The crypt has two floors and a warden at the bottom. We'll talk about that next.",
              ],
              tip="Forests and plains hide it. Use Track in the Journal.")],
           [Q("into_the_crypt", "Into the Crypt", "weaponsmith",
              "Clear the Forgotten Crypt by defeating its warden.",
              [dungeon("forgotten_crypt")], 800, 120, [("minecraft:golden_apple", 2)],
              shop=True,
              offer=[
                  ("serious", "Two floors of the dead, a champion on the first, and the Crypt Warden below. Mob packs come in waves, with traps in the corridors and levers that mean something."),
                  "Every chest is &6yours alone&r: each player rolls their own loot, so no one can steal your drop. Bosses grow tougher with the size of your party. Bring friends, bring armour, and eat first.",
                  "Cut down the warden and the crypt is cleared. The first clear pays a bonus. It resets after a few days when nobody's near.",
              ],
              complete=[
                  ("proud", "The warden's down. You did what most only talk about. There's Delving XP in that, and loot in your pack. Mind the gear: some of it can only be made at the bench."),
              ],
              tip="Take armour, food and a good weapon. Chests are only yours, so loot them all.")],
           [Q("slayer_master", "The Slayer Master", "slayer_master",
              "Speak to Morwen and start a Slayer quest.",
              [slayer(1, started=True)], 400, 60,
              shop=True,
              offer=[
                  ("serious", "Morwen Ashgrave. I hunt what others flee from. You've the look of someone who'd like to, as well."),
                  "&6Slayer quests&r work like this. You pick a line and a tier, and pay the fee. Then you kill that line's monsters anywhere in the survival world. Each kill fills a bar. When it's full, the boss rises beside you.",
                  "Spawner mobs don't count; the boss can tell. If you die, or time runs out, the fee is gone. I don't do refunds. Start a quest at tier one, and see what it feels like.",
              ],
              complete=[
                  "A contract signed. You can see it on your screen now: the bar and the timer. Kill their kind and the bar fills. When it fills, don't run.",
              ],
              tip="Talk to Morwen and choose the Slayer quests tab. Tier one is the cheapest."),
            Q("a_touch_of_magic", "A Touch of Magic", "enchanter",
              "Enchant a piece of gear.",
              [obtain([], "Have an enchanted item in your inventory", enchanted=True)], 400, 60,
              shop=True,
              offer=[
                  ("whisper", "The runes are awake. I am &6Seraphine Vael&r. A blade is only a blade until it learns something."),
                  "An &6enchanting table&r offers levels by the bookshelves around it: fifteen make the best. My library counts as fifteen. Binding costs coins, experience levels and lapis. Higher enchants also need your Enchanting level.",
                  "Bring me gear and bind something. Anything. A pickaxe, a sword. Enchants are how a good item becomes a great one. Tomes drop from monsters, and can be combined or salvaged for Runic Dust.",
              ],
              complete=[
                  ("happy", "There. It hums. That is the first of many. Every enchant you discover goes into your Codex, and first discoveries pay Enchanting XP."),
              ],
              tip="Open the table, pick a piece of gear, pick an enchant and press Bind."),
            Q("crypt_spoils", "Crypt Spoils", "blacksmith",
              "Craft the Cryptbreaker at the Artisan Bench.",
              [craft("skyrpg_items:cryptbreaker", "Craft the Cryptbreaker")], 500, 80,
              shop=True,
              offer=[
                  "The warden's loot can't be worn as it is. Bring it to the bench and I'll show you how to turn it into something that bites back.",
                  "Dungeon gear is made from boss tokens. The &6Cryptbreaker&r needs Delving level five. The bench's Delving tab shows the recipe.",
              ],
              complete=[
                  "Good work. Dungeon gear is yours for life: no one else's loot, no one else's blade.",
              ],
              tip="Open the bench's Delving tab. The recipe shows what's missing.")],
           [Q("first_slayer", "Hunter's Mark", "slayer_master",
              "Finish a tier 1 Slayer quest by defeating its boss.",
              [slayer(1)], 600, 80,
              shop=True,
              offer=[
                  "Contracts aren't paid for by trying. Kill enough of the line to fill the bar, then face what rises. Finish one, and I'll call you a hunter. Not before.",
              ],
              complete=[
                  ("proud", "A hunter. Slayer XP raises your Slayer level, and each one brings better tiers and rewards. The bosses' drops are worth more than the coin. Bring ash, silk, marrow or void to the bench."),
              ],
              tip="Kill the line's monsters until the bar fills. The boss rises beside you.")],
           [Q("ready_for_more", "Ready for More", "tavern_keeper",
              "Reach Combat 12 and SkyRPG Level 5.",
              [skill("combat", 12), level(5)], 700, 100,
              offer=[
                  "You're no longer the person who walked in my door. Keep levelling a little, and then we'll talk about the red lands, where the world turns hostile.",
              ],
              complete=[
                  ("proud", "Combat twelve and level five. Pull up a stool. The next stretch is red and angry, and I want you well fed for it."),
              ],
              tip="Fight, level and quest. Everything counts towards SkyRPG Level.")],
       ])

CH4 = ("04_red_lands", "The Red Lands", "minecraft:red_sand",
       "Beyond the green hills the land turns hostile. Ash, frost, bog and blight. Each has something worth taking.", [
           [Q("danger_on_the_horizon", "Danger on the Horizon", "weaponsmith",
              "Enter a danger region.",
              [visit("region", "any", "Enter a danger region")], 800, 100,
              hint="Danger regions show on your map as coloured, skull-marked zones.", shop=True,
              offer=[
                  ("serious", "Skulls on the map mean exactly what you think they mean. Four &6danger regions&r are scattered about: the Blightlands, the Ashen Wastes, Frostgrave and the Mire."),
                  "Monsters inside are twelve to eighteen levels stronger, the land itself is corrupted, and special creatures roam. Their kills drop &6essences&r, and the rare &6Dread Crystal&r. Type &6/region&r to list the ones nearby.",
                  "Walk into one and feel the world change. Then walk back out, if you like.",
              ],
              complete=[
                  "You walked in, and you walked out. That's the right way round. Now, what you bring back is the point.",
              ],
              tip="Check your map for red zones, or try /region. Track points to the nearest one.")],
           [Q("essence_of_the_land", "Essence of the Land", "curio_dealer",
              "Collect essences from the creatures of the danger regions.",
              [obtain(["skyrpg_world:cinder_essence", "skyrpg_world:frost_essence", "skyrpg_world:mire_essence", "skyrpg_world:blight_essence"],
                      "Carry 10 region essences", 10)], 900, 120,
              shop=True,
              offer=[
                  ("whisper", "Each cursed place leaves something in the things that live there. Bring me some of it. No, I won't say what for."),
                  "Defeat monsters inside the danger regions and they drop &6essences&r: cinder, frost, mire and blight. Ten of any kind will do.",
              ],
              complete=[
                  ("happy", "Oh, lovely. Cold, and a little bit sad. Thank you. Essences are also the key to region gear. Brannoc will be very interested."),
              ],
              tip="Kill monsters inside a danger region. They drop essences.")],
           [Q("regional_pride", "Regional Pride", "blacksmith",
              "Craft a piece of region gear at the Artisan Bench.",
              [craft(["skyrpg_items:ashen_bracers", "skyrpg_items:mirefang", "skyrpg_items:frostwoven_cloak", "skyrpg_items:bog_belt",
                      "skyrpg_items:cinderbrand", "skyrpg_items:blightreaver", "skyrpg_items:rimecleaver", "skyrpg_items:blightheart_amulet"],
                     "Craft a piece of region gear")], 1100, 150,
              shop=True,
              offer=[
                  "Region essence, region steel. Take what the land gave you and forge it into something the land would hate.",
                  "Open the bench and look at the region gear recipes. Some need a Dread Crystal, which only the toughest region monsters drop.",
              ],
              complete=[
                  "Fine work. Region gear carries the land's own power. It feels it, when you wear it.",
              ],
              tip="Check the bench for recipes that use essences."),
            Q("webs_and_water", "Webs and Water", "weaponsmith",
              "Clear Spider Hollow or the Drowned Temple.",
              [dungeon(["spider_hollow", "drowned_temple"], "Clear Spider Hollow or the Drowned Temple")], 1500, 200, [("minecraft:golden_apple", 3)],
              shop=True,
              offer=[
                  "Two more lairs, two nightmares. A hollow full of spiders under the dark forest, and a drowned temple under the sea. Clear whichever you dare first.",
                  "Both are tougher than the crypt. Bring antidotes, food and a friend. Both have their own bosses: Queen Arachne and the Tidemother.",
              ],
              complete=[
                  ("proud", "Whichever you chose, it's down. Their gear is craftable too: Arachne's Fang, the Tidemother Pendant. Go and see what the bench says."),
              ],
              tip="Spider Hollow lies under dark forests, the Drowned Temple under oceans."),
            Q("second_hunt", "A Bigger Beast", "slayer_master",
              "Finish a tier 2 Slayer quest.",
              [slayer(2)], 1200, 150,
              shop=True,
              offer=[
                  "Tier one was practice. Tier two will bite. The bar's longer, the boss bigger. Pay the fee, finish the hunt, and come back.",
              ],
              complete=[
                  ("proud", "Tier two. You're getting good at this. Slayer bosses can drop rare tomes. Seraphine will tell you what to do with them."),
              ],
              tip="Pick a tier two contract at Morwen's stall.")],
           [Q("scarlet_scholar", "Scarlet Scholar", "tavern_keeper",
              "Reach Combat 18, Mining 14 and SkyRPG Level 9.",
              [skill("combat", 18), skill("mining", 14), level(9)], 1500, 200,
              offer=[
                  "The red lands don't forgive, and you walked through them anyway. Come, sit, tell me everything. And while you do, keep levelling, would you?",
              ],
              complete=[
                  ("proud", "Everything, and then some. Sit down, have a bowl. There's a whole other world beneath your feet, and it's time you went to see it."),
              ],
              tip="Levels in Combat, Mining and your SkyRPG Level all count.")],
       ])

CH5 = ("05_the_deep", "The Deep", "minecraft:deepslate",
       "Under the world, where the stone is old. Glimmer, spores, dwarven halls and a sea of fire.", [
           [Q("down_we_go", "Down We Go", "mining_master",
              "Descend into the Glimmer Caverns.",
              [layer("glimmer_caverns")], 1500, 150, [("minecraft:torch", 64)],
              hint="Layers are underground bands: you only enter them below the open sky.", shop=True,
              offer=[
                  ("excited", "{player}! The world goes a long way down, and every layer has its own kind of dark. We call it &6the Deep&r."),
                  "There are four layers. The &6Glimmer Caverns&r, from sea level and a little above. Then the Fungal Depths, the Dwarven Halls, and at the very bottom, the Magma Rifts. Each is stranger and more dangerous than the last.",
                  "Underground in a layer you get an arrival title, special monsters and rich drops. &6/layer&r tells you where you are. Start with the glimmer. Mind the miners; they're not friendly.",
              ],
              complete=[
                  ("proud", "The glimmer! Look at those crystals. You've seen what's down there. Geodes, shards, all of it feeds my forge and yours."),
              ],
              tip="Dig down from a cave until the title says Glimmer Caverns.")],
           [Q("fungus_among_us", "Fungus Among Us", None,
              "Descend into the Fungal Depths.",
              [layer("fungal_depths")], 1800, 180,
              offer=[
                  "The stone beneath your feet grows soft. A damp sweetness fills the air, and the walls are furred with something pale and glowing.",
                  "&7These are the &6Fungal Depths&7: mycelium, glowcaps and giant mushrooms. Spore Puffballs poison you, so don't breathe deep, and keep a lantern lit.",
              ])],
           [Q("the_dwarven_halls", "The Dwarven Halls", "mining_master",
              "Descend to the Dwarven Halls layer and clear the Dwarven Halls dungeon.",
              [layer("dwarven_halls"), dungeon("dwarven_halls")], 2800, 300,
              shop=True,
              offer=[
                  "Stone and runes, and machines that never learned the dwarves had left. Below the fungus lies the &6Dwarven Halls&r layer, and in it, a ruined dwarven city.",
                  "Find the halls, then find the Thane. The &6Iron Thane&r is a boss unlike the others. Abandoned mine camps in caves sometimes hold a map to it.",
              ],
              complete=[
                  ("excited", "The Thane's down! There hasn't been a delver like you in a hundred years. His halls are yours."),
              ],
              tip="Dig into the Dwarven Halls layer (below the fungus) and look for the surface gate.")],
           [Q("skyiron_smith", "Skyiron Smith", "blacksmith",
              "Reach Mining 18 and craft a Skyiron Pickaxe at the Artisan Bench.",
              [skill("mining", 18), craft("skyrpg_items:skyiron_pickaxe", "Craft a Skyiron Pickaxe")], 2500, 250,
              shop=True,
              offer=[
                  "Skyiron rings like a bell. Make a pick out of it and the whole mountain will hear you coming. It needs &6Mining 18&r and a Delver's Pickaxe to upgrade.",
              ],
              complete=[
                  "Hm. Listen to that ring. Upgrading keeps the enchants. A proper pick, at last.",
              ],
              tip="Upgrade your Delver Pickaxe at the bench."),
            Q("delvers_creed", "Delver's Creed", "mining_master",
              "Reach Delving level 5.",
              [skill("delving", 5)], 2000, 200,
              shop=True,
              offer=[
                  "Delving is a skill of its own, my friend: discovering places, opening them and surviving them. Practice makes you better at all three. Reach &6Delving 5&r.",
              ],
              complete=[
                  ("proud", "Delving five. Dungeon gear is unlocked at five, twelve and twenty. Have a look at the bench."),
              ],
              tip="Clear dungeons: every chest and boss pays Delving XP.")],
           [Q("the_magma_rifts", "The Magma Rifts", None,
              "Descend into the Magma Rifts.",
              [layer("magma_rifts")], 3000, 350, [("minecraft:golden_apple", 4)],
              offer=[
                  "The air thickens and the rock turns black. Far below, orange light flickers through cracks, and the stone is warm under your boots.",
                  "&7You have reached the very bottom of the world: the &6Magma Rifts&7. Basalt, magma and lava lakes, and creatures that thrive on the heat. There's a stair sealed here that leads to the Catacombs.",
              ])],
       ])

CH6 = ("06_fire_below", "Fire Below", "minecraft:netherrack",
       "The Nether is not a place for tourists. Forge in its fire, fish in its lava and burn down a citadel.", [
           [Q("through_the_portal", "Through the Portal", "weaponsmith",
              "Enter the Nether.",
              [visit("dimension", "minecraft:the_nether", "Enter the Nether")], 3000, 300, [("minecraft:golden_apple", 4)],
              shop=True,
              offer=[
                  ("serious", "Obsidian, a flint and your courage. The first two are easy to come by."),
                  "The &6Nether&r is a different world: fire, lava, and creatures that burn. Build a portal in the survival world and step through. Your waypoints and map are kept per dimension, so mark where you came from.",
                  "Bring fire resistance if you have it, and plenty of blocks. Lava doesn't give second chances.",
              ],
              complete=[
                  "You're back, a little singed. Good. Nobody likes the Nether the first time. They just go back.",
              ],
              tip="A portal is a frame of obsidian, lit with flint and steel.")],
           [Q("the_ember_forest", "The Ember Forest", None,
              "Find the Ember Forest in the Nether.",
              [visit("biome", "skyrpg_world:ember_forest", "Find the Ember Forest", dimension="minecraft:the_nether")], 3500, 350,
              offer=[
                  "Heat shimmers over a landscape of ash and ember. Trees that burn and never fall stand in rows, and small imps flit between them.",
                  "&7Somewhere in this world is the &6Ember Forest&7: Emberwood trees on smouldering ash. Press &6Track&7 and the compass will lead you there.",
              ])],
           [Q("hellforged", "Hellforged", "mining_master",
              "Gather Hellforged Scrap.",
              [collect("hellforged_scrap", 64)], 4000, 400,
              shop=True,
              offer=[
                  ("excited", "Scrap from the deep fire. Hellforged ore grows in netherrack, blackstone and basalt, and it takes a Tier V pickaxe to break. It's richest around the Obsidian Spires."),
                  "Gather sixty-four pieces. Blast the scrap into ingots, and you'll have the best metal there is.",
              ],
              complete=[
                  "Look at all that! Smelt it in a blast furnace. Brannoc will want to talk about armour.",
              ],
              tip="Hellforged ore: Tier V pickaxe, in the Nether."),
            Q("lava_angler", "Lava Angler", "fisher",
              "Reach Fishing 20 and catch lava fish.",
              [skill("fishing", 20), collect("magma_fish", 8)], 4000, 400,
              shop=True,
              offer=[
                  "A line in lava? Aye, with the right rod and the right nerve. The fish down there are warm and very angry.",
                  "You need &6Fishing 20&r and a rod that won't burn. Catch eight &6Magma Fish&r.",
              ],
              complete=[
                  ("laugh", "Hah! Lava fish. Thirty years on these waters and I never thought I'd see it. Good catch, love."),
              ],
              tip="Cast into lava. Mind the heat.")],
           [Q("the_infernal_citadel", "The Infernal Citadel", "weaponsmith",
              "Clear the Infernal Citadel.",
              [dungeon("infernal_citadel")], 6000, 600, [("minecraft:golden_apple", 6)],
              shop=True,
              offer=[
                  ("serious", "A blackstone fortress on the lava sea, and a tyrant who has been waiting for you since before you were born. Take friends if you have them."),
                  "The &6Infernal Citadel&r is built over lava bridges and into cliffs. The Infernal Knight guards the way, and the &6Magma Tyrant&r sits on the Molten Throne. It drops Hellforged scrap and the Tyrant's Core.",
              ],
              complete=[
                  ("proud", "The Tyrant is dead. I never thought I'd see it. Take this, and take the Core to Brannoc."),
              ],
              tip="The citadel is in the Nether. Track will point to it.")],
           [Q("forged_in_fire", "Forged in Fire", "blacksmith",
              "Craft a piece of Hellforged armor at the Artisan Bench.",
              [craft(["skyrpg_items:hellforged_helmet", "skyrpg_items:hellforged_chestplate", "skyrpg_items:hellforged_leggings",
                      "skyrpg_items:hellforged_boots"], "Craft a piece of Hellforged armor")], 5000, 500,
              shop=True,
              offer=[
                  "The Hellforged set is a thing of terrible beauty. Bring me what the Tyrant left and I'll make you into a walking forge.",
              ],
              complete=[
                  "Hm. Warm to the touch. Good. It's meant to be.",
              ],
              tip="Hellforged ingots and the Tyrant's Core. See the bench.")],
           [Q("fireproof", "Fireproof", "tavern_keeper",
              "Reach Combat 28 and SkyRPG Level 20.",
              [skill("combat", 28), level(20)], 5000, 500,
              offer=[
                  "You smell of smoke and you're smiling. I know that look. Rest your feet, the next stretch is old stone. Reach &6Combat 28&r and &6SkyRPG Level 20&r first.",
              ],
              complete=[
                  ("proud", "Smoke and all. Sit, eat. When you're rested, there are ruins that have been waiting a very long time."),
              ],
              tip="Keep levelling. Contracts and slayer quests add up.")],
       ])

CH7 = ("07_ancient_ruins", "Ancient Ruins", "minecraft:stone_bricks",
       "Cities that fell long before Skyhaven rose. They have not forgotten. Neither will you.", [
           [Q("whispers_of_stone", "Whispers of Stone", "curio_dealer",
              "Find the Ruined Citadel.",
              [found("ruined_citadel")], 6000, 500,
              shop=True,
              offer=[
                  ("whisper", "I hear a fortress in the cold peaks is still standing, if you call that standing. Go and see for yourself."),
                  "The &6Ruined Citadel&r lies in the mountains and peaks. Three floors sunk into the rock. Look for the waystone at its door.",
              ],
              complete=[
                  ("happy", "You found it! Lovely. Don't go in unprepared; the Fallen Knight keeps the gate."),
              ],
              tip="Mountain and peak biomes. Use Track.")],
           [Q("the_ruined_citadel", "The Ruined Citadel", "weaponsmith",
              "Clear the Ruined Citadel.",
              [dungeon("ruined_citadel")], 9000, 800, [("minecraft:golden_apple", 8)],
              shop=True,
              offer=[
                  ("serious", "The Fallen Knight still keeps the gate. Honour him, then end him."),
                  "Three floors: the Outer Ward, the Keep and the Throne Room. Armoured knights and archers, then the Knight-Captain, then the Knight himself. A Citadel Crest Key opens the vault.",
              ],
              complete=[
                  ("proud", "Done. The Citadel is yours, and you've earned the Fallen Crest. Wear it."),
              ],
              tip="Bring a party. Boss health scales with size.")],
           [Q("the_catacombs", "The Catacombs", "weaponsmith",
              "Find the Catacombs and clear them.",
              [found("catacombs"), dungeon("catacombs")], 12000, 1000,
              shop=True,
              offer=[
                  ("serious", "Bones upon bones. Somewhere beneath all of it, a Bonelord. Find a way in, and find a way out."),
                  "The &6Catacombs&r lie deep underground, at the bottom of the world. A sealed stair in the Magma Rifts leads to them. Three Bone Wardens each hold a key; you need all three for the tomb.",
              ],
              complete=[
                  ("proud", "The Bonelord's down, and the Catacombs are silent. The Phylactery is yours. Don't ask what it does."),
              ],
              tip="Magma Rifts. Look for the sealed stair.")],
           [Q("slayer_veteran", "Veteran of the Hunt", "slayer_master",
              "Finish a tier 4 Slayer quest.",
              [slayer(4)], 10000, 800,
              shop=True,
              offer=[
                  "I've trained a dozen hunters. You are the first to make it to this table. Finish a tier four contract and I'll call you friend.",
              ],
              complete=[
                  ("proud", "Friend, then. Tier four, and the golden egg with it, if luck allows. The hunt is yours."),
              ],
              tip="Tier four costs sixty thousand coins. Prepare."),
            Q("pearls_before_the_void", "Pearls Before the Void", "curio_dealer",
              "Gather Ender Pearls.",
              [collect("ender_pearl", 64)], 9000, 700,
              shop=True,
              offer=[
                  ("whisper", "The way to the End needs a pearl, and a lot of pearls. Sixty-four, to be safe. I'm not exaggerating much."),
              ],
              complete=[
                  ("happy", "Pearls! Lovely, lovely. These will take you to the edge. Hold them close."),
              ],
              tip="Endermen drop them. Night is best.")],
       ])

CH8 = ("08_the_void", "The Void", "minecraft:end_stone",
       "The edge of the world, and what lives beyond it. The last chapter, for now.", [
           [Q("edge_of_the_world", "Edge of the World", "weaponsmith",
              "Enter the End.",
              [visit("dimension", "minecraft:the_end", "Enter the End")], 12000, 800,
              shop=True,
              offer=[
                  ("serious", "The End isn't a place. It's the absence of one. Mind your step."),
                  "Find a stronghold, fill the portal with eyes, and jump. Take blocks to bridge, and don't look down.",
              ],
              complete=[
                  "You came back. Good. Few do twice.",
              ],
              tip="A stronghold's portal frame needs twelve eyes of ender.")],
           [Q("outer_islands", "The Outer Islands", None,
              "Reach the Shattered Isles of the outer End.",
              [visit("biome", "skyrpg_world:shattered_isles", "Reach the Shattered Isles", dimension="minecraft:the_end")], 14000, 1000,
              offer=[
                  "Past the main island the void drifts with scattered shores, small floating rubble islands of end stone and amethyst, with gaps in between.",
                  "&7Find the &6Shattered Isles&7. Bridge the gaps and look up. It's quite a view. Press &6Track&7 to be pointed there.",
              ])],
           [Q("the_astral_observatory", "The Astral Observatory", "weaponsmith",
              "Find the Astral Observatory and defeat the Star Warden.",
              [found("astral_observatory"), dungeon("astral_observatory")], 20000, 1500, [("minecraft:golden_apple", 10)],
              shop=True,
              offer=[
                  ("serious", "Somebody built a place to watch the stars from the edge of everything. Something else moved in. Clear it out."),
                  "The &6Astral Observatory&r stands on stilts on the outer islands. Past the Astral Sentinel, up the Ascent, the Star Warden waits in his glass dome. Astral Shards drop here: the key to the Ender Wyrm's altar.",
              ],
              complete=[
                  ("proud", "The Star Warden has fallen. You've stood where nobody stands. The shards are yours."),
              ],
              tip="Outer End islands. Track will find it.")],
           [Q("the_wyrm", "The Ender Wyrm", "tavern_keeper",
              "Defeat the Ender Wyrm.",
              [kill("skyrpg_combat:ender_wyrm", 1, "Ender Wyrm")], 25000, 2000, [("minecraft:golden_apple", 16)],
              offer=[
                  "There's a story about a wyrm that coils through the dark. I always took it for a story. Do me a favour, {player}, and prove me wrong.",
                  "Gather Astral Shards for the altar, and call it. It's the toughest fight there is. Bring everything.",
              ],
              complete=[
                  ("laugh", "You killed it! I told them! I told every one of them it was real!"),
              ],
              tip="Astral Shards build the altar in the End.")],
           [Q("the_chronicle_ends", "The Chronicle, So Far", "tavern_keeper",
              "Reach SkyRPG Level 35 and tell Hollis the tale.",
              [level(35)], 30000, 2500, [],
              hint="More chapters will follow. For now, the whole sky is yours.",
              offer=[
                  "You came in as a stranger. Now look at you. I'd like to hear every tale of it, from the first stew. But first, a few more levels, would you? A legend should be properly levelled.",
              ],
              complete=[
                  ("proud", "Level thirty-five. Sit. Eat. Tell me everything, from the carrots on. The Chronicle is yours to keep writing. I'll keep the stew hot."),
                  "And that is the end of the book, for now. More chapters will come. The whole sky is yours.",
              ],
              tip="Keep going: every quest, collection and boss adds to your level.")],
       ])

CHAPTERS_B = [CH3, CH4, CH5, CH6, CH7, CH8]
