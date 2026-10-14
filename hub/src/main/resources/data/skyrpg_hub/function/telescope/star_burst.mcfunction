# One rocket rising over the observatory and bursting into a white-blue star, with sparkles where it pops.
execute in skyrpg_hub:hub run summon minecraft:firework_rocket 38.5 104 -66 {LifeTime:22,FireworksItem:{id:"minecraft:firework_rocket",components:{"minecraft:fireworks":{explosions:[{shape:"star",colors:[I;16777215,9419007],fade_colors:[I;6253311],has_trail:true,has_twinkle:true}]}}}}
execute in skyrpg_hub:hub run summon minecraft:firework_rocket 30.5 102 -60 {LifeTime:26,FireworksItem:{id:"minecraft:firework_rocket",components:{"minecraft:fireworks":{explosions:[{shape:"small_ball",colors:[I;13226239,16777215],has_twinkle:true}]}}}}
execute in skyrpg_hub:hub positioned 38.5 120 -64 run particle minecraft:end_rod ~ ~ ~ 10 2 8 0.02 60 force @a[distance=..96]
