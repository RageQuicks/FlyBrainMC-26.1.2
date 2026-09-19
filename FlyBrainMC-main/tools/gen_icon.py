#!/usr/bin/env python3
"""Generate the mod icon: a stylised fruit fly seen from above with a pink, glowing brain.

Writes src/main/resources/assets/fruitfly/icon.png (128x128 RGBA, drawn at 512x512 and LANCZOS-downsampled).
Usage:  python tools/gen_icon.py
"""
import math
import os
import random
import sys

try:
    from PIL import Image, ImageDraw, ImageFilter
except ImportError:  # pragma: no cover
    sys.exit("Pillow is required: pip install pillow")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "fruitfly", "icon.png")
S = 512


def P(fx, fy):
    return (S * fx, S * fy)


def box(cx, cy, rx, ry):
    return [S * (cx - rx), S * (cy - ry), S * (cx + rx), S * (cy + ry)]


def layer():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


def main():
    im = layer()
    d = ImageDraw.Draw(im)

    # background: dark plum rounded tile with a faint radial glow behind the head
    d.rounded_rectangle([0, 0, S - 1, S - 1], radius=96, fill=(28, 22, 34, 255))
    glow_bg = layer()
    ImageDraw.Draw(glow_bg).ellipse(box(0.5, 0.28, 0.34, 0.30), fill=(120, 60, 110, 140))
    im.alpha_composite(glow_bg.filter(ImageFilter.GaussianBlur(60)))

    # faint connectome: a ring of nodes with edges (deterministic)
    rnd = random.Random(7)
    net = layer()
    nd = ImageDraw.Draw(net)
    nodes = []
    for k in range(26):
        a = 2 * math.pi * k / 26 + rnd.uniform(-0.08, 0.08)
        r = rnd.uniform(0.40, 0.46)
        nodes.append((0.5 + r * math.cos(a), 0.5 + r * math.sin(a)))
    for k in range(len(nodes)):
        for m in (k + 1, k + 5, k + 9):
            a, b = nodes[k], nodes[m % len(nodes)]
            nd.line([P(*a), P(*b)], fill=(150, 90, 160, 70), width=2)
    for x, y in nodes:
        nd.ellipse(box(x, y, 0.010, 0.010), fill=(210, 140, 220, 160))
    im.alpha_composite(net)

    # wings (translucent, drawn behind the body), spread in a shallow V
    for sign in (-1, 1):
        wing = layer()
        wd = ImageDraw.Draw(wing)
        cx = 0.5 + sign * 0.19
        wd.ellipse(box(cx, 0.50, 0.19, 0.075), fill=(220, 232, 240, 120), outline=(110, 90, 60, 210), width=5)
        # longitudinal veins
        for dy in (-0.03, 0.0, 0.035):
            wd.line([P(0.5 + sign * 0.03, 0.50 + dy * 0.6), P(cx + sign * 0.16, 0.50 + dy)], fill=(110, 90, 60, 170), width=3)
        wing = wing.rotate(sign * 28, center=P(0.5, 0.42), resample=Image.BICUBIC)
        im.alpha_composite(wing)

    # legs: three pairs of dark two-segment strokes
    for sign in (-1, 1):
        for (y0, ang) in ((0.40, -0.9), (0.47, 0.05), (0.54, 0.9)):
            x0 = 0.5 + sign * 0.085
            kx, ky = x0 + sign * 0.11 * math.cos(ang), y0 + 0.11 * math.sin(ang) - 0.04
            tx, ty = kx + sign * 0.10 * math.cos(ang + 0.5), ky + 0.10 * math.sin(ang + 0.5) + 0.05
            d.line([P(x0, y0), P(kx, ky), P(tx, ty)], fill=(58, 42, 26, 255), width=11, joint="curve")

    # abdomen: tan, banded, with the male's black tip
    d.ellipse(box(0.5, 0.66, 0.125, 0.21), fill=(200, 161, 101, 255))
    ab = layer()
    ad = ImageDraw.Draw(ab)
    for y in (0.58, 0.66, 0.74):
        ad.rectangle([S * 0.36, S * (y - 0.012), S * 0.64, S * (y + 0.012)], fill=(42, 30, 20, 255))
    ad.rectangle([S * 0.36, S * 0.79, S * 0.64, S * 0.90], fill=(26, 20, 16, 255))
    mask = layer()
    ImageDraw.Draw(mask).ellipse(box(0.5, 0.66, 0.125, 0.21), fill=(255, 255, 255, 255))
    ab.putalpha(Image.composite(ab.getchannel("A"), Image.new("L", (S, S), 0), mask.getchannel("A")))
    im.alpha_composite(ab)

    # thorax and head
    d.ellipse(box(0.5, 0.43, 0.115, 0.12), fill=(184, 144, 90, 255))
    for (x, y) in ((0.46, 0.39), (0.54, 0.39), (0.47, 0.46), (0.53, 0.46)):
        d.ellipse(box(x, y, 0.008, 0.008), fill=(90, 62, 36, 255))
    d.ellipse(box(0.5, 0.265, 0.095, 0.09), fill=(195, 154, 98, 255))
    # antennae + aristae
    for sign in (-1, 1):
        d.line([P(0.5 + sign * 0.03, 0.19), P(0.5 + sign * 0.06, 0.14)], fill=(166, 127, 78, 255), width=8)
        d.line([P(0.5 + sign * 0.06, 0.14), P(0.5 + sign * 0.10, 0.09)], fill=(58, 42, 26, 255), width=3)
    # brick-red compound eyes with facet highlights
    for sign in (-1, 1):
        ex = 0.5 + sign * 0.075
        d.ellipse(box(ex, 0.265, 0.045, 0.07), fill=(178, 34, 34, 255))
        d.ellipse(box(ex + sign * 0.012, 0.235, 0.014, 0.018), fill=(224, 74, 44, 255))
        for k in range(6):
            fx = ex + sign * 0.02 * math.cos(k) * 0.8
            fy = 0.265 + 0.045 * math.sin(k * 1.7)
            d.ellipse(box(fx, fy, 0.005, 0.005), fill=(122, 30, 18, 255))

    # the brain: pink lobes (central brain + optic lobes) hovering over the head, with a soft glow
    brain = layer()
    bd = ImageDraw.Draw(brain)
    # hovering just above the head so the red eyes stay visible
    lobes = ((0.5, 0.16, 0.072, 0.058), (0.42, 0.175, 0.036, 0.042), (0.58, 0.175, 0.036, 0.042),
             (0.47, 0.125, 0.033, 0.028), (0.53, 0.125, 0.033, 0.028))
    for cx, cy, rx, ry in lobes:
        bd.ellipse(box(cx, cy, rx, ry), fill=(255, 150, 175, 235), outline=(200, 80, 120, 255), width=5)
    # gyri-like squiggles
    for k in range(5):
        y = 0.125 + 0.015 * k
        bd.arc(box(0.5, y, 0.05 - 0.006 * k, 0.012), 0, 180, fill=(210, 95, 135, 220), width=4)
    glow = brain.filter(ImageFilter.GaussianBlur(22))
    glow2 = brain.filter(ImageFilter.GaussianBlur(8))
    im.alpha_composite(glow)
    im.alpha_composite(glow)
    im.alpha_composite(glow2)
    im.alpha_composite(brain)

    # spikes: bright cyan sparks radiating from the brain
    sp = layer()
    sd = ImageDraw.Draw(sp)
    for _ in range(38):
        a = rnd.uniform(0, 2 * math.pi)
        r = rnd.uniform(0.09, 0.2)
        x, y = 0.5 + r * math.cos(a), 0.16 + r * 0.8 * math.sin(a)
        rad = rnd.uniform(0.004, 0.011)
        sd.ellipse(box(x, y, rad, rad), fill=(120, 255, 210, 255))
    im.alpha_composite(sp.filter(ImageFilter.GaussianBlur(5)))
    im.alpha_composite(sp)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    icon = im.resize((128, 128), Image.LANCZOS)
    icon.save(OUT, "PNG")
    print(f"wrote {OUT} ({icon.size[0]}x{icon.size[1]})")


if __name__ == "__main__":
    main()
