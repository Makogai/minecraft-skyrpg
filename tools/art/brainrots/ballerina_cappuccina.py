"""Ballerina Cappuccina: a ballerina whose head is a cappuccino cup. Parts: body > (head, arm_r > forearm_r, arm_l > forearm_l, tutu), leg_r, leg_l."""
from kit import blank, box, dot, eye, face, fill, hexc, hline, ramp, vline
from cube_model import CubeModel

CERAMIC = ramp("ece8e0", "f4f0e8", "faf6f0")
COFFEE = ramp("5a3418", "6e4424", "84582e")
FOAM = ramp("e8d4b0", "f4e4c4", "fcf0d8")
PINK = ramp("e0609c", "f074ae", "f88cc0", "ffa4d0")
TUTU = ramp("f488b8", "ff9ccc", "ffb4dc", "ffcce8")
SKIN = ramp("e8b090", "f4c4a4")
SHOE = ramp("f8b4cc", "ffd0e0")


def build():
    M = CubeModel("ballerina_cappuccina")
    p, c = M.part, M.cube
    for s, sg in (("r", -1), ("l", 1)):
        p(f"leg_{s}", None, (1.5 * sg, 16, 0))
        c(f"leg_{s}", f"leg_{s}", "pink", (-1, 0, -1), (2, 5, 2), share=None if s == "r" else "leg_r")
        c(f"leg_{s}", f"shoe_{s}", "shoe", (-1, 5, -2), (2, 3, 3), share=None if s == "r" else "shoe_r")
    p("body", None, (0, 16, 0))
    c("body", "torso", "pink", (-3, -6, -2), (6, 6, 4))
    c("body", "tutu", "tutu", (-6, -2, -6), (12, 1, 12))
    p("head", "body", (0, -6, 0))
    c("head", "cup", "ceramic", (-4, -7, -4), (8, 7, 8))
    c("head", "coffee", "coffee", (-3, -8, -3), (6, 1, 6))
    c("head", "handle_t", "ceramic", (4, -5, -1), (2, 1, 2))
    c("head", "handle_s", "ceramic", (5, -4, -1), (1, 2, 2))
    c("head", "handle_b", "ceramic", (4, -2, -1), (2, 1, 2))
    for s, sg in (("r", -1), ("l", 1)):
        p(f"arm_{s}", "body", (4.5 * sg, -5, 0), rot=(0, 0, 0.4 * sg))
        c(f"arm_{s}", f"arm_{s}", "skin", (-1, -6, -1), (2, 6, 2), share=None if s == "r" else "arm_r")
        p(f"forearm_{s}", f"arm_{s}", (0, -6, 0), rot=(0, 0, -1.2 * sg))
        c(f"forearm_{s}", f"fore_{s}", "skin", (-1, -6, -1), (2, 6, 2), share=None if s == "r" else "fore_r")
    return M, paint


def paint(M):
    img, glow = blank(M)
    mats = {"ceramic": CERAMIC, "coffee": COFFEE, "pink": PINK, "tutu": TUTU, "skin": SKIN, "shoe": SHOE}
    for c in M.owners():
        g = "horizontal" if c["mat"] in ("pink", "tutu") else ("speckle" if c["mat"] == "coffee" else "noise")
        fill(img, c, mats[c["mat"]], grain=g, seed=sum(map(ord, c["id"])))
    # foam heart on the coffee
    f = face(M, "coffee", "top")
    box(img, f, 0, 0, 6, 6, FOAM[0])
    for x, y in ((1, 1), (2, 1), (4, 1), (3, 2), (2, 2), (4, 2), (3, 3), (1, 2), (5, 2)):
        pass
    for x, y in ((1, 1), (2, 1), (4, 1), (5, 1), (1, 2), (2, 2), (3, 2), (4, 2), (5, 2), (2, 3), (3, 3), (4, 3), (3, 4)):
        dot(img, f, x, y, hexc("7a4a28"))
    for x, y in ((0, 0), (5, 0), (0, 5), (5, 5)):
        dot(img, f, x, y, COFFEE[1])
    ft = face(M, "coffee", "front")
    box(img, ft, 0, 0, 6, 1, FOAM[1])
    # cup top face: rim ring around the coffee
    ct = face(M, "cup", "top")
    box(img, ct, 0, 0, 8, 8, CERAMIC[2])
    # face on the cup: lashes, eyes, blush, smile
    f = face(M, "cup", "front")
    eye(img, f, 1, 2, 2, 2, pupil=(60, 30, 60), look=(1, 1))
    eye(img, f, 5, 2, 2, 2, pupil=(60, 30, 60), look=(0, 1))
    hline(img, f, 1, 2, 1, hexc("2a1a2a"))
    hline(img, f, 5, 6, 1, hexc("2a1a2a"))
    dot(img, f, 0, 1, hexc("2a1a2a"))
    dot(img, f, 7, 1, hexc("2a1a2a"))
    box(img, f, 0, 4, 2, 1, hexc("f8a4b8"))
    box(img, f, 6, 4, 2, 1, hexc("f8a4b8"))
    hline(img, f, 3, 4, 5, hexc("c02850"))
    dot(img, f, 2, 4, hexc("c02850"))
    dot(img, f, 5, 4, hexc("c02850"))
    # saucer-ish brown band at the cup bottom
    for n in ("front", "back", "left", "right"):
        fr = face(M, "cup", n)
        hline(img, fr, 0, fr[2] - 1, fr[3] - 1, hexc("c8a878"))
    # tutu rim, pointe ribbons
    ft = face(M, "tutu", "top")
    for x in range(0, ft[2], 2):
        dot(img, ft, x, 0, TUTU[3])
    for s in ("r",):
        for n in ("front", "right", "left", "back"):
            fr = face(M, "shoe_r", n)
            hline(img, fr, 0, fr[2] - 1, 0, hexc("ff7ab0"))
    fr = face(M, "torso", "front")
    hline(img, fr, 2, 3, 0, SKIN[1])
    return img, glow
