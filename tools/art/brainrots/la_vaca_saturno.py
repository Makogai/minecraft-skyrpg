"""La Vaca Saturno Saturnita: a cow that is also the planet Saturn. Floats; a glowing golden ring spins around it.
Part tree: root > body > (head > (ear_r, ear_l), ring), leg_r, leg_l, leg_br, leg_bl (root parts). ~20 px tall without the ring.
"""
from kit import blank, box, dot, eye, face, fill, hexc, hline, ramp
from cube_model import CubeModel, face_rects, put, vnoise

BANDS = [hexc("6a4a96"), hexc("8a66b8"), hexc("e8d8b0"), hexc("b89ad4"), hexc("f2e6c4"), hexc("7a58a6")]
RING = ramp("b08a30", "d4aa48", "f0cc6c", "fae6a0")
HIDE = ramp("e0dccc", "f4f0e4")
PINK = ramp("d88090", "ee9aa8", "f8b4be")
HORN = ramp("c8c0a0", "ece4c8")
LEG = ramp("d8d4c4", "f0ece0")
INK = hexc("1c1a1e")


def build():
    M = CubeModel("la_vaca_saturno")
    p, c = M.part, M.cube
    for nm, x, z in (("leg_r", -4, -4), ("leg_l", 4, -4), ("leg_br", -4, 4), ("leg_bl", 4, 4)):
        p(nm, None, (x, 16, z))
        c(nm, nm, "leg", (-2, 0, -2), (4, 8, 4), share=None if nm == "leg_r" else "leg_r", mirror=nm.endswith("l"))
    p("body", None, (0, 16, 0))
    c("body", "core_a", "planet", (-7, -11, -6), (14, 10, 12))
    c("body", "core_b", "planet", (-6, -13, -7), (12, 14, 14))
    c("body", "core_c", "planet", (-5, -14, -5), (10, 16, 10))
    p("head", "body", (0, -9, -6))
    c("head", "skull", "hide", (-4, -4, -5), (8, 8, 5))
    c("head", "muzzle", "pink", (-3, 0, -7), (6, 3, 2))
    c("head", "horn_r", "horn", (-5, -6, -3), (2, 3, 2))
    c("head", "horn_l", "horn", (3, -6, -3), (2, 3, 2), share="horn_r", mirror=True)
    p("ear_r", "head", (-4, -2, -2))
    c("ear_r", "ear_r", "hide", (-3, -1, -1), (3, 2, 1))
    p("ear_l", "head", (4, -2, -2))
    c("ear_l", "ear_l", "hide", (0, -1, -1), (3, 2, 1), share="ear_r", mirror=True)
    p("ring", "body", (0, -2, 0))
    c("ring", "ring_f", "ring", (-15, 0, -15), (30, 1, 2))
    c("ring", "ring_b", "ring", (-15, 0, 13), (30, 1, 2), share="ring_f")
    c("ring", "ring_r", "ring", (-15, 0, -13), (2, 1, 26))
    c("ring", "ring_l", "ring", (13, 0, -13), (2, 1, 26), share="ring_r")
    return M, paint


def planet(img, c, seed):
    for fname, (fx, fy, fw, fh) in face_rects(c).items():
        for y in range(fh):
            for x in range(fw):
                X, Y = fx + x, fy + y
                band = BANDS[((Y + (1 if x % 5 == 0 else 0)) // 3 + seed) % len(BANDS)]
                n = vnoise(X, Y, 3.0, seed + 7)
                col = band
                if n > 0.7:
                    col = hexc("f4f4f0") if n < 0.76 else INK
                if fname == "top":
                    col = BANDS[3] if n < 0.7 else INK
                put(img, X, Y, col)


def paint(M):
    img, glow = blank(M)
    mats = {"leg": LEG, "hide": HIDE, "pink": PINK, "horn": HORN}
    for cu in M.owners():
        if cu["mat"] == "planet":
            planet(img, cu, sum(map(ord, cu["id"])) % 5)
        elif cu["mat"] == "ring":
            fill(img, cu, RING, grain="horizontal", seed=3)
            for fname, (fx, fy, fw, fh) in face_rects(cu).items():
                for y in range(fh):
                    for x in range(fw):
                        if (x + y) % 3 == 0:
                            put(glow, fx + x, fy + y, hexc("f4d890"), 110)
        else:
            fill(img, cu, mats[cu["mat"]], grain="noise", seed=sum(map(ord, cu["id"])))
    # hooves
    for nm in ("front", "right", "left", "back"):
        box(img, face(M, "leg_r", nm), 0, 6, 4, 2, hexc("2a2024"))
    box(img, face(M, "leg_r", "bottom"), 0, 0, 4, 4, hexc("2a2024"))
    # face
    f = face(M, "skull", "front")
    eye(img, f, 1, 2, w=2, h=3, look=(1, 1))
    eye(img, f, 5, 2, w=2, h=3, look=(0, 1))
    hline(img, f, 1, 2, 1, INK)
    hline(img, f, 5, 6, 1, INK)
    f = face(M, "muzzle", "front")
    dot(img, f, 1, 1, hexc("5a2430"))
    dot(img, f, 4, 1, hexc("5a2430"))
    hline(img, f, 1, 4, 2, hexc("c06070"))
    # spots on the head, pink inner ears
    box(img, face(M, "skull", "top"), 0, 1, 3, 3, INK)
    box(img, face(M, "skull", "left"), 1, 2, 2, 3, INK)
    box(img, face(M, "ear_r", "front"), 0, 0, 3, 2, hexc("e090a0"))
    return img, glow
