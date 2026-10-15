"""Boneca Ambalabu: a frog head on a car tire with human legs.
Part tree: root > body (tire) > (head > (eye_r, eye_l), hub_r, hub_l), leg_r, leg_l (root). ~24 px tall.
"""
from kit import blank, box, dot, face, fill, hexc, hline, ramp
from cube_model import CubeModel

FROG = ramp("2e7a2c", "3e9a38", "56b848", "78d060")
BELLY = ramp("b8e08c", "d4f0a8")
RUBBER = ramp("101012", "1a1a1e", "26262c")
HUB = ramp("6a6e78", "8a8e98", "aab0ba")
SKIN = ramp("d8a070", "e8b484", "f4c698")
SHOE = ramp("d8d8e0", "f4f4f8")
INK = hexc("14141a")


def build():
    M = CubeModel("boneca_ambalabu")
    p, c = M.part, M.cube
    for side, sg in (("r", 1), ("l", -1)):
        p(f"leg_{side}", None, (-3 * sg, 19, 0))
        c(f"leg_{side}", f"skin_{side}", "skin", (-2, 0, -2), (4, 3, 4), share="skin_r" if side == "l" else None, mirror=side == "l")
        c(f"leg_{side}", f"shoe_{side}", "shoe", (-2, 3, -3), (4, 2, 6), share="shoe_r" if side == "l" else None, mirror=side == "l")
    p("body", None, (0, 19, 0))
    c("body", "tire", "rubber", (-5, -10, -6), (10, 10, 12))
    c("body", "tread_h", "rubber", (-4, -11, -5), (8, 12, 10))
    p("hub_r", "body", (-5, -5, 0))
    c("hub_r", "hub_r", "hub", (-1, -3, -3), (1, 6, 6))
    p("hub_l", "body", (5, -5, 0))
    c("hub_l", "hub_l", "hub", (0, -3, -3), (1, 6, 6), share="hub_r", mirror=True)
    p("head", "body", (0, -10, 0))
    c("head", "skull", "frog", (-6, -7, -5), (12, 7, 10))
    c("head", "belly", "belly", (-5, -1, -6), (10, 1, 1))
    p("eye_r", "head", (-3, -7, -2))
    c("eye_r", "eye_r", "frog", (-2, -3, -2), (4, 3, 4))
    p("eye_l", "head", (3, -7, -2))
    c("eye_l", "eye_l", "frog", (-2, -3, -2), (4, 3, 4), share="eye_r", mirror=True)
    return M, paint


def paint(M):
    img, glow = blank(M)
    mats = {"frog": FROG, "belly": BELLY, "rubber": RUBBER, "hub": HUB, "skin": SKIN, "shoe": SHOE}
    for cu in M.owners():
        g = "speckle" if cu["mat"] == "frog" else "flat" if cu["mat"] in ("shoe", "belly", "hub") else "noise"
        fill(img, cu, mats[cu["mat"]], grain=g, seed=sum(map(ord, cu["id"])))
    # tire tread: blocky pattern on the outer faces
    for cid in ("tire", "tread_h"):
        for fname in ("front", "back", "top", "bottom"):
            f = face(M, cid, fname)
            for y in range(f[3]):
                for x in range(f[2]):
                    if (x // 2 + y // 2) % 2 == 0:
                        dot(img, f, x, y, hexc("2e2e36"))
    for r in ("right", "left"):
        f = face(M, "tire", r)
        for y in range(f[3]):
            for x in range(f[2]):
                if x in (0, f[2] - 1) or y in (0, f[3] - 1):
                    dot(img, f, x, y, hexc("0a0a0c"))
    # hubs: bolts
    for h in ("hub_r", "hub_l"):
        for nm in ("right", "left"):
            f = face(M, h, nm)
            for (x, y) in ((1, 1), (4, 1), (1, 4), (4, 4)):
                dot(img, f, x, y, hexc("4a4e58"))
            box(img, f, 2, 2, 2, 2, hexc("c8ced8"))
    # frog face
    f = face(M, "skull", "front")
    hline(img, f, 1, 10, 4, INK)
    hline(img, f, 2, 9, 5, hexc("a02838"))
    dot(img, f, 0, 3, INK)
    dot(img, f, 11, 3, INK)
    hline(img, f, 3, 8, 6, hexc("c04858"))
    dot(img, f, 5, 1, hexc("1e5a1e"))
    dot(img, f, 6, 1, hexc("1e5a1e"))
    for e in ("eye_r", "eye_l"):
        f = face(M, e, "front")
        box(img, f, 0, 0, 4, 3, hexc("f8f8e8"))
        box(img, f, 1, 0, 2, 2, hexc("e8c828"))
        box(img, f, 1, 1, 2, 2, INK)
        dot(img, f, 1, 1, hexc("f8f8f8"))
        for s in ("right", "left", "back"):
            box(img, face(M, e, s), 0, 0, 4, 1, hexc("e8c828"))
    # spots on the frog's back
    for (x, y) in ((2, 1), (7, 2), (4, 3)):
        box(img, face(M, "skull", "back"), x, y, 2, 1, hexc("2a6a28"))
    # sneaker toe cap and laces
    f = face(M, "shoe_r", "front")
    box(img, f, 0, 0, 4, 1, hexc("ffffff"))
    dot(img, f, 1, 1, hexc("d04050"))
    dot(img, f, 2, 1, hexc("d04050"))
    box(img, face(M, "shoe_r", "bottom"), 0, 0, 4, 6, hexc("5a5a68"))
    return img, glow
