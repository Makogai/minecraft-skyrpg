"""Burbaloni Luliloli: a calm capybara sitting inside a hairy coconut half, with a little orange and a palm leaf.
Part tree: root > body (shell) > (head > (ear_r, ear_l, orange > leaf)), leg_r, leg_l (root). ~18 px tall.
"""
from kit import blank, box, dot, face, fill, hexc, hline, ramp
from cube_model import CubeModel

SHELL = ramp("2e1c10", "46301c", "5c4026", "74522e")
RIM = ramp("e4dcc4", "f4eedc")
INNER = ramp("d8cca8", "ece4c8")
FUR = ramp("94683c", "ac8248", "c29a5c", "d4b070")
NOSE = ramp("5a3a24", "6e4a30", "7e5a3c")
ORANGE = ramp("e07818", "f8962a")
LEAF = ramp("2e7a2c", "48a040", "6cc058")
FOOT = ramp("5a3c22", "74502c")
INK = hexc("1e1410")


def build():
    M = CubeModel("burbaloni_luliloli")
    p, c = M.part, M.cube
    for side, sg in (("r", 1), ("l", -1)):
        p(f"leg_{side}", None, (-3 * sg, 22, 0))
        c(f"leg_{side}", f"foot_{side}", "foot", (-2, 0, -3), (4, 2, 5), share="foot_r" if side == "l" else None, mirror=side == "l")
    p("body", None, (0, 22, 0))
    c("body", "shell", "shell", (-7, -7, -7), (14, 6, 14))
    c("body", "bowl", "shell", (-6, -1, -6), (12, 1, 12))
    c("body", "rim", "rim", (-7, -9, -7), (14, 2, 14))
    c("body", "shoulders", "fur", (-6, -12, -5), (12, 5, 10))
    p("head", "body", (0, -12, -1))
    c("head", "skull", "fur", (-5, -5, -4), (10, 5, 8))
    c("head", "nose", "nose", (-2, -4, -8), (4, 4, 4))
    p("ear_r", "head", (-4, -5, 1))
    c("ear_r", "ear_r", "fur", (-1, -3, -1), (3, 3, 2))
    p("ear_l", "head", (4, -5, 1))
    c("ear_l", "ear_l", "fur", (-1, -3, -1), (3, 3, 2), share="ear_r", mirror=True)
    p("orange", "head", (0, -5, -1))
    c("orange", "orange", "orange", (-1, -2, -1), (3, 2, 3))
    p("leaf", "orange", (1, -2, 0))
    c("leaf", "leaf", "leaf", (0, -3, -1), (1, 3, 3))
    return M, paint


def paint(M):
    img, glow = blank(M)
    mats = {"shell": SHELL, "rim": RIM, "fur": FUR, "nose": NOSE, "orange": ORANGE, "leaf": LEAF, "foot": FOOT}
    for cu in M.owners():
        g = {"shell": "speckle", "fur": "vertical", "rim": "flat"}.get(cu["mat"], "noise")
        fill(img, cu, mats[cu["mat"]], grain=g, seed=sum(map(ord, cu["id"])))
    # hair streaks on the shell
    for nm in ("front", "back", "left", "right"):
        f = face(M, "shell", nm)
        for x in range(0, f[2], 2):
            for y in range(f[3]):
                if (x * 3 + y * 5) % 7 == 0:
                    dot(img, f, x, y, hexc("86603a"))
    # creamy coconut flesh peeking on the rim's top
    f = face(M, "rim", "top")
    box(img, f, 1, 1, f[2] - 2, f[3] - 2, hexc("ece4c8"))
    # the face: sleepy half-closed eyes, a big blunt nose
    f = face(M, "skull", "front")
    for x0 in (2, 6):
        hline(img, f, x0, x0 + 1, 1, hexc("5a3c22"))
        hline(img, f, x0, x0 + 1, 2, INK)
        dot(img, f, x0, 3, hexc("e8d8b0"))
        dot(img, f, x0 + 1, 3, hexc("e8d8b0"))
    f = face(M, "nose", "front")
    dot(img, f, 0, 1, hexc("14100a"))
    dot(img, f, 3, 1, hexc("14100a"))
    hline(img, f, 1, 2, 2, hexc("5e3c28"))
    dot(img, face(M, "orange", "front"), 1, 0, hexc("f8b858"))
    return img, glow
