#!/usr/bin/env python3
"""Generates the models, textures and icons of the parts added on the expansion-testing branch.

Pure Python (no Pillow, no Blender), so anyone can rerun it:

    python script/expansion_models.py

Models are Wavefront OBJ files in the layout Obj3D reads: an `o` line per part, `usemtl` before the faces, triangles
only, and a .txt file with the pivots of the moving parts. Six-node parts stand on the wall at x = 0 and stick out
towards +x, within -0.5..0.5 in y and z. Blocks (batteries) fill -0.5..0.5 in all axes.

An artist can replace any of these files with a Blender export of the same part names; the code doesn't change.
"""
import math
import os
import random
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'eln')
MODEL = os.path.join(ROOT, 'model')
ICONS = os.path.join(ROOT, 'textures', 'block')
ITEMS = os.path.join(ROOT, 'textures', 'item')


# ---------------------------------------------------------------- PNG

def png_read(path):
    """Reads a non-interlaced 8 or 16 bit RGB(A) PNG into (w, h, [[r,g,b,a] ...]) with 8 bit channels."""
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', path
    pos, idat, w = 8, b'', 0
    while pos < len(data):
        n, kind = struct.unpack('>I4s', data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + n]
        if kind == b'IHDR':
            w, h, depth, color, _, _, interlace = struct.unpack('>IIBBBBB', body)
            assert interlace == 0 and color in (2, 6) and depth in (8, 16), (path, depth, color)
        elif kind == b'IDAT':
            idat += body
        pos += 12 + n
    channels = 4 if color == 6 else 3
    bpp = channels * depth // 8
    raw = zlib.decompress(idat)
    stride = w * bpp
    rows, prev, i = [], bytearray(stride), 0
    for _ in range(h):
        f = raw[i]
        line = bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(line)
        prev = line
    px = []
    step = depth // 8
    for line in rows:
        for x in range(w):
            ch = [line[(x * channels + k) * step] for k in range(channels)]
            if channels == 3:
                ch.append(255)
            px.append(ch)
    return w, h, px


def png_write(path, w, h, px):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw.extend(max(0, min(255, int(c))) for c in px[y * w + x])

    def chunk(kind, body):
        return struct.pack('>I', len(body)) + kind + body + struct.pack('>I', zlib.crc32(kind + body) & 0xffffffff)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n')
        f.write(chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)))
        f.write(chunk(b'IDAT', zlib.compress(bytes(raw), 9)))
        f.write(chunk(b'IEND', b''))


class Canvas:
    def __init__(self, w, h, fill=(0, 0, 0, 0)):
        self.w, self.h = w, h
        self.px = [list(fill) for _ in range(w * h)]
        self.rng = random.Random(w * 31 + h)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            c = list(c) + [255] * (4 - len(c))
            self.px[y * self.w + x] = c

    def get(self, x, y):
        return self.px[y * self.w + x]

    def rect(self, x0, y0, x1, y1, c, noise=0):
        """Fills x0 <= x < x1, y0 <= y < y1."""
        for y in range(y0, y1):
            for x in range(x0, x1):
                n = self.rng.randint(-noise, noise) if noise else 0
                self.set(x, y, [c[0] + n, c[1] + n, c[2] + n] + list(c[3:4] or [255]))

    def frame(self, x0, y0, x1, y1, light, dark):
        for x in range(x0, x1):
            self.set(x, y0, light)
            self.set(x, y1 - 1, dark)
        for y in range(y0, y1):
            self.set(x0, y, light)
            self.set(x1 - 1, y, dark)

    def disc(self, cx, cy, r, c):
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                    self.set(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        for i in range(n + 1):
            t = i / n
            self.set(int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t)), c)

    def save(self, path):
        png_write(path, self.w, self.h, self.px)


# ---------------------------------------------------------------- OBJ

