"""Draws the SkyRPG VFX particle textures and writes their particle definitions.

    python tools/art/generate_vfx.py [--preview FILE]

Every particle texture is white (or grey shading on white) with the shape in the alpha channel, so an effect's
colour tints it to anything. Writes:

    vfx/src/main/resources/assets/skyrpg_vfx/textures/particle/<kind>[_<frame>].png
    vfx/src/main/resources/assets/skyrpg_vfx/particles/<kind>.json     (the frame list the game loads)
    vfx/src/main/resources/assets/skyrpg_vfx/textures/gui/vfx.png      (the Settings category icon, in colour)

The kinds match com.skyrpg.vfx.particle.VfxParticleKind: spark, glow, ember, rune, shard, star, wisp, bubble, leaf,
snowflake, smoke_puff, ring. Kinds with several frames pick one at random (ember, rune, shard, star, leaf, snowflake)
or play them over the particle's life (wisp, smoke_puff). --preview writes one sheet of everything on a dark
background, tinted, for a quick look.
"""
import json
import math
import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "vfx/src/main/resources/assets/skyrpg_vfx"
TEX = ASSETS / "textures/particle"
PARTICLES = ASSETS / "particles"
GUI = ASSETS / "textures/gui"

SS = 4  # supersampling for stroked shapes


# ------------------------------------------------------------------------------------------------ helpers
def grid(size):
    """Pixel-centre coordinates in -1..1."""
    c = (np.arange(size) + 0.5) / size * 2 - 1
    return np.meshgrid(c, c)


def to_image(alpha, shade=None):
    """White (or shaded grey) RGBA from an alpha field in 0..1."""
    a = np.clip(alpha, 0, 1)
    rgb = np.ones(a.shape + (3,)) if shade is None else np.repeat(np.clip(shade, 0, 1)[..., None], 3, axis=2)
    out = np.dstack([rgb * 255, a * 255]).round().astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def value_noise(size, cells, seed):
    rng = np.random.default_rng(seed)
    lattice = rng.random((cells + 1, cells + 1))
    xs = np.linspace(0, cells, size, endpoint=False)
    xi = xs.astype(int)
    xf = xs - xi
    xf = xf * xf * (3 - 2 * xf)
    a = lattice[np.ix_(xi, xi)]
    b = lattice[np.ix_(xi, xi + 1)]
    c = lattice[np.ix_(xi + 1, xi)]
    d = lattice[np.ix_(xi + 1, xi + 1)]
    top = a + (b - a) * xf[None, :]
    bottom = c + (d - c) * xf[None, :]
    return top + (bottom - top) * xf[:, None]


def stroke_image(size, draw_fn, width, glow=0.0, glow_radius=1.2):
    """Draws white strokes at SS x size, downsamples, and optionally adds a soft halo under them."""
    big = Image.new("L", (size * SS, size * SS), 0)
    draw_fn(ImageDraw.Draw(big), size * SS, width * SS)
    core = big.resize((size, size), Image.LANCZOS)
    a = np.asarray(core, dtype=float) / 255
    if glow > 0:
        halo = np.asarray(core.filter(ImageFilter.GaussianBlur(glow_radius)), dtype=float) / 255
        a = np.maximum(a, np.clip(halo * glow * 2.2, 0, glow))
    return to_image(a)


def save(img, name):
    TEX.mkdir(parents=True, exist_ok=True)
    img.save(TEX / f"{name}.png")


# ------------------------------------------------------------------------------------------------ kinds
def spark():
    size = 16
    x, y = grid(size)
    along = np.cos(np.clip(np.abs(x), 0, 1) * math.pi / 2) ** 1.6  # taper to both ends
    across = np.exp(-(y / 0.14) ** 2)
    core = np.exp(-(y / 0.06) ** 2) * np.exp(-(x / 0.45) ** 2)
    a = np.clip(along * across * 0.95 + core * 0.4, 0, 1)
    save(to_image(a), "spark")
    return ["spark"]


def glow():
    size = 32
    x, y = grid(size)
    r = np.sqrt(x * x + y * y)
    a = np.exp(-(r / 0.42) ** 2) * 0.85 + np.exp(-(r / 0.16) ** 2) * 0.35
    a *= 1 - smoothstep(0.85, 1.0, r)
    save(to_image(a), "glow")
    return ["glow"]


def ember():
    names = []
    size = 16
    x, y = grid(size)
    for i, (sx, sy, rot) in enumerate([(0.42, 0.46, 0.0), (0.36, 0.5, 0.5), (0.46, 0.38, 1.1)]):
        c, s = math.cos(rot), math.sin(rot)
        u, v = x * c - y * s, x * s + y * c
        d = np.sqrt((u / sx) ** 2 + (v / sy) ** 2)
        a = np.exp(-d ** 2 * 1.4) + np.exp(-(d / 0.45) ** 2) * 0.5
        r = np.sqrt(x * x + y * y)
        a *= 1 - smoothstep(0.8, 1.0, r)
        save(to_image(np.clip(a, 0, 1)), f"ember_{i}")
        names.append(f"ember_{i}")
    return names


