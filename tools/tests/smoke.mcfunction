# End-to-end smoke test for economy + skills, driven by a Carpet fake player "bot".
# python tools/mc.py script tools/tests/smoke.mcfunction
# Uses a stone platform at y=200 around 0,0 so terrain doesn't matter.

forceload add -8 -8 8 8
fill -6 199 -6 6 199 6 minecraft:stone
fill -6 200 -6 6 206 6 minecraft:air
kill @e[type=!player,distance=..20,x=0,y=200,z=0]
player bot kill
sleep 0.5
player bot spawn at 0 200 0 facing 0 0 in minecraft:overworld in survival
sleep 1
clear bot
coins set bot 0
skills reset bot

# --- Economy ---
give bot minecraft:wheat 64
execute as bot run worth
execute as bot run sell
coins bot
give bot minecraft:carrot 10
give bot minecraft:cobblestone 32
give bot minecraft:diamond_sword 1
execute as bot run sell inventory
coins bot
# The sword has no price, so it must still be there (expect: found 1)
clear bot minecraft:diamond_sword 0
execute as bot run sell
coins give bot 1000
coins take bot 50
coins bot

# --- Farming: grown wheat gives XP, unripe wheat doesn't ---
setblock 0 199 2 minecraft:farmland
setblock 0 200 2 minecraft:wheat[age=7]
player bot look at 0 200.2 2.5
sleep 0.5
player bot attack once
sleep 0.5
setblock 0 200 2 minecraft:wheat[age=3]
sleep 0.3
player bot attack once
sleep 0.5

# --- Mining: diamond ore with a pickaxe ---
setblock 0 200 2 minecraft:diamond_ore
item replace entity bot weapon.mainhand with minecraft:diamond_pickaxe
sleep 0.3
player bot attack continuous
sleep 2
player bot stop

# --- Combat: kill credited to the bot ---
summon minecraft:zombie 3 200 3 {NoAI:1b}
damage @e[type=minecraft:zombie,limit=1,sort=nearest,x=3,y=200,z=3] 100 minecraft:player_attack by bot
sleep 0.5

# --- Level up through the admin command ---
skills give bot farming 46
skills bot
