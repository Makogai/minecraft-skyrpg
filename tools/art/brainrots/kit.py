"""Small painting kit shared by the brainrot models (tools/art/brainrots/<kind>.py, driven by generate_brainrots.py).

Every model module has ``build() -> (M, paint)``: ``M`` is a cube_model.CubeModel, ``paint(M)`` returns the texture
(and optionally a glow texture, or None) as RGBA images of M.width x M.height, after ``M.pack`` (the driver packs).
These helpers make the usual painting short:

    MATS = {"wood": ramp("84603a", "9c7444", "b48c56", "c8a46e")}       # darkest first
    def paint(M):
        img, glow = blank(M)
        for c in M.owners():
            fill(img, c, MATS[c["mat"]], grain="vertical", seed=hash(c["id"]))
        f = face(M, "trunk", "front")                                    # a face of a cube, as (x, y, w, h)
        eye(img, f, 2, 5)                                                # a 2x2 eye with a pupil, 2 px in, 5 px down
        dot(img, f, 4, 10, hexc("3a2414"))                               # one pixel, face-relative
        return img, glow

Faces: "front" (-z, the side the mob looks to), "back", "top", "bottom", "right" (-x, the mob's right), "left" (+x).
In "front"/"back"/"right"/"left" the first row is the top of the cube. See cube_model.face_rects.
"""
import math
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from cube_model import face_rects, hexc, mix, mul, put, ramp, vnoise  # noqa: E402,F401


def blank(M):
    return Image.new("RGBA", (M.width, M.height), (0, 0, 0, 0)), Image.new("RGBA", (M.width, M.height), (0, 0, 0, 0))


def cube_by_id(M, cid):
    for c in M.cubes:
        if c["id"] == cid:
            return c
    raise KeyError(cid)


def face(M, cid, name):
    """The (x, y, w, h) rectangle of one face of a cube (its owner's, if it shares UVs)."""
    c = cube_by_id(M, cid)
    while c["share"] is not None:
        c = cube_by_id(M, c["share"])
    return face_rects(c)[name]


def fill(img, c, colors, grain="noise", seed=0, scale=3.0, shade=True):
    """Fills every face of a cube with a colour ramp (darkest first) and a texture: "noise" (blotchy), "vertical" or
    "horizontal" (streaks, like wood grain or planks), "flat" (just the middle colour), "speckle" (fine dots).
    With ``shade`` the top is a touch lighter and the bottom darker."""
    rects = face_rects(c)
    n = len(colors)
    for fname, (fx, fy, fw, fh) in rects.items():
        lift = {"top": 0.12, "bottom": -0.18}.get(fname, 0.0) if shade else 0.0
        for y in range(fh):
            for x in range(fw):
                X, Y = fx + x, fy + y
                if grain == "flat":
                    t = 0.55
                elif grain == "vertical":
                    t = 0.5 + 0.45 * (vnoise(X * 1.7, Y * 0.25, scale, seed) - 0.5) * 2
                elif grain == "horizontal":
                    t = 0.5 + 0.45 * (vnoise(X * 0.25, Y * 1.7, scale, seed) - 0.5) * 2
                elif grain == "speckle":
                    t = 0.55 + (0.35 if ((X * 7 + Y * 13 + seed) % 11) == 0 else 0) - (0.3 if ((X * 5 + Y * 3 + seed) % 13) == 0 else 0)
                else:
                    t = vnoise(X, Y, scale, seed)
                t = max(0.0, min(0.999, t + lift))
                col = colors[min(n - 1, int(t * n))]
                put(img, X, Y, col)


def dot(img, rect, x, y, col, a=255):
    """One pixel at (x, y) inside a face rectangle (ignored outside it)."""
    fx, fy, fw, fh = rect
    if 0 <= x < fw and 0 <= y < fh:
        put(img, fx + x, fy + y, col, a)


def box(img, rect, x, y, w, h, col, a=255):
    """A filled rectangle inside a face."""
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            dot(img, rect, xx, yy, col, a)


def hline(img, rect, x0, x1, y, col):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        dot(img, rect, x, y, col)


def vline(img, rect, x, y0, y1, col):
    for y in range(min(y0, y1), max(y0, y1) + 1):
        dot(img, rect, x, y, col)


def eye(img, rect, x, y, w=2, h=2, white=(250, 250, 250), pupil=(20, 20, 24), look=(1, 1)):
    """A cartoon eye: a white w x h block with a 1 px pupil at offset ``look`` inside it."""
    box(img, rect, x, y, w, h, white)
    dot(img, rect, x + min(w - 1, look[0]), y + min(h - 1, look[1]), pupil)


def frame(img, rect, col):
    """A 1 px border around a face (an outline, a rim)."""
    fx, fy, fw, fh = rect
    hline(img, rect, 0, fw - 1, 0, col)
    hline(img, rect, 0, fw - 1, fh - 1, col)
    vline(img, rect, 0, 0, fh - 1, col)
    vline(img, rect, fw - 1, 0, fh - 1, col)


def rings(img, rect, colors, cx=None, cy=None):
    """Concentric rings on a face, like a cut log end (colors darkest first, alternating)."""
    fx, fy, fw, fh = rect
    cx = (fw - 1) / 2 if cx is None else cx
    cy = (fh - 1) / 2 if cy is None else cy
    for y in range(fh):
        for x in range(fw):
            d = math.hypot(x - cx, y - cy)
            dot(img, rect, x, y, colors[int(d) % len(colors)])
