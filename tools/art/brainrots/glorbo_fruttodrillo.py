"""Glorbo Fruttodrillo: a watermelon crocodile. Low and long, ~11 px tall, ~28 px long.
Part tree: root > body > (head > (eye_r, eye_l), tail > tail_tip), leg_r, leg_l, leg_br, leg_bl (root).
"""
from kit import blank, box, dot, face, fill, hexc, hline, ramp
from cube_model import CubeModel, face_rects, put, vnoise

RIND = ramp("1c5a24", "2a7a30", "3a9a40")
STRIPE = ramp("4ab04a", "62c85a")
FLESH = ramp("e04848", "f06058", "f88070")
CROC = ramp("3a7a2a", "4a9632", "62b040")
BELLY = ramp("c8d890", "e4eea8")
TOOTH = hexc("f8f4e8")
INK = hexc("14141a")


def build():
    M = CubeModel("glorbo_fruttodrillo")
    p, c = M.part, M.cube
    for nm, x, z in (("leg_r", -5, -6), ("leg_l", 5, -6), ("leg_br", -5, 5), ("leg_bl", 5, 5)):
        p(nm, None, (x, 18, z))
        c(nm, nm, "croc", (-2, 0, -2), (4, 6, 4), share=None if nm == "leg_r" else "leg_r", mirror=nm.endswith("l"))
    p("body", None, (0, 18, 0))
    c("body", "melon", "rind", (-5, -8, -9), (10, 8, 18))
    c("body", "melon_top", "rind", (-4, -9, -7), (8, 1, 14))
    p("head", "body", (0, -3, -9))
    c("head", "skull", "croc", (-3, -3, -5), (6, 5, 5))
    c("head", "snout", "croc", (-2, -1, -10), (4, 2, 5))
    c("head", "jaw", "belly", (-2, 1, -10), (4, 1, 5))
    p("eye_r", "head", (-2, -3, -3))
    c("eye_r", "eye_r", "croc", (-1, -2, -1), (2, 2, 2))
    p("eye_l", "head", (2, -3, -3))
    c("eye_l", "eye_l", "croc", (-1, -2, -1), (2, 2, 2), share="eye_r", mirror=True)
    p("tail", "body", (0, -3, 9))
    c("tail", "tail1", "rind", (-3, -3, 0), (6, 5, 6))
    p("tail_tip", "tail", (0, 0, 6))
    c("tail_tip", "tail2", "rind", (-2, -3, 0), (4, 3, 7))
    return M, paint


def stripes(img, cu, seed):
    for fname, (fx, fy, fw, fh) in face_rects(cu).items():
        for y in range(fh):
            for x in range(fw):
                X, Y = fx + x, fy + y
                if fname in ("top", "bottom"):
                    k = (x + (1 if (y // 3) % 2 else 0)) // 2
                elif fname in ("front", "back"):
                    k = x // 2
                else:
                    k = (x + (y // 4)) // 2
                n = vnoise(X, Y, 2.5, seed)
                if k % 3 == 0:
                    col = STRIPE[1] if n > 0.55 else STRIPE[0]
                else:
                    col = RIND[min(2, int(n * 3))]
                put(img, X, Y, col)


def paint(M):
    img, glow = blank(M)
    for cu in M.owners():
        s = sum(map(ord, cu["id"]))
        if cu["mat"] == "rind":
            stripes(img, cu, s)
        elif cu["mat"] == "belly":
            fill(img, cu, BELLY, grain="flat")
        else:
            fill(img, cu, CROC, grain="speckle", seed=s)
    # the flesh slice on top, with a pale rind border and seeds
    f = face(M, "melon_top", "top")
    for y in range(f[3]):
        for x in range(f[2]):
            edge = x in (0, f[2] - 1) or y in (0, f[3] - 1)
            dot(img, f, x, y, hexc("e8f0c8") if edge else FLESH[(x + y) % 2 + (1 if (x * 3 + y) % 7 == 0 else 0)])
    for (x, y) in ((2, 2), (5, 3), (3, 5), (6, 7), (2, 9), (5, 10), (3, 12), (1, 6)):
        dot(img, f, x, y, hexc("14100e"))
        dot(img, f, x, y + 1, hexc("14100e"))
    for s in ("right", "left", "front", "back"):
        f = face(M, "melon_top", s)
        box(img, f, 0, 0, f[2], 1, hexc("f06058"))
    # croc head
    for e in ("eye_r", "eye_l"):
        f = face(M, e, "front")
        box(img, f, 0, 0, 2, 2, hexc("f8d820"))
        dot(img, f, 0, 0, INK)
        dot(img, f, 0, 1, INK)
    f = face(M, "snout", "front")
    dot(img, f, 0, 0, INK)
    dot(img, f, 3, 0, INK)
    for nm in ("right", "left"):
        f = face(M, "snout", nm)
        hline(img, f, 0, 4, 1, INK)
        for x in (0, 2, 4):
            dot(img, f, x, 1, TOOTH)
        f = face(M, "jaw", nm)
        for x in (1, 3):
            dot(img, f, x, 0, TOOTH)
    f = face(M, "skull", "front")
    hline(img, f, 0, 5, 3, hexc("2e6a22"))
    # claws
    for nm in ("front", "right", "left", "back"):
        hline(img, face(M, "leg_r", nm), 0, 3, 5, hexc("e8e0c0"))
    return img, glow
