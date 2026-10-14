# Runs as the telescope's interaction entity: "on target" is the player who just right-clicked it.
execute on target at @s run function skyrpg_hub:telescope/use
data remove entity @s interaction
