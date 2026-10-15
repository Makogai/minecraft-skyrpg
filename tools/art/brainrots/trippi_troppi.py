"""Trippi Troppi: a shrimp with a grey tabby cat head. Segmented body stepping down to a fan tail, thin legs, antennae."""
from kit import blank, box, dot, eye, face, fill, hexc, hline, ramp, vline
from cube_model import CubeModel

SHRIMP = ramp("d85a3c", "ec7450", "f98e68", "ffa682")
BAND = ramp("c04430", "d85a3c")
FAN = ramp("e0603e", "f27c58", "ffa07a")
LEG = ramp("c4482e", "d85a3c")
FUR = ramp("6c7078", "848890", "9ca0a8", "b4b8c0")
STRIPE = ramp("40444c", "50545c")
PINK = ramp("e890a0", "f4a8b4")
ANT = ramp("c4482e", "e0603e")


def build():
    M = CubeModel("trippi_troppi")
    p, c = M.part, M.cube
    # Thin legs underneath (two rows, right and left).
    for side, sg in (("r", 1), ("l", -1)):
        p(f"leg_{side}", None, (-2 * sg, 21, 0))
        for i, z in enumerate((-3, -1, 1)):
            c(f"leg_{side}", f"leg_{side}{i}", "leg", (-0.5, 0, z), (1, 3, 1), share=f"leg_r{i}" if side == "l" else None, mirror=side == "l")
    # Segments stepping down: front is highest, the fan is lowest.
    p("body", None, (0, 21, -1))
    c("body", "seg1", "shrimp", (-3, -8, -3), (6, 8, 5))
    p("seg2", "body", (0, 0, 2))
    c("seg2", "seg2", "shrimp", (-3, -7, 0), (6, 7, 3))
    c("seg2", "seg2_ridge", "band", (-1, -8, 0), (2, 1, 3))
    p("seg3", "seg2", (0, 0, 3))
    c("seg3", "seg3", "shrimp", (-2.5, -6, 0), (5, 6, 3))
    p("seg4", "seg3", (0, 0, 3))
    c("seg4", "seg4", "shrimp", (-2, -4, 0), (4, 4, 2))
    p("tail", "seg4", (0, 0, 2))
    c("tail", "fan_mid", "fan", (-1, -3, 0), (2, 3, 3))
    c("tail", "fan_r", "fan", (-4, -1, 0), (3, 1, 3))
    c("tail", "fan_l", "fan", (1, -1, 0), (3, 1, 3))
    # Cat head up front.
    p("head", "body", (0, -3, -3))
    c("head", "skull", "fur", (-3, -5, -5), (6, 6, 5))
    c("head", "muzzle", "fur", (-1.5, -2, -6), (3, 2, 1))
    c("head", "nose", "pink", (-0.5, -3, -6.5), (1, 1, 1))
    c("head", "ear_r", "fur", (-3, -8, -3), (2, 3, 1))
    c("head", "ear_l", "fur", (1, -8, -3), (2, 3, 1))
    p("ant_r", "head", (-2, -5, -2), rot=(0.5, 0, -0.35))
    c("ant_r", "ant_r", "ant", (-0.5, -7, -0.5), (1, 7, 1))
    p("ant_l", "head", (2, -5, -2), rot=(0.5, 0, 0.35))
    c("ant_l", "ant_l", "ant", (-0.5, -7, -0.5), (1, 7, 1))
    return M, paint


def paint(M):
    img, glow = blank(M)
    mats = {"shrimp": SHRIMP, "band": BAND, "fan": FAN, "leg": LEG, "fur": FUR, "pink": PINK, "ant": ANT}
    grains = {"shrimp": "horizontal", "band": "flat", "fan": "vertical", "leg": "flat", "fur": "speckle", "pink": "flat", "ant": "flat"}
    for c in M.owners():
        fill(img, c, mats[c["mat"]], grain=grains[c["mat"]], seed=sum(map(ord, c["id"])))
    dark = hexc("a03a26")
    for n in ("seg1", "seg2", "seg3", "seg4"):
        for side in ("right", "left"):
            f = face(M, n, side)
            hline(img, f, 0, f[2] - 1, 3, dark)
    f = face(M, "skull", "front")
    # tabby stripes on the brow, big green eyes, whiskers
    for x in (1, 3, 4):
        dot(img, f, x, 0, hexc("40444c"))
    dot(img, f, 2, 0, hexc("40444c"))
    box(img, f, 0, 1, 2, 2, hexc("38c060")); box(img, f, 4, 1, 2, 2, hexc("38c060"))
    dot(img, f, 1, 1, hexc("101418"));
    dot(img, f, 4, 1, hexc("101418")); 
    dot(img, f, 0, 1, hexc("d8ffe0")); dot(img, f, 5, 1, hexc("d8ffe0"))
    for x, y in ((0, 4), (1, 5), (4, 5), (5, 4)):
        dot(img, f, x, y, hexc("f8f8f8"))
    dot(img, f, 2, 4, hexc("e890a0")); dot(img, f, 3, 4, hexc("e890a0"))
    for n in ("ear_r", "ear_l"):
        box(img, face(M, n, "front"), 0, 1, 2, 2, hexc("e890a0"))
    for s in ("right", "left"):
        f = face(M, "skull", s)
        for x, y in ((1, 2), (1, 3), (3, 3)):
            dot(img, f, x, y, hexc("40444c"))
    return img, glow
