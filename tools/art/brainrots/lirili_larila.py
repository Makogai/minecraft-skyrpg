"""Lirili Larila: an elephant-headed cactus in sandals. Parts: body > (head > (ear_r, ear_l, trunk), arm_r, arm_l, flower), leg_r, leg_l."""
from kit import blank, box, dot, eye, face, fill, hexc, hline, ramp, vline
from cube_model import CubeModel

CACTUS = ramp("2a6a30", "388040", "46984c", "58ac5a")
RIB = ramp("6cc468", "82d878", "96e88a")
GREY = ramp("7a808c", "8c929e", "a0a6b2", "b4bac6")
EAR = ramp("8c929e", "a0a6b2", "b4bac6")
PINKEAR = hexc("e8a8b8")
SANDAL = ramp("5a3a1e", "6e4828", "84602e")
FLOWER = ramp("d82880", "f040a0", "ff70c0")


def build():
    M = CubeModel("lirili_larila")
    p, c = M.part, M.cube
    for s, sg in (("r", -1), ("l", 1)):
        p(f"leg_{s}", None, (2 * sg, 17, 0))
        c(f"leg_{s}", f"leg_{s}", "cactus", (-1.5, 0, -1.5), (3, 5, 3), share=None if s == "r" else "leg_r")
        c(f"leg_{s}", f"sandal_{s}", "sandal", (-2, 5, -3), (4, 2, 6), share=None if s == "r" else "sandal_r")
    p("body", None, (0, 17, 0))
    c("body", "trunk_body", "cactus", (-4, -11, -3), (8, 11, 6))
    c("body", "rib_f", "rib", (-1, -11, -4), (2, 11, 1))
    c("body", "rib_b", "rib", (-1, -11, 3), (2, 11, 1), share="rib_f")
    c("body", "flower", "flower", (-1, -13, -1), (2, 2, 2))
    for s, sg in (("r", -1), ("l", 1)):
        p(f"arm_{s}", "body", (4 * sg, -5, 0))
        pos_h = (0, -1, -1.5) if s == "l" else (-2, -1, -1.5)
        pos_u = (1, -6, -1.5) if s == "l" else (-3, -6, -1.5)
        c(f"arm_{s}", f"armh_{s}", "cactus", pos_h, (2, 2, 3), share=None if s == "r" else "armh_r")
        c(f"arm_{s}", f"armu_{s}", "cactus", pos_u, (2, 5, 3), share=None if s == "r" else "armu_r")
    p("head", "body", (0, -9, -1))
    c("head", "skull", "grey", (-4, -6, -5), (8, 7, 6))
    c("head", "tusk_r", "tusk", (-3.5, 0, -6), (1, 2, 1))
    c("head", "tusk_l", "tusk", (2.5, 0, -6), (1, 2, 1), share="tusk_r")
    for s, sg in (("r", -1), ("l", 1)):
        p(f"ear_{s}", "head", (4 * sg, -3, -2), rot=(0, 0.35 * sg, 0))
        pos = (-5, -3, -0.5) if s == "r" else (0, -3, -0.5)
        c(f"ear_{s}", f"ear_{s}", "ear", (pos[0] - (0 if s == "r" else 0), pos[1], pos[2]) if False else ((-5, -3, -0.5) if s == "r" else (0, -3, -0.5)), (5, 7, 1),
          share=None if s == "r" else "ear_r", mirror=s == "l")
    p("trunk", "head", (0, -3, -5))
    c("trunk", "trunk1", "grey", (-1, -1, -1), (2, 7, 2))
    c("trunk", "trunk2", "grey", (-1, 5, -3), (2, 2, 4))
    return M, paint


def paint(M):
    img, glow = blank(M)
    mats = {"cactus": CACTUS, "rib": RIB, "grey": GREY, "ear": EAR, "sandal": SANDAL, "flower": FLOWER, "tusk": ramp("f0ecd8", "fcf8ec")}
    for c in M.owners():
        g = "vertical" if c["mat"] in ("cactus", "rib") else ("noise" if c["mat"] in ("grey", "ear") else "flat")
        fill(img, c, mats[c["mat"]], grain=g, seed=sum(map(ord, c["id"])))
    # spines on cactus parts
    spine = hexc("f8f4d0")
    for cid in ("trunk_body", "armh_r", "armu_r", "leg_r"):
        for n in ("front", "back", "left", "right"):
            f = face(M, cid, n)
            for y in range(1, f[3], 3):
                for x in range((y // 3) % 2 + 1, f[2], 3):
                    dot(img, f, x, y, spine)
    # ribs: darker line down each rib
    for n in ("front", "back"):
        pass
    for cid in ("rib_f",):
        for n in ("front", "left", "right"):
            f = face(M, cid, n)
            vline(img, f, 0, 0, f[3] - 1, RIB[1])
    # elephant face
    f = face(M, "skull", "front")
    box(img, f, 0, 0, 8, 7, GREY[1])
    eye(img, f, 1, 2, 2, 2, look=(1, 1))
    eye(img, f, 5, 2, 2, 2, look=(0, 1))
    hline(img, f, 1, 2, 1, hexc("4a505c"))
    hline(img, f, 5, 6, 1, hexc("4a505c"))
    for cid in ("ear_r",):
        for n in ("front", "back"):
            fe = face(M, cid, n)
            box(img, fe, 1, 1, fe[2] - 2, fe[3] - 2, PINKEAR)
    for n in ("left", "right"):
        f = face(M, "skull", n)
        box(img, f, 3, 0, 2, 2, GREY[0])
    ft = face(M, "trunk2", "front")
    box(img, ft, 0, 0, 2, 2, GREY[0])
    f = face(M, "trunk1", "front")
    for y in range(1, 7, 2):
        hline(img, f, 0, 1, y, GREY[0])
    # flower petals
    f = face(M, "flower", "top")
    box(img, f, 0, 0, 2, 2, FLOWER[2])
    dot(img, f, 0, 0, hexc("ffe040"))
    # sandal straps
    for n in ("front", "left", "right"):
        f = face(M, "sandal_r", n)
        hline(img, f, 0, f[2] - 1, 0, hexc("c8a060"))
    f = face(M, "sandal_r", "top")
    hline(img, f, 0, 3, 2, hexc("c8a060"))
    hline(img, f, 0, 3, 3, hexc("c8a060"))
    return img, glow
