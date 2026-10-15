#!/usr/bin/env python3
"""Item art for SkyRPG Crates: the Hallow's Crate, its key, the candy and the Pumpkin Pouch (16x16, one mask each).

    python tools/art/generate_crates.py [--preview FILE]

Writes crates/src/main/resources/assets/skyrpg_crates/{textures/item,models/item,items}/<name>.png/.json.
Each mask row is 16 characters; '.' is transparent and every other character is a palette colour.
"""
import json
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "crates/src/main/resources/assets/skyrpg_crates"

PALETTE = {
    # dark purple-black wood and its grain
    "w": "2A1E36", "W": "3D2C4F", "v": "1A1224",
    # orange iron bands / rivets
    "o": "C55A12", "O": "F08A2C", "r": "7A3308",
    # pumpkin and its glowing face
    "p": "E8761B", "P": "FFA54A", "q": "9C4A0E", "y": "FFE27A", "Y": "FFF6C8", "s": "3E6B22",
    # bone key
    "b": "E8E0C8", "B": "FFFFFF", "d": "A89F86", "D": "6E6656",
    # candy corn
    "t": "FFFDF2", "c": "FF8A1E", "C": "FFB24A", "k": "FFD43B", "K": "E0A800",
    # toffee apple
    "a": "C2262E", "A": "E8444A", "m": "7A1218", "f": "C8862E", "F": "F0B65A", "g": "8E5A1A", "h": "D9C49A",
    # pouch string and coin
    "n": "5A3A1A", "N": "8A6234", "$": "FFD24A",
}

MASKS = {
    "hallows_crate": [
        "................",
        ".rrrrrrrrrrrrrr.",
        ".rOoWWWWWWWWoOr.",
        ".roWwwwwwwwwWor.",
        ".rWwvwwwwwwvwWr.",
        ".rWwwwqppqwwwWr.",
        ".rWwwqpPPpqwwWr.",
        ".roooyyPPyyooor.",
        ".rWwwpyyyypwwWr.",
        ".rWwwqpppppqwWr.",
        ".rWwvwqqqqwvwWr.",
        ".rWwwwwwwwwwwWr.",
        ".roWwwwwwwwwWor.",
        ".rOoWWWWWWWWoOr.",
        ".rrrrrrrrrrrrrr.",
        "................",
    ],
    "hallows_key": [
        "................",
        "..........sss...",
        ".........qppq...",
        "........qpPPpq..",
        "........pyPyPp..",
        "........pPPPPp..",
        "........qyyyyq..",
        ".........dqqd...",
        "........bd......",
        ".......bd.......",
        "......bd........",
        ".....bd.........",
        "....bdbb........",
        "...bd.Bd........",
        "..bdbbd.........",
        "...Dd...........",
    ],
    "candy_corn": [
        "................",
        "................",
        ".......tt.......",
        "......tttt......",
        "......tBtt......",
        ".....CcccCc.....",
        ".....ccCccc.....",
        "....cccccccC....",
        "....cCccccccc...",
        "...kkkkkkkkkk...",
        "...kKkkkkkkkkk..",
        "..kkkkkkkkkkkK..",
        "..KkkkkkkkkkkKK.",
        "...KKKKKKKKKKK..",
        "................",
        "................",
    ],
    "toffee_apple": [
        "................",
        ".......hh.......",
        ".......hh.......",
        ".......hh.......",
        ".......hh.......",
        "....fFFhhFFf....",
        "...fFFFffFFFf...",
        "..fFFaAAAAaFFf..",
        "..fFaAAAAAAaFf..",
        "..faAABAAAAAaf..",
        "..faAAAAAAAAaf..",
        "..faaAAAAAAaaf..",
        "...fmaaaaaamf...",
        "...gfmmmmmmfg...",
        "....ggffffgg....",
        "................",
    ],
    "pumpkin_pouch": [
        "................",
        "......nNNn......",
        ".......nn.......",
        "......NnnN......",
        ".....qpppPq.....",
        "....qpPPPPpq....",
        "...qpPyPPyPpq...",
        "...pPPPPPPPPp...",
        "..qpPyyyyyyPpq..",
        "..pPPPyPyPPPPp..",
        "..pPPPPPPPPPPp..",
        "..qpPPPPPPPP$q..",
        "...qppppppp$$...",
        "....qqqqqqq$....",
        "................",
        "................",
    ],
}


def hexrgb(text):
    return tuple(int(text[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def render(mask):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(mask):
        assert len(row) == 16, (row, len(row))
        for x, ch in enumerate(row):
            if ch != ".":
                image.putpixel((x, y), hexrgb(PALETTE[ch]))
    return image


def main():
    (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
    (ASSETS / "models/item").mkdir(parents=True, exist_ok=True)
    (ASSETS / "items").mkdir(parents=True, exist_ok=True)
    images = []
    for name, mask in MASKS.items():
        image = render(mask)
        image.save(ASSETS / f"textures/item/{name}.png")
        images.append(image)
        (ASSETS / f"models/item/{name}.json").write_text(json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"skyrpg_crates:item/{name}"}}, indent=2) + "\n", encoding="utf-8")
        (ASSETS / f"items/{name}.json").write_text(json.dumps(
            {"model": {"type": "minecraft:model", "model": f"skyrpg_crates:item/{name}"}}, indent=2) + "\n", encoding="utf-8")
    if "--preview" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--preview") + 1])
        sheet = Image.new("RGBA", (len(images) * 136 + 8, 144), (30, 34, 44, 255))
        for i, image in enumerate(images):
            sheet.alpha_composite(image.resize((128, 128), Image.NEAREST), (8 + i * 136, 8))
        sheet.save(out)
        print("preview", out)
    print("wrote", len(MASKS), "items")


if __name__ == "__main__":
    main()