# Runes: strokes on a 0..1 box (x right, y down), loosely after the elder futhark.
RUNES = [
    [((0.35, 0.1), (0.35, 0.9)), ((0.35, 0.3), (0.72, 0.12)), ((0.35, 0.52), (0.72, 0.34))],          # fehu
    [((0.3, 0.9), (0.3, 0.1)), ((0.3, 0.1), (0.7, 0.35)), ((0.7, 0.35), (0.7, 0.9))],                 # uruz
    [((0.32, 0.1), (0.32, 0.9)), ((0.32, 0.28), (0.7, 0.5)), ((0.7, 0.5), (0.32, 0.72))],             # thurisaz
    [((0.5, 0.1), (0.5, 0.9)), ((0.5, 0.1), (0.25, 0.4)), ((0.5, 0.1), (0.75, 0.4)),
     ((0.25, 0.4), (0.5, 0.62)), ((0.75, 0.4), (0.5, 0.62))],                                          # ingwaz-ish
    [((0.2, 0.1), (0.8, 0.9)), ((0.8, 0.1), (0.2, 0.9)), ((0.5, 0.1), (0.5, 0.9))],                   # star rune
    [((0.68, 0.12), (0.3, 0.5)), ((0.3, 0.5), (0.68, 0.88))],                                          # kaunan
    [((0.32, 0.1), (0.32, 0.9)), ((0.68, 0.1), (0.68, 0.9)), ((0.32, 0.3), (0.68, 0.62))],            # hagalaz
    [((0.5, 0.1), (0.5, 0.9)), ((0.5, 0.35), (0.22, 0.12)), ((0.5, 0.35), (0.78, 0.12)),
     ((0.5, 0.65), (0.22, 0.88)), ((0.5, 0.65), (0.78, 0.88))],                                        # algiz + reversed
]


def rune():
    names = []
    for i, strokes in enumerate(RUNES):
        def draw(d, n, w, strokes=strokes):
            pad = n * 0.08
            span = n - 2 * pad
            for (x0, y0), (x1, y1) in strokes:
                d.line([(pad + x0 * span, pad + y0 * span), (pad + x1 * span, pad + y1 * span)], fill=255, width=int(w))
                for px, py in ((x0, y0), (x1, y1)):  # round the joints
                    cx, cy = pad + px * span, pad + py * span
                    d.ellipse([cx - w / 2, cy - w / 2, cx + w / 2, cy + w / 2], fill=255)
        save(stroke_image(16, draw, 1.6, glow=0.45, glow_radius=1.1), f"rune_{i}")
        names.append(f"rune_{i}")
    return names


