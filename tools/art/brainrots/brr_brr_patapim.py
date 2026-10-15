"""Brr Brr Patapim: a tree-trunk monkey with a leaf canopy, branch arms and huge flat feet. Parts: body > (head > ears/canopy, arm_r, arm_l), leg_r, leg_l."""
from kit import blank, box, dot, eye, face, fill, hexc, hline, ramp, vline
from cube_model import CubeModel

BARK = ramp("5a3a20", "6e4a2a", "84603a", "9a7448")
FUR = ramp("6a4424", "7e5530", "946a3c")
TAN = ramp("d8b078", "e8c48c", "f4d8a4")
LEAF = ramp("2a6a24", "3a8a30", "4ea440", "6cc058")
BRANCH = ramp("5a3c22", "6e4c2c", "846038")
FOOT = ramp("7a5430", "906838", "a67c44")


def build():
    M = CubeModel("brr_brr_patapim")
    p, c = M.part, M.cube
    for s, sg in (("r", -1), ("l", 1)):
        p(f"leg_{s}", None, (2 * sg, 20, 0))
        c(f"leg_{s}", f"leg_{s}", "bark", (-1, 0, -1), (2, 2, 2), share=None if s == "r" else "leg_r")
        c(f"leg_{s}", f"foot_{s}", "foot", (-2, 2, -8), (4, 2, 11), share=None if s == "r" else "foot_r")
    p("body", None, (0, 20, 0))
    c("body", "trunk", "bark", (-4, -10, -3), (8, 10, 6))
    p("head", "body", (0, -10, 0))
    c("head", "face", "fur", (-4, -7, -4), (8, 7, 8))
    c("head", "ear_r", "tan", (-5, -5, -1), (1, 2, 2))
    c("head", "ear_l", "tan", (4, -5, -1), (1, 2, 2), share="ear_r")
    c("head", "canopy", "leaf", (-5, -10, -5), (10, 3, 10))
    c("head", "canopy2", "leaf", (-3, -12, -3), (6, 2, 6))
    c("head", "tuft_r", "leaf", (-6, -9, -2), (2, 3, 4))
    c("head", "tuft_l", "leaf", (4, -9, -2), (2, 3, 4), share="tuft_r")
    for s, sg in (("r", -1), ("l", 1)):
        p(f"arm_{s}", "body", (5.5 * sg, -9, 0), rot=(0, 0, -0.3 * sg))
        c(f"arm_{s}", f"arm_{s}", "branch", (-1, 0, -1), (2, 11, 2), share=None if s == "r" else "arm_r")
        c(f"arm_{s}", f"hand_{s}", "tan", (-1.5, 10, -1.5), (3, 2, 3), share=None if s == "r" else "hand_r")
        c(f"arm_{s}", f"twig_{s}", "leaf", (-2, 4, 0), (1, 2, 2), share=None if s == "r" else "twig_r")
    return M, paint


def paint(M):
    img, glow = blank(M)
    mats = {"bark": BARK, "fur": FUR, "tan": TAN, "leaf": LEAF, "branch": BRANCH, "foot": FOOT}
    for c in M.owners():
        g = "vertical" if c["mat"] in ("bark", "branch") else "noise"
        fill(img, c, mats[c["mat"]], grain=g, seed=sum(map(ord, c["id"])))
    # face patch
    f = face(M, "face", "front")
    box(img, f, 0, 0, 8, 7, TAN[0])
    box(img, f, 2, 3, 4, 4, TAN[2])
    eye(img, f, 0, 1, 3, 3, look=(2, 1))
    eye(img, f, 5, 1, 3, 3, look=(0, 1))
    hline(img, f, 0, 2, 0, hexc("3a2414"))
    hline(img, f, 5, 7, 0, hexc("3a2414"))
    dot(img, f, 3, 3, hexc("6a4020"))
    dot(img, f, 4, 3, hexc("6a4020"))
    hline(img, f, 1, 6, 5, hexc("3a1a14"))
    dot(img, f, 0, 4, hexc("3a1a14"))
    dot(img, f, 7, 4, hexc("3a1a14"))
    hline(img, f, 2, 5, 6, hexc("3a1a14"))
    hline(img, f, 2, 5, 5, hexc("f4f0e0"))
    # bark: knots and a hole
    f = face(M, "trunk", "front")
    box(img, f, 2, 3, 4, 4, BARK[0])
    box(img, f, 3, 4, 2, 2, hexc("2e1c0e"))
    for n, (x, y) in (("back", (3, 4)), ("left", (2, 6)), ("right", (3, 2))):
        r = face(M, "trunk", n)
        box(img, r, x, y, 2, 2, BARK[0])
        dot(img, r, x, y, hexc("3a2410"))
    # toes on the big feet
    for s in ("r",):
        f = face(M, "foot_r", "front")
        for x in range(0, 4, 1):
            dot(img, f, x, 0, FOOT[2] if x % 2 else FOOT[0])
        fb = face(M, "foot_r", "bottom")
        box(img, fb, 0, 0, fb[2], fb[3], FOOT[0])
        ft = face(M, "foot_r", "top")
        for x in range(0, 4):
            dot(img, ft, x, 0, hexc("d8b078"))
    return img, glow