class Model:
    """Parts made of boxes and cylinders, mapped onto one texture."""

    def __init__(self, name, tex_w, tex_h):
        self.name = name
        self.tw, self.th = tex_w, tex_h
        self.parts = []  # (part, material, [faces of (v index, vt index)])
        self.v, self.vt = [], []
        self.floats = {}  # part -> {key: value}

    def _part(self, part, mtl):
        for p in self.parts:
            if p[0] == part and p[1] == mtl:
                return p[2]
        faces = []
        self.parts.append((part, mtl, faces))
        return faces

    def _uv(self, u, v):
        """u, v in texture pixels, from the top left."""
        self.vt.append((u / self.tw, 1 - v / self.th))
        return len(self.vt)

    def _vert(self, p):
        self.v.append(tuple(p))
        return len(self.v)

    def quad(self, part, mtl, corners, uvs, outward):
        """Two triangles, wound so that their normal points along `outward`."""
        a, b, c = corners[0], corners[1], corners[2]
        u = [b[i] - a[i] for i in range(3)]
        w = [c[i] - a[i] for i in range(3)]
        n = (u[1] * w[2] - u[2] * w[1], u[2] * w[0] - u[0] * w[2], u[0] * w[1] - u[1] * w[0])
        if sum(n[i] * outward[i] for i in range(3)) < 0:
            corners = corners[::-1]
            uvs = uvs[::-1]
        vi = [self._vert(p) for p in corners]
        ti = [self._uv(*t) for t in uvs]
        faces = self._part(part, mtl)
        faces.append(((vi[0], ti[0]), (vi[1], ti[1]), (vi[2], ti[2])))
        faces.append(((vi[0], ti[0]), (vi[2], ti[2]), (vi[3], ti[3])))

    def box(self, part, mtl, lo, hi, tex, faces='xXyYzZ'):
        """An axis-aligned box. `tex` is a texture rect (u0, v0, u1, v1) for all faces or a dict per face letter
        (x = -x, X = +x, ...). Faces not in `faces` are left out."""
        x0, y0, z0 = lo
        x1, y1, z1 = hi

        def rect(f):
            return tex[f] if isinstance(tex, dict) else tex

        def uv4(r):
            u0, v0, u1, v1 = r
            # Corners go: (low a, low b), (high a, low b), (high a, high b), (low a, high b); b is "up" in the texture.
            return [(u0, v1), (u1, v1), (u1, v0), (u0, v0)]

        spec = {
            'x': ([(x0, y0, z1), (x0, y0, z0), (x0, y1, z0), (x0, y1, z1)], (-1, 0, 0)),
            'X': ([(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)], (1, 0, 0)),
            'y': ([(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)], (0, -1, 0)),
            'Y': ([(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)], (0, 1, 0)),
            'z': ([(x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0)], (0, 0, -1)),
            'Z': ([(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], (0, 0, 1)),
        }
        for f in faces:
            corners, normal = spec[f]
            self.quad(part, mtl, corners, uv4(rect(f)), normal)

    def cylinder(self, part, mtl, axis, center, radius, length, tex_side, tex_cap, segments=12, caps='ab'):
        """A cylinder along `axis` ('x', 'y' or 'z') from center to center + length."""
        ai = 'xyz'.index(axis)
        bi, ci = [i for i in range(3) if i != ai]

        def point(angle, along):
            p = list(center)
            p[ai] += along
            p[bi] += radius * math.cos(angle)
            p[ci] += radius * math.sin(angle)
            return p

        u0, v0, u1, v1 = tex_side
        for s in range(segments):
            a0 = 2 * math.pi * s / segments
            a1 = 2 * math.pi * (s + 1) / segments
            mid = (a0 + a1) / 2
            out = [0, 0, 0]
            out[bi] = math.cos(mid)
            out[ci] = math.sin(mid)
            ua = u0 + (u1 - u0) * s / segments
            ub = u0 + (u1 - u0) * (s + 1) / segments
            self.quad(part, mtl, [point(a0, 0), point(a1, 0), point(a1, length), point(a0, length)],
                      [(ua, v1), (ub, v1), (ub, v0), (ua, v0)], out)
        cu0, cv0, cu1, cv1 = tex_cap
        cu, cv = (cu0 + cu1) / 2, (cv0 + cv1) / 2
        for end, along in (('a', 0), ('b', length)):
            if end not in caps:
                continue
            out = [0, 0, 0]
            out[ai] = -1 if end == 'a' else 1
            c = list(center)
            c[ai] += along
            for s in range(segments):
                a0 = 2 * math.pi * s / segments
                a1 = 2 * math.pi * (s + 1) / segments
                p0, p1 = point(a0, along), point(a1, along)
                t0 = (cu + (cu1 - cu0) / 2 * math.cos(a0), cv + (cv1 - cv0) / 2 * math.sin(a0))
                t1 = (cu + (cu1 - cu0) / 2 * math.cos(a1), cv + (cv1 - cv0) / 2 * math.sin(a1))
                # A degenerate quad (two corners at the centre) is a triangle.
                self.quad(part, mtl, [c, p0, p1, c], [(cu, cv), t0, t1, (cu, cv)], out)

    def float(self, part, key, value):
        self.floats.setdefault(part, {})[key] = value

    def write(self, folder, materials, strings=None):
        """materials: {material: texture file name}."""
        d = os.path.join(MODEL, folder)
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, self.name + '.obj'), 'w', newline='\n') as f:
            f.write('# Generated by script/expansion_models.py\n')
            f.write('mtllib %s.mtl\n' % self.name)
            # Obj3D reads the vertices of a part after its `o` line, but indexes them globally in file order. So the
            # vertices are written part by part and renumbered as they are written.
            vmap, tmap = {}, {}
            for part in dict.fromkeys(p[0] for p in self.parts):
                f.write('o %s\n' % part)
                groups = [p for p in self.parts if p[0] == part]
                for _, _, faces in groups:
                    for tri in faces:
                        for vi, ti in tri:
                            if vi not in vmap:
                                vmap[vi] = len(vmap) + 1
                                f.write('v %.5f %.5f %.5f\n' % self.v[vi - 1])
                            if ti not in tmap:
                                tmap[ti] = len(tmap) + 1
                                f.write('vt %.5f %.5f\n' % self.vt[ti - 1])
                for _, mtl, faces in groups:
                    f.write('usemtl %s\n' % mtl)
                    for tri in faces:
                        f.write('f %s\n' % ' '.join('%d/%d' % (vmap[vi], tmap[ti]) for vi, ti in tri))
        with open(os.path.join(d, self.name + '.mtl'), 'w', newline='\n') as f:
            for mtl, tex in materials.items():
                f.write('newmtl %s\nKa 1.0 1.0 1.0\nKd 1.0 1.0 1.0\nd 1\nillum 1\nmap_Kd %s\n\n' % (mtl, tex))
        with open(os.path.join(d, self.name + '.txt'), 'w', newline='\n') as f:
            f.write('# Generated by script/expansion_models.py\n')
            for k, v in (strings or {}).items():
                f.write('s %s %s\n' % (k, v))
            for part, kv in self.floats.items():
                f.write('o %s\n' % part)
                for k, v in kv.items():
                    f.write('f %s %s\n' % (k, v))


def renumber_check(model):
    """Obj3D indexes vertices globally but reads them per part, so each part's vertices must come after its `o`
    line and before any later part's. Geometry is built part by part here; check that it stays so."""
    last = 0
    seen = []
    for part, _, faces in model.parts:
        idx = [vi for t in faces for vi, _ in t]
        if part in seen:
            continue
        seen.append(part)
        assert min(idx) > last or not idx, (model.name, part)
        last = max(idx)


# ---------------------------------------------------------------- Palette

STEEL = (150, 155, 160)
STEEL_DARK = (95, 100, 106)
ENCLOSURE = (70, 78, 88)
BRASS = (196, 158, 74)
BLACK = (28, 30, 33)
WHITE = (232, 232, 228)
RED = (205, 40, 35)
GREEN = (60, 200, 70)
AMBER = (240, 165, 30)
BLUE = (55, 110, 200)
ORANGE = (235, 110, 20)
YELLOW = (230, 200, 40)
LCD = (40, 70, 45)
SCREEN = (12, 22, 16)


