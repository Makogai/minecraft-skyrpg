"""Tung Tung Tung Sahur: a living wooden log with a wide-eyed face, thin wooden arms and legs, and a baseball bat.

The example the other brainrots follow. Part tree: root > body > (head_top, arm_r > bat, arm_l), leg_r, leg_l.
About 30 px tall (just under two blocks), feet on the ground at y 24, facing -z.
"""
from kit import blank, box, dot, eye, face, fill, hexc, hline, ramp, rings
from cube_model import CubeModel

LOG = ramp("7a5530", "8c6238", "a0743f", "b4864c", "c69a5c")
CUT = ramp("8a6236", "c8a066", "b08850", "d4b07a")
LIMB = ramp("6e4a2a", "805834", "94683e")
BAT = ramp("5a3a20", "6e4828", "845a34", "9a6c40")
GRIP = ramp("1e1e24", "2c2c34")


def build():
    M = CubeModel("tung_tung_sahur")
    p, c = M.part, M.cube
    # Legs: root parts, so they stay planted while the body hops.
    for side, sg in (("r", 1), ("l", -1)):
        p(f"leg_{side}", None, (-2 * sg, 16, 0))
        c(f"leg_{side}", f"leg_{side}", "limb", (-1, 0, -1), (2, 8, 2), share="leg_r" if side == "l" else None, mirror=side == "l")
    # The log body on the hips, its cut top a separate cube so it gets the rings.
    p("body", None, (0, 16, 0))
    c("body", "trunk", "log", (-4, -20, -4), (8, 20, 8))
    p("head_top", "body", (0, -20, 0))
    c("head_top", "top", "cut", (-4, -1, -4), (8, 1, 8))
    # Arms at the shoulders; the right one holds the bat, pointing forward and up.
    for side, sg in (("r", 1), ("l", -1)):
        p(f"arm_{side}", "body", (-5 * sg, -14, 0), rot=(0, 0, 0.12 * sg))
        c(f"arm_{side}", f"arm_{side}", "limb", (-1, 0, -1), (2, 10, 2), share="arm_r" if side == "l" else None, mirror=side == "l")
    p("bat", "arm_r", (0, 9, 0), rot=(-0.35, 0, 0.15))
    c("bat", "grip", "grip", (-0.5, -1, -0.5), (1, 4, 1))
    c("bat", "barrel", "bat", (-1, -12, -1), (2, 11, 2))
    return M, paint


def paint(M):
    img, glow = blank(M)
    mats = {"log": LOG, "cut": CUT, "limb": LIMB, "bat": BAT, "grip": GRIP}
    grains = {"log": "vertical", "limb": "vertical", "bat": "vertical", "cut": "flat", "grip": "flat"}
    for c in M.owners():
        fill(img, c, mats[c["mat"]], grain=grains[c["mat"]], seed=sum(map(ord, c["id"])))
    rings(img, face(M, "top", "top"), [hexc("8a6236"), hexc("c8a066"), hexc("b08850")])
    # The face: big round staring eyes, little brows, a wide open grin.
    f = face(M, "trunk", "front")
    eye(img, f, 1, 4, w=2, h=3, look=(1, 1))
    eye(img, f, 5, 4, w=2, h=3, look=(0, 1))
    hline(img, f, 1, 2, 3, hexc("3a2414"))
    hline(img, f, 5, 6, 3, hexc("3a2414"))
    box(img, f, 2, 9, 4, 2, hexc("2a140a"))
    hline(img, f, 2, 5, 9, hexc("f0ece0"))
    dot(img, f, 3, 10, hexc("c04030"))
    dot(img, f, 4, 10, hexc("c04030"))
    # Bark knots down the back and sides.
    for name, (x, y) in (("back", (3, 12)), ("left", (4, 15)), ("right", (2, 6))):
        r = face(M, "trunk", name)
        box(img, r, x, y, 2, 2, hexc("6a4626"))
        dot(img, r, x, y, hexc("4a2e16"))
    return img, glow
