"""Bombombini Gusini: a goose on a grey fighter jet; delta wings, two missiles, tail fin, glowing exhaust."""
from kit import blank, box, dot, eye, face, fill, hexc, hline, ramp, vline
from cube_model import CubeModel

JET = ramp("6a707a", "808792", "98a0aa", "b0b8c2")
DARK = ramp("3c4048", "4c525c", "5c636e")
WHITE = ramp("d8dce0", "e8ecee", "f6f8f8")
ORANGE = ramp("d8680c", "ee8416", "fa9c2a")
MISSILE = ramp("a8aeb6", "c4cad0", "dce0e4")
RED = ramp("a01818", "c82424")
EXH = ramp("20242a", "34383e")


def build():
    M = CubeModel("bombombini_gusini")
    p, c = M.part, M.cube
    p("body", None, (0, 14, 0))
    c("body", "fuselage", "jet", (-3, -3, -4), (6, 6, 16))
    c("body", "nose", "dark", (-2, -2, -9), (4, 4, 5))
    c("body", "canopy", "dark", (-2, -5, 0), (4, 2, 5))
    c("body", "exhaust", "exh", (-2, -2, 12), (4, 4, 2))
    c("body", "fin", "jet", (-1, -7, 7), (2, 4, 5))
    c("body", "fin_tip", "red", (-1, -9, 7), (2, 2, 5))
    c("body", "stab_r", "dark", (-8, 0, 9), (5, 1, 3))
    c("body", "stab_l", "dark", (3, 0, 9), (5, 1, 3))
    # Goose neck + head on the front.
    p("neck", "body", (0, -2, -6))
    c("neck", "neck", "white", (-1, -7, -1), (2, 7, 2))
    p("head", "neck", (0, -7, 0))
    c("head", "skull", "white", (-2, -4, -2), (4, 4, 4))
    c("head", "beak", "orange", (-1, -2, -5), (2, 1, 3))
    c("head", "beak_top", "orange", (-1, -3, -5), (2, 1, 3))
    # Swept delta wings (root + tip) with a missile under each.
    for side, sg in (("r", 1), ("l", -1)):
        sh = (lambda x0, w: (-x0 - w) if sg == 1 else x0)  # x offset for the right (-x) or left (+x) wing
        p(f"wing_{side}", "body", (-3 * sg, 1, 0))
        c(f"wing_{side}", f"wing_{side}", "jet", (sh(0, 8), 0, 0), (8, 1, 9), share=None)
        c(f"wing_{side}", f"wtip_{side}", "dark", (sh(8, 5), 0, 3), (5, 1, 6))
        c(f"wing_{side}", f"missile_{side}", "missile", (sh(6, 1), 1, 1), (1, 1, 6))
        c(f"wing_{side}", f"mhead_{side}", "red", (sh(6, 1), 1, -1), (1, 1, 2))
    # Tiny orange goose feet tucked under the belly.
    for side, sg in (("r", 1), ("l", -1)):
        p(f"leg_{side}", None, (-1.5 * sg, 17, -1))
        c(f"leg_{side}", f"foot_{side}", "orange", (-1, 0, -1), (2, 1, 3), share="foot_r" if side == "l" else None, mirror=side == "l")
    return M, paint


def paint(M):
    img, glow = blank(M)
    mats = {"jet": JET, "dark": DARK, "white": WHITE, "orange": ORANGE, "missile": MISSILE, "red": RED, "exh": EXH}
    grains = {"jet": "horizontal", "dark": "noise", "white": "speckle", "orange": "flat", "missile": "flat", "red": "flat", "exh": "flat"}
    for c in M.owners():
        fill(img, c, mats[c["mat"]], grain=grains[c["mat"]], seed=sum(map(ord, c["id"])))
    # Goose face: big eyes with orange rings on both sides, front of the skull.
    for name in ("right", "left"):
        f = face(M, "skull", name)
        box(img, f, 1, 1, 3, 3, hexc("f08a18"))
        dot(img, f, 2, 2, hexc("f8f8f8"))
        dot(img, f, 2 if name == "right" else 3, 2, hexc("14141a"))
    f = face(M, "skull", "front")
    box(img, f, 0, 1, 1, 2, hexc("f08a18")); box(img, f, 3, 1, 1, 2, hexc("f08a18"))
    dot(img, f, 0, 1, hexc("14141a")); dot(img, f, 3, 1, hexc("14141a"))
    # Beak nostril, canopy glint, jet panel lines, roundels on the wings.
    dot(img, face(M, "beak_top", "top"), 0, 1, hexc("8a3a08"))
    f = face(M, "fuselage", "right"); hline(img, f, 0, 15, 2, hexc("5c636e"))
    f = face(M, "fuselage", "left"); hline(img, f, 0, 15, 2, hexc("5c636e"))
    for s in ("r", "l"):
        f = face(M, f"wing_{s}", "top")
        box(img, f, 2, 3, 2, 2, hexc("c82424"))
    f = face(M, "exhaust", "back")
    box(img, f, 0, 0, 4, 4, hexc("20242a"))
    for g in (img, glow):
        box(g, f, 1, 1, 2, 2, hexc("ff9a20"))
    dot(glow, f, 1, 1, hexc("ffe060")); dot(img, f, 1, 1, hexc("ffe060"))
    return img, glow
