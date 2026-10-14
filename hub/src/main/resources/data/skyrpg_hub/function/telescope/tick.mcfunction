# The Starwatch telescope is an interaction entity (tag skyrpg_telescope) over the spyglass decor; a right-click
# leaves an "interaction" record on it, which starts the stargazing show for that player.
execute as @e[type=minecraft:interaction,tag=skyrpg_telescope] if data entity @s interaction run function skyrpg_hub:telescope/clicked
