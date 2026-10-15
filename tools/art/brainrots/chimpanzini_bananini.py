"""Chimpanzini Bananini: a chimp popping out of a peeled banana, floppy peel strips, stubby feet."""
from kit import blank, box, dot, eye, face, fill, hexc, hline, ramp
from cube_model import CubeModel

BANANA = ramp("d8a818", "ecc22a", "f6d640", "fce46a")
PEEL = ramp("c89a14", "e6b820", "f2cc34", "f8dc58")
TIP = ramp("3a2410", "553818", "6a4620")
FUR = ramp("4a2c18", "5e3a20", "744a2a", "86583a")
SKIN = ramp("c89a6c", "dcae80", "ecc496", "f6d4a8")
FOOT = ramp("3a2412", "4e301a")


def build():
    M = CubeModel("chimpanzini_bananini")
    p, c = M.part, M.cube
    for side, sg in (("r", 1), ("l", -1)):
        p(f"leg_{side}", None, (-2 * sg, 21, 0))
        c(f"leg_{side}", f"leg_{side}", "foot", (-1.5, 0, -2), (3, 3, 4), share="leg_r" if side == "l" else None, mirror=side == "l")
    p("body", None, (0, 21, 0))
    c("body", "banana", "banana", (-3, -9, -3), (6, 9, 6))
    c("body", "torso", "fur", (-2, -12, -2), (4, 4, 4))
    # Peel strips: front, back, right, left, hinged on the banana's top rim and flopping outward.
    p("peel_f", "body", (0, -9, -3), rot=(-1.0, 0, 0))
    c("peel_f", "peel_f", "peel", (-3, 0, -1), (6, 7, 1))
    c("peel_f", "peel_f_tip", "tip", (-3, 7, -1), (6, 2, 1))
    p("peel_b", "body", (0, -9, 3), rot=(1.0, 0, 0))
    c("peel_b", "peel_b", "peel", (-3, 0, 0), (6, 7, 1))
    c("peel_b", "peel_b_tip", "tip", (-3, 7, 0), (6, 2, 1))
    p("peel_r", "body", (-3, -9, 0), rot=(0, 0, 1.0))
    c("peel_r", "peel_r", "peel", (-1, 0, -3), (1, 7, 6))
    c("peel_r", "peel_r_tip", "tip", (-1, 7, -3), (1, 2, 6))
    p("peel_l", "body", (3, -9, 0), rot=(0, 0, -1.0))
    c("peel_l", "peel_l", "peel", (0, 0, -3), (1, 7, 6))
    c("peel_l", "peel_l_tip", "tip", (0, 7, -3), (1, 2, 6))
    # Head with a tan face plate, big ears.
    p("head", "body", (0, -12, 0))
    c("head", "skull", "fur", (-3, -6, -3), (6, 6, 6))
    c("head", "face", "skin", (-3, -5, -4), (6, 5, 1))
    c("head", "ear_r", "skin", (-5, -4, -1), (2, 3, 1))
    c("head", "ear_l", "skin", (3, -4, -1), (2, 3, 1))
    for side, sg in (("r", 1), ("l", -1)):
        p(f"arm_{side}", "body", (-3 * sg, -11, -2), rot=(-0.8, 0, 0.5 * sg))
        c(f"arm_{side}", f"arm_{side}", "fur", (-1, 0, -1), (2, 6, 2), share="arm_r" if side == "l" else None, mirror=side == "l")
    return M, paint


def paint(M):
    img, glow = blank(M)
    mats = {"banana": BANANA, "peel": PEEL, "tip": TIP, "fur": FUR, "skin": SKIN, "foot": FOOT}
    grains = {"banana": "vertical", "peel": "vertical", "tip": "noise", "fur": "speckle", "skin": "flat", "foot": "flat"}
    for c in M.owners():
        fill(img, c, mats[c["mat"]], grain=grains[c["mat"]], seed=sum(map(ord, c["id"])))
    f = face(M, "banana", "front")
    for x in (1, 4):
        box(img, f, x, 0, 1, 9, hexc("c89a14"))
    f = face(M, "face", "front")
    eye(img, f, 0, 0, w=2, h=2, look=(1, 1))
    eye(img, f, 4, 0, w=2, h=2, look=(0, 1))
    dot(img, f, 2, 2, hexc("3a2414"))
    dot(img, f, 3, 2, hexc("3a2414"))
    hline(img, f, 0, 5, 3, hexc("2a140a"))
    hline(img, f, 1, 4, 4, hexc("f0ece0"))
    return img, glow
