"""Cappuccino Assassino: a cappuccino cup ninja with a black mask band, red glowing eyes, katanas, headband tail."""
from kit import blank, box, dot, eye, face, fill, hexc, hline, ramp, rings, vline
from cube_model import CubeModel

CUP = ramp("c8ccd4", "dce0e6", "eceef2", "f8f8fa")
SAUCER = ramp("b4b8c2", "ccd0d8", "e0e4ea")
FOAM = ramp("e4d0b0", "f0e0c4", "faf0dc")
COFFEE = ramp("5a3418", "6e4424", "825430")
BLACK = ramp("101014", "1a1a20", "26262e")
BLADE = ramp("a8b0bc", "c8d0da", "e8eef4")
HILT = ramp("0c0c10", "181820")
GOLD = ramp("a08020", "c8a030")


def build():
    M = CubeModel("cappuccino_assassino")
    p, c = M.part, M.cube
    for side, sg in (("r", 1), ("l", -1)):
        p(f"leg_{side}", None, (-2.5 * sg, 21, 0))
        c(f"leg_{side}", f"leg_{side}", "black", (-1, 0, -1), (2, 3, 2), share="leg_r" if side == "l" else None, mirror=side == "l")
    p("body", None, (0, 20, 0))
    c("body", "saucer", "saucer", (-6, 0, -6), (12, 1, 12))
    c("body", "cup", "cup", (-4, -12, -4), (8, 12, 8))
    c("body", "handle", "cup", (4, -9, -1), (2, 5, 2))
    c("body", "coffee", "coffee", (-3, -13, -3), (6, 1, 6))
    c("body", "foam", "foam", (-3, -15, -3), (6, 2, 6))
    # Ninja band across the front of the cup, a knot and a tail flapping at the back.
    c("body", "mask", "black", (-5, -10, -5), (10, 4, 10))
    p("tail", "body", (0, -8, 5))
    c("tail", "tail_a", "black", (-1, -1, 0), (2, 2, 4))
    c("tail", "tail_b", "black", (-1, 0, 4), (2, 1, 3))
    # Arms with katanas.
    for side, sg in (("r", 1), ("l", -1)):
        p(f"arm_{side}", "body", (-5 * sg, -7, 0), rot=(-0.6, 0, 0.35 * sg))
        c(f"arm_{side}", f"arm_{side}", "black", (-0.5, 0, -0.5), (1, 6, 1), share="arm_r" if side == "l" else None, mirror=side == "l")
        p(f"katana_{side}", f"arm_{side}", (0, 6, 0), rot=(-0.4, 0, -0.5 * sg))
        c(f"katana_{side}", f"hilt_{side}", "hilt", (-0.5, -2, -0.5), (1, 4, 1))
        c(f"katana_{side}", f"guard_{side}", "gold", (-1.5, -3, -0.5), (3, 1, 1))
        c(f"katana_{side}", f"blade_{side}", "blade", (-0.5, -13, -0.5), (1, 10, 1))
    return M, paint


def paint(M):
    img, glow = blank(M)
    mats = {"saucer": SAUCER, "cup": CUP, "coffee": COFFEE, "foam": FOAM, "black": BLACK, "blade": BLADE, "hilt": HILT, "gold": GOLD}
    grains = {"cup": "vertical", "foam": "noise", "coffee": "noise"}
    for c in M.owners():
        fill(img, c, mats[c["mat"]], grain=grains.get(c["mat"], "flat"), seed=sum(map(ord, c["id"])))
    # Latte-art heart on the foam top.
    f = face(M, "foam", "top")
    heart = hexc("8a5a30")
    for x, y in ((1, 1), (4, 1), (1, 2), (2, 2), (3, 2), (4, 2), (2, 3), (3, 3)):
        dot(img, f, x, y, heart)
    # Mask: fierce slit eyes with glowing red pupils, brow wedges, white sclera sliver.
    f = face(M, "mask", "front")
    for x0, flip in ((1, 1), (6, -1)):
        box(img, f, x0, 1, 3, 2, hexc("ece8e0"))
        dot(img, f, x0 + (2 if flip == 1 else 0), 1, hexc("ece8e0"))
        for g in (img, glow):
            dot(g, f, x0 + 1, 1, hexc("ff2020")); dot(g, f, x0 + 1, 2, hexc("ff2020"))
        dot(img, f, x0, 0, hexc("0c0c10")); dot(img, f, x0 + 1, 0, hexc("0c0c10"))
        dot(img, f, x0 + (0 if flip == 1 else 2), 1, hexc("0c0c10"))
    hline(img, f, 4, 5, 2, hexc("303038"))
    # Ties on the back of the band; cup details.
    hline(img, face(M, "mask", "back"), 3, 6, 1, hexc("303038"))
    f = face(M, "cup", "front")
    box(img, f, 0, 10, 8, 2, hexc("c0c4cc"))
    box(img, f, 3, 5, 2, 2, hexc("8a5a30")); dot(img, f, 3, 5, hexc("c08850"))
    return img, glow