def swatches(c, entries):
    """Paints named 8x8 swatches in a row-major grid and returns their rects."""
    rects = {}
    cols = c.w // 8
    for i, (name, color, noise) in enumerate(entries):
        x = (i % cols) * 8
        y = (i // cols) * 8
        c.rect(x, y, x + 8, y + 8, color, noise)
        rects[name] = (x + 1, y + 1, x + 7, y + 7)
    return rects


def icon(name, draw, size=32, folder=ICONS):
    c = Canvas(size, size)
    draw(c)
    c.save(os.path.join(folder, name + '.png'))


# ---------------------------------------------------------------- Six-node parts

def terminals(m, s, z_ends=(True, True), y_side=False, half_z=0.4):
    """Brass cable lugs where the cables come in: at the z ends, and optionally on the -y side."""
    if z_ends[0]:
        m.box('case', 'main', (0.0, -0.06, -half_z - 0.1), (0.07, 0.06, -half_z), s['brass'])
    if z_ends[1]:
        m.box('case', 'main', (0.0, -0.06, half_z), (0.07, 0.06, half_z + 0.1), s['brass'])
    if y_side:
        m.box('case', 'main', (0.0, -0.5, -0.06), (0.07, -0.4, 0.06), s['brass'])


def protection_relay():
    """A molded-case breaker with a relay head: a status window, trip flag, three LEDs and the operating lever."""
    c = Canvas(64, 64)
    s = swatches(c, [
        ('plate', STEEL, 6), ('body', ENCLOSURE, 5), ('dark', BLACK, 3), ('brass', BRASS, 10),
        ('lcd', LCD, 2), ('white', WHITE, 2), ('red', RED, 0), ('green', GREEN, 0),
        ('amber', AMBER, 0), ('orange', ORANGE, 0), ('lever', (40, 40, 44), 4), ('label', (220, 200, 60), 4),
    ])
    # A face with a printed curve and a scale, on the lower half of the texture.
    c.rect(0, 32, 32, 64, ENCLOSURE, 4)
    c.frame(1, 33, 31, 63, (110, 118, 128), (45, 50, 58))
    c.rect(4, 36, 28, 48, LCD, 2)
    for x in range(5, 27):
        y = 46 - int(9 * math.exp(-(x - 5) / 6.0))
        c.set(x, y, (140, 230, 140))
    for x in range(6, 26, 4):
        c.rect(x, 52, x + 2, 54, WHITE)
    c.rect(4, 57, 28, 60, (220, 200, 60))
    face = (0, 32, 32, 64)

    m = Model('protectionrelay', 64, 64)
    m.box('case', 'main', (0.0, -0.32, -0.42), (0.03, 0.32, 0.42), s['plate'])
    body_tex = dict.fromkeys('xyYzZ', s['body'])
    body_tex['X'] = face
    m.box('case', 'main', (0.03, -0.24, -0.3), (0.24, 0.24, 0.3), body_tex)
    # Lever slot and the trip flag window.
    m.box('case', 'main', (0.24, -0.2, 0.05), (0.25, -0.04, 0.2), s['dark'])
    m.box('case', 'main', (0.24, 0.06, 0.12), (0.25, 0.14, 0.24), s['dark'])
    terminals(m, s, half_z=0.32)
    # Arc chute vents on the top and bottom.
    for z in (-0.22, -0.14, -0.06, 0.02, 0.1, 0.18):
        m.box('case', 'main', (0.08, 0.24, z), (0.2, 0.25, z + 0.04), s['dark'], faces='Y')
        m.box('case', 'main', (0.08, -0.25, z), (0.2, -0.24, z + 0.04), s['dark'], faces='y')

    m.box('lever', 'main', (0.24, -0.15, 0.1), (0.36, -0.09, 0.15), s['lever'])
    m.box('lever', 'main', (0.33, -0.17, 0.085), (0.38, -0.07, 0.165), s['red'])
    m.float('lever', 'originX', 0.245)
    m.float('lever', 'originY', -0.12)
    m.float('lever', 'originZ', 0.125)
    m.float('lever', 'alphaOff', 35)
    m.float('lever', 'alphaOn', -35)
    m.float('lever', 'speed', 0.08)

    # The flag shows through the window when the relay has tripped.
    m.box('flag', 'main', (0.25, 0.07, 0.13), (0.255, 0.13, 0.23), s['orange'], faces='X')
    for part, colour, z in (('ledready', 'green', -0.2), ('ledreclose', 'amber', -0.13), ('ledtrip', 'red', -0.06)):
        m.box(part, 'main', (0.24, 0.16, z), (0.255, 0.2, z + 0.04), s[colour])
    m.write('protectionrelay', {'main': 'protectionrelay.png'})
    c.save(os.path.join(MODEL, 'protectionrelay', 'protectionrelay.png'))

    def draw(i):
        i.rect(2, 4, 30, 28, (150, 155, 160))
        i.rect(6, 7, 26, 25, ENCLOSURE)
        i.frame(6, 7, 26, 25, (110, 118, 128), (45, 50, 58))
        i.rect(9, 9, 23, 14, LCD)
        i.line(10, 13, 22, 10, (140, 230, 140))
        i.rect(9, 16, 11, 18, GREEN)
        i.rect(12, 16, 14, 18, AMBER)
        i.rect(15, 16, 17, 18, RED)
        i.rect(19, 16, 23, 23, (40, 40, 44))
        i.rect(19, 15, 23, 17, RED)
        i.rect(14, 1, 18, 5, BRASS)
        i.rect(14, 27, 18, 31, BRASS)
    icon('overcurrentprotectionrelay', draw)


def bms():
    """A battery management system: a black box with three LEDs and a five-segment charge bar."""
    c = Canvas(64, 64)
    s = swatches(c, [
        ('case', (34, 36, 40), 4), ('plate', STEEL, 6), ('brass', BRASS, 10), ('green', GREEN, 0),
        ('amber', AMBER, 0), ('red', RED, 0), ('seg', (70, 190, 255), 0), ('segoff', (25, 40, 50), 0),
        ('label', (40, 150, 90), 6), ('white', WHITE, 2),
    ])
    c.rect(32, 32, 64, 64, (34, 36, 40), 3)
    c.rect(34, 34, 62, 40, (40, 150, 90), 4)
    for x in range(36, 60, 5):
        c.rect(x, 44, x + 3, 47, (25, 40, 50))
    c.rect(36, 52, 60, 54, WHITE)
    face = (32, 32, 64, 64)
    m = Model('bms', 64, 64)
    m.box('case', 'main', (0.0, -0.28, -0.36), (0.03, 0.28, 0.36), s['plate'])
    tex = dict.fromkeys('xyYzZ', s['case'])
    tex['X'] = face
    m.box('case', 'main', (0.03, -0.22, -0.26), (0.17, 0.22, 0.26), tex)
    terminals(m, s, half_z=0.26)
    for i, z in enumerate((-0.2, -0.12, -0.04, 0.04, 0.12)):
        m.box('seg%d' % i, 'main', (0.17, -0.06, z), (0.178, 0.02, z + 0.06), s['seg'], faces='X')
    for part, colour, z in (('ledok', 'green', -0.2), ('ledlow', 'amber', -0.1), ('ledfault', 'red', 0.0)):
        m.box(part, 'main', (0.17, 0.1, z), (0.185, 0.16, z + 0.05), s[colour])
    m.write('bms', {'main': 'bms.png'})
    c.save(os.path.join(MODEL, 'bms', 'bms.png'))

    def draw(i):
        i.rect(3, 6, 29, 26, (34, 36, 40))
        i.frame(3, 6, 29, 26, (70, 72, 78), (15, 15, 18))
        i.rect(5, 8, 27, 11, (40, 150, 90))
        for k, col in enumerate([(70, 190, 255)] * 3 + [(25, 40, 50)] * 2):
            i.rect(6 + k * 4, 15, 9 + k * 4, 19, col)
        i.rect(6, 21, 8, 23, GREEN)
        i.rect(10, 21, 12, 23, AMBER)
        i.rect(14, 21, 16, 23, RED)
        i.rect(14, 1, 18, 6, BRASS)
        i.rect(14, 26, 18, 31, BRASS)
    icon('batterymanagementsystem', draw)


def transfer_switch():
    """An automatic transfer switch: a grey cabinet with a big rotary handle between I (mains) and II (backup)."""
    c = Canvas(64, 64)
    s = swatches(c, [
        ('case', (165, 168, 172), 5), ('plate', STEEL_DARK, 6), ('brass', BRASS, 10), ('handle', (200, 35, 30), 6),
        ('hub', (230, 200, 40), 4), ('blue', BLUE, 0), ('yellow', YELLOW, 0), ('dark', BLACK, 3),
        ('green', GREEN, 0), ('amber', AMBER, 0),
    ])
    c.rect(0, 32, 32, 64, (165, 168, 172), 4)
    c.frame(1, 33, 31, 63, (205, 208, 212), (110, 112, 118))
    c.disc(16, 46, 11, (230, 200, 40))
    c.disc(16, 46, 9, (215, 185, 35))
    # I and II marks.
    c.rect(4, 36, 6, 41, BLACK)
    c.rect(25, 36, 27, 41, BLACK)
    c.rect(28, 36, 30, 41, BLACK)
    c.rect(6, 56, 26, 60, (60, 60, 64))
    face = (0, 32, 32, 64)
    m = Model('transferswitch', 64, 64)
    m.box('case', 'main', (0.0, -0.4, -0.42), (0.03, 0.42, 0.42), s['plate'])
    tex = dict.fromkeys('xyYzZ', s['case'])
    tex['X'] = face
    m.box('case', 'main', (0.03, -0.3, -0.32), (0.22, 0.32, 0.32), tex)
    terminals(m, s, y_side=True, half_z=0.32)
    m.box('case', 'main', (0.0, -0.4, -0.06), (0.07, -0.3, 0.06), s['brass'])
    m.cylinder('handle', 'main', 'x', (0.22, 0.03, 0.0), 0.06, 0.04, s['hub'], s['hub'])
    m.box('handle', 'main', (0.23, -0.0, -0.2), (0.3, 0.06, 0.2), s['handle'])
    m.box('handle', 'main', (0.3, 0.0, 0.12), (0.33, 0.06, 0.2), s['handle'])
    m.float('handle', 'originX', 0.22)
    m.float('handle', 'originY', 0.03)
    m.float('handle', 'originZ', 0.0)
    m.float('handle', 'alphaPrimary', -45)
    m.float('handle', 'alphaOff', 0)
    m.float('handle', 'alphaBackup', 45)
    m.float('handle', 'speed', 0.06)
    m.box('ledprimary', 'main', (0.22, -0.26, -0.22), (0.235, -0.2, -0.16), s['blue'])
    m.box('ledbackup', 'main', (0.22, -0.26, 0.16), (0.235, -0.2, 0.22), s['yellow'])
    m.write('transferswitch', {'main': 'transferswitch.png'})
    c.save(os.path.join(MODEL, 'transferswitch', 'transferswitch.png'))

    def draw(i):
        i.rect(3, 3, 29, 29, (165, 168, 172))
        i.frame(3, 3, 29, 29, (205, 208, 212), (110, 112, 118))
        i.disc(16, 16, 8, (230, 200, 40))
        i.line(10, 22, 22, 10, (200, 35, 30))
        i.line(11, 22, 23, 10, (200, 35, 30))
        i.line(10, 21, 22, 9, (200, 35, 30))
        i.rect(5, 5, 7, 9, BLACK)
        i.rect(24, 5, 26, 9, BLACK)
        i.rect(27, 5, 28, 9, BLACK)
        i.rect(5, 24, 8, 27, BLUE)
        i.rect(24, 24, 27, 27, YELLOW)
    icon('automatictransferswitch', draw)


def mppt():
    """A solar charge controller: blue case, a black finned heatsink, a small display and three LEDs."""
    c = Canvas(64, 64)
    s = swatches(c, [
        ('case', (40, 95, 170), 5), ('fin', (40, 42, 46), 6), ('plate', STEEL, 6), ('brass', BRASS, 10),
        ('lcd', LCD, 2), ('amber', AMBER, 0), ('green', GREEN, 0), ('blue', (120, 190, 255), 0), ('white', WHITE, 2),
    ])
    c.rect(32, 32, 64, 64, (40, 95, 170), 4)
    c.rect(35, 50, 61, 61, LCD, 2)
    for x in range(37, 59, 3):
        c.set(x, 58 - (x - 37) // 3, (140, 230, 140))
    c.rect(35, 35, 61, 38, WHITE)
    face = (32, 32, 64, 64)
    m = Model('mpptcontroller', 64, 64)
    m.box('case', 'main', (0.0, -0.3, -0.38), (0.03, 0.3, 0.38), s['plate'])
    tex = dict.fromkeys('xyYzZ', s['case'])
    tex['X'] = face
    m.box('case', 'main', (0.03, -0.26, -0.28), (0.16, 0.26, 0.28), tex)
    for k in range(7):
        z = -0.25 + k * 0.08
        m.box('case', 'main', (0.16, 0.02, z), (0.24, 0.24, z + 0.025), s['fin'])
    terminals(m, s, half_z=0.28)
    for part, colour, z in (('ledsun', 'amber', -0.2), ('ledcharge', 'green', -0.12), ('ledfull', 'blue', -0.04)):
        m.box(part, 'main', (0.16, -0.06, z), (0.175, -0.01, z + 0.05), s[colour])
    m.write('mpptcontroller', {'main': 'mpptcontroller.png'})
    c.save(os.path.join(MODEL, 'mpptcontroller', 'mpptcontroller.png'))

    def draw(i):
        i.rect(3, 4, 29, 28, (40, 95, 170))
        i.frame(3, 4, 29, 28, (80, 135, 210), (20, 55, 110))
        for k in range(6):
            i.rect(5 + k * 4, 6, 7 + k * 4, 14, (40, 42, 46))
        i.rect(6, 17, 21, 25, LCD)
        i.line(7, 23, 19, 19, (140, 230, 140))
        i.rect(23, 17, 26, 19, AMBER)
        i.rect(23, 20, 26, 22, GREEN)
        i.rect(23, 23, 26, 25, (120, 190, 255))
    icon('mpptchargecontroller', draw)


# Where the oscilloscope draws its traces, in model space; the render code uses the same numbers.
SCOPE_SCREEN = dict(x=0.2605, y0=-0.2, y1=0.3, z0=-0.4, z1=0.2)


def oscilloscope():
    """A bench-style oscilloscope hung on the wall: grey case, a dark screen with a grid, knobs and two probe jacks."""
    c = Canvas(64, 64)
    s = swatches(c, [
        ('case', (190, 192, 196), 4), ('bezel', (60, 62, 66), 4), ('plate', STEEL_DARK, 6), ('knob', BLACK, 3),
        ('knobcap', (200, 200, 200), 3), ('jack1', YELLOW, 0), ('jack2', (70, 200, 230), 0), ('brass', BRASS, 10),
        ('green', GREEN, 0),
    ])
    # The screen with a 8 x 10 graticule.
    c.rect(0, 32, 40, 64, SCREEN, 1)
    for k in range(11):
        x = 1 + k * 38 // 10
        for y in range(33, 63, 2):
            c.set(x, y, (40, 80, 50))
    for k in range(9):
        y = 33 + k * 29 // 8
        for x in range(1, 39, 2):
            c.set(x, y, (40, 80, 50))
    screen = (0, 32, 40, 64)
    sc = SCOPE_SCREEN
    m = Model('oscilloscope', 64, 64)
    m.box('case', 'main', (0.0, -0.4, -0.46), (0.03, 0.42, 0.46), s['plate'])
    m.box('case', 'main', (0.03, -0.32, -0.45), (0.25, 0.36, 0.45), s['case'])
    m.box('case', 'main', (0.25, sc['y0'] - 0.03, sc['z0'] - 0.03), (0.258, sc['y1'] + 0.03, sc['z1'] + 0.03), s['bezel'])
    m.box('case', 'main', (0.258, sc['y0'], sc['z0']), (0.26, sc['y1'], sc['z1']), {'X': screen}, faces='X')
    for y, z in ((0.22, 0.32), (0.08, 0.32), (-0.06, 0.32), (0.22, 0.4), (0.08, 0.4)):
        m.cylinder('case', 'main', 'x', (0.25, y, z), 0.03, 0.04, s['knob'], s['knobcap'], segments=10)
    m.cylinder('case', 'main', 'x', (0.25, -0.26, -0.3), 0.025, 0.04, s['jack1'], s['jack1'], segments=10)
    m.cylinder('case', 'main', 'x', (0.25, -0.26, -0.18), 0.025, 0.04, s['jack2'], s['jack2'], segments=10)
    m.box('ledpower', 'main', (0.25, -0.27, 0.36), (0.262, -0.23, 0.4), s['green'])
    terminals(m, s, half_z=0.45)
    m.write('oscilloscope', {'main': 'oscilloscope.png'})
    c.save(os.path.join(MODEL, 'oscilloscope', 'oscilloscope.png'))

    def draw(i):
        i.rect(1, 4, 31, 28, (190, 192, 196))
        i.frame(1, 4, 31, 28, (225, 226, 228), (120, 122, 126))
        i.rect(3, 6, 22, 22, SCREEN)
        for x in range(4, 21):
            y = 14 - int(5 * math.sin((x - 4) / 16.0 * 2 * math.pi))
            i.set(x, y, (250, 230, 60))
            y2 = 14 - int(3 * math.sin((x - 2) / 16.0 * 2 * math.pi))
            i.set(x, y2, (90, 220, 250))
        for k, y in enumerate((8, 13, 18)):
            i.disc(26, y + 0.5, 2, BLACK)
        i.disc(6, 25, 1.5, YELLOW)
        i.disc(11, 25, 1.5, (70, 200, 230))
    icon('oscilloscope', draw)


# ---------------------------------------------------------------- Batteries

def read_obj_parts(path, keep):
    """Parts of an OBJ file as {part: (material, [(v, vt) triangles])}, with coordinates instead of indices."""
    v, vt, parts, part, mtl = [], [], {}, None, None
    for line in open(path, encoding='utf-8', errors='replace'):
        w = line.split()
        if not w:
            continue
        if w[0] == 'o':
            part = w[1]
        elif w[0] == 'v':
            v.append(tuple(map(float, w[1:4])))
        elif w[0] == 'vt':
            vt.append(tuple(map(float, w[1:3])))
        elif w[0] == 'usemtl':
            mtl = w[1]
        elif w[0] == 'f' and part in keep:
            tri = []
            for c in w[1:]:
                ids = c.split('/')
                tri.append((v[int(ids[0]) - 1], vt[int(ids[1]) - 1] if len(ids) > 1 and ids[1] else (0, 0)))
            parts.setdefault(part, []).append((mtl, tri))
    return parts


def battery_chemistries():
    base = os.path.join(MODEL, 'batterybig')
    copied = read_obj_parts(os.path.join(base, 'batterybig.obj'), {'main', 'plugPlus', 'plugMinus'})

    c = Canvas(64, 64)
    s = swatches(c, [
        ('lead', (32, 33, 36), 4), ('leadtop', (60, 62, 66), 4), ('cap', (150, 152, 150), 6), ('red', RED, 0),
        ('blackpost', (20, 20, 22), 0), ('nicd', (230, 185, 30), 8), ('nicdcap', (200, 200, 200), 6),
        ('frame', STEEL_DARK, 5), ('liion', (45, 90, 170), 5), ('pcb', (30, 120, 60), 8), ('wire', ORANGE, 0),
        ('lfp', (40, 110, 115), 5), ('busbar', (210, 210, 215), 6), ('label', WHITE, 2), ('steel', STEEL, 6),
        ('lfpdark', (25, 70, 75), 4),
    ])
    m = Model('batterychem', 64, 64)
    # The plinth and terminals of the original battery block, so all batteries connect the same way.
    for part in ('main', 'plugPlus', 'plugMinus'):
        for mtl, tri in copied[part]:
            corners = [p for p, _ in tri]
            corners.append(corners[2])
            uvs = [(t[0] * 32, (1 - t[1]) * 32) for _, t in tri]
            uvs.append(uvs[2])
            # The base keeps its own 32x32 texture through its own material.
            vi = [m._vert(p) for p in corners[:3]]
            ti = []
            for (u, vv) in uvs[:3]:
                m.vt.append((u / 32, 1 - vv / 32))
                ti.append(len(m.vt))
            m._part(part, 'base').append(tuple(zip(vi, ti)))

    top = -0.31  # the plinth's top
    # Lead-acid: a black monobloc with six vent caps and red and black posts.
    m.box('battery_leadacid', 'cells', (-0.25, top, -0.34), (0.25, 0.2, 0.34), s['lead'])
    m.box('battery_leadacid', 'cells', (-0.26, 0.2, -0.35), (0.26, 0.24, 0.35), s['leadtop'])
    for k in range(6):
        z = -0.27 + k * 0.108
        m.cylinder('battery_leadacid', 'cells', 'y', (0.08, 0.24, z), 0.035, 0.03, s['cap'], s['cap'], segments=8)
    m.cylinder('battery_leadacid', 'cells', 'y', (-0.15, 0.24, -0.25), 0.04, 0.06, s['red'], s['red'], segments=10)
    m.cylinder('battery_leadacid', 'cells', 'y', (-0.15, 0.24, 0.25), 0.04, 0.06, s['blackpost'], s['blackpost'], segments=10)
    m.box('battery_leadacid', 'cells', (-0.251, -0.1, -0.2), (-0.25, 0.08, 0.2), s['label'], faces='x')

    # NiCd: a steel frame holding ten yellow cells.
    m.box('battery_nicd', 'cells', (-0.27, top, -0.35), (0.27, top + 0.04, 0.35), s['frame'])
    m.box('battery_nicd', 'cells', (-0.27, 0.16, -0.35), (0.27, 0.19, 0.35), s['frame'])
    for x in (-0.13, 0.13):
        for k in range(5):
            z = -0.27 + k * 0.135
            m.cylinder('battery_nicd', 'cells', 'y', (x, top + 0.04, z), 0.06, 0.16 - (top + 0.04), s['nicd'], s['nicdcap'], segments=12)
            m.cylinder('battery_nicd', 'cells', 'y', (x, 0.19, z), 0.025, 0.03, s['nicdcap'], s['nicdcap'], segments=8)
    for x in (-0.27, 0.24):
        for z in (-0.35, 0.32):
            m.box('battery_nicd', 'cells', (x, top, z), (x + 0.03, 0.19, z + 0.03), s['frame'])

    # Li-ion: a blue pack with its management board and an orange lead on top.
    m.box('battery_liion', 'cells', (-0.24, top, -0.33), (0.24, 0.14, 0.33), s['liion'])
    m.box('battery_liion', 'cells', (-0.18, 0.14, -0.26), (0.18, 0.17, 0.2), s['pcb'])
    m.box('battery_liion', 'cells', (-0.05, 0.17, -0.2), (0.05, 0.2, -0.1), s['blackpost'])
    m.box('battery_liion', 'cells', (-0.02, 0.14, 0.2), (0.02, 0.18, 0.33), s['wire'])
    for k in range(4):
        y = top + 0.06 + k * 0.1
        m.box('battery_liion', 'cells', (-0.245, y, -0.3), (-0.24, y + 0.06, 0.3), s['steel'], faces='x')
    m.box('battery_liion', 'cells', (0.24, -0.05, -0.15), (0.245, 0.05, 0.15), s['label'], faces='X')

    # LiFePO4: four prismatic cells with bright busbars.
    for k in range(4):
        z = -0.33 + k * 0.165
        m.box('battery_lifepo4', 'cells', (-0.24, top, z), (0.24, 0.16, z + 0.155), s['lfp'])
        m.box('battery_lifepo4', 'cells', (-0.24, 0.16, z), (0.24, 0.17, z + 0.155), s['lfpdark'])
        for x in (-0.14, 0.14):
            m.cylinder('battery_lifepo4', 'cells', 'y', (x, 0.17, z + 0.0775), 0.03, 0.03, s['busbar'], s['busbar'], segments=8)
    for k in range(3):
        z = -0.33 + k * 0.165 + 0.0775
        x = 0.14 if k % 2 == 0 else -0.14
        m.box('battery_lifepo4', 'cells', (x - 0.03, 0.2, z), (x + 0.03, 0.215, z + 0.165), s['busbar'])

    m.write('batterychem', {'base': 'batterybase.png', 'cells': 'batterychem.png'}, strings={'type': 'B'})
    c.save(os.path.join(MODEL, 'batterychem', 'batterychem.png'))
    w, h, px = png_read(os.path.join(base, 'batterybase.png'))
    png_write(os.path.join(MODEL, 'batterychem', 'batterybase.png'), w, h, px)

    def battery_icon(name, body, top_colour, detail):
        def draw(i):
            i.rect(4, 26, 28, 30, (120, 120, 125))
            i.rect(6, 8, 26, 26, body)
            i.frame(6, 8, 26, 26, tuple(min(255, k + 40) for k in body), tuple(max(0, k - 30) for k in body))
            i.rect(5, 6, 27, 9, top_colour)
            detail(i)
        icon(name, draw)

    battery_icon('lead-acidbattery', (32, 33, 36), (60, 62, 66), lambda i: (
        i.rect(8, 3, 12, 6, RED), i.rect(20, 3, 24, 6, (20, 20, 22)), i.rect(10, 14, 22, 20, WHITE)))
    battery_icon('nickel-cadmiumbattery', (230, 185, 30), STEEL_DARK, lambda i: [
        i.rect(9 + k * 4, 9, 10 + k * 4, 26, (180, 140, 20)) for k in range(4)])
    battery_icon('lithium-ionbattery', (45, 90, 170), (30, 120, 60), lambda i: (
        i.rect(14, 3, 18, 6, ORANGE), i.rect(10, 15, 22, 19, WHITE)))
    battery_icon('lifepo4battery', (40, 110, 115), (210, 210, 215), lambda i: [
        i.rect(11 + k * 5, 9, 12 + k * 5, 26, (25, 70, 75)) for k in range(3)])


# ---------------------------------------------------------------- Generators

def recolour(src, dst, fn):
    w, h, px = png_read(src)
    for i, p in enumerate(px):
        px[i] = fn(i % w, i // w, p)
    png_write(dst, w, h, px)


def copy_model(src_folder, src_name, dst_folder, dst_name, textures):
    """Copies an OBJ model under a new name, pointing its materials at new textures {old file: new file}."""
    s = os.path.join(MODEL, src_folder)
    d = os.path.join(MODEL, dst_folder)
    os.makedirs(d, exist_ok=True)
    obj = open(os.path.join(s, src_name + '.obj'), encoding='utf-8', errors='replace').read()
    obj = obj.replace('mtllib %s.mtl' % src_name, 'mtllib %s.mtl' % dst_name)
    lines = obj.splitlines()
    out = [l for l in lines if not l.startswith('mtllib')]
    out.insert(0, 'mtllib %s.mtl' % dst_name)
    open(os.path.join(d, dst_name + '.obj'), 'w', newline='\n').write('\n'.join(out) + '\n')
    mtl = open(os.path.join(s, src_name + '.mtl'), encoding='utf-8', errors='replace').read()
    for old, new in textures.items():
        mtl = mtl.replace(old, new)
    open(os.path.join(d, dst_name + '.mtl'), 'w', newline='\n').write(mtl)
    txt = os.path.join(s, src_name + '.txt')
    if os.path.exists(txt):
        open(os.path.join(d, dst_name + '.txt'), 'w', newline='\n').write(open(txt, encoding='utf-8').read())


def mono_solar_panel():
    """The 2x3 panel with black monocrystalline cells and silver busbars instead of blue polycrystalline ones."""
    copy_model('solarpanel2x3', 'bigsolarpanel', 'monosolarpanel', 'monosolarpanel',
               {'map_Kd bigSolarPanel.png': 'map_Kd monosolarpanel.png', 'map_Kd bigsolarpanel.png': 'map_Kd monosolarpanel.png',
                'map_Kd BigSolarPanel.png': 'map_Kd monosolarpanel.png'})
    mtl = open(os.path.join(MODEL, 'monosolarpanel', 'monosolarpanel.mtl')).read()
    if 'monosolarpanel.png' not in mtl:
        import re
        mtl = re.sub(r'map_Kd .*', 'map_Kd monosolarpanel.png', mtl)
        open(os.path.join(MODEL, 'monosolarpanel', 'monosolarpanel.mtl'), 'w', newline='\n').write(mtl)

    def cell(x, y, p):
        r, g, b, a = p
        if b > r + 25 and b > g:  # the blue cells
            lum = (r + g + b) / 3
            v = 14 + lum * 0.25
            if x % 16 in (7, 8) or y % 16 in (0,):
                return [205, 208, 212, a]  # busbars
            if (x % 16 in (0, 15)) and (y % 16 in (0, 15)):
                return [230, 230, 235, a]  # the white corners of round-cut cells
            return [v, v + 2, v + 10, a]
        return p
    recolour(os.path.join(MODEL, 'solarpanel2x3', 'bigsolarpanel.png'),
             os.path.join(MODEL, 'monosolarpanel', 'monosolarpanel.png'), cell)

    def draw(i):
        i.rect(2, 6, 30, 24, (190, 192, 196))
        for gx in range(3):
            for gy in range(2):
                x0, y0 = 3 + gx * 9, 7 + gy * 8
                i.rect(x0, y0, x0 + 8, y0 + 7, (18, 20, 30))
                i.rect(x0 + 3, y0, x0 + 4, y0 + 7, (170, 172, 176))
        i.rect(14, 24, 18, 30, (120, 122, 126))
    icon('monocrystallinesolarpanel', draw)


def pmg_wind_turbine():
    """The small wind turbine with white blades and a red nacelle stripe, for the permanent-magnet version."""
    copy_model('windturbinemini', 'windturbinemini', 'pmgwindturbine', 'pmgwindturbine', {})
    mtl_path = os.path.join(MODEL, 'pmgwindturbine', 'pmgwindturbine.mtl')
    import re
    mtl = open(mtl_path).read()
    mtl = re.sub(r'map_Kd [Ww]ind[Tt]urbine[Mm]ini\.png', 'map_Kd pmgwindturbine.png', mtl)
    open(mtl_path, 'w', newline='\n').write(mtl)
    w, h, px = png_read(os.path.join(MODEL, 'windturbinemini', 'halo.png'))
    png_write(os.path.join(MODEL, 'pmgwindturbine', 'halo.png'), w, h, px)

    def paint(x, y, p):
        r, g, b, a = p
        lum = (r + g + b) / 3
        if y < 16:  # blades
            v = 200 + lum * 0.2
            return [v, v, v + 4, a]
        if 16 <= y < 24:
            return [min(255, lum * 0.6 + 120), lum * 0.4 + 20, lum * 0.4 + 20, a]
        v = 150 + lum * 0.3
        return [v, v + 2, v + 6, a]
    recolour(os.path.join(MODEL, 'windturbinemini', 'windturbinemini.png'),
             os.path.join(MODEL, 'pmgwindturbine', 'pmgwindturbine.png'), paint)

    def draw(i):
        i.rect(15, 12, 17, 31, (200, 202, 206))
        i.rect(13, 9, 21, 13, (200, 40, 35))
        for ang in (90, 210, 330):
            a = math.radians(ang)
            i.line(14, 11, 14 + 11 * math.cos(a), 11 - 11 * math.sin(a), (240, 240, 244))
            i.line(15, 11, 15 + 11 * math.cos(a), 11 - 11 * math.sin(a), (225, 225, 230))
        i.disc(14, 11, 2, (90, 92, 96))
    icon('permanent-magnetwindturbine', draw)


# ---------------------------------------------------------------- Tools

# The brush colors of the eight lines of a power bundle (PowerBundle.LINE_DYES).
LINE_DYES = [0, 1, 14, 11, 2, 4, 5, 15]

# The brush colors, by index, as Utils.setGlColorFromDye draws cables.
DYES = [(51, 51, 51), (255, 13, 13), (51, 128, 26), (77, 51, 26), (51, 51, 255), (179, 13, 255), (51, 179, 230),
        (179, 179, 179), (102, 102, 102), (255, 128, 128), (13, 255, 13), (230, 204, 26), (102, 128, 255),
        (230, 77, 230), (255, 153, 77), (255, 255, 255)]


def breaker_box(folder='breakerbox', icon_name='breakerbox', body=(190, 192, 196), label=WHITE):
    """A consumer unit: a grey enclosure with two rows of eight white breakers, each with a color tag below it.
    Each breaker has its handle up (upN) and down (downN), of which the render draws one, and a trip LED (ledN).
    The boxes for other voltages differ in the color of the enclosure and of the label strip."""
    c = Canvas(64, 64)
    entries = [('plate', STEEL, 6), ('body', body, 4), ('dark', BLACK, 3), ('breaker', (236, 236, 230), 3),
               ('handle', (30, 30, 34), 2), ('red', RED, 0), ('rail', STEEL_DARK, 4), ('label', label, 2)]
    entries += [('dye%d' % i, d, 0) for i, d in enumerate(DYES)]
    s = swatches(c, entries)
    m = Model(folder, 64, 64)
    m.box('case', 'main', (0.0, -0.38, -0.46), (0.03, 0.38, 0.46), s['plate'])
    m.box('case', 'main', (0.03, -0.34, -0.45), (0.16, 0.34, 0.45), s['body'])
    cy = -0.02
    m.box('case', 'main', (0.16, cy - 0.03, -0.44), (0.17, cy + 0.03, 0.44), s['rail'], faces='XyYzZ')
    for line in range(8):
        cz = -0.385 + line * 0.11
        m.box('case', 'main', (0.16, cy - 0.16, cz - 0.045), (0.23, cy + 0.16, cz + 0.045), s['breaker'])
        m.box('case', 'main', (0.23, cy - 0.03, cz - 0.03), (0.235, cy + 0.03, cz + 0.03), s['dark'], faces='X')
        m.box('case', 'main', (0.16, cy - 0.215, cz - 0.04), (0.17, cy - 0.175, cz + 0.04), s['dye%d' % LINE_DYES[line]],
              faces='XyYzZ')
        m.box('up%d' % line, 'main', (0.23, cy + 0.01, cz - 0.022), (0.29, cy + 0.08, cz + 0.022), s['handle'])
        m.box('down%d' % line, 'main', (0.23, cy - 0.08, cz - 0.022), (0.29, cy - 0.01, cz + 0.022), s['handle'])
        m.box('led%d' % line, 'main', (0.23, cy + 0.105, cz - 0.016), (0.24, cy + 0.135, cz + 0.016), s['red'])
    # A label strip above the breakers.
    m.box('case', 'main', (0.16, 0.2, -0.4), (0.162, 0.26, 0.4), s['label'], faces='X')
    m.write(folder, {'main': folder + '.png'})
    c.save(os.path.join(MODEL, folder, folder + '.png'))

    def draw(i):
        i.rect(1, 2, 31, 30, body)
        i.frame(1, 2, 31, 30, tuple(min(255, v + 35) for v in body), tuple(max(0, v - 80) for v in body))
        if label != WHITE:
            i.rect(3, 3, 29, 4, label)
        for k in range(8):
            x = 3 + k * 3 + (1 if k >= 4 else 0)
            i.rect(x, 9, x + 2, 21, (236, 236, 230))
            i.rect(x, 12, x + 2, 15, (30, 30, 34))
            i.rect(x, 23, x + 2, 25, DYES[LINE_DYES[k]])
    icon(icon_name, draw)


def power_bundle_cable(sprite='powerbundle', icon_name='powerbundlecable', jacket=(38, 40, 44)):
    """The cable sprite (a dark jacket flecked with the colors of its sixteen cores) and its icon."""
    rnd = random.Random(16)
    c = Canvas(32, 16)
    c.rect(0, 0, 32, 16, jacket, 6)
    for y in range(0, 16, 2):
        for x in range(0, 32, 2):
            if rnd.random() < 0.35:
                c.rect(x, y, x + 1, y + 1, DYES[rnd.randrange(16)])
    c.save(os.path.join(ROOT, 'sprites', sprite + '.png'))

    def draw(i):
        i.rect(0, 5, 16, 11, jacket)
        i.rect(0, 5, 16, 6, tuple(min(255, v + 32) for v in jacket))
        for k in range(8):
            i.rect(k * 2, 7, k * 2 + 1, 8, DYES[(k * 2) % 16])
            i.rect(k * 2 + 1, 9, k * 2 + 2, 10, DYES[(k * 2 + 1) % 16])
    icon(icon_name, draw, size=16)


def supply_cable_icon(name, band):
    """A thick black cable, with a band in the color of its voltage."""
    def draw(i):
        i.rect(0, 4, 16, 12, (34, 35, 38))
        i.rect(0, 4, 16, 5, (70, 72, 78))
        i.rect(6, 4, 10, 12, band)
    icon(name, draw, size=16)


def lockout_padlock():
    """A red safety padlock with a steel shackle, as used for lockout/tagout."""
    def draw(i):
        i.rect(5, 2, 11, 3, (170, 174, 180))
        i.rect(4, 3, 6, 8, (170, 174, 180))
        i.rect(10, 3, 12, 8, (170, 174, 180))
        i.rect(3, 7, 13, 15, (200, 30, 28))
        i.frame(3, 7, 13, 15, (235, 70, 60), (120, 15, 15))
        i.rect(7, 9, 9, 12, (40, 20, 20))
        i.rect(5, 13, 11, 14, (240, 240, 240))
    icon('lockoutpadlock', draw, size=16, folder=ITEMS)


def substation(folder, icon_name, label):
    """A substation: a concrete pad, a transformer tank with radiators and a conservator, tall high voltage bushings on
    the left (-z) and shorter low voltage ones on the right (+z), a breaker cabinet on each side, a control panel at
    the front (-x) and fence posts. The node block is at the middle of the front edge; it spans x -0.5..2.5,
    z -1.5..1.5, y -0.5..2.5. Grid lines hang from p0/g0 (high voltage) and p1/g1 (low voltage). The lamps lamphv and
    lamplv show the breakers: drawn lit green when closed, red parts tripphv/triplv when tripped."""
    c = Canvas(64, 64)
    s = swatches(c, [
        ('concrete', (150, 148, 142), 10), ('tank', (88, 104, 92), 6), ('fin', (74, 88, 78), 4),
        ('porcelain', (120, 70, 45), 6), ('cap', (170, 174, 180), 4), ('cabinet', (165, 168, 172), 5),
        ('dark', BLACK, 3), ('green', GREEN, 0), ('red', RED, 0), ('label', label, 0), ('steel', STEEL, 6),
        ('panel', (40, 44, 48), 3), ('yellow', YELLOW, 0), ('lcd', LCD, 2),
    ])
    m = Model(folder, 64, 64)
    # Pad and fence posts.
    m.box('main', 'main', (-0.5, -0.5, -1.5), (2.5, -0.38, 1.5), s['concrete'])
    for x in (-0.45, 2.38):
        for z in (-1.45, 1.38):
            m.box('main', 'main', (x, -0.38, z), (x + 0.07, 1.0, z + 0.07), s['steel'])
    # Tank, radiators at the back, conservator on top.
    m.box('main', 'main', (0.3, -0.38, -0.75), (1.9, 1.3, 0.75), s['tank'])
    for z in [-0.6 + 0.2 * i for i in range(7)]:
        m.box('main', 'main', (1.9, -0.2, z - 0.04), (2.3, 1.15, z + 0.04), s['fin'])
    m.cylinder('main', 'main', 'z', (1.4, 1.6, -0.6), 0.22, 1.2, s['tank'], s['fin'])
    m.box('main', 'main', (1.3, 1.3, -0.05), (1.5, 1.4, 0.05), s['fin'])
    # Warning label and control panel at the front.
    m.box('main', 'main', (0.29, 0.7, -0.35), (0.3, 1.0, 0.35), s['yellow'], faces='x')
    m.box('main', 'main', (-0.4, -0.38, -0.3), (-0.1, 0.9, 0.3), s['cabinet'])
    m.box('main', 'main', (-0.41, 0.4, -0.22), (-0.4, 0.8, 0.22), s['lcd'], faces='x')
    m.box('main', 'main', (-0.41, 0.1, -0.25), (-0.4, 0.2, 0.25), s['label'], faces='x')
    # Breaker cabinets: high voltage on the left, low voltage on the right.
    for z0, z1 in ((-1.4, -0.95), (0.95, 1.4)):
        m.box('main', 'main', (0.2, -0.38, z0), (1.3, 0.9, z1), s['cabinet'])
    m.box('lamphv', 'main', (0.19, 0.7, -1.3), (0.2, 0.8, -1.2), s['green'], faces='x')
    m.box('triphv', 'main', (0.19, 0.55, -1.3), (0.2, 0.65, -1.2), s['red'], faces='x')
    m.box('lamplv', 'main', (0.19, 0.7, 1.2), (0.2, 0.8, 1.3), s['green'], faces='x')
    m.box('triplv', 'main', (0.19, 0.55, 1.2), (0.2, 0.65, 1.3), s['red'], faces='x')

    # Bushings: porcelain stacks with a metal cap; the lines hang from the caps.
    def bushing(x, z, height, tip):
        y = 1.3
        while y < 1.3 + height:
            m.cylinder('main', 'main', 'y', (x, y, z), 0.09, 0.06, s['porcelain'], s['porcelain'])
            m.cylinder('main', 'main', 'y', (x, y + 0.06, z), 0.06, 0.05, s['porcelain'], s['porcelain'])
            y += 0.11
        m.cylinder('main', 'main', 'y', (x, y, z), 0.05, 0.05, s['cap'], s['cap'])
        m.box(tip, 'main', (x - 0.03, y + 0.05, z - 0.03), (x + 0.03, y + 0.11, z + 0.03), s['cap'])

    bushing(0.7, -0.55, 0.95, 'p0')
    bushing(1.4, -0.55, 0.95, 'g0')
    bushing(0.7, 0.55, 0.5, 'p1')
    bushing(1.4, 0.55, 0.5, 'g1')
    m.write(folder, {'main': folder + '.png'})
    c.save(os.path.join(MODEL, folder, folder + '.png'))

    def draw(i):
        i.rect(1, 26, 31, 30, (150, 148, 142))
        i.rect(8, 12, 24, 26, (88, 104, 92))
        for x in range(24, 30, 2):
            i.rect(x, 14, x + 1, 25, (74, 88, 78))
        i.rect(2, 18, 7, 26, (165, 168, 172))
        i.rect(3, 20, 6, 23, label)
        for x, top in ((11, 3), (19, 3)):
            i.rect(x, top, x + 2, 12, (120, 70, 45))
            i.rect(x - 1, top, x + 3, top + 1, (170, 174, 180))
        for x in (11, 19):
            i.rect(x, 12, x + 2, 12, (120, 70, 45))
        i.rect(9, 8, 23, 11, (74, 88, 78))
    icon(icon_name, draw)


def thermal_camera():
    def draw(i):
        i.rect(2, 4, 14, 12, (60, 62, 66))
        i.frame(2, 4, 14, 12, (95, 97, 102), (30, 31, 34))
        i.rect(4, 12, 7, 15, (45, 46, 50))
        i.disc(11.5, 8, 2.2, (20, 20, 24))
        i.disc(11.5, 8, 1.2, (90, 60, 140))
        for x in range(3, 9):
            for y in range(5, 11):
                t = (x - 3) / 5 + (y - 5) / 10
                col = (min(255, int(60 + 300 * t)), int(max(0, 220 * (t - 0.35))), int(max(0, 200 * (0.6 - t))))
                i.set(x, y, col)
    icon('thermalcamera', draw, size=16, folder=ITEMS)


if __name__ == '__main__':
    protection_relay()
    bms()
    transfer_switch()
    mppt()
    oscilloscope()
    battery_chemistries()
    mono_solar_panel()
    pmg_wind_turbine()
    thermal_camera()
    breaker_box(icon_name='200vbreakerbox')
    power_bundle_cable(icon_name='200vpowerbundlecable')
    # The 50 V and 600 V breaker boxes, and their bundles: orange for 600 V, as high voltage cables are, green for 50 V.
    breaker_box('breakerbox50', '50vbreakerbox', body=(206, 214, 222), label=GREEN)
    breaker_box('breakerbox800', '800vbreakerbox', body=(72, 76, 82), label=ORANGE)
    power_bundle_cable('powerbundlehv', '800vpowerbundlecable', jacket=(170, 70, 20))
    power_bundle_cable('powerbundlelv', '50vpowerbundlecable', jacket=(46, 96, 58))
    lockout_padlock()
    for volts, colour in (('50v', GREEN), ('200v', BLUE), ('800v', ORANGE)):
        supply_cable_icon(volts + 'supplycable', colour)
    supply_cable_icon('3200vsupplycable', (150, 60, 200))
    # Substations: a distribution substation (51.2 kV to 12.8 kV lines) and a step-up one (3.2 kV cables to 51.2 kV).
    substation('substation', 'distributionsubstation', BLUE)
    substation('substationstepup', 'stepupsubstation', ORANGE)
    print('done')
