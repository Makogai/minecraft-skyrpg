"""Generates the enchanted materials' resources for skyrpg_items: item models (copies of each material's), names, Artisan
Bench recipes and sell prices, plus the Java table for Enchanted.MATERIALS (written to %TEMP%). See docs/design/compactors.md.

    python tools/gen_enchanted.py
"""
import json, os, zipfile, glob

ROOT = r'D:\code\mc-modpack'
JAR = os.path.expanduser(r'~\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-clientonly-deobf\26.3\minecraft-clientonly-deobf-26.3.jar')

GROUPS = {
    'mining': ['cobblestone', 'cobbled_deepslate', 'granite', 'diorite', 'andesite', 'tuff', 'calcite', 'dripstone_block', 'dirt',
               'gravel', 'sand', 'red_sand', 'clay_ball', 'flint', 'netherrack', 'basalt', 'blackstone', 'soul_sand', 'soul_soil',
               'end_stone', 'obsidian', 'ice', 'snowball', 'coal', 'raw_iron', 'iron_ingot', 'raw_gold', 'gold_ingot', 'raw_copper',
               'copper_ingot', 'lapis_lazuli', 'redstone', 'emerald', 'diamond', 'quartz', 'glowstone_dust', 'amethyst_shard',
               'skyrpg_mining:glimmer_shard', 'skyrpg_mining:raw_skyiron', 'skyrpg_mining:duskquartz_shard',
               'skyrpg_mining:emberite_chunk', 'skyrpg_mining:hellforged_scrap', 'skyrpg_mining:lumenite_crystal'],
    'farming': ['wheat', 'wheat_seeds', 'carrot', 'potato', 'beetroot', 'pumpkin', 'melon_slice', 'sugar_cane', 'cactus',
                'cocoa_beans', 'nether_wart', 'red_mushroom', 'brown_mushroom', 'sweet_berries', 'apple',
                'skyrpg_farming:dewdrop', 'skyrpg_farming:sunwheat_sheaf', 'skyrpg_farming:frostberries',
                'skyrpg_farming:emberpepper', 'skyrpg_farming:moonmelon_slice'],
    'foraging': ['oak_log', 'spruce_log', 'birch_log', 'jungle_log', 'acacia_log', 'dark_oak_log', 'mangrove_log', 'cherry_log',
                 'pale_oak_log', 'crimson_stem', 'warped_stem', 'bamboo',
                 'skyrpg_foraging:ironbark_log', 'skyrpg_foraging:mistpine_log', 'skyrpg_foraging:duskwillow_log',
                 'skyrpg_foraging:emberwood_log', 'skyrpg_foraging:starwood_log'],
    'combat': ['rotten_flesh', 'bone', 'string', 'spider_eye', 'gunpowder', 'ender_pearl', 'slime_ball', 'blaze_rod', 'magma_cream',
               'leather', 'feather', 'beef', 'porkchop', 'chicken', 'mutton', 'rabbit', 'white_wool'],
    'fishing': ['cod', 'salmon', 'pufferfish', 'tropical_fish', 'ink_sac', 'prismarine_shard', 'prismarine_crystals'],
}
NAME_FIX = {'skyrpg_farming:emberpepper': 'Emberpepper', 'minecraft:quartz': 'Nether Quartz'}
COUNT = 160

jar = zipfile.ZipFile(JAR)
vlang = json.loads(jar.read('assets/minecraft/lang/en_us.json'))
mod_lang = {}
for f in glob.glob(ROOT + r'\*\src\main\resources\assets\skyrpg_*\lang\en_us.json'):
    mod_lang.update(json.load(open(f, encoding='utf-8')))
prices = {}
for f in glob.glob(ROOT + r'\*\src\main\resources\data\*\skyrpg\sell_prices\*.json'):
    if 'enchanted' in f:
        continue
    prices.update(json.load(open(f, encoding='utf-8')))

def model_of(full):
    ns, path = full.split(':')
    if ns == 'minecraft':
        return json.loads(jar.read(f'assets/minecraft/items/{path}.json'))
    mod = ns.replace('skyrpg_', '')
    return json.load(open(rf'{ROOT}\{mod}\src\main\resources\assets\{ns}\items\{path}.json', encoding='utf-8'))

def name_of(full):
    if full in NAME_FIX:
        return NAME_FIX[full]
    ns, path = full.split(':')
    lang = vlang if ns == 'minecraft' else mod_lang
    for k in (f'item.{ns}.{path}', f'block.{ns}.{path}'):
        if k in lang:
            return lang[k]
    raise SystemExit('no name for ' + full)

ITEMS = ROOT + r'\items\src\main\resources'
os.makedirs(ITEMS + r'\assets\skyrpg_items\items', exist_ok=True)
os.makedirs(ITEMS + r'\data\skyrpg_items\skyrpg\recipes\enchanted', exist_ok=True)
os.makedirs(ITEMS + r'\data\skyrpg_items\skyrpg\sell_prices', exist_ok=True)
lang_path = ITEMS + r'\assets\skyrpg_items\lang\en_us.json'
lang = json.load(open(lang_path, encoding='utf-8'))
sell = {}
rows = []
wiki = {}
for group, ids in GROUPS.items():
    for raw in ids:
        full = raw if ':' in raw else 'minecraft:' + raw
        path = full.split(':')[1]
        eid = 'enchanted_' + path
        name = name_of(full)
        rows.append((full, eid, name, group))
        wiki.setdefault(group, []).append((name, prices.get(full, 0) * COUNT))
        json.dump(model_of(full), open(rf'{ITEMS}\assets\skyrpg_items\items\{eid}.json', 'w', encoding='utf-8'), indent=2)
        lang[f'item.skyrpg_items.{eid}'] = 'Enchanted ' + name
        json.dump({'category': 'enchanted', 'result': 'skyrpg_items:' + eid, 'ingredients': {full: COUNT}, 'upgrade': False},
                  open(rf'{ITEMS}\data\skyrpg_items\skyrpg\recipes\enchanted\{eid}.json', 'w', encoding='utf-8'), indent=2)
        if full in prices:
            sell['skyrpg_items:' + eid] = prices[full] * COUNT
json.dump(lang, open(lang_path, 'w', encoding='utf-8'), indent=2, ensure_ascii=False)
open(lang_path, 'a', encoding='utf-8').write('\n')
json.dump(sell, open(ITEMS + r'\data\skyrpg_items\skyrpg\sell_prices\enchanted.json', 'w', encoding='utf-8'), indent=2)
java = ',\n'.join(f'            {{"{full}", "{name}"}}' for full, eid, name, g in rows)
open(os.path.join(os.environ.get('TEMP', '.'), 'enchanted_table.java.txt'), 'w', encoding='utf-8').write(java + '\n')
json.dump(wiki, open(os.path.join(os.environ.get('TEMP', '.'), 'enchanted_wiki.json'), 'w', encoding='utf-8'), indent=1)
print(len(rows), 'enchanted materials;', len(sell), 'with sell prices')
