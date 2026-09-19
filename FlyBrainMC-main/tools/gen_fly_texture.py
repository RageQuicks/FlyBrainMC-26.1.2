#!/usr/bin/env python3
"""Generate the fruit-fly entity textures (64x64 RGBA) for FlyModel.

Writes
    src/main/resources/assets/fruitfly/textures/entity/fruit_fly.png          (male: black abdomen tip)
    src/main/resources/assets/fruitfly/textures/entity/fruit_fly_female.png   (female: banded tan tip)

The atlas layout MUST match the texOffs/addBox calls in
src/client/java/com/fruitfly/client/render/FlyModel.java (also documented in its class Javadoc):

    part         texOffs  box (x,y,z, w,h,d)                footprint (x0..x1, y0..y1)
    thorax       ( 0, 0)  (-3,-3,-3, 6,6,6)                 0..23,  0..11
    head         (24, 0)  (-2,-2,-4, 4,4,4)                 24..39, 0..7
    eye          (40, 0)  ( 2,-1.5,-3.5, 1,3,3) +0.2        40..47, 0..5    (right eye shares, .mirror())
    proboscis    (48, 0)  (-0.5,0,-0.5, 1,3,1)              48..51, 0..3
    labellum     (48, 4)  (-1,0,-1, 2,1,2)                  48..55, 4..6
    antenna      (56, 0)  (-0.5,-0.5,-1, 1,1,1)             56..59, 0..1
    arista       (60, 0)  ( 0,-2,-0.5, 0,2,1) +0.001        60..61, 0..2    (only the two 1x2 side faces exist)
    abdomen      ( 0,12)  (-2.5,-2.5,0, 5,5,8)              0..25,  12..24
    abdomen_tip  (26,12)  (-1.5,-1.5,8, 3,3,2)              26..35, 12..16
    haltere      (36,12)  ( 0,-0.5,-0.5, 2,1,1)             36..41, 12..13
    femur        (42,12)  ( 0,-0.5,-0.5, 4,1,1)             42..51, 12..13
    tibia        (42,14)  same                              42..51, 14..15
    tarsus       (42,16)  same                              42..51, 16..17
    wing         ( 0,26)  (-0.5,0,0, 4,0,14) +0.001         0..35,  26..39  (top face (14,26) 4x14, bottom (18,26) 4x14)
    free                                                    52..63 x 12..25, rows 40..63

Face rectangles for texOffs(u,v) and size (w,h,d), and the model cell each texel shows
(derived from ModelPart$Cube's polygon UV order in 1.21.1; verified against the player-skin head layout):

    face            rect (x, y, width, height)   texel (i,j) -> model cell (ix, iy, iz)
    top    (DOWN)   (u+d,      v,   w, d)        ix=i,      iy=0,    iz=d-1-j
    bottom (UP)     (u+d+w,    v,   w, d)        ix=i,      iy=h-1,  iz=d-1-j
    right  (WEST)   (u,        v+d, d, h)        ix=0,      iy=j,    iz=d-1-i
    front  (NORTH)  (u+d,      v+d, w, h)        ix=i,      iy=j,    iz=0
    left   (EAST)   (u+d+w,    v+d, d, h)        ix=w-1,    iy=j,    iz=i
    back   (SOUTH)  (u+2d+w,   v+d, w, h)        ix=w-1-i,  iy=j,    iz=d-1

    ix grows toward the fly's LEFT (+X), iy grows DOWN, iz grows toward the TAIL (+Z); iz=0 is the front.
    .mirror() flips U on every face, so one painted region serves both sides of a symmetric part.

Alpha: the entity shaders discard texels with alpha < 0.1, so unused texels are fully transparent and the wing
membrane stays >= 100/255 (ghost strokes multiply it by 80/255 and must remain above 0.1).

Usage:  python tools/gen_fly_texture.py            (from the project root; pip install pillow if needed)
"""
import os
import random
import sys

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("Pillow is required: pip install pillow")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "fruitfly", "textures", "entity")
W = H = 64

