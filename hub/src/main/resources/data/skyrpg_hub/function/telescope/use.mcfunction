# A player looks through the Starwatch telescope: a few seconds of stargazing (fireworks bursting into stars over
# the observatory, a sparkle field, night vision) and the Stargazer achievement the first time. One show at a time.
execute if entity @a[tag=skyrpg_stargazing] run return run title @s actionbar {text:"Someone is already looking through the telescope...",color:"#C9B8FF"}
tag @s add skyrpg_stargazing
title @s times 10 70 20
title @s subtitle {text:"The sky above Skyhaven glitters...",color:"#C9B8FF",italic:true}
title @s title [{text:"✦ ",color:"#8FB8FF"},{text:"Stargazing",color:"#E6E0FF",bold:true},{text:" ✦",color:"#8FB8FF"}]
effect give @s minecraft:night_vision 15 0 true
playsound minecraft:block.amethyst_block.chime master @s ~ ~ ~ 1 0.8
playsound minecraft:block.beacon.activate master @s ~ ~ ~ 0.5 1.8
particle minecraft:end_rod 38.5 116 -62 14 3 12 0.01 160 force @a[distance=..96]
schedule function skyrpg_hub:telescope/star_burst 10t append
schedule function skyrpg_hub:telescope/star_burst 30t append
schedule function skyrpg_hub:telescope/star_burst 50t append
schedule function skyrpg_hub:telescope/star_burst 70t append
schedule function skyrpg_hub:telescope/done 110t replace
advancement grant @s only skyrpg_hub:skyhaven/stargazer