def shard():
    names = []
    size = 16
    shapes = [
        [(0.5, 0.04), (0.72, 0.42), (0.56, 0.96), (0.34, 0.5)],
        [(0.42, 0.06), (0.66, 0.24), (0.62, 0.9), (0.38, 0.72), (0.3, 0.3)],
        [(0.56, 0.02), (0.7, 0.55), (0.48, 0.98), (0.3, 0.6), (0.38, 0.22)],
    ]
    for i, pts in enumerate(shapes):
        n = size * SS
        alpha = Image.new("L", (n, n), 0)
        shade = Image.new("L", (n, n), 0)
        da, ds = ImageDraw.Draw(alpha), ImageDraw.Draw(shade)
        poly = [(x * n, y * n) for x, y in pts]
        da.polygon(poly, fill=255)
        # Two facets: a lit half and a shadowed half, split along the long axis.
        top, bottom = poly[0], poly[len(poly) // 2]
        left = [top] + [p for p in poly if p[0] < (top[0] + bottom[0]) / 2] + [bottom]
        ds.polygon(poly, fill=200)
        ds.polygon(left, fill=255)
        ds.line([top, bottom], fill=255, width=SS)
        a = np.asarray(alpha.resize((size, size), Image.LANCZOS), dtype=float) / 255
        s = np.asarray(shade.resize((size, size), Image.LANCZOS), dtype=float) / 255
        s = np.where(a > 0.02, s / np.maximum(a, 0.02), 1)
        save(to_image(a * 0.95, np.clip(s, 0.6, 1)), f"shard_{i}")
        names.append(f"shard_{i}")
    return names


def star():
    names = []
    size = 16
    x, y = grid(size)
    for i, (reach, diag) in enumerate([(1.0, 0.0), (0.85, 0.45), (0.7, 0.3)]):
        ax, ay = np.abs(x), np.abs(y)
        rays = np.exp(-(ay / 0.07) ** 2) * (1 - smoothstep(0.2, reach, ax)) + np.exp(-(ax / 0.07) ** 2) * (1 - smoothstep(0.2, reach, ay))
        if diag:
            u, v = (x + y) / math.sqrt(2), (x - y) / math.sqrt(2)
            au, av = np.abs(u), np.abs(v)
            rays += diag * (np.exp(-(av / 0.06) ** 2) * (1 - smoothstep(0.1, reach * 0.55, au))
                            + np.exp(-(au / 0.06) ** 2) * (1 - smoothstep(0.1, reach * 0.55, av)))
        r = np.sqrt(x * x + y * y)
        a = np.clip(rays + np.exp(-(r / 0.2) ** 2), 0, 1)
        save(to_image(a), f"star_{i}")
        names.append(f"star_{i}")
    return names


def wisp():
    names = []
    size = 16
    x, y = grid(size)
    frames = 4
    for i in range(frames):
        t = i / (frames - 1)
        # A teardrop: round at the bottom, a tail flicking up; thinner and fainter with age.
        cy = 0.3 - 0.12 * t
        width = 0.6 - 0.16 * t
        sway = 0.12 * math.sin(i * 1.7)
        yy = y - cy
        tail = np.clip(-yy, 0, None)
        half = width * np.sqrt(np.clip(1 - (yy / 0.62) ** 2, 0, 1)) * np.where(yy < 0, np.exp(-tail * (0.9 + t)), 1)
        dx = x - sway * tail * 1.8
        a = smoothstep(0, 1, 1 - np.abs(dx) / np.maximum(half, 1e-3)) * (yy < 0.62)
        core = np.exp(-((x / 0.26) ** 2 + ((y - cy - 0.15) / 0.3) ** 2))
        a = np.clip(a * (0.75 - 0.25 * t) + core * (0.5 - 0.3 * t), 0, 1)
        save(to_image(a), f"wisp_{i}")
        names.append(f"wisp_{i}")
    return names


def bubble():
    size = 16
    x, y = grid(size)
    r = np.sqrt(x * x + y * y)
    rim = np.exp(-((r - 0.78) / 0.09) ** 2)
    fill = 0.12 * (r < 0.8)
    hx, hy = x + 0.32, y + 0.32
    highlight = np.exp(-((hx / 0.16) ** 2 + (hy / 0.12) ** 2))
    a = np.clip(rim * 0.9 + fill + highlight * 0.9, 0, 1) * (1 - smoothstep(0.92, 1.0, r))
    save(to_image(a), "bubble")
    return ["bubble"]


def leaf():
    names = []
    size = 16
    x, y = grid(size)
    for i, (bend, length, wide) in enumerate([(0.0, 0.85, 0.38), (0.25, 0.8, 0.32), (-0.2, 0.75, 0.42), (0.15, 0.9, 0.28)]):
        rot = 0.6
        u = x * math.cos(rot) - y * math.sin(rot)
        v = x * math.sin(rot) + y * math.cos(rot)
        v = v - bend * (u ** 2 - 0.3)
        t = np.clip(u / length, -1, 1)
        half = wide * np.sqrt(np.clip(1 - t ** 2, 0, 1)) * (1 - 0.3 * t)
        inside = smoothstep(0, 0.08, half - np.abs(v)) * (np.abs(u) < length)
        stem = np.exp(-(v / 0.04) ** 2) * ((u < -length * 0.95) & (u > -length * 1.15))
        midrib = np.exp(-(v / 0.035) ** 2) * (np.abs(u) < length * 0.9)
        shade = 0.85 + 0.15 * smoothstep(-0.2, 0.3, v) - 0.25 * midrib
        a = np.clip(inside + stem, 0, 1)
        save(to_image(a, np.clip(shade, 0.55, 1)), f"leaf_{i}")
        names.append(f"leaf_{i}")
    return names


def snowflake():
    names = []
    for i, (branches, tip) in enumerate([(1, 0.92), (2, 0.85), (0, 0.95)]):
        def draw(d, n, w, branches=branches, tip=tip):
            c = n / 2
            r = n * 0.46 * tip
            for k in range(6):
                ang = k * math.pi / 3 + math.pi / 6
                ex, ey = c + math.cos(ang) * r, c + math.sin(ang) * r
                d.line([(c, c), (ex, ey)], fill=255, width=int(w))
                for b in range(branches):
                    f = 0.45 + 0.25 * b
                    bx, by = c + math.cos(ang) * r * f, c + math.sin(ang) * r * f
                    for side in (-1, 1):
                        ba = ang + side * math.pi / 3
                        blen = r * (0.32 - 0.08 * b)
                        d.line([(bx, by), (bx + math.cos(ba) * blen, by + math.sin(ba) * blen)], fill=255, width=int(w * 0.8))
            d.ellipse([c - w, c - w, c + w, c + w], fill=255)
        save(stroke_image(16, draw, 1.3, glow=0.35, glow_radius=0.9), f"snowflake_{i}")
        names.append(f"snowflake_{i}")
    return names


def smoke_puff():
    names = []
    size = 32
    x, y = grid(size)
    rng = random.Random(7)
    blobs = [(rng.uniform(-0.28, 0.28), rng.uniform(-0.22, 0.25), rng.uniform(0.38, 0.52)) for _ in range(7)]
    frames = 6
    for i in range(frames):
        t = i / (frames - 1)
        a = np.zeros_like(x)
        grow = 0.85 + 0.3 * t
        for bx, by, br in blobs:
            a = np.maximum(a, np.exp(-(((x - bx * grow) ** 2 + (y - by * grow) ** 2) / (br * grow) ** 2) * 1.15))
        noise = value_noise(size, 5, 100 + i) * 0.6 + value_noise(size, 11, 200 + i) * 0.4
        a = a * (0.65 + 0.55 * noise)
        a = smoothstep(0.08 + 0.22 * t, 0.85, a) * (0.95 - 0.45 * t)
        r = np.sqrt(x * x + y * y)
        a *= 1 - smoothstep(0.85, 1.0, r)
        shade = 0.82 + 0.18 * (1 - smoothstep(-0.6, 0.6, y))  # lighter on top
        save(to_image(a, shade), f"smoke_puff_{i}")
        names.append(f"smoke_puff_{i}")
    return names


def ring():
    size = 32
    x, y = grid(size)
    r = np.sqrt(x * x + y * y)
    band = np.exp(-((r - 0.84) / 0.055) ** 2)
    inner = 0.18 * smoothstep(0.3, 0.84, r) * (r < 0.84)
    a = np.clip(band + inner, 0, 1) * (1 - smoothstep(0.95, 1.0, r))
    save(to_image(a), "ring")
    return ["ring"]


KINDS = {
    "spark": spark, "glow": glow, "ember": ember, "rune": rune, "shard": shard, "star": star, "wisp": wisp,
    "bubble": bubble, "leaf": leaf, "snowflake": snowflake, "smoke_puff": smoke_puff, "ring": ring,
}


def gui_icon():
    """The Settings category icon: a gold four-point glint with a cyan and a violet spark."""
    n = 16 * SS
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def glint(cx, cy, r, color, width):
        d.polygon([(cx, cy - r), (cx + width, cy), (cx, cy + r), (cx - width, cy)], fill=color)
        d.polygon([(cx - r, cy), (cx, cy - width), (cx + r, cy), (cx, cy + width)], fill=color)

    glint(n * 0.42, n * 0.56, n * 0.36, (255, 214, 92, 255), n * 0.09)
    glint(n * 0.42, n * 0.56, n * 0.16, (255, 248, 214, 255), n * 0.05)
    glint(n * 0.8, n * 0.2, n * 0.15, (110, 232, 255, 255), n * 0.04)
    glint(n * 0.8, n * 0.82, n * 0.1, (196, 140, 255, 255), n * 0.03)
    GUI.mkdir(parents=True, exist_ok=True)
    img.resize((16, 16), Image.LANCZOS).save(GUI / "vfx.png")


def preview(path, frames):
    tints = [(255, 170, 70), (120, 230, 255), (200, 140, 255), (255, 240, 200), (140, 255, 140)]
    cell = 40
    names = [n for kind in frames.values() for n in kind]
    cols = 12
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * cell), (18, 20, 28, 255))
    for i, name in enumerate(names):
        tex = Image.open(TEX / f"{name}.png").convert("RGBA").resize((32, 32), Image.NEAREST)
        tint = tints[i % len(tints)]
        arr = np.asarray(tex, dtype=float)
        arr[..., :3] *= np.array(tint) / 255
        tex = Image.fromarray(arr.astype(np.uint8), "RGBA")
        sheet.alpha_composite(tex, ((i % cols) * cell + 4, (i // cols) * cell + 4))
    sheet = sheet.resize((sheet.width * 2, sheet.height * 2), Image.NEAREST)
    sheet.save(path)
    print(f"preview: {path}")


def main():
    frames = {}
    for kind, fn in KINDS.items():
        frames[kind] = fn()
        PARTICLES.mkdir(parents=True, exist_ok=True)
        (PARTICLES / f"{kind}.json").write_text(
            json.dumps({"textures": [f"skyrpg_vfx:{name}" for name in frames[kind]]}, indent=2) + "\n", encoding="utf-8")
    gui_icon()
    total = sum(len(v) for v in frames.values())
    print(f"wrote {total} textures for {len(frames)} particle kinds")
    if "--preview" in sys.argv:
        preview(Path(sys.argv[sys.argv.index("--preview") + 1]), frames)


if __name__ == "__main__":
    main()