# ----------------------------------------------------------------------------------------------------------- palette
TAN = (0xC8, 0xA1, 0x65)          # abdomen / body base (#C8A165)
SHADE = (0x8B, 0x5A, 0x2B)        # dark shading (#8B5A2B)
THORAX = (0xB8, 0x90, 0x5A)
HEAD = (0xC3, 0x9A, 0x62)
BAND = (0x2A, 0x1E, 0x14)         # transverse tergite bands
TIP = (0x1A, 0x14, 0x10)          # male A5/A6 + genital arch
BRISTLE = (0x5A, 0x3E, 0x24)
EYE = (0xB2, 0x22, 0x22)          # brick red (#B22222)
EYE_DARK = (0x7A, 0x1E, 0x12)     # facet shadow
EYE_HI = (0xE0, 0x4A, 0x2C)       # specular facets
PROBOSCIS = (0xA9, 0x85, 0x55)
LABELLUM = (0x8B, 0x6A, 0x3E)
ANTENNA = (0xA6, 0x7F, 0x4E)
ARISTA = (0x3A, 0x2A, 0x1A)
HALTERE = (0xC9, 0xA2, 0x6B)
KNOB = (0x8B, 0x6A, 0x3E)
FEMUR = (0x6E, 0x52, 0x36)
TIBIA = (0x55, 0x3E, 0x2A)
TARSUS = (0x42, 0x30, 0x20)
JOINT = (0x2E, 0x22, 0x18)
CLAW = (0x1A, 0x14, 0x10)
MEMBRANE = (0xDC, 0xE8, 0xF0)     # glassy blue-white
VEIN = (0x6E, 0x5A, 0x3C)
MEMBRANE_ALPHA = 110
VEIN_ALPHA = 200

# per-face light multipliers (light from above and slightly in front)
FACE_LIGHT = {"top": 1.12, "bottom": 0.72, "right": 0.93, "left": 0.93, "front": 1.0, "back": 0.84}

rng = random.Random(1234)


def clamp8(v):
    return max(0, min(255, int(round(v))))


def mul(rgb, f):
    return tuple(clamp8(c * f) for c in rgb)


def rgba(rgb, a=255):
    return (rgb[0], rgb[1], rgb[2], a)


def noisy(rgb, amount=0.04):
    f = 1.0 + rng.uniform(-amount, amount)
    return mul(rgb, f)


class Atlas:
    def __init__(self):
        self.img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        self.px = self.img.load()
        self.used = set()

    def put(self, x, y, c):
        if not (0 <= x < W and 0 <= y < H):
            raise ValueError(f"texel out of atlas: ({x},{y})")
        if (x, y) in self.used:
            raise ValueError(f"atlas overlap at ({x},{y})")
        self.used.add((x, y))
        self.px[x, y] = c


def faces(u, v, w, h, d):
    """Face name -> (rect x, y, width, height, cell(i, j) -> (ix, iy, iz))."""
    return {
        "top": (u + d, v, w, d, lambda i, j: (i, 0, d - 1 - j)),
        "bottom": (u + d + w, v, w, d, lambda i, j: (i, h - 1, d - 1 - j)),
        "right": (u, v + d, d, h, lambda i, j: (0, j, d - 1 - i)),
        "front": (u + d, v + d, w, h, lambda i, j: (i, j, 0)),
        "left": (u + d + w, v + d, d, h, lambda i, j: (w - 1, j, i)),
        "back": (u + 2 * d + w, v + d, w, h, lambda i, j: (w - 1 - i, j, d - 1)),
    }


def paint_box(atlas, u, v, w, h, d, shader):
    """shader(face, ix, iy, iz, light) -> RGBA (or None to leave transparent)."""
    for face, (x, y, fw, fh, cell) in faces(u, v, w, h, d).items():
        if fw <= 0 or fh <= 0:
            continue
        for j in range(fh):
            for i in range(fw):
                ix, iy, iz = cell(i, j)
                c = shader(face, ix, iy, iz, FACE_LIGHT[face])
                if c is not None:
                    atlas.put(x + i, y + j, c)


