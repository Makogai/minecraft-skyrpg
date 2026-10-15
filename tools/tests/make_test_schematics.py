#!/usr/bin/env python3
"""Writes the same little test hut in every format the Builder reads, to check its schematic readers.

    python tools/tests/make_test_schematics.py run/client-cam/schematics

Writes test_hut.litematic (Litematica, with a sub-region of negative size and a palette whose entries span longs),
test_hut_v2.schem and test_hut_v3.schem (Sponge 2 and 3) and test_hut.nbt (vanilla structure). Each holds a 7x5x6
hut: cobblestone floor, oak plank walls with glass windows, an oak door, a wall torch, a signed oak sign, a bed and
a slab roof. No dependencies: it has its own small NBT writer.
"""
import gzip
import io
import struct
import sys
from pathlib import Path

# ---------------------------------------------------------------- tiny NBT writer
END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class Tag:
    def __init__(self, kind, value, item_kind=None):
        self.kind = kind
        self.value = value
        self.item_kind = item_kind


def i(v): return Tag(INT, v)
def s(v): return Tag(STRING, v)
def sh(v): return Tag(SHORT, v)
def c(d): return Tag(COMPOUND, d)
def lst(kind, items): return Tag(LIST, items, kind)
def ia(v): return Tag(INT_ARRAY, v)
def la(v): return Tag(LONG_ARRAY, v)
def ba(v): return Tag(BYTE_ARRAY, v)


def write_payload(out, tag):
    k, v = tag.kind, tag.value
    if k == BYTE:
        out.write(struct.pack(">b", v))
    elif k == SHORT:
        out.write(struct.pack(">h", v))
    elif k == INT:
        out.write(struct.pack(">i", v))
    elif k == LONG:
        out.write(struct.pack(">q", v))
    elif k == STRING:
        data = v.encode("utf-8")
        out.write(struct.pack(">H", len(data)))
        out.write(data)
    elif k == BYTE_ARRAY:
        out.write(struct.pack(">i", len(v)))
        out.write(bytes(v))
    elif k == INT_ARRAY:
        out.write(struct.pack(">i", len(v)))
        for x in v:
            out.write(struct.pack(">i", x))
    elif k == LONG_ARRAY:
        out.write(struct.pack(">i", len(v)))
        for x in v:
            out.write(struct.pack(">q", x))
    elif k == LIST:
        out.write(struct.pack(">bi", tag.item_kind if v else END, len(v)))
        for item in v:
            write_payload(out, item)
    elif k == COMPOUND:
        for name, item in v.items():
            out.write(struct.pack(">b", item.kind))
            write_payload(out, s(name))
            write_payload(out, item)
        out.write(b"\x00")


def save(path, root, name=""):
    out = io.BytesIO()
    out.write(struct.pack(">b", COMPOUND))
    write_payload(out, s(name))
    write_payload(out, root)
    path.write_bytes(gzip.compress(out.getvalue()))


def signed(v):
    return v - (1 << 64) if v >= 1 << 63 else v


# ---------------------------------------------------------------- the hut
W, H, D = 7, 5, 6
# 0: "current", so no data fixers run (the sign text below is already in the current format).
DATA_VERSION = 0


def hut():
    """{(x, y, z): (name, properties)} for every non-air block, plus block entities {(x, y, z): extra nbt}."""
    blocks = {}
    for x in range(W):
        for z in range(D):
            blocks[(x, 0, z)] = ("minecraft:cobblestone", {})
    for y in (1, 2, 3):
        for x in range(W):
            for z in range(D):
                if x in (0, W - 1) or z in (0, D - 1):
                    window = y == 2 and ((x == 0 and z == 2) or (x == W - 1 and z == 3) or (z == D - 1 and x == 3))
                    blocks[(x, y, z)] = ("minecraft:glass", {}) if window else ("minecraft:oak_planks", {})
    # Door in the north wall (z = 0).
    blocks[(3, 1, 0)] = ("minecraft:oak_door", {"facing": "south", "half": "lower", "hinge": "left", "open": "false", "powered": "false"})
    blocks[(3, 2, 0)] = ("minecraft:oak_door", {"facing": "south", "half": "upper", "hinge": "left", "open": "false", "powered": "false"})
    # Inside: a wall torch, a sign and a bed.
    blocks[(1, 2, 1)] = ("minecraft:wall_torch", {"facing": "south"})
    blocks[(5, 2, 1)] = ("minecraft:oak_wall_sign", {"facing": "south", "waterlogged": "false"})
    blocks[(1, 1, 3)] = ("minecraft:red_bed", {"facing": "south", "part": "foot", "occupied": "false"})
    blocks[(1, 1, 4)] = ("minecraft:red_bed", {"facing": "south", "part": "head", "occupied": "false"})
    # Slab roof.
    for x in range(W):
        for z in range(D):
            blocks[(x, 4, z)] = ("minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"})
    entities = {(5, 2, 1): {"front_text": c({"messages": lst(STRING, [s("Built by"), s("the"), s("Builder"), s("Station!")]),
                                              "color": s("black"), "has_glowing_text": Tag(BYTE, 0)}),
                            "id": s("minecraft:sign")}}
    return blocks, entities


def state_tag(name, props):
    tag = {"Name": s(name)}
    if props:
        tag["Properties"] = c({k: s(v) for k, v in sorted(props.items())})
    return c(tag)


