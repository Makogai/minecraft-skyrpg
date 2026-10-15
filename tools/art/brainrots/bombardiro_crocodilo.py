"""Bombardiro Crocodilo: a crocodile-headed WWII bomber. Parts: body > (head, tail, bombs), wing_r > prop_r, wing_l > prop_l. Flies level, no legs."""
from kit import blank, box, dot, eye, face, fill, hexc, hline, ramp, rings, vline
from cube_model import CubeModel

OLIVE = ramp("4c5a34", "5e6e40", "70824c", "84965c")
GREY = ramp("7c8088", "90949c", "a4a8b0")
CROC = ramp("2c6a28", "3a8434", "4a9c42", "62b454")
BELLY = ramp("c8c488", "dcd8a0")
STEEL = ramp("2c2e34", "40444c", "585c64")
TEETH = hexc("fafaf0")


def build():
    M = CubeModel("bombardiro_crocodilo")
    p, c = M.part, M.cube
    p("body", None, (0, 12, 0))
    c("body", "fuselage", "olive", (-3, -3, -6), (6, 6, 16))
    for i, z in enumerate((-1, 2, 5, 8)):
        c("body", f"bump{i}", "croc", (-1, -4, z), (2, 1, 2))
    c("body", "bomb_r", "steel", (-2.5, 3, -1), (2, 2, 5))
    c("body", "bomb_l", "steel", (0.5, 3, -1), (2, 2, 5), share="bomb_r")
    p("head", "body", (0, 0, -6))
    c("head", "skull", "croc", (-3, -3, -3), (6, 6, 3))
    c("head", "snout", "croc", (-2, -1, -8), (4, 2, 5))
    c("head", "jaw", "belly", (-2, 2, -8), (4, 1, 5))
    c("head", "eye_r", "croc", (-3, -4, -3), (2, 1, 2))
    c("head", "eye_l", "croc", (1, -4, -3), (2, 1, 2), share="eye_r")
    for s, sg in (("r", -1), ("l", 1)):
        p(f"wing_{s}", "body", (3 * sg, 1, 0))
        pos = (-17, -1, -3) if s == "r" else (0, -1, -3)
        c(f"wing_{s}", f"wing_{s}", "grey", pos, (17, 2, 6), share=None if s == "r" else "wing_r", mirror=s == "l")
        nx = -10 if s == "r" else 6
        c(f"wing_{s}", f"nac_{s}", "olive", (nx, -2, -7), (4, 4, 8), share=None if s == "r" else "nac_r")
        p(f"prop_{s}", f"wing_{s}", (nx + 2, 0, -7.5))
        sh = None if s == "r" else "hub_r"
        c(f"prop_{s}", f"hub_{s}", "steel", (-1, -1, -1.5), (2, 2, 2), share=sh)
        c(f"prop_{s}", f"bladeV_{s}", "steel", (-0.5, -5, -0.5), (1, 10, 1), share=None if s == "r" else "bladeV_r")
        c(f"prop_{s}", f"bladeH_{s}", "steel", (-5, -0.5, -0.5), (10, 1, 1), share=None if s == "r" else "bladeH_r")
    p("tail", "body", (0, 0, 10))
    c("tail", "stab", "grey", (-5, 0, -1), (10, 1, 4))
    c("tail", "fin", "olive", (-0.5, -6, 0), (1, 6, 4))
    return M, paint


