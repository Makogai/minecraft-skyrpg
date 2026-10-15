"""Tralalero Tralala: a blue-grey shark on three human legs in blue sneakers. Parts: body > (head > (snout, jaw), fin, tail), leg_r, leg_l, leg_m."""
from kit import blank, box, dot, eye, face, fill, hexc, hline, ramp, vline
from cube_model import CubeModel

SHARK = ramp("4a6a8c", "5a7ea4", "6c92b8", "82a8c8")
BELLY = ramp("d8e0e8", "e8eef4", "f4f8fa")
SKIN = ramp("d8a078", "e4b088", "f0c098")
SHOE = ramp("1c4cc0", "2a64e0", "3c7cf0")
SOLE = ramp("d8d8e0", "f0f0f4")
INSIDE = ramp("8a1c24", "a82c34")


def build():
    M = CubeModel("tralalero_tralala")
    p, c = M.part, M.cube
    for name, pos in (("leg_r", (-2.5, 15, -3)), ("leg_l", (2.5, 15, -3)), ("leg_m", (0, 15, 5))):
        p(name, None, pos)
        sh = None if name == "leg_r" else "leg_r"
        c(name, name, "skin", (-1.5, 0, -1.5), (3, 6, 3), share=sh)
        c(name, name + "_shoe", "shoe", (-2, 6, -4), (4, 3, 6), share=None if sh is None else "leg_r_shoe")
        c(name, name + "_sole", "sole", (-2, 8, -4), (4, 1, 6), share=None if sh is None else "leg_r_sole")
    p("body", None, (0, 15, 0))
    c("body", "torso", "shark", (-4, -7, -7), (8, 7, 14))
    p("head", "body", (0, 0, -7))
    c("head", "skull", "shark", (-4, -7, -4), (8, 7, 4))
    c("head", "snout", "shark", (-3, -5, -8), (6, 2, 4))
    c("head", "jaw", "belly", (-3, -2, -8), (6, 2, 4))
    p("fin", "body", (0, -7, 0))
    c("fin", "fin", "shark", (-0.5, -5, -2), (1, 5, 5))
    p("tail", "body", (0, -3.5, 7))
    c("tail", "stub", "shark", (-2, -2.5, 0), (4, 5, 3))
    c("tail", "tailfin", "shark", (-0.5, -5.5, 2), (1, 11, 4))
    return M, paint


def paint(M):
    img, glow = blank(M)
    mats = {"skin": SKIN, "shoe": SHOE, "sole": SOLE, "shark": SHARK, "belly": BELLY, "inside": INSIDE}
    for c in M.owners():
        fill(img, c, mats[c["mat"]], grain="noise" if c["mat"] in ("shark", "skin") else "flat", seed=sum(map(ord, c["id"])))
    # white belly on the lower rows / bottom of the shark
    for cid in ("torso", "skull", "tailfin", "stub"):
        for n in ("front", "back", "left", "right"):
            f = face(M, cid, n)
            if cid in ("torso", "skull"):
                box(img, f, 0, f[3] - 2, f[2], 2, BELLY[1])
        f = face(M, "torso" if cid == "torso" else cid, "bottom")
        box(img, f, 0, 0, f[2], f[3], BELLY[1])
    # mouth: dark gap on the skull front, rows of teeth
    f = face(M, "skull", "front")
    box(img, f, 1, 2, 6, 5, hexc("7a1a22"))
    eye(img, f, 0, 0, 2, 2, look=(1, 1))
    eye(img, f, 6, 0, 2, 2, look=(0, 1))
    hline(img, f, 0, 1, 0, hexc("2a3c58"))
    hline(img, f, 6, 7, 0, hexc("2a3c58"))
    fs = face(M, "snout", "front")
    hline(img, fs, 0, 5, 1, hexc("fafafa"))
    for x in range(1, 6, 2):
        dot(img, fs, x, 1, hexc("d8dce4"))
    for cid, n in (("snout", "bottom"), ("jaw", "top")):
        ft = face(M, cid, n)
        box(img, ft, 0, 0, ft[2], ft[3], hexc("8a1c24"))
        for x in range(0, ft[2], 2):
            vline(img, ft, x, 0, ft[3] - 1, hexc("fafafa"))
    fj = face(M, "jaw", "front")
    hline(img, fj, 0, 5, 0, hexc("fafafa"))
    for x in range(0, 6, 2):
        dot(img, fj, x, 1, hexc("e0e4ea"))
    # eyes on the sides of the head
    for n, x in (("right", 0), ("left", 0)):
        f = face(M, "skull", n)
        eye(img, f, 1, 1, 2, 2, look=(1, 1) if n == "left" else (0, 1))
    # swoosh on every shoe
    for leg in ("leg_r",):
        for n in ("right", "left"):
            f = face(M, leg + "_shoe", n)
            hline(img, f, 1, 4, 2, hexc("ffffff"))
            hline(img, f, 2, 3, 1, hexc("ffffff"))
            dot(img, f, 4, 0, hexc("ffffff"))
        f = face(M, leg + "_shoe", "front")
        hline(img, f, 0, 2, 0, hexc("ffffff"))
    return img, glow