# ----------------------------------------------------------------------------------------------------------- shaders
def thorax_shader(face, ix, iy, iz, light):
    base = THORAX
    if face == "top":
        # dorsal bristle sockets + darker scutellum at the rear, lighter midline
        if (ix, iz) in {(1, 1), (4, 1), (1, 3), (4, 3), (2, 4), (3, 4)}:
            return rgba(BRISTLE)
        if iz == 5:
            return rgba(noisy(mul(base, 0.9)))
        if ix in (2, 3):
            light *= 1.04
    elif face in ("left", "right"):
        if iy >= 4:
            light *= 0.86          # pleura darker toward the legs
        if iy == 0:
            light *= 1.05
    elif face == "bottom":
        if 1 <= ix <= 4 and 1 <= iz <= 4:
            return rgba(noisy(mul(SHADE, 0.9)))
    elif face == "front":
        light *= 0.92              # tucked behind the head
    return rgba(noisy(mul(base, light)))


def head_shader(face, ix, iy, iz, light):
    base = HEAD
    if face == "front":
        if iy == 0 and ix in (1, 2):
            return rgba(BRISTLE)   # ocelli
        if iy == 3:
            light *= 0.85          # clypeus / mouthparts
        if iy == 1 and ix in (1, 2):
            light *= 1.05
    elif face in ("left", "right"):
        light *= 0.9               # mostly hidden by the eyes
    elif face == "bottom":
        light *= 0.9
    return rgba(noisy(mul(base, light)))


def eye_shader(face, ix, iy, iz, light):
    checker = (ix + iy + iz) % 2 == 0
    if face in ("left", "right"):
        if iy == 0 and iz == 1:
            return rgba(EYE_HI)    # specular highlight, upper front
        return rgba(EYE if checker else EYE_DARK)
    if face == "top":
        return rgba(mul(EYE if checker else EYE_HI, 1.02))
    if face == "bottom":
        return rgba(EYE_DARK)
    return rgba(EYE if checker else EYE_DARK)


def proboscis_shader(face, ix, iy, iz, light):
    if iy == 0:
        light *= 0.9               # rostrum joint
    return rgba(noisy(mul(PROBOSCIS, light)))


def labellum_shader(face, ix, iy, iz, light):
    if face == "bottom":
        return rgba(mul(LABELLUM, 0.8))
    return rgba(noisy(mul(LABELLUM, light)))


def antenna_shader(face, ix, iy, iz, light):
    return rgba(noisy(mul(ANTENNA, light)))


def arista_shader(face, ix, iy, iz, light):
    return rgba(ARISTA, 230 if iy == 0 else 255)


def make_abdomen_shader(male):
    def shader(face, ix, iy, iz, light):
        base = TAN
        if face == "bottom":
            # pale ventral sternites, no bands
            if male and iz == 7:
                return rgba(TIP)
            return rgba(noisy(mul(base, 0.86)))
        if male and iz == 7:
            return rgba(noisy(TIP, 0.03))          # A5 fully dark
        if iz in (2, 4, 6):
            return rgba(noisy(BAND, 0.05))         # posterior tergite bands A2..A4
        if face == "top":
            if ix in (1, 2, 3):
                light *= 1.03
            if iz == 0:
                light *= 0.95                      # A1 slightly darker under the wing hinge
        elif face in ("left", "right"):
            if iy >= 3:
                light *= 0.9
        elif face == "back":
            light *= 0.9
        return rgba(noisy(mul(base, light)))
    return shader


def make_tip_shader(male):
    def shader(face, ix, iy, iz, light):
        if male:
            return rgba(noisy(mul(TIP, light * 0.9 + 0.1), 0.03))
        if face == "bottom":
            return rgba(noisy(mul(TAN, 0.86)))
        if iz == 1 or face == "back":
            return rgba(noisy(BAND, 0.05))         # posterior band + ovipositor
        return rgba(noisy(mul(TAN, light)))
    return shader