def paint(M):
    img, glow = blank(M)
    mats = {"olive": OLIVE, "grey": GREY, "croc": CROC, "belly": BELLY, "steel": STEEL}
    for c in M.owners():
        g = "horizontal" if c["id"] in ("fuselage", "nac_r") else "noise"
        fill(img, c, mats[c["mat"]], grain=g, seed=sum(map(ord, c["id"])))
    f = face(M, "fuselage", "bottom")
    box(img, f, 0, 0, f[2], f[3], GREY[1])
    for n in ("left", "right"):
        f = face(M, "fuselage", n)
        box(img, f, 0, 4, f[2], 2, GREY[1])
        for x in range(1, f[2], 3):
            dot(img, f, x, 1, OLIVE[0])
        box(img, f, 4, 1, 3, 2, hexc("8ec8e0"))   # cockpit window
    f = face(M, "fuselage", "top")
    box(img, f, 1, 1, 4, 3, hexc("8ec8e0"))
    hline(img, f, 1, 4, 1, hexc("d8f0fa"))
    # roundels on the wing tops
    for w in ("wing_r", "wing_l"):
        f = face(M, w, "top")
        rings(img, (f[0] + (f[2] - 9 if w == "wing_r" else 4), f[1], 5, 5) if False else (f[0] + (3 if w == "wing_r" else f[2] - 8), f[1] + 1, 5, 5),
              [hexc("c01c1c"), hexc("f8f8f8"), hexc("c01c1c"), hexc("2040a0")]) if False else None
        x0 = 2 if w == "wing_r" else f[2] - 7
        box(img, f, x0, 1, 5, 4, hexc("2040a0"))
        box(img, f, x0 + 1, 2, 3, 2, hexc("f8f8f8"))
        dot(img, f, x0 + 2, 2, hexc("c01c1c"))
        dot(img, f, x0 + 2, 3, hexc("c01c1c"))
    # head: eyes, nostrils, teeth
    for e in ("eye_r", "eye_l"):
        f = face(M, e, "front")
        box(img, f, 0, 0, 2, 1, hexc("f4d020"))
        dot(img, f, 0 if e == "eye_r" else 1, 0, hexc("101010"))
        for n in ("left", "right"):
            fe = face(M, "eye_r", n)
            dot(img, fe, 0, 0, hexc("f4d020"))
            dot(img, fe, 1, 0, hexc("101010"))
    f = face(M, "skull", "front")
    box(img, f, 0, 2, 6, 4, CROC[0])
    f = face(M, "snout", "front")
    box(img, f, 0, 0, 4, 2, hexc("7a1a22"))
    hline(img, f, 0, 3, 1, TEETH)
    dot(img, f, 1, 0, TEETH)
    dot(img, f, 3, 0, TEETH)
    fb = face(M, "snout", "top")
    dot(img, fb, 1, 0, CROC[0])
    dot(img, fb, 2, 0, CROC[0])
    for cid, n in (("snout", "bottom"), ("jaw", "top")):
        ft = face(M, cid, n)
        box(img, ft, 0, 0, ft[2], ft[3], hexc("a82c34"))
        for x in range(0, ft[2], 2):
            hline(img, ft, 0, ft[2] - 1, 0, TEETH) if False else None
        for y in range(0, ft[3], 2):
            hline(img, ft, 0, 0, y, TEETH)
            hline(img, ft, ft[2] - 1, ft[2] - 1, y, TEETH)
    fj = face(M, "jaw", "front")
    hline(img, fj, 0, 3, 0, TEETH)
    # propeller tips
    for s in ("r", "l"):
        for b in ("bladeV", "bladeH"):
            for n in ("front", "right", "left", "top", "bottom"):
                pass
    for s in ("r",):
        f = face(M, "bladeV_r", "front")
        vline(img, f, 0, 0, 1, hexc("e0b020"))
        vline(img, f, 0, 8, 9, hexc("e0b020"))
        f = face(M, "bladeH_r", "front")
        hline(img, f, 0, 1, 0, hexc("e0b020"))
        hline(img, f, 8, 9, 0, hexc("e0b020"))
    # bombs: yellow band
    f = face(M, "bomb_r", "right")
    box(img, f, 2, 0, 1, 2, hexc("d8c020"))
    f = face(M, "bomb_r", "left")
    box(img, f, 2, 0, 1, 2, hexc("d8c020"))
    return img, glow
