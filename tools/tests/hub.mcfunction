# Hub behaviour test with a Carpet fake player "bot".
# python tools/mc.py script tools/tests/hub.mcfunction
# Starts the bot on a platform at y=200 in the overworld (made by smoke.mcfunction).

player bot kill
sleep 0.5
execute in minecraft:overworld run forceload add -8 -8 8 8
execute in minecraft:overworld run fill -2 199 -2 2 199 2 minecraft:stone
player bot spawn at 0 200 0 facing 0 0 in minecraft:overworld in survival
sleep 1

# /hub -> hub spawn (expect: skyrpg_hub:hub, near 0.5 65 10.5)
execute as bot run hub
sleep 1
data get entity bot Dimension
data get entity bot Pos

# Protection: can't mine the plaza even with a pickaxe (expect: Test passed)
item replace entity bot weapon.mainhand with minecraft:diamond_pickaxe
player bot look at 0.5 64.5 8.5
sleep 0.3
player bot attack continuous
sleep 1.5
player bot stop
execute in skyrpg_hub:hub if block 0 64 8 minecraft:stone_bricks

# Protection: can't place blocks (expect: Test passed, the spot stays air)
item replace entity bot weapon.mainhand with minecraft:stone 16
player bot use once
sleep 0.5
execute in skyrpg_hub:hub if block 0 65 8 minecraft:air

# No damage in the hub (expect: Health 20.0f)
damage bot 5 minecraft:generic
sleep 0.3
data get entity bot Health

# Falling into the void puts you back at spawn (expect: Pos near 0.5 65 10.5)
execute in skyrpg_hub:hub run tp bot 0 -5 0
sleep 1
data get entity bot Pos

# Walking into the gate goes back to where you left the survival world (expect: overworld, 0 200 0)
execute in skyrpg_hub:hub run tp bot 0 66 47.5
sleep 1
data get entity bot Dimension
data get entity bot Pos

# /survival outside the hub is refused
execute as bot run survival

# /hub then /survival returns to the same place (expect: overworld, 0 200 0)
execute as bot run hub
sleep 0.5
execute as bot run survival
sleep 0.5
data get entity bot Dimension
data get entity bot Pos

player bot kill

# /survival with nowhere saved goes to the survival spawn on the surface, not the void (expect: Pos y well above -64)
player fresh spawn at 0.5 65 14.5 facing 180 0 in skyrpg_hub:hub in survival
sleep 1
execute as fresh run survival
sleep 0.5
data get entity fresh Pos
player fresh kill
execute in minecraft:overworld run forceload remove -8 -8 8 8
