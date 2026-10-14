#!/usr/bin/env python3
"""Draws SkyRPG's detailed weapon art: 32x32 swords, pickaxes, spears and bows (vanilla and SkyRPG) and a 128x128 shield.

    python tools/art/generate_weapon_art.py                  # writes the pack below
    python tools/art/generate_weapon_art.py --preview DIR    # also writes contact sheets (and animation strips) to DIR

Everything goes into core's built-in resource pack resourcepacks/skyrpg_weapon_art ("Detailed weapon art" in Settings,
on by default), so switching it off brings back the plain 16px look:
  * assets/minecraft/textures/item/<tier>_{sword,pickaxe,spear,spear_in_hand}.png, bow*.png
  * assets/minecraft/textures/entity/shield/shield_base{,_nopattern}.png (128x128: same layout, twice the detail)
  * assets/skyrpg_items/textures/item/<id>.png for SkyRPG's swords and pickaxes; the strongest are animated
    (<id>.png.mcmeta, frames stacked downwards) and the biggest blades use models/item/handheld_large.json

How it draws: every piece is built from shape settings, not hand-drawn masks. A weapon lies on the diagonal from the
bottom-left (handle) to the top-right (tip). Each pixel's centre is measured along that line (t) and across it (s;
positive is the upper-left, lit side), and the parts (blade, guard, grip, pommel, gems; pick heads; spear heads; bow
limbs) decide whether they cover it and how light it is. Materials are 6-shade ramps, darkest first. Afterwards every
part gets a dark outline and a darker seam where it meets a part below or right of it, the way hand pixel art does.
Animated pieces are drawn again for every frame with an effect (a sheen running up the blade, lightning, embers,
twinkling stars, pulsing runes and gems).
"""
import argparse
import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
PACK = ROOT / "core" / "src" / "main" / "resources" / "resourcepacks" / "skyrpg_weapon_art"
VANILLA = PACK / "assets" / "minecraft"
SKY = PACK / "assets" / "skyrpg_items"

N = 32
SQ2 = math.sqrt(2)
D = (1 / SQ2, -1 / SQ2)     # along the weapon: right and up
S = (-1 / SQ2, -1 / SQ2)    # across it: left and up, the lit side
FRAMES = 16