def state_string(name, props):
    if not props:
        return name
    return name + "[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]"


def litematic(path):
    blocks, entities = hut()
    palette = [("minecraft:air", {})]
    keys = {}
    for value in blocks.values():
        key = state_string(*value)
        if key not in keys:
            keys[key] = len(palette)
            palette.append(value)
    bits = max(2, (len(palette) - 1).bit_length())
    volume = W * H * D
    longs = [0] * ((volume * bits + 63) // 64)
    for index in range(volume):
        x = index % W
        z = (index // W) % D
        y = index // (W * D)
        value = keys[state_string(*blocks[(x, y, z)])] if (x, y, z) in blocks else 0
        start = index * bits
        lo = start >> 6
        hi = (start + bits - 1) >> 6
        bit = start & 63
        longs[lo] |= (value << bit) & ((1 << 64) - 1)
        if hi != lo:
            longs[hi] |= value >> (64 - bit)
    tile_entities = [c({"x": i(x), "y": i(y), "z": i(z), **extra}) for (x, y, z), extra in entities.items()]
    # Negative size: the region grows from its position towards -x, -z (Litematica allows either way).
    region = c({
        "Position": c({"x": i(W - 1), "y": i(0), "z": i(D - 1)}),
        "Size": c({"x": i(-W), "y": i(H), "z": i(-D)}),
        "BlockStatePalette": lst(COMPOUND, [state_tag(*p) for p in palette]),
        "BlockStates": la([signed(v) for v in longs]),
        "TileEntities": lst(COMPOUND, tile_entities),
        "Entities": lst(COMPOUND, []),
        "PendingBlockTicks": lst(COMPOUND, []),
        "PendingFluidTicks": lst(COMPOUND, []),
    })
    root = c({
        "MinecraftDataVersion": i(DATA_VERSION),
        "Version": i(7),
        "SubVersion": i(1),
        "Metadata": c({"Name": s("Test Hut (litematic)"), "Author": s("SkyRPG"), "EnclosingSize": c({"x": i(W), "y": i(H), "z": i(D)}),
                       "RegionCount": i(1), "TotalBlocks": i(len(blocks)), "TotalVolume": i(volume)}),
        "Regions": c({"Hut": region}),
    })
    save(path, root)


def varints(values):
    out = bytearray()
    for v in values:
        while True:
            b = v & 0x7F
            v >>= 7
            if v:
                out.append(b | 0x80)
            else:
                out.append(b)
                break
    return [x - 256 if x > 127 else x for x in out]


def sponge(path, version):
    blocks, entities = hut()
    palette = {"minecraft:air": 0}
    data = []
    for y in range(H):
        for z in range(D):
            for x in range(W):
                key = state_string(*blocks[(x, y, z)]) if (x, y, z) in blocks else "minecraft:air"
                palette.setdefault(key, len(palette))
                data.append(palette[key])
    block_entities = []
    for (x, y, z), extra in entities.items():
        fields = {k: v for k, v in extra.items() if k != "id"}
        if version >= 3:
            block_entities.append(c({"Pos": ia([x, y, z]), "Id": s("minecraft:sign"), "Data": c(fields)}))
        else:
            block_entities.append(c({"Pos": ia([x, y, z]), "Id": s("minecraft:sign"), **fields}))
    common = {"Version": i(version), "DataVersion": i(DATA_VERSION), "Width": sh(W), "Height": sh(H), "Length": sh(D),
              "Metadata": c({"Name": s(f"Test Hut (schem v{version})")})}
    if version >= 3:
        common["Blocks"] = c({"Palette": c({k: i(v) for k, v in palette.items()}), "Data": ba(varints(data)),
                              "BlockEntities": lst(COMPOUND, block_entities)})
        save(path, c({"Schematic": c(common)}))
    else:
        common["Palette"] = c({k: i(v) for k, v in palette.items()})
        common["PaletteMax"] = i(len(palette))
        common["BlockData"] = ba(varints(data))
        common["BlockEntities"] = lst(COMPOUND, block_entities)
        save(path, c(common), "Schematic")


def structure(path):
    blocks, entities = hut()
    palette = []
    keys = {}
    entries = []
    for (x, y, z), value in sorted(blocks.items()):
        key = state_string(*value)
        if key not in keys:
            keys[key] = len(palette)
            palette.append(value)
        entry = {"pos": lst(INT, [i(x), i(y), i(z)]), "state": i(keys[key])}
        if (x, y, z) in entities:
            entry["nbt"] = c(dict(entities[(x, y, z)]))
        entries.append(c(entry))
    root = c({"DataVersion": i(DATA_VERSION), "size": lst(INT, [i(W), i(H), i(D)]),
              "palette": lst(COMPOUND, [state_tag(*p) for p in palette]), "blocks": lst(COMPOUND, entries),
              "entities": lst(COMPOUND, [])})
    save(path, root)


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    out.mkdir(parents=True, exist_ok=True)
    litematic(out / "test_hut.litematic")
    sponge(out / "test_hut_v2.schem", 2)
    sponge(out / "test_hut_v3.schem", 3)
    structure(out / "test_hut.nbt")
    print("Wrote the test hut to", out)


if __name__ == "__main__":
    main()