def haltere_shader(face, ix, iy, iz, light):
    return rgba(mul(KNOB if ix == 1 else HALTERE, light))


def make_leg_shader(color, claw):
    def shader(face, ix, iy, iz, light):
        if ix == 0:
            return rgba(mul(JOINT, light))          # joint
        if claw and ix == 3:
            return rgba(CLAW)
        return rgba(noisy(mul(color, light), 0.03))
    return shader


# --------------------------------------------------------------------------------------------------------------- wing
# Rows r = 0 (tip) .. 13 (root); columns c = 0 (trailing/medial edge) .. 3 (leading/lateral edge, costa).
WING_SHAPE = {
    0: (1, 2),
    1: (1, 3),
    2: (0, 3), 3: (0, 3), 4: (0, 3), 5: (0, 3), 6: (0, 3), 7: (0, 3), 8: (0, 3), 9: (0, 3), 10: (0, 3),
    11: (1, 3),
    12: (2, 3),
    13: (2, 3),
}


def paint_wing(atlas, u, v):
    w, d = 4, 14
    for x0 in (u + d, u + d + w):          # top face, then bottom face
        for r in range(d):
            lo, hi = WING_SHAPE[r]
            for c in range(w):
                if not (lo <= c <= hi):
                    continue               # fully transparent -> discarded -> wing outline
                if c == 3 and r >= 1:
                    col = rgba(VEIN, VEIN_ALPHA)                     # costa / L1 leading edge
                elif c == 1 and 2 <= r <= 11:
                    col = rgba(mul(VEIN, 1.15), 170)                 # L3 longitudinal vein
                elif r in (5, 9) and 0 <= c <= 2:
                    col = rgba(mul(VEIN, 1.25), 150)                 # cross veins
                elif r >= 12:
                    col = rgba(mul(MEMBRANE, 0.9), MEMBRANE_ALPHA + 40)   # thicker hinge region
                else:
                    a = MEMBRANE_ALPHA - (10 if r <= 1 else 0)
                    col = rgba(MEMBRANE, a)
                atlas.put(x0 + c, v + r, col)


# ---------------------------------------------------------------------------------------------------------- assemble
def build(male):
    global rng
    rng = random.Random(1234)
    atlas = Atlas()
    paint_box(atlas, 0, 0, 6, 6, 6, thorax_shader)
    paint_box(atlas, 24, 0, 4, 4, 4, head_shader)
    paint_box(atlas, 40, 0, 1, 3, 3, eye_shader)
    paint_box(atlas, 48, 0, 1, 3, 1, proboscis_shader)
    paint_box(atlas, 48, 4, 2, 1, 2, labellum_shader)
    paint_box(atlas, 56, 0, 1, 1, 1, antenna_shader)
    paint_box(atlas, 60, 0, 0, 2, 1, arista_shader)
    paint_box(atlas, 0, 12, 5, 5, 8, make_abdomen_shader(male))
    paint_box(atlas, 26, 12, 3, 3, 2, make_tip_shader(male))
    paint_box(atlas, 36, 12, 2, 1, 1, haltere_shader)
    paint_box(atlas, 42, 12, 4, 1, 1, make_leg_shader(FEMUR, False))
    paint_box(atlas, 42, 14, 4, 1, 1, make_leg_shader(TIBIA, False))
    paint_box(atlas, 42, 16, 4, 1, 1, make_leg_shader(TARSUS, True))
    paint_wing(atlas, 0, 26)
    return atlas.img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for male, name in ((True, "fruit_fly.png"), (False, "fruit_fly_female.png")):
        img = build(male)
        path = os.path.join(OUT_DIR, name)
        img.save(path, "PNG")
        used = sum(1 for p in img.getdata() if p[3] > 0)
        print(f"wrote {path} ({img.size[0]}x{img.size[1]}, {used} opaque/translucent texels)")


if __name__ == "__main__":
    main()