def hexc(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


class Ramp:
    """Six shades, darkest first."""

    def __init__(self, *colors):
        assert len(colors) == 6
        self.c = [hexc(c) for c in colors]

    def __getitem__(self, shade):
        return self.c[max(0, min(5, round(shade)))]


# ---------------------------------------------------------------- palettes
OAK = Ramp("2a1a0c", "4a2f17", "6b4524", "8d5e33", "b07c46", "cfa06a")
DARK_WOOD = Ramp("1e130a", "33200f", "4c3018", "674224", "835733", "a06f45")
PLANK = Ramp("3a2611", "5c3d1d", "7e5730", "a07548", "c09462", "dcb784")
STONE = Ramp("2a2a2e", "45464c", "62636a", "80818a", "a1a2aa", "c6c7cc")
COPPER = Ramp("3d1a0e", "6a2f18", "9a4a26", "c76b3a", "e59660", "f9c59a")
PATINA = Ramp("123a33", "1d5a4e", "2c7d6a", "48a58a", "73c9ab", "a8e6cf")
IRON = Ramp("2b2f37", "4a505c", "707886", "9aa3b1", "c6cdd7", "f1f4f8")
STEEL = Ramp("232733", "3d4354", "636b80", "8e97ab", "bcc4d4", "eef2fa")
GOLD = Ramp("4a2c04", "7d5108", "b47f12", "e0ad25", "f6d559", "fff3b0")
DIAMOND = Ramp("0b3a3e", "0f5d63", "1a8f93", "3fc4c2", "86ecdf", "dafff8")
NETHERITE = Ramp("17131a", "2a2430", "3e3646", "564c5f", "726779", "978b9c")
LEATHER = Ramp("2a160c", "472514", "653620", "86492b", "a76339", "c48250")
RED_LEATHER = Ramp("2c0a0d", "4d1016", "741a22", "9c2630", "c23b40", "e2676a")
CLOTH_BLUE = Ramp("0e1a3a", "182c5c", "254184", "3a5daf", "5a80d3", "86a8ee")
STRING = Ramp("5a5650", "7e7a72", "a29d93", "c4bfb4", "e0dccf", "f8f5ec")
FEATHER = Ramp("5c1414", "8a2020", "b83232", "dc5050", "f07c7c", "ffb0b0")
RUBY = Ramp("3a0610", "6a0c1c", "a8162c", "e02c44", "ff6a7a", "ffd0d6")
SAPPHIRE = Ramp("071a4a", "0c2c7c", "1846b4", "2f6ee6", "6aa2ff", "cfe0ff")
EMERALD = Ramp("053a1c", "0a5c2c", "128a42", "22bc5c", "62e690", "c8ffdc")
AMETHYST = Ramp("2a0c4a", "45187a", "6a2cb0", "9450e0", "be86ff", "eedcff")
TOPAZ = Ramp("4a2a00", "7c4a00", "b47400", "ec9e10", "ffc84a", "fff0b8")
# SkyRPG metals
SKYIRON = Ramp("1d2c3e", "2f4762", "47688c", "6a92b8", "98bfdf", "dcf0ff")
STORM = Ramp("1a2240", "2c3a6a", "455a9a", "6a86c8", "a0bcf0", "e8f2ff")
VOLT = Ramp("4a3a00", "8a6c00", "c8a000", "f2cc10", "ffe85a", "fffbd0")
RIFT = Ramp("1c0a36", "321460", "4e2290", "7438c4", "a46cf0", "e2ccff")
VOID = Ramp("05030c", "0d0820", "1a1036", "2a1a52", "3e2a74", "5c449c")
CINDER = Ramp("0f0a0a", "1e1414", "2e1e1c", "40292a", "563634", "6e4440")
EMBER = Ramp("4a0e00", "8a2200", "cc4400", "ff7a10", "ffb43c", "fff0a0")
STAR = Ramp("0a1238", "142266", "1e3a9a", "3462d0", "6a98f0", "d8e8ff")
BONE = Ramp("4a4234", "726650", "9c8f74", "c4b898", "e2d9bd", "faf5e6")
BLOOD = Ramp("2a0406", "500a0e", "801218", "b01e24", "d8443e", "ff8070")
GRAVE = Ramp("162214", "263a20", "3c5a30", "5a8044", "86ac62", "c4e098")
SOUL = Ramp("0a2a26", "104a42", "187066", "28a090", "5cd4c0", "c0fff4")
BROOD = Ramp("1a0608", "34080e", "560c18", "801424", "aa2a3a", "d8606a")
DUSK = Ramp("1c1430", "2e2250", "463478", "6250a0", "8a76c8", "c4b4f0")
LUMEN = Ramp("0e3a4a", "16627a", "2a92aa", "52c4d6", "9ae8f0", "f0ffff")
GRAVE_IRON = Ramp("0c0e12", "1a1e26", "2c323e", "444c5c", "666f82", "8e98ae")
CHITIN_GREEN = Ramp("04120a", "0a2a16", "14482a", "24703e", "3a9e58", "6ad084")
VENOM_GREEN = Ramp("1c4a0c", "3a7a1c", "62b02c", "8ee03c", "c4f878", "f0ffc8")
PRISMARINE = Ramp("0c2e3a", "14505e", "1f7e88", "36b4b4", "6adccb", "c8fff0")
CORAL = Ramp("4a1424", "7e2840", "b04460", "e0708a", "ff9eb0", "ffd8de")
BRONZE = Ramp("3a2208", "623a10", "8c5a1c", "b47c30", "d8a050", "f4cc88")
# Danger-region blades.
BLIGHT_STEEL = Ramp("12040e", "2a0a22", "4a1236", "74204e", "a03868", "d070a0")
ASH_BLACK = Ramp("050404", "0f0c0c", "1c1716", "2c2423", "42383a", "5c5050")
RIME = Ramp("4a6a88", "7a9cbc", "a8cae2", "d0e8f6", "ecf8ff", "ffffff")
BOG_STEEL = Ramp("0a1a08", "182e12", "2c4a1c", "46702a", "6c9c38", "9ccc54")


# ---------------------------------------------------------------- canvas
class Canvas:
    """Pixels with the part that owns them (for outlines and seams), a ramp and a shade."""

    def __init__(self, size=N):
        self.size = size
        self.px = {}

    def put(self, x, y, part, ramp, shade, z=1, color=None):
        if not (0 <= x < self.size and 0 <= y < self.size):
            return
        old = self.px.get((x, y))
        if old is None or z >= old[4]:
            self.px[(x, y)] = (part, ramp, shade, color, z)

    def has(self, x, y):
        return (x, y) in self.px

    def render(self, outline=True, seams=True):
        img = Image.new("RGBA", (self.size, self.size), (0, 0, 0, 0))
        for (x, y), (part, ramp, shade, color, z) in self.px.items():
            s = shade
            if seams:
                # A darker seam where this part sits on top of another one below or to the right of it.
                for nx, ny in ((x + 1, y), (x, y + 1)):
                    other = self.px.get((nx, ny))
                    if other is not None and other[0] != part and other[4] < z:
                        s -= 1
                        break
            rgb = color if color is not None else ramp[s]
            img.putpixel((x, y), rgb + (255,))
        if outline:
            out = img.copy()
            for y in range(self.size):
                for x in range(self.size):
                    if (x, y) in self.px:
                        continue
                    darkest = None
                    for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                        cell = self.px.get((nx, ny))
                        if cell is not None:
                            c = cell[1][0] if cell[3] is None else tuple(v // 3 for v in cell[3])
                            if darkest is None or sum(c) < sum(darkest):
                                darkest = c
                    if darkest is not None:
                        out.putpixel((x, y), tuple(max(0, int(v * 0.55)) for v in darkest) + (255,))
            img = out
        return img


def local(x, y, origin):
    px, py = x + 0.5 - origin[0], y + 0.5 - origin[1]
    return px * D[0] + py * D[1], px * S[0] + py * S[1]


def pixels(size=N):
    for y in range(size):
        for x in range(size):
            yield x, y


def gem(cv, origin, t0, s0, ramp, r=1.2, z=6, glow=0.0):
    """A cut gem centred at (t0, s0): bright top-left facet, dark bottom-right."""
    for x, y in pixels(cv.size):
        t, s = local(x, y, origin)
        dt, ds = t - t0, s - s0
        if dt * dt + ds * ds <= r * r:
            shade = 3 + (1 if ds > 0.2 else 0) - (1 if ds < -0.4 else 0) + glow
            cv.put(x, y, "gem", ramp, shade, z)
    # one sparkle pixel
    gx, gy = point(origin, t0 + 0.2, s0 + 0.5)
    if cv.has(gx, gy) and cv.px[(gx, gy)][0] == "gem":
        cv.put(gx, gy, "gem", ramp, 5, z)


def point(origin, t, s):
    x = origin[0] + t * D[0] + s * S[0]
    y = origin[1] + t * D[1] + s * S[1]
    return int(math.floor(x)), int(math.floor(y))


# ---------------------------------------------------------------- swords
def sword(spec, phase=None):
    cv = Canvas()
    origin = spec.get("origin", (1.7, 30.3))
    rng = random.Random(spec.get("seed", 1))
    pommel = spec.get("pommel", {})
    grip = spec["grip"]
    guard = spec["guard"]
    blade = spec["blade"]
    pr = pommel.get("r", 1.6)
    g0 = 2 * pr - 0.4
    g1 = g0 + grip.get("len", 6.5)
    gt = guard.get("thick", 1.6)
    b0 = g1 + gt
    blen = blade["len"]
    w0 = blade["width"]
    tip = blade.get("tip", 5.0)
    curve = blade.get("curve", 0.0)
    style = blade.get("style", "straight")
    chips = [rng.uniform(-0.45, 0.35) for _ in range(64)]

    def half_width(u):
        """Blade half-width at u units past the guard (before style tweaks)."""
        w = w0 * (1 + blade.get("flare", 0.0) * (u / blen))
        if u > blen - tip:
            w *= max(0.0, (blen - u) / tip) ** blade.get("tip_power", 0.85)
        return w

    def centre(u):
        return -curve * (u / blen) ** 2

    # Blade.
    for x, y in pixels():
        t, s = local(x, y, origin)
        u = t - b0
        if u < -0.2 or u > blen:
            continue
        w = half_width(max(0.0, u))
        c = centre(max(0.0, u))
        rel = s - c
        lo, hi = -w, w
        if style == "serrated" and u < blen - tip:
            hi += 0.55 if int(u * 1.1) % 2 == 0 else -0.1
        if style == "flame":
            wave = 0.55 * math.sin(u * 1.25)
            lo -= wave
            hi += wave * 0.6
        if style == "chipped":
            k = min(len(chips) - 1, int(u))
            lo -= chips[k]
            hi += chips[(k + 7) % len(chips)]
        if style == "backspike" and blen * 0.35 < u < blen * 0.45:
            hi += 1.2
        ricasso = u < blade.get("ricasso", 1.6)
        if ricasso:
            lo, hi = lo * 0.8, hi * 0.8
        if not (lo <= rel <= hi):
            continue
        span = hi - lo
        f = (rel - lo) / span if span > 0 else 0.5          # 0 = dark edge, 1 = lit edge
        mid = (hi + lo) / 2
        edge_lit = f > 1 - 0.85 / max(span, 0.9)
        edge_dark = f < 0.8 / max(span, 0.9)
        # A bevelled blade: a bright ridge down the middle, the lit half light, the shaded half dark, and a sharpened edge
        # on each side (pale on the lit side, deep on the other).
        shade = 4 if rel > mid else 2
        if abs(rel - mid) < 0.5 and rel >= mid - 0.1:
            shade = 5
        if edge_lit:
            shade = 3
        elif edge_dark:
            shade = 1
        fuller = blade.get("fuller")
        if fuller and fuller[0] <= u <= fuller[1] and abs(rel - mid) < 0.62:
            shade = 2 if rel <= mid else 3
            if blade.get("fuller_ramp"):
                cv.put(x, y, "blade", blade["fuller_ramp"], blade.get("fuller_shade", 3) + (1 if rel > mid else 0), 2)
                continue
        if style == "crystal":
            band = (u * 0.8 + rel * 1.6) % 3
            shade = {0: 5, 1: 3}.get(int(band), 2) if 0.15 < f < 0.85 else shade
        if u > blen - 1.2:
            shade = max(shade, 4)
        if ricasso:
            shade = max(1, shade - 1)
        ramp = blade["ramp"]
        if blade.get("edge_ramp") and (edge_lit or edge_dark):
            ramp = blade["edge_ramp"]
            shade = 4 if edge_lit else 2
        cv.put(x, y, "blade", ramp, shade, 2)

    # Guard.
    gs = guard.get("style", "straight")
    gh = guard.get("half", 5.0)
    for x, y in pixels():
        t, s = local(x, y, origin)
        a = abs(s)
        if a > gh + 0.3:
            continue
        off = 0.0
        thick = gt
        if gs == "curved":
            off = guard.get("bend", 1.6) * (a / gh) ** 2
        elif gs == "winged":
            off = -guard.get("bend", 1.2) * (a / gh) ** 1.5
            thick = gt + 0.5 * (a / gh)
        elif gs == "fangs":
            off = guard.get("bend", 2.2) * max(0.0, (a - gh * 0.55) / (gh * 0.45)) ** 1.4
            thick = gt * (1 - 0.45 * max(0.0, (a - gh * 0.6) / (gh * 0.4)))
        elif gs == "disc":
            thick = gt * math.sqrt(max(0.0, 1 - (a / (gh + 0.3)) ** 2)) * 1.4
        if not (g1 + off <= t <= g1 + off + thick):
            continue
        shade = 4 if s > 0.3 else 2 if s < -0.3 else 3
        if t > g1 + off + thick - 0.6:
            shade += 1   # the face towards the blade catches the light
        cv.put(x, y, "guard", guard["ramp"], shade, 4)
    # Round knobs on the guard's ends.
    if guard.get("knobs", True) and gs != "disc":
        for side in (-1, 1):
            a = gh
            off = guard.get("bend", 1.6) * 1.0 if gs == "curved" else (-guard.get("bend", 1.2) if gs == "winged" else 0.0)
            if gs == "fangs":
                continue
            kt, ks = g1 + off + gt / 2, side * a
            for x, y in pixels():
                t, s = local(x, y, origin)
                if (t - kt) ** 2 + (s - ks) ** 2 <= 1.05 ** 2:
                    cv.put(x, y, "guard", guard["ramp"], 5 if (s - ks) > 0 or (t - kt) > 0.3 else 3, 4)
    if guard.get("gem"):
        gem(cv, origin, g1 + gt / 2, 0.0, guard["gem"], r=guard.get("gem_r", 1.05), glow=guard.get("glow", 0))

    # Grip.
    gw = grip.get("half", 1.05)
    for x, y in pixels():
        t, s = local(x, y, origin)
        if not (g0 - 0.2 <= t <= g1 + 0.2) or abs(s) > gw:
            continue
        band = ((t - g0) * grip.get("wrap_rate", 1.4)) % 2
        shade = 3 if band < 1 else 2
        if s > 0.25:
            shade += 1
        elif s < -0.45:
            shade -= 1
        cv.put(x, y, "grip", grip["ramp"], shade, 3)

    # Pommel.
    pramp = pommel.get("ramp", guard["ramp"])
    for x, y in pixels():
        t, s = local(x, y, origin)
        dt, ds = t - pr, s
        if dt * dt + ds * ds <= (pr + 0.25) ** 2:
            shade = 4 if ds > 0.3 or dt > 0.6 else 2
            cv.put(x, y, "pommel", pramp, shade, 4)
    if pommel.get("gem"):
        gem(cv, origin, pr, 0.0, pommel["gem"], r=0.75)

    for extra in spec.get("extras", []):
        extra(cv, origin, b0, blen, phase)
    if phase is not None:
        for fx in spec.get("fx", []):
            fx(cv, origin, b0, blen, phase)
    return cv.render()


# ---------------------------------------------------------------- effects (one frame each; phase 0..1)
def fx_sheen(color=(255, 255, 255), strength=0.55):
    def run(cv, origin, b0, blen, phase):
        centre = b0 - 4 + phase * (blen + 8)
        for (x, y), (part, ramp, shade, col, z) in list(cv.px.items()):
            if part != "blade":
                continue
            t, s = local(x, y, origin)
            d = abs((t + s * 0.6) - centre)
            if d < 1.6:
                k = strength * (1 - d / 1.6)
                base = col if col is not None else ramp[shade]
                cv.px[(x, y)] = (part, ramp, shade, tuple(int(b + (c - b) * k) for b, c in zip(base, color)), z)
    return run


def fx_pulse(part_name="gem", amount=1):
    def run(cv, origin, b0, blen, phase):
        bump = amount * math.sin(phase * math.tau)
        for key, (part, ramp, shade, col, z) in list(cv.px.items()):
            if part == part_name and col is None:
                cv.px[key] = (part, ramp, shade + bump, col, z)
    return run


def fx_lightning(seed=3):
    def run(cv, origin, b0, blen, phase):
        frame = int(phase * FRAMES)
        if frame % 4 == 3:
            return  # a beat of calm between bolts
        rng = random.Random(seed * 100 + frame)
        s = 0.0
        t = b0 + 1
        while t < b0 + blen - 1:
            s = max(-1.2, min(1.2, s + rng.choice((-0.8, 0.8))))
            x, y = point(origin, t, s)
            if cv.has(x, y) and cv.px[(x, y)][0] == "blade":
                cv.px[(x, y)] = ("blade", VOLT, 5 if rng.random() < 0.5 else 4, None, 3)
            t += 0.7
    return run


def fx_embers(seed=5):
    def run(cv, origin, b0, blen, phase):
        for (x, y), (part, ramp, shade, col, z) in list(cv.px.items()):
            if part != "blade":
                continue
            t, s = local(x, y, origin)
            n = math.sin(t * 0.9 - phase * math.tau * 2) + math.sin(t * 2.3 + s * 3 + phase * math.tau) * 0.6
            if n > 0.85 or (n > 0.4 and abs(s) < 0.6):
                cv.px[(x, y)] = (part, EMBER, 2 + min(3, (n - 0.4) * 3), None, z)
    return run


def fx_stars(ramp=STAR, seed=9, count=7):
    def run(cv, origin, b0, blen, phase):
        rng = random.Random(seed)
        blade = [k for k, v in cv.px.items() if v[0] == "blade"]
        if not blade:
            return
        for i in range(count):
            key = blade[rng.randrange(len(blade))]
            twinkle = math.sin((phase + i / count) * math.tau)
            if twinkle > 0.2:
                part, r, shade, col, z = cv.px[key]
                cv.px[key] = (part, r, shade, (255, 255, 255) if twinkle > 0.7 else ramp[5], z)
    return run


def fx_runes(ramp, period=1.0):
    """Lights the fuller's rune pixels one after another."""
    def run(cv, origin, b0, blen, phase):
        for (x, y), (part, r, shade, col, z) in list(cv.px.items()):
            if r is not ramp:
                continue
            t, s = local(x, y, origin)
            k = 0.5 + 0.5 * math.sin((t / blen) * math.tau * 1.5 - phase * math.tau * period)
            cv.px[(x, y)] = (part, r, 2 + k * 3, col, z)
    return run


def fx_tide():
    """Bands of light drifting along the blade like sunlight through shallow water."""
    def run(cv, origin, b0, blen, phase):
        for (x, y), (part, ramp, shade, col, z) in list(cv.px.items()):
            if part != "blade":
                continue
            t, s = local(x, y, origin)
            n = math.sin(t * 0.75 - phase * math.tau * 2) + math.sin(t * 1.9 + s * 2.2 + phase * math.tau) * 0.7
            if n > 0.9 or (n > 0.45 and abs(s) < 0.7):
                cv.px[(x, y)] = (part, PRISMARINE, 3 + min(2, (n - 0.45) * 2.5), None, z)
    return run


# ---------------------------------------------------------------- pickaxes
def pickaxe(spec, phase=None):
    cv = Canvas()
    origin = spec.get("origin", (2.4, 29.6))
    head = spec["head"]
    handle = spec["handle"]
    hl = handle.get("len", 29.0)
    hw = handle.get("half", 1.1)
    for x, y in pixels():
        t, s = local(x, y, origin)
        if not (0 <= t <= hl) or abs(s) > hw:
            continue
        shade = 4 if s > 0.25 else 2 if s < -0.45 else 3
        if (t * 1.3) % 6 < 0.8:
            shade -= 1  # grain
        part, ramp = "handle", handle["ramp"]
        wrap = handle.get("wrap")
        if wrap and t < handle.get("wrap_len", 6):
            part, ramp = "wrap", wrap
            shade = (3 if (t * 1.4) % 2 < 1 else 2) + (1 if s > 0.25 else 0)
        cv.put(x, y, part, ramp, shade, 2)
    # End cap.
    if handle.get("cap"):
        for x, y in pixels():
            t, s = local(x, y, origin)
            if -0.4 <= t <= 1.0 and abs(s) <= hw + 0.35:
                cv.put(x, y, "cap", handle["cap"], 4 if s > 0 else 2, 3)
    # Head. Every style is its own silhouette:
    #   double  - two points curving back towards the grip (the classic pick)
    #   hammer  - a long spike one way, a square hammer face the other
    #   mattock - a spike one way, a wide flat adze blade the other
    #   claw    - jagged crystal shards on both sides
    #   curled  - long points that curl back at the ends
    #   maul    - a heavy block on both sides, with a spike on top
    #   winged  - a double pick with fins rising from the collar
    #   drill   - no cross-head: a spiral bore continuing the handle
    style = head.get("style", "double")
    ht = hl - head.get("drop", 2.0)
    W = head.get("span", 12.0)
    B = head.get("droop", 5.2)
    T = head.get("thick", 3.1)
    if style == "drill":
        base = hl - 9.0
        length = 15.5
        R = head.get("radius", 4.0)
        for x, y in pixels():
            t, s = local(x, y, origin)
            k = (t - base) / length
            if not (0 <= k <= 1):
                continue
            r = R * (1 - k) ** 0.85 + (0.4 if k < 0.95 else 0.0)
            if abs(s) > r:
                continue
            band = (t * 1.25 + s * 0.9) % 2.4
            shade = 4 if band < 1.1 else 2
            if s > r - 0.8:
                shade += 1
            if k < 0.12:
                cv.put(x, y, "collar", head.get("collar_ramp", head["ramp"]), 4 if s > 0 else 2, 4)
            else:
                cv.put(x, y, "head", head["ramp"], shade, 3)
        # Two small cutting fins at the base of the bore.
        for side in (-1, 1):
            for i in range(4):
                fx, fy = point(origin, base + 1.0 + i * 0.6, side * (R + 0.6 + i * 0.35))
                cv.put(fx, fy, "head", head["ramp"], 4 if side > 0 else 2, 3)
        ht = base + 2.5
    else:
        samples = [i / 200 * 2 - 1 for i in range(201)]
        def droop(u):
            extra = head.get("curl", 0.0) * max(0.0, abs(u) - 0.6) ** 2 * 12 if style == "curled" else 0.0
            return ht - B * u * u - extra
        curve = [(u, droop(u), u * W) for u in samples]
        for x, y in pixels():
            t, s = local(x, y, origin)
            best = None
            for u, ct, cs in curve:
                d = (t - ct) ** 2 + (s - cs) ** 2
                if best is None or d < best[0]:
                    best = (d, u, ct, cs)
            d, u, ct, cs = best
            a = abs(u)
            thick = T * (1 - a) ** head.get("taper", 0.6) + 0.45
            end = 1.0
            blunt = False
            if style == "hammer" and u < 0:
                thick, end, blunt = T * 0.95, 0.6, True
            elif style == "mattock" and u < 0:
                thick, end, blunt = T * 0.5 + T * 0.9 * (a / 0.85) ** 1.5, 0.85, True
            elif style == "maul":
                thick, end, blunt = T * 1.12, 0.62, True
            elif style == "claw":
                thick = (T * (1 - a) ** 0.4 + 0.6) * (0.55 + 0.55 * abs(math.sin(a * math.pi * 2.6)))
            if head.get("chisel") and u < 0 and not blunt:
                thick = max(thick, T * 0.75 * (1 - a * 0.5))
            if a > end or math.sqrt(d) > thick:
                continue
            above = (t - ct) > 0       # the side facing the tip: lit
            shade = 4 if above else 2
            if a > end - 0.08 and blunt:
                shade = 5 if above else 3          # the flat striking face
            elif a > 0.78:
                shade = 5 if above else 3
            if math.sqrt(d) > thick - 0.7:
                shade += 1 if above else -1
            if style == "claw":
                shade = {0: 5, 1: 3, 2: 2}[int((a * 9 + (t - ct) * 1.3) % 3)]
            ramp = head["ramp"]
            part = "head"
            if a < head.get("collar", 0.17) and head.get("collar_ramp"):
                ramp, part = head["collar_ramp"], "collar"
                shade = 4 if above else 2
            cv.put(x, y, part, ramp, shade, 4 if part == "collar" else 3)
        if style == "maul":
            # A spike on top of the maul.
            for x, y in pixels():
                t, s = local(x, y, origin)
                k = (t - (ht + T * 0.8)) / 3.2
                if 0 <= k <= 1 and abs(s) <= 1.2 * (1 - k) + 0.2:
                    cv.put(x, y, "head", head["ramp"], 5 if s > 0 else 3, 3)
        if style == "winged":
            # Fins rising from the collar, like swept-back wings.
            for side in (-1, 1):
                for x, y in pixels():
                    t, s = local(x, y, origin)
                    k = (t - (ht + T * 0.6)) / 4.0
                    if not (0 <= k <= 1):
                        continue
                    cs = side * (1.2 + 3.2 * k)
                    if abs(s - cs) <= 0.9 * (1 - k) + 0.35:
                        cv.put(x, y, "head", head.get("fin_ramp", head["ramp"]), 5 if side > 0 else 3, 3)
    if head.get("gem"):
        gem(cv, origin, ht + 0.3, 0.0, head["gem"], r=head.get("gem_r", 1.1), z=7)
    for extra in spec.get("extras", []):
        extra(cv, origin, ht, W, phase)
    if phase is not None:
        for fx in spec.get("fx", []):
            fx(cv, origin, ht, W, phase)
    return cv.render()


def fx_head_glow(ramp, part="head"):
    def run(cv, origin, ht, W, phase):
        for key, (p, r, shade, col, z) in list(cv.px.items()):
            if p != part:
                continue
            t, s = local(key[0], key[1], origin)
            k = 0.5 + 0.5 * math.sin(s / W * math.pi * 2 - phase * math.tau)
            if k > 0.55:
                cv.px[key] = (p, ramp, 2 + (k - 0.55) * 7, None, z)
    return run


def fx_head_sheen(color=(255, 255, 255)):
    def run(cv, origin, ht, W, phase):
        c = -W - 3 + phase * (2 * W + 6)
        for key, (p, r, shade, col, z) in list(cv.px.items()):
            if p != "head":
                continue
            t, s = local(key[0], key[1], origin)
            d = abs(s - c)
            if d < 1.5:
                base = r[shade]
                k = 0.55 * (1 - d / 1.5)
                cv.px[key] = (p, r, shade, tuple(int(b + (cc - b) * k) for b, cc in zip(base, color)), z)
    return run


# ---------------------------------------------------------------- axes
def axe(spec, phase=None):
    """An axe: a handle up the diagonal and a head near its top, the blade towards the upper left.

    Styles: hatchet (small bit, a square poll behind), classic, bearded (the edge hangs down towards the grip), crescent
    (a big curved edge), double (a blade on both sides), cleaver (a tall square blade), maul (a heavy wedge, splitting).
    """
    cv = Canvas()
    origin = spec.get("origin", (2.4, 29.6))
    head = spec["head"]
    handle = spec["handle"]
    hl = handle.get("len", 25.5)
    hw = handle.get("half", 1.1)
    style = head.get("style", "classic")
    for x, y in pixels():
        t, s = local(x, y, origin)
        if not (0 <= t <= hl) or abs(s) > hw:
            continue
        shade = 4 if s > 0.25 else 2 if s < -0.45 else 3
        if (t * 1.3) % 6 < 0.8:
            shade -= 1
        part, ramp = "handle", handle["ramp"]
        wrap = handle.get("wrap")
        if wrap and t < handle.get("wrap_len", 6):
            part, ramp = "wrap", wrap
            shade = (3 if (t * 1.4) % 2 < 1 else 2) + (1 if s > 0.25 else 0)
        cv.put(x, y, part, ramp, shade, 2)
    if handle.get("cap"):
        for x, y in pixels():
            t, s = local(x, y, origin)
            if -0.4 <= t <= 1.0 and abs(s) <= hw + 0.35:
                cv.put(x, y, "cap", handle["cap"], 4 if s > 0 else 2, 3)

    tc = hl - head.get("drop", 2.6)
    reach = head.get("reach", 11.0)      # how far the blade sticks out from the handle
    edge = head.get("edge", 6.0)         # half the height of the cutting edge
    neck = head.get("neck", 1.5)         # half the height where it meets the handle
    curve = head.get("curve", 2.4)       # how much the edge bulges out in the middle
    beard = head.get("beard", 1.0)       # >1: the edge hangs further down than up

    def blade(t, s, side):
        """Shade of a blade pixel on this side (1 = upper left, -1 = lower right), or None."""
        depth = side * s - hw
        r = reach if side > 0 else head.get("back_reach", reach)
        if depth < -0.2 or depth > r:
            return None
        k = max(0.0, depth / r)
        e = edge if side > 0 else head.get("back_edge", edge)
        if style == "cleaver" and side > 0:
            half = e * (0.75 + 0.25 * min(1.0, k * 3))
        else:
            half = neck + (e - neck) * k ** head.get("flare", 2.3)
            if k > 0.82:
                half *= 1 + 0.18 * (k - 0.82) / 0.18   # the edge's corners flare into horns
        up, down = half, half * (beard if side > 0 else 1.0)
        dt = t - tc
        if not (-down <= dt <= up):
            return None
        span = up if dt >= 0 else down
        limit = r - curve * (dt / max(span, 0.1)) ** 2 if style != "cleaver" else r
        if depth > limit:
            return None
        if depth > limit - 1.1:
            return 5                              # the honed edge
        if depth < 1.0:
            return 2                              # the neck, in shadow
        return 4 if dt > up * 0.15 else 3

    for x, y in pixels():
        t, s = local(x, y, origin)
        sides = [1]
        if style in ("double", "crescent_double"):
            sides.append(-1)
        for side in sides:
            shade = blade(t, s, side)
            if shade is not None:
                ramp = head["ramp"]
                if head.get("edge_ramp") and shade == 5:
                    ramp, shade = head["edge_ramp"], 4
                cv.put(x, y, "head", ramp, shade if side > 0 else shade - 1, 3)
                break
        # The back: a square poll (hatchet, classic), a spike (bearded war axes) or a wedge (maul).
        back = -s - hw
        dt = t - tc
        if style in ("hatchet", "classic") and 0 <= back <= head.get("poll", 1.6) and abs(dt) <= neck + 0.2:
            cv.put(x, y, "head", head["ramp"], 3 if dt > 0 else 2, 3)
        elif style == "bearded" and head.get("spike") and 0 <= back <= 3.5 and abs(dt) <= 1.1 * (1 - back / 3.5) + 0.2:
            cv.put(x, y, "head", head["ramp"], 4 if dt > 0 else 2, 3)
        elif style == "maul" and 0 <= back <= 2.6 and abs(dt) <= neck + 1.0:
            cv.put(x, y, "head", head["ramp"], 5 if back > 2.0 else 3, 3)
    # The socket around the handle.
    for x, y in pixels():
        t, s = local(x, y, origin)
        if abs(s) <= hw + 0.55 and abs(t - tc) <= neck + 0.4:
            cv.put(x, y, "collar", head.get("collar_ramp", head["ramp"]), 4 if s > 0 else 2, 4)
    if head.get("gem"):
        gem(cv, origin, tc, 0.0, head["gem"], r=0.95, z=7)
    for extra in spec.get("extras", []):
        extra(cv, origin, tc, reach, phase)
    if phase is not None:
        for fx in spec.get("fx", []):
            fx(cv, origin, tc, reach, phase)
    return cv.render()


def fx_axe_glow(ramp):
    """A glow running along the axe's edge."""
    def run(cv, origin, tc, reach, phase):
        for key, (p, r, shade, col, z) in list(cv.px.items()):
            if p != "head":
                continue
            t, s = local(key[0], key[1], origin)
            k = 0.5 + 0.5 * math.sin((t - tc) * 0.8 - phase * math.tau)
            if abs(s) > reach * 0.5 and k > 0.5:
                cv.px[key] = (p, ramp, 2 + (k - 0.5) * 6, None, z)
    return run


def fx_axe_stars(seed=5, count=8):
    def run(cv, origin, tc, reach, phase):
        rng = random.Random(seed)
        keys = [k for k, v in cv.px.items() if v[0] == "head"]
        for i in range(count):
            if not keys:
                return
            key = keys[rng.randrange(len(keys))]
            tw = math.sin((phase + i / count) * math.tau)
            if tw > 0.25:
                p, r, shade, col, z = cv.px[key]
                cv.px[key] = (p, r, shade, (255, 255, 255) if tw > 0.7 else STAR[5], z)
    return run


# ---------------------------------------------------------------- spears
def spear(spec):
    cv = Canvas()
    origin = (1.4, 30.6)
    sl = spec.get("shaft", 27.5)
    for x, y in pixels():
        t, s = local(x, y, origin)
        if 0 <= t <= sl and abs(s) <= 0.95:
            shade = 4 if s > 0.2 else 2
            if (t * 1.1) % 7 < 0.7:
                shade -= 1
            cv.put(x, y, "shaft", spec.get("shaft_ramp", OAK), shade, 2)
    # Binding where the head sits.
    for x, y in pixels():
        t, s = local(x, y, origin)
        if sl - 2.6 <= t <= sl + 0.4 and abs(s) <= 1.15:
            cv.put(x, y, "bind", spec.get("bind", LEATHER), (3 if (t * 1.6) % 2 < 1 else 2) + (1 if s > 0.2 else 0), 3)
    # Head: a leaf blade.
    hl = spec.get("head_len", 14.0)
    hw = spec.get("head_w", 2.6)
    for x, y in pixels():
        t, s = local(x, y, origin)
        u = t - sl
        if not (0 <= u <= hl):
            continue
        k = u / hl
        # A long diamond: widest a third of the way up, straight sides to a sharp point.
        w = hw * (k / 0.32) ** 0.8 if k < 0.32 else hw * ((1 - k) / 0.68) ** 1.1
        w = max(w, 0.7 if k < 0.9 else 0.0)
        if spec.get("wings") and u < 2.2:
            w = max(w, spec["wings"] * (1 - u / 2.2))
        if abs(s) > w:
            continue
        f = (s + w) / (2 * w) if w > 0 else 0.5
        shade = 4 if f > 0.5 else 2
        if f > 0.82:
            shade = 5
        elif f < 0.18:
            shade = 1
        if abs(s) < 0.45 and u < hl * 0.75:
            shade = 3  # the ridge
        if spec.get("style") == "crystal":
            band = (u * 0.9 + s * 1.5) % 3
            shade = {0: 5, 1: 3}.get(int(band), 2) if 0.15 < f < 0.85 else shade
        if spec.get("style") == "chipped" and int(u * 2 + s * 3) % 5 == 0:
            shade -= 1
        cv.put(x, y, "head", spec["ramp"], shade, 4)
    if spec.get("gem"):
        gem(cv, origin, sl + 0.9, 0.0, spec["gem"], r=0.8, z=7)
    # A ribbon hanging from the binding.
    if spec.get("ribbon"):
        bx, by = point(origin, sl - 1.2, -1.0)
        for i in range(5):
            for dx in (0, 1):
                cv.put(bx + dx + (1 if i > 2 else 0), by + 1 + i, "ribbon", spec["ribbon"], 3 + (1 if dx == 0 else -1) - (1 if i == 4 else 0), 5)
    return cv.render()


# ---------------------------------------------------------------- bows
def bow(spec, pull):
    """pull: None (idle) or 0..1."""
    cv = Canvas()
    origin = (4.2, 27.8)
    L = 33.0
    bulge = spec.get("bulge", 6.0) + (pull or 0) * 1.6
    recurve = spec.get("recurve", 1.6)

    def limb_s(t):
        k = t / L
        s = bulge * math.sin(math.pi * k)
        edge = min(k, 1 - k)
        if edge < 0.12:
            s -= recurve * (1 - edge / 0.12) ** 2   # tips flick back
        return s

    for x, y in pixels():
        t, s = local(x, y, origin)
        if not (-0.6 <= t <= L + 0.6):
            continue
        tc = max(0.0, min(L, t))
        c = limb_s(tc)
        k = tc / L
        thick = 0.75 + 0.75 * math.sin(math.pi * k) ** 2
        grip = abs(tc - L / 2) < 2.6
        if grip:
            thick += 0.35
        if abs(s - c) > thick:
            continue
        lit = s - c > 0
        if grip:
            cv.put(x, y, "grip", spec.get("grip", LEATHER), (3 if (tc * 1.5) % 2 < 1 else 2) + (1 if lit else 0), 3)
        elif min(k, 1 - k) < 0.07 and spec.get("tips"):
            cv.put(x, y, "tip", spec["tips"], 4 if lit else 2, 3)
        else:
            shade = 4 if lit else 2
            if (tc * 1.2) % 5 < 0.6:
                shade -= 1
            cv.put(x, y, "limb", spec["ramp"], shade, 2)
    # The string: from tip to tip, or to the nock point while drawn.
    a = (0.4, limb_s(0.4))
    b = (L - 0.4, limb_s(L - 0.4))
    nock = (L / 2, -(pull or 0) * spec.get("draw", 6.5))
    segments = [(a, b)] if pull is None else [(a, nock), (nock, b)]
    for (t1, s1), (t2, s2) in segments:
        steps = 80
        for i in range(steps + 1):
            t = t1 + (t2 - t1) * i / steps
            s = s1 + (s2 - s1) * i / steps
            x, y = point(origin, t, s)
            cv.put(x, y, "string", STRING, 4, 1)
    if pull is not None:
        # The arrow: from the nock, across the grip and past it.
        tail = nock[1] - 0.2
        front = bulge + 4.2
        steps = 60
        for i in range(steps + 1):
            s = tail + (front - tail) * i / steps
            x, y = point(origin, L / 2, s)
            if s > front - 2.4:
                cv.put(x, y, "arrowhead", IRON, 5 if i % 2 else 4, 6)
            elif s < tail + 2.2:
                cv.put(x, y, "fletch", FEATHER, 4, 6)
            else:
                cv.put(x, y, "arrow", PLANK, 5, 5)
        for ds in (0.0, 0.8):
            for side in (-1, 1):
                x, y = point(origin, L / 2 + side * 0.9, tail + 0.4 + ds)
                cv.put(x, y, "fletch", FEATHER, 3, 6)
        hx, hy = point(origin, L / 2, front - 1.6)
        for side in (-1, 1):
            x, y = point(origin, L / 2 + side * 0.9, front - 2.2)
            cv.put(x, y, "arrowhead", IRON, 2, 6)
    return cv.render()


# ---------------------------------------------------------------- shield (128x128, the vanilla 64x64 layout doubled)
def shield(pattern_base):
    size = 128
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    rng = random.Random(11)

    def fill(x0, y0, w, h, fn):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                img.putpixel((x, y), fn(x - x0, y - y0, w, h) + (255,))

    def plate(u, v, w, h, front):
        rim = 2
        on_rim = u < rim or v < rim or u >= w - rim or v >= h - rim
        if on_rim:
            light = u < rim or v < rim
            c = IRON[4 if light else 2]
            if (u in (0, w - 1)) or (v in (0, h - 1)):
                c = IRON[1]
            return c
        if front and pattern_base:
            # The banner's colour goes over this, so keep it pale, with a faint grain.
            return STRING[5] if (u + v) % 9 else STRING[4]
        plank = (u - rim) // 6
        ramp = PLANK if plank % 2 == 0 else OAK
        shade = 3 if (u - rim) % 6 not in (0, 5) else 2
        if (v * 3 + plank * 7) % 13 == 0:
            shade -= 1
        # Iron bands across the planks.
        if v in (10, 11, 34, 35):
            return IRON[3 if v % 2 == 0 else 2]
        # Rivets on the bands.
        if v in (10, 34) and (u - rim) % 6 == 3:
            return IRON[5]
        if front:
            # The boss: a domed iron disc in the middle.
            cx, cy = w / 2 - 0.5, h / 2 - 0.5
            d = math.hypot(u - cx, v - cy)
            if d < 6.5:
                if d > 5.5:
                    return IRON[1]
                light = (u - cx) + (v - cy) < -1
                return IRON[5 if d < 2 and light else 4 if light else 2]
        return ramp[shade]

    fill(2, 2, 24, 44, lambda u, v, w, h: plate(u, v, w, h, True))      # front
    fill(28, 2, 24, 44, lambda u, v, w, h: plate(u, v, w, h, False))    # back
    fill(0, 2, 2, 44, lambda u, v, w, h: IRON[2])                       # sides
    fill(26, 2, 2, 44, lambda u, v, w, h: IRON[2])
    fill(2, 0, 24, 2, lambda u, v, w, h: IRON[3])                       # top and bottom
    fill(26, 0, 24, 2, lambda u, v, w, h: IRON[2])
    # Handle: leather over a wooden grip.
    for (x0, y0, w, h) in ((52, 12, 12, 12), (64, 12, 4, 12), (68, 12, 12, 12), (80, 12, 4, 12), (64, 0, 4, 12), (68, 0, 4, 12)):
        fill(x0, y0, w, h, lambda u, v, w2, h2: LEATHER[3 if (u + v) % 4 else 2])
    return img


# ---------------------------------------------------------------- the catalogue
def guard(ramp, half=5.0, style="straight", gem_ramp=None, thick=1.6, bend=1.6, glow=0):
    return {"ramp": ramp, "half": half, "style": style, "gem": gem_ramp, "thick": thick, "bend": bend, "glow": glow}


VANILLA_SWORDS = {
    "wooden_sword": {"blade": {"ramp": PLANK, "len": 25, "width": 2.4, "tip": 5, "style": "chipped"},
                     "guard": guard(DARK_WOOD, 5.2, thick=2.1), "grip": {"ramp": LEATHER, "len": 5.2}, "pommel": {"ramp": DARK_WOOD, "r": 1.8},
                     "seed": 2},
    "stone_sword": {"blade": {"ramp": STONE, "len": 25, "width": 2.8, "tip": 5.5, "style": "chipped"},
                    "guard": guard(STONE, 5.6, thick=2.3), "grip": {"ramp": LEATHER, "len": 5.2}, "pommel": {"ramp": STONE, "r": 1.9}, "seed": 4},
    "copper_sword": {"blade": {"ramp": COPPER, "len": 26, "width": 2.5, "tip": 7, "flare": 0.3, "fuller": (2, 16), "fuller_ramp": PATINA},
                     "guard": guard(COPPER, 5.8, "curved", thick=2.0, bend=1.8), "grip": {"ramp": LEATHER, "len": 5.2},
                     "pommel": {"ramp": COPPER, "r": 1.8, "gem": PATINA}},
    "iron_sword": {"blade": {"ramp": IRON, "len": 27, "width": 2.5, "tip": 6, "fuller": (2, 19)},
                   "guard": guard(IRON, 6.2, thick=2.0), "grip": {"ramp": LEATHER, "len": 5.4}, "pommel": {"ramp": IRON, "r": 1.9}},
    "golden_sword": {"blade": {"ramp": GOLD, "len": 27, "width": 2.6, "tip": 6.5, "fuller": (2, 18), "fuller_ramp": RUBY, "fuller_shade": 2},
                     "guard": guard(GOLD, 6.8, "curved", RUBY, thick=2.2, bend=2.0), "grip": {"ramp": RED_LEATHER, "len": 5.4},
                     "pommel": {"ramp": GOLD, "r": 2.0, "gem": RUBY}},
    "diamond_sword": {"blade": {"ramp": DIAMOND, "len": 28, "width": 2.7, "tip": 7, "style": "crystal"},
                      "guard": guard(STEEL, 6.8, "winged", SAPPHIRE, thick=2.1, bend=1.6), "grip": {"ramp": CLOTH_BLUE, "len": 5.4},
                      "pommel": {"ramp": STEEL, "r": 2.0, "gem": DIAMOND}},
    "netherite_sword": {"blade": {"ramp": NETHERITE, "len": 28, "width": 3.1, "tip": 7, "fuller": (2, 19), "fuller_ramp": GOLD, "fuller_shade": 3},
                        "guard": guard(NETHERITE, 7.2, "fangs", RUBY, thick=2.4, bend=2.4), "grip": {"ramp": LEATHER, "len": 5.4},
                        "pommel": {"ramp": GOLD, "r": 2.1, "gem": RUBY}},
}

VANILLA_PICKS = {
    "wooden_pickaxe": {"head": {"ramp": PLANK, "style": "hammer", "collar_ramp": LEATHER, "thick": 2.9}, "handle": {"ramp": OAK}},
    "stone_pickaxe": {"head": {"ramp": STONE, "style": "maul", "thick": 3.0, "span": 11.0, "collar_ramp": LEATHER}, "handle": {"ramp": OAK, "wrap": LEATHER}},
    "copper_pickaxe": {"head": {"ramp": COPPER, "style": "mattock", "collar_ramp": PATINA}, "handle": {"ramp": OAK, "wrap": LEATHER}},
    "iron_pickaxe": {"head": {"ramp": IRON, "style": "double", "collar_ramp": STEEL}, "handle": {"ramp": OAK, "wrap": LEATHER, "cap": IRON}},
    "golden_pickaxe": {"head": {"ramp": GOLD, "style": "curled", "curl": 1.0, "collar_ramp": RED_LEATHER, "gem": RUBY},
                       "handle": {"ramp": DARK_WOOD, "wrap": RED_LEATHER, "cap": GOLD}},
    "diamond_pickaxe": {"head": {"ramp": DIAMOND, "style": "claw", "thick": 3.3, "collar_ramp": STEEL, "gem": SAPPHIRE},
                        "handle": {"ramp": DARK_WOOD, "wrap": CLOTH_BLUE, "cap": STEEL}},
    "netherite_pickaxe": {"head": {"ramp": NETHERITE, "style": "winged", "fin_ramp": GOLD, "thick": 3.3, "collar_ramp": GOLD, "gem": RUBY},
                          "handle": {"ramp": DARK_WOOD, "wrap": LEATHER, "cap": GOLD}},
}

VANILLA_AXES = {
    "wooden_axe": {"head": {"ramp": PLANK, "style": "hatchet", "reach": 9.0, "edge": 4.8, "collar_ramp": LEATHER}, "handle": {"ramp": OAK}},
    "stone_axe": {"head": {"ramp": STONE, "style": "hatchet", "reach": 9.5, "edge": 5.2, "curve": 3.0, "collar_ramp": LEATHER},
                  "handle": {"ramp": OAK, "wrap": LEATHER}},
    "copper_axe": {"head": {"ramp": COPPER, "style": "bearded", "beard": 1.7, "reach": 11.0, "edge": 4.8, "collar_ramp": PATINA},
                   "handle": {"ramp": OAK, "wrap": LEATHER}},
    "iron_axe": {"head": {"ramp": IRON, "style": "classic", "reach": 10.5, "edge": 5.8, "collar_ramp": STEEL},
                 "handle": {"ramp": OAK, "wrap": LEATHER, "cap": IRON}},
    "golden_axe": {"head": {"ramp": GOLD, "style": "classic", "reach": 11.0, "edge": 7.0, "curve": 3.4, "collar_ramp": RED_LEATHER, "gem": RUBY},
                   "handle": {"ramp": DARK_WOOD, "wrap": RED_LEATHER, "cap": GOLD}},
    "diamond_axe": {"head": {"ramp": DIAMOND, "style": "double", "reach": 9.5, "edge": 5.8, "back_reach": 8.0, "back_edge": 5.0, "collar_ramp": STEEL,
                             "gem": SAPPHIRE}, "handle": {"ramp": DARK_WOOD, "wrap": CLOTH_BLUE, "cap": STEEL}},
    "netherite_axe": {"head": {"ramp": NETHERITE, "style": "bearded", "beard": 1.8, "spike": True, "reach": 11.5, "edge": 5.6, "collar_ramp": GOLD,
                               "edge_ramp": GOLD}, "handle": {"ramp": DARK_WOOD, "wrap": LEATHER, "cap": GOLD}},
}

VANILLA_SPEARS = {
    "wooden_spear": {"ramp": PLANK, "style": "chipped"},
    "stone_spear": {"ramp": STONE, "style": "chipped", "bind": LEATHER},
    "copper_spear": {"ramp": COPPER, "ribbon": PATINA},
    "iron_spear": {"ramp": IRON, "ribbon": RED_LEATHER, "wings": 2.0},
    "golden_spear": {"ramp": GOLD, "ribbon": RED_LEATHER, "gem": RUBY, "wings": 2.4},
    "diamond_spear": {"ramp": DIAMOND, "style": "crystal", "ribbon": CLOTH_BLUE, "wings": 2.2, "shaft_ramp": DARK_WOOD},
    "netherite_spear": {"ramp": NETHERITE, "ribbon": RUBY, "wings": 2.6, "shaft_ramp": DARK_WOOD, "bind": GOLD, "head_w": 3.3},
}


def rune_line(ramp, every=2.0):
    """Little glowing marks along the middle of the blade (animated by fx_runes)."""
    def run(cv, origin, b0, blen, phase):
        t = b0 + 2
        while t < b0 + blen - 5:
            x, y = point(origin, t, 0.0)
            if cv.has(x, y):
                cv.put(x, y, "rune", ramp, 4, 5)
                if cv.has(x + 1, y):
                    cv.put(x + 1, y, "rune", ramp, 3, 5)
            t += every
    return run


SKY_SWORDS = {
    "vanguard_blade": {"blade": {"ramp": STEEL, "len": 27, "width": 2.6, "tip": 6, "fuller": (2, 18)},
                       "guard": guard(GOLD, 6.4, "curved", RUBY, thick=2.1), "grip": {"ramp": RED_LEATHER, "len": 5.4},
                       "pommel": {"ramp": GOLD, "r": 1.9}},
    "fangsteel_sabre": {"blade": {"ramp": STEEL, "len": 27, "width": 2.4, "tip": 6, "curve": 3.2, "edge_ramp": BLOOD},
                        "guard": guard(BONE, 6.2, "fangs", thick=2.1, bend=2.4), "grip": {"ramp": RED_LEATHER, "len": 5.4},
                        "pommel": {"ramp": BONE, "r": 1.9}, "fx": [fx_sheen((255, 120, 120), 0.45)]},
    "stormedge": {"blade": {"ramp": STORM, "len": 28, "width": 2.7, "tip": 7, "fuller": (2, 19), "fuller_ramp": VOLT, "fuller_shade": 3},
                  "guard": guard(VOLT, 7.0, "winged", SAPPHIRE, thick=2.2), "grip": {"ramp": CLOTH_BLUE, "len": 5.4},
                  "pommel": {"ramp": VOLT, "r": 1.9, "gem": SAPPHIRE}, "fx": [fx_lightning(), fx_pulse()]},
    "riftblade": {"blade": {"ramp": RIFT, "len": 28, "width": 2.6, "tip": 7, "fuller": (2, 19), "fuller_ramp": VOID, "fuller_shade": 2},
                  "guard": guard(DUSK, 7.0, "winged", AMETHYST, thick=2.2), "grip": {"ramp": VOID, "len": 5.4},
                  "pommel": {"ramp": DUSK, "r": 2.0, "gem": AMETHYST}, "fx": [fx_stars(RIFT, 4, 7), fx_sheen((220, 180, 255)), fx_pulse()]},
    "cinderfang": {"blade": {"ramp": CINDER, "len": 28, "width": 2.8, "tip": 6.5, "style": "flame", "edge_ramp": EMBER},
                   "guard": guard(CINDER, 7.0, "fangs", TOPAZ, thick=2.3, bend=2.6), "grip": {"ramp": LEATHER, "len": 5.4},
                   "pommel": {"ramp": EMBER, "r": 2.0}, "fx": [fx_embers(), fx_pulse()]},
    "starfall_blade": {"blade": {"ramp": STAR, "len": 29, "width": 3.0, "tip": 8, "fuller": (2, 20), "fuller_ramp": GOLD, "fuller_shade": 3},
                       "guard": guard(GOLD, 7.6, "winged", SAPPHIRE, thick=2.4, bend=1.8), "grip": {"ramp": CLOTH_BLUE, "len": 5.2},
                       "pommel": {"ramp": GOLD, "r": 2.1, "gem": SAPPHIRE}, "fx": [fx_stars(STAR, 9, 10), fx_sheen((255, 240, 190)), fx_pulse()],
                       "large": True},
    "gravebane": {"blade": {"ramp": GRAVE, "len": 28, "width": 2.9, "tip": 7, "edge_ramp": BONE},
                  "guard": guard(BONE, 7.0, "fangs", EMERALD, thick=2.2, bend=2.2), "grip": {"ramp": DARK_WOOD, "len": 5.4},
                  "pommel": {"ramp": BONE, "r": 2.0}, "extras": [rune_line(SOUL)], "fx": [fx_runes(SOUL), fx_pulse()], "large": True},
    "broodfang": {"blade": {"ramp": BROOD, "len": 28, "width": 2.6, "tip": 7, "style": "serrated", "edge_ramp": BONE},
                  "guard": guard(BROOD, 6.6, "fangs", EMERALD, thick=2.1, bend=2.8), "grip": {"ramp": VOID, "len": 5.4},
                  "pommel": {"ramp": BONE, "r": 1.9}, "fx": [fx_sheen((140, 255, 140), 0.4), fx_pulse()], "large": True},
    "cryptbreaker": {"blade": {"ramp": GRAVE_IRON, "len": 29, "width": 3.1, "tip": 7, "edge_ramp": BONE, "style": "chipped", "seed": 4},
                     "guard": guard(BONE, 7.2, "fangs", AMETHYST, thick=2.3, bend=2.4), "grip": {"ramp": DARK_WOOD, "len": 5.4},
                     "pommel": {"ramp": BONE, "r": 2.0, "gem": AMETHYST}, "extras": [rune_line(AMETHYST)],
                     "fx": [fx_runes(AMETHYST), fx_pulse()], "large": True},
    "arachne_fang": {"blade": {"ramp": CHITIN_GREEN, "len": 27, "width": 2.6, "tip": 9, "curve": 4.2, "style": "serrated", "edge_ramp": VENOM_GREEN},
                     "guard": guard(CHITIN_GREEN, 6.4, "fangs", EMERALD, thick=2.1, bend=2.8), "grip": {"ramp": VOID, "len": 5.4},
                     "pommel": {"ramp": VENOM_GREEN, "r": 1.8}, "fx": [fx_sheen((150, 255, 110), 0.45), fx_pulse()]},
    "tidecaller_blade": {"blade": {"ramp": PRISMARINE, "len": 29, "width": 3.0, "tip": 8, "style": "flame", "edge_ramp": SOUL},
                         "guard": guard(CORAL, 7.4, "winged", DIAMOND, thick=2.3, bend=2.0), "grip": {"ramp": CLOTH_BLUE, "len": 5.4},
                         "pommel": {"ramp": CORAL, "r": 2.0, "gem": DIAMOND},
                         "fx": [fx_tide(), fx_sheen((190, 255, 245), 0.45), fx_pulse()], "large": True},
    "blightreaver": {"blade": {"ramp": BLIGHT_STEEL, "len": 29, "width": 3.0, "tip": 7, "style": "chipped", "edge_ramp": BLOOD, "seed": 7},
                     "guard": guard(BLIGHT_STEEL, 7.2, "fangs", RUBY, thick=2.3, bend=2.4), "grip": {"ramp": DARK_WOOD, "len": 5.4},
                     "pommel": {"ramp": BLOOD, "r": 2.0, "gem": RUBY}, "fx": [fx_sheen((255, 120, 170), 0.45), fx_pulse()], "large": True},
    "cinderbrand": {"blade": {"ramp": ASH_BLACK, "len": 29, "width": 3.1, "tip": 7, "style": "flame", "edge_ramp": EMBER},
                    "guard": guard(CINDER, 7.4, "winged", TOPAZ, thick=2.3, bend=1.9), "grip": {"ramp": DARK_WOOD, "len": 5.4},
                    "pommel": {"ramp": EMBER, "r": 2.0, "gem": TOPAZ}, "fx": [fx_embers(), fx_pulse()], "large": True},
    "rimecleaver": {"blade": {"ramp": RIME, "len": 29, "width": 3.2, "tip": 7, "style": "crystal", "edge_ramp": DIAMOND},
                    "guard": guard(STEEL, 7.2, "winged", SAPPHIRE, thick=2.2, bend=1.8), "grip": {"ramp": CLOTH_BLUE, "len": 5.4},
                    "pommel": {"ramp": STEEL, "r": 2.0, "gem": DIAMOND}, "fx": [fx_sheen((200, 240, 255), 0.55), fx_pulse()], "large": True},
    "mirefang": {"blade": {"ramp": BOG_STEEL, "len": 27, "width": 2.7, "tip": 8, "curve": 3.4, "style": "serrated", "edge_ramp": VENOM_GREEN},
                 "guard": guard(BOG_STEEL, 6.4, "fangs", EMERALD, thick=2.1, bend=2.6), "grip": {"ramp": DARK_WOOD, "len": 5.4},
                 "pommel": {"ramp": VENOM_GREEN, "r": 1.8}, "fx": [fx_sheen((170, 255, 90), 0.45), fx_pulse()]},
    "marrowblade": {"blade": {"ramp": BONE, "len": 28, "width": 2.9, "tip": 7, "edge_ramp": BLOOD, "style": "backspike"},
                    "guard": guard(GOLD, 7.0, "curved", RUBY, thick=2.2, bend=2.0), "grip": {"ramp": RED_LEATHER, "len": 5.4},
                    "pommel": {"ramp": BONE, "r": 2.0, "gem": RUBY}, "fx": [fx_sheen((255, 200, 200), 0.4), fx_pulse()], "large": True},
    "voidrender": {"blade": {"ramp": VOID, "len": 29, "width": 3.1, "tip": 8, "flare": 0.15, "edge_ramp": RIFT},
                   "guard": guard(RIFT, 7.6, "winged", AMETHYST, thick=2.4, bend=1.8), "grip": {"ramp": VOID, "len": 5.2},
                   "pommel": {"ramp": RIFT, "r": 2.1, "gem": AMETHYST}, "fx": [fx_stars(RIFT, 21, 11), fx_sheen((200, 160, 255), 0.5), fx_pulse()],
                   "large": True},
    "voidreaver": {"blade": {"ramp": VOID, "len": 30, "width": 3.3, "tip": 8, "style": "flame", "edge_ramp": RIFT},
                   "guard": guard(BONE, 8.0, "fangs", AMETHYST, thick=2.5, bend=2.2), "grip": {"ramp": RIFT, "len": 5.2},
                   "pommel": {"ramp": RIFT, "r": 2.2, "gem": AMETHYST}, "fx": [fx_stars(RIFT, 21, 13), fx_sheen((230, 180, 255), 0.55), fx_pulse()],
                   "large": True},
}

SKY_PICKS = {
    "quarry_pick": {"head": {"ramp": STONE, "style": "hammer", "thick": 3.2, "collar_ramp": LEATHER}, "handle": {"ramp": OAK, "wrap": LEATHER}},
    "prospector_pickaxe": {"head": {"ramp": IRON, "style": "double", "collar_ramp": BRONZE}, "handle": {"ramp": OAK, "wrap": LEATHER, "cap": BRONZE}},
    "delver_pickaxe": {"head": {"ramp": BRONZE, "style": "mattock", "collar_ramp": IRON, "gem": TOPAZ}, "handle": {"ramp": DARK_WOOD, "wrap": LEATHER, "cap": IRON}},
    "skyiron_pickaxe": {"head": {"ramp": SKYIRON, "style": "winged", "fin_ramp": GOLD, "collar_ramp": GOLD, "gem": SAPPHIRE},
                        "handle": {"ramp": DARK_WOOD, "wrap": CLOTH_BLUE, "cap": GOLD}, "fx": [fx_head_sheen((220, 240, 255))]},
    "duskbore_pick": {"head": {"ramp": DUSK, "style": "drill", "radius": 3.4, "collar_ramp": STEEL, "gem": AMETHYST},
                      "handle": {"ramp": DARK_WOOD, "wrap": VOID, "cap": STEEL}, "fx": [fx_head_sheen((200, 180, 255)), fx_pulse()]},
    "emberheart_pickaxe": {"head": {"ramp": CINDER, "style": "maul", "thick": 3.3, "span": 11.5, "collar_ramp": GOLD, "gem": TOPAZ},
                           "handle": {"ramp": DARK_WOOD, "wrap": LEATHER, "cap": GOLD}, "fx": [fx_head_glow(EMBER), fx_pulse()]},
    "lumen_breaker": {"head": {"ramp": LUMEN, "style": "claw", "thick": 3.6, "span": 13.0, "droop": 5.6, "collar_ramp": GOLD, "gem": DIAMOND},
                      "handle": {"ramp": DARK_WOOD, "wrap": CLOTH_BLUE, "cap": GOLD}, "fx": [fx_head_glow(LUMEN), fx_pulse()]},
}

SKY_AXES = {
    "woodsman_axe": {"head": {"ramp": IRON, "style": "classic", "reach": 10.0, "edge": 5.4, "collar_ramp": LEATHER}, "handle": {"ramp": OAK, "wrap": LEATHER}},
    "ironbark_hatchet": {"head": {"ramp": STEEL, "style": "hatchet", "reach": 9.0, "edge": 5.2, "curve": 2.6, "collar_ramp": OAK},
                         "handle": {"ramp": DARK_WOOD, "wrap": LEATHER, "len": 23.0}},
    "mistpine_splitter": {"head": {"ramp": SKYIRON, "style": "maul", "reach": 8.5, "edge": 4.2, "neck": 3.0, "flare": 0.7, "curve": 0.8,
                                   "collar_ramp": STEEL}, "handle": {"ramp": DARK_WOOD, "wrap": CLOTH_BLUE, "cap": STEEL}},
    "willowcleaver": {"head": {"ramp": STEEL, "style": "cleaver", "reach": 10.5, "edge": 6.8, "neck": 2.4, "collar_ramp": GRAVE, "edge_ramp": IRON},
                      "handle": {"ramp": DARK_WOOD, "wrap": GRAVE, "cap": IRON}},
    "emberbrand_axe": {"head": {"ramp": CINDER, "style": "bearded", "beard": 1.9, "spike": True, "reach": 12.0, "edge": 6.0, "collar_ramp": GOLD,
                                "edge_ramp": EMBER, "gem": TOPAZ}, "handle": {"ramp": DARK_WOOD, "wrap": LEATHER, "cap": GOLD},
                       "fx": [fx_axe_glow(EMBER)]},
    "starwood_greataxe": {"head": {"ramp": STAR, "style": "double", "reach": 11.0, "edge": 7.2, "curve": 3.6, "back_reach": 9.5, "back_edge": 6.4,
                                   "collar_ramp": GOLD, "edge_ramp": GOLD, "gem": SAPPHIRE},
                          "handle": {"ramp": DARK_WOOD, "wrap": CLOTH_BLUE, "cap": GOLD}, "fx": [fx_axe_stars()], "large": True},
}

BOW = {"ramp": OAK, "grip": LEATHER, "tips": IRON, "bulge": 6.0}


def frames(draw, spec):
    """A vertical strip of animation frames, or None if the piece isn't animated."""
    if not spec.get("fx"):
        return None
    strip = Image.new("RGBA", (N, N * FRAMES), (0, 0, 0, 0))
    for i in range(FRAMES):
        strip.alpha_composite(draw(spec, i / FRAMES), (0, i * N))
    return strip


def write_png(path, img):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8", newline="\n")


def build(preview=None):
    out = {}
    tex = VANILLA / "textures"
    for name, spec in VANILLA_SWORDS.items():
        out[f"minecraft:{name}"] = sword(spec)
        write_png(tex / "item" / f"{name}.png", out[f"minecraft:{name}"])
    for name, spec in VANILLA_PICKS.items():
        out[f"minecraft:{name}"] = pickaxe(spec)
        write_png(tex / "item" / f"{name}.png", out[f"minecraft:{name}"])
    for name, spec in VANILLA_AXES.items():
        out[f"minecraft:{name}"] = axe(spec)
        write_png(tex / "item" / f"{name}.png", out[f"minecraft:{name}"])
    for name, spec in VANILLA_SPEARS.items():
        img = spear(spec)
        out[f"minecraft:{name}"] = img
        write_png(tex / "item" / f"{name}.png", img)
        write_png(tex / "item" / f"{name}_in_hand.png", img)
    out["minecraft:bow"] = bow(BOW, None)
    write_png(tex / "item" / "bow.png", out["minecraft:bow"])
    for i, pull in enumerate((0.3, 0.65, 1.0)):
        out[f"minecraft:bow_pulling_{i}"] = bow(BOW, pull)
        write_png(tex / "item" / f"bow_pulling_{i}.png", out[f"minecraft:bow_pulling_{i}"])
    write_png(tex / "entity" / "shield" / "shield_base.png", shield(True))
    write_png(tex / "entity" / "shield" / "shield_base_nopattern.png", shield(False))

    anim = {}
    for name, spec in SKY_SWORDS.items():
        out[f"skyrpg_items:{name}"] = sword(spec)
        strip = frames(sword, spec)
        anim[name] = strip
        write_png(SKY / "textures" / "item" / f"{name}.png", strip or out[f"skyrpg_items:{name}"])
        mcmeta = SKY / "textures" / "item" / f"{name}.png.mcmeta"
        if strip is not None:
            write_json(mcmeta, {"animation": {"frametime": 2, "interpolate": True}})
        elif mcmeta.exists():
            mcmeta.unlink()
        model = {"parent": "skyrpg_items:item/handheld_large" if spec.get("large") else "minecraft:item/handheld",
                 "textures": {"layer0": f"skyrpg_items:item/{name}"}}
        write_json(SKY / "models" / "item" / f"{name}.json", model)
    for name, spec in SKY_PICKS.items():
        out[f"skyrpg_items:{name}"] = pickaxe(spec)
        strip = frames(pickaxe, spec)
        anim[name] = strip
        write_png(SKY / "textures" / "item" / f"{name}.png", strip or out[f"skyrpg_items:{name}"])
        mcmeta = SKY / "textures" / "item" / f"{name}.png.mcmeta"
        if strip is not None:
            write_json(mcmeta, {"animation": {"frametime": 2, "interpolate": True}})
    for name, spec in SKY_AXES.items():
        out[f"skyrpg_items:{name}"] = axe(spec)
        strip = frames(axe, spec)
        anim[name] = strip
        write_png(SKY / "textures" / "item" / f"{name}.png", strip or out[f"skyrpg_items:{name}"])
        mcmeta = SKY / "textures" / "item" / f"{name}.png.mcmeta"
        if strip is not None:
            write_json(mcmeta, {"animation": {"frametime": 2, "interpolate": True}})
        elif mcmeta.exists():
            mcmeta.unlink()
        model = {"parent": "skyrpg_items:item/handheld_large" if spec.get("large") else "minecraft:item/handheld",
                 "textures": {"layer0": f"skyrpg_items:item/{name}"}}
        write_json(SKY / "models" / "item" / f"{name}.json", model)
    # Bigger in the hand, like a greatsword (vanilla handheld, scaled up a quarter).
    write_json(SKY / "models" / "item" / "handheld_large.json", {
        "parent": "minecraft:item/handheld",
        "display": {
            "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 5.2, 0.6], "scale": [1.08, 1.08, 1.0]},
            "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 5.2, 0.6], "scale": [1.08, 1.08, 1.0]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.8, 1.13], "scale": [0.84, 0.84, 0.84]},
            "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.8, 1.13], "scale": [0.84, 0.84, 0.84]},
        },
    })
    write_json(PACK / "pack.mcmeta", {"pack": {"description": "SkyRPG's detailed weapon art", "min_format": 97, "max_format": 97}})
    print(f"wrote {len(out)} weapon textures ({sum(1 for v in anim.values() if v is not None)} animated) and 2 shield textures")

    if preview:
        preview = Path(preview)
        preview.mkdir(parents=True, exist_ok=True)
        keys = list(out)
        cell = 128
        cols = 8
        rows = (len(keys) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * cell, rows * cell), (52, 48, 64, 255))
        for i, key in enumerate(keys):
            sheet.alpha_composite(out[key].resize((cell - 8, cell - 8), Image.NEAREST), ((i % cols) * cell + 4, (i // cols) * cell + 4))
        sheet.save(preview / "weapons.png")
        strips = [(k, v) for k, v in anim.items() if v is not None]
        sheet = Image.new("RGBA", (FRAMES * 64, len(strips) * 64), (52, 48, 64, 255))
        for row, (k, strip) in enumerate(strips):
            for f in range(FRAMES):
                frame = strip.crop((0, f * N, N, (f + 1) * N)).resize((64, 64), Image.NEAREST)
                sheet.alpha_composite(frame, (f * 64, row * 64))
        sheet.save(preview / "animations.png")
        sh = Image.new("RGBA", (512, 256), (52, 48, 64, 255))
        sh.alpha_composite(shield(False).resize((256, 256), Image.NEAREST), (0, 0))
        sh.alpha_composite(shield(True).resize((256, 256), Image.NEAREST), (256, 0))
        sh.save(preview / "shield.png")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview")
    build(parser.parse_args().preview)
