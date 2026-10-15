"""Frigo Camelo: a camel whose body is a white refrigerator; camel neck/head, hump, four booted legs."""
from kit import blank, box, dot, eye, face, fill, hexc, hline, ramp, vline
from cube_model import CubeModel

FRIDGE = ramp("c4cad2", "d8dde4", "e8ecf0", "f6f8fa")
CAMEL = ramp("a07840", "b48c52", "c8a066", "d8b47a")
MUZZLE = ramp("c8a470", "dcb884", "ecca98")
BOOT = ramp("3c2414", "4e301c", "603c24")
HANDLE = ramp("8a9098", "a8aeb6", "c8ced6")
LIP = ramp("c07860", "d89078")


def build():
    M = CubeModel("frigo_camelo")
    p, c = M.part, M.cube
    for name, x, z in (("leg_r", -3, -3), ("leg_l", 3, -3), ("leg_br", -3, 2), ("leg_bl", 3, 2)):
        p(name, None, (x, 16, z))
        c(name, name, "camel", (-1.5, 0, -1.5), (3, 5, 3), share=None)
        c(name, name + "_boot", "boot", (-2, 5, -2), (4, 3, 4), share=None)
    p("body", None, (0, 16, 0))
    c("body", "fridge", "fridge", (-5, -14, -5), (10, 14, 9))
    c("body", "hump", "camel", (-3, -18, 0), (6, 4, 5))
    p("neck", "body", (0, -14, -2))
    c("neck", "neck", "camel", (-2, -6, -2), (4, 6, 4))
    p("head", "neck", (0, -6, 0))
    c("head", "skull", "camel", (-2, -4, -2), (4, 4, 4))
    c("head", "snout", "muzzle", (-2, -3, -5), (4, 3, 3))
    c("head", "lips", "lip", (-2, -1, -6), (4, 1, 1))
    c("head", "ear_r", "camel", (-3, -4, 0), (1, 2, 1))
    c("head", "ear_l", "camel", (2, -4, 0), (1, 2, 1))
    return M, paint


def paint(M):
    img, glow = blank(M)
    mats = {"camel": CAMEL, "fridge": FRIDGE, "muzzle": MUZZLE, "boot": BOOT, "lip": LIP}
    grains = {"camel": "speckle", "fridge": "flat", "muzzle": "flat", "boot": "vertical", "lip": "flat"}
    for c in M.owners():
        fill(img, c, mats[c["mat"]], grain=grains[c["mat"]], seed=sum(map(ord, c["id"])))
    f = face(M, "fridge", "front")
    seam = hexc("8a929c")
    hline(img, f, 0, 9, 5, seam); hline(img, f, 0, 9, 6, hexc("b4bac4"))   # freezer door / fridge door split
    for y in range(14):
        dot(img, f, 0, y, hexc("c4cad2")); dot(img, f, 9, y, hexc("c4cad2"))
    vline(img, f, 8, 1, 4, hexc("8a9098")); vline(img, f, 8, 8, 12, hexc("8a9098"))   # handles
    vline(img, f, 7, 1, 4, hexc("c8ced6")); vline(img, f, 7, 8, 12, hexc("c8ced6"))
    box(img, f, 2, 2, 3, 1, hexc("38a0e0"))   # tiny display
    dot(img, f, 3, 2, hexc("c8f0ff"))
    # Fridge sides get a faint seam, the top is dull.
    for s in ("right", "left"):
        hline(img, face(M, "fridge", s), 0, 8, 5, seam)
    # Camel face: sleepy half-closed eyes, big lips, nostrils.
    f = face(M, "skull", "front")
    for x in (0, 2):
        box(img, f, x, 1, 2, 1, hexc("f4f0e0")); dot(img, f, x + 1, 1, hexc("1c1410"))
        hline(img, f, x, x + 1, 0, hexc("5a3a1c"))
    f = face(M, "snout", "front")
    dot(img, f, 1, 0, hexc("5a3a1c")); dot(img, f, 2, 0, hexc("5a3a1c"))
    hline(img, f, 0, 3, 2, hexc("c07860"))
    hline(img, face(M, "lips", "front"), 0, 3, 0, hexc("e8a088"))
    hline(img, face(M, "lips", "front"), 1, 2, 0, hexc("a04838"))
    return img, glow
