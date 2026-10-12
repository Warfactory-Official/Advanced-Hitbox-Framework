#!/usr/bin/env python3
"""AHF asset tool. `body`: jointed player body GLBs (wide, slim) -> common/src/main/resources/assets/ahf/models/."""
from __future__ import annotations

import argparse
import json
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'common/src/main/resources/assets/ahf/models'

# PlayerModel parts (1.21.1): name, joint chain (top half, half-angle ring, bottom half), texOffs, box origin, size, grow,
# pivot. Split = cube centre (bendy-lib / PlayerAnimator bend pivot). Torso: top half bends (head + arms follow).
WIDE_ARM = {'right': ((-3, -2, -2), (4, 12, 4), (-5, 2, 0)), 'left': ((-1, -2, -2), (4, 12, 4), (5, 2, 0))}
SLIM_ARM = {'right': ((-2, -2, -2), (3, 12, 4), (-5, 2.5, 0)), 'left': ((-1, -2, -2), (3, 12, 4), (5, 2.5, 0))}


def parts(slim):
    arm = SLIM_ARM if slim else WIDE_ARM
    out = [('head', ('head',), (0, 0), (-4, -8, -4), (8, 8, 8), 0, (0, 0, 0)),
           ('hat', ('head',), (32, 0), (-4, -8, -4), (8, 8, 8), 0.5, (0, 0, 0)),
           ('body', ('chest', 'waist', 'body'), (16, 16), (-4, 0, -2), (8, 12, 4), 0, (0, 0, 0)),
           ('jacket', ('chest', 'waist', 'body'), (16, 32), (-4, 0, -2), (8, 12, 4), 0.25, (0, 0, 0))]
    for side, skin, sleeve in (('right', (40, 16), (40, 32)), ('left', (32, 48), (48, 48))):
        o, s, p = arm[side]
        chain = (f'{side}_arm', f'{side}_elbow', f'{side}_forearm')
        out.append((f'{side}_arm', chain, skin, o, s, 0, p))
        out.append((f'{side}_sleeve', chain, sleeve, o, s, 0.25, p))
    for side, skin, pants, x in (('right', (0, 16), (0, 32), -1.9), ('left', (16, 48), (0, 48), 1.9)):
        chain = (f'{side}_leg', f'{side}_knee', f'{side}_shin')
        out.append((f'{side}_leg', chain, skin, (-2, 0, -2), (4, 12, 4), 0, (x, 12, 0)))
        out.append((f'{side}_pants', chain, pants, (-2, 0, -2), (4, 12, 4), 0.25, (x, 12, 0)))
    return out


def cube(uv, origin, size, grow):
    """Vanilla ModelPart.Cube polygons (mirror off): [(normal, [(pos px, uv px) x4])], part-local model space."""
    (u, v), (x0, y0, z0), (dx, dy, dz) = uv, origin, size
    x1, y1, z1 = x0 + dx + grow, y0 + dy + grow, z0 + dz + grow
    x0, y0, z0 = x0 - grow, y0 - grow, z0 - grow
    c = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1),
         (x0, y1, z1)]
    v7, v0, v1, v2, v3, v4, v5, v6 = c
    f4, f5, f6, f7 = u, u + dz, u + dz + dx, u + dz + dx + dx
    f8, f9 = u + dz + dx + dz, u + dz + dx + dz + dx
    f10, f11, f12 = v, v + dz, v + dz + dy
    faces = [((0, -1, 0), (v4, v3, v7, v0), (f5, f10, f6, f11)), ((0, 1, 0), (v1, v2, v6, v5), (f6, f11, f7, f10)),
             ((-1, 0, 0), (v7, v3, v6, v2), (f4, f11, f5, f12)), ((0, 0, -1), (v0, v7, v2, v1), (f5, f11, f6, f12)),
             ((1, 0, 0), (v4, v0, v1, v5), (f6, f11, f8, f12)), ((0, 0, 1), (v3, v4, v5, v6), (f8, f11, f9, f12))]
    out = []
    for n, vs, (ua, va, ub, vb) in faces:
        uvs = [(ub, va), (ua, va), (ua, vb), (ub, vb)]
        out.append((n, list(zip(vs, uvs))))
    return out


def lerp(a, b, t):
    return tuple(x + (y - x) * t for x, y in zip(a, b))


class Glb:
    def __init__(self):
        self.bin = bytearray()
        self.doc = {'asset': {'version': '2.0', 'generator': 'AHF tools/ahf.py body'}, 'scene': 0,
                    'scenes': [{'nodes': [0]}], 'nodes': [], 'meshes': [], 'skins': [], 'accessors': [],
                    'bufferViews': [], 'buffers': [],
                    'materials': [{'name': 'player_skin', 'extras': {'textureSlot': 'player_skin'},
                                   'alphaMode': 'BLEND', 'doubleSided': False,
                                   'pbrMetallicRoughness': {'metallicFactor': 0.0, 'roughnessFactor': 1.0}}]}

    def accessor(self, rows, kind, fmt, component, target=None, bounds=False):
        while len(self.bin) % 4:
            self.bin.append(0)
        start = len(self.bin)
        for r in rows:
            self.bin += struct.pack('<' + fmt * (len(r) if isinstance(r, tuple) else 1), *(r if isinstance(r, tuple) else (r,)))
        view = {'buffer': 0, 'byteOffset': start, 'byteLength': len(self.bin) - start}
        if target:
            view['target'] = target
        self.doc['bufferViews'].append(view)
        acc = {'bufferView': len(self.doc['bufferViews']) - 1, 'componentType': component, 'count': len(rows),
               'type': kind}
        if bounds:
            acc['min'] = [min(r[i] for r in rows) for i in range(3)]
            acc['max'] = [max(r[i] for r in rows) for i in range(3)]
        self.doc['accessors'].append(acc)
        return len(self.doc['accessors']) - 1

    def encode(self):
        while len(self.bin) % 4:
            self.bin.append(0)
        self.doc['buffers'] = [{'byteLength': len(self.bin)}]
        js = json.dumps(self.doc, separators=(',', ':')).encode()
        js += b' ' * (-len(js) % 4)
        total = 12 + 8 + len(js) + 8 + len(self.bin)
        return (struct.pack('<III', 0x46546C67, 2, total) + struct.pack('<II', len(js), 0x4E4F534A) + js
                + struct.pack('<II', len(self.bin), 0x004E4942) + bytes(self.bin))


def to_gltf(p):
    """Model space px (y down, front -z, pivot origin 24 px above the feet) -> glTF blocks (y up, feet 0, front +z)."""
    return (p[0] / 16, (24 - p[1]) / 16, -p[2] / 16)


def body(slim):
    g = Glb()
    nodes = g.doc['nodes']
    nodes.append({'name': 'player', 'children': []})
    joints, pivots = [], {}

    def joint(name, pivot_px, parent):
        nodes.append({'name': name, 'translation': [0.0, 0.0, 0.0]})
        index = len(nodes) - 1
        pivots[name] = pivot_px
        world = to_gltf(pivot_px)
        base = to_gltf(pivots[parent]) if parent else (0.0, 0.0, 0.0)
        nodes[index]['translation'] = [w - b for w, b in zip(world, base)]
        nodes[nodes_index[parent] if parent else 0].setdefault('children', []).append(index)
        nodes_index[name] = index
        joints.append(name)

    nodes_index = {}
    arm = SLIM_ARM if slim else WIDE_ARM
    joint('body', (0, 0, 0), None)
    joint('waist', (0, 6, 0), 'body')
    joint('chest', (0, 6, 0), 'body')
    joint('head', (0, 0, 0), 'chest')
    for side in ('right', 'left'):
        o, s, p = arm[side]
        cx = o[0] + s[0] / 2
        joint(f'{side}_arm', p, 'chest')
        elbow = (p[0] + cx, p[1] + o[1] + s[1] / 2, p[2])
        joint(f'{side}_elbow', elbow, f'{side}_arm')
        joint(f'{side}_forearm', elbow, f'{side}_arm')
    for side, x in (('right', -1.9), ('left', 1.9)):
        joint(f'{side}_leg', (x, 12, 0), None)
        joint(f'{side}_knee', (x, 18, 0), f'{side}_leg')
        joint(f'{side}_shin', (x, 18, 0), f'{side}_leg')

    inverse = []
    for name in joints:
        t = to_gltf(pivots[name])
        inverse.append((1.0, 0, 0, 0, 0, 1.0, 0, 0, 0, 0, 1.0, 0, -t[0], -t[1], -t[2], 1.0))
    g.doc['skins'].append({'name': 'player', 'joints': [nodes_index[j] for j in joints],
                           'inverseBindMatrices': g.accessor(inverse, 'MAT4', 'f', 5126)})

    primitives = []
    for name, chain, uv, origin, size, grow, pivot in parts(slim):
        cut = origin[1] + size[1] / 2 if len(chain) == 3 else None
        pos, nrm, tex, jnt, quads = [], [], [], [], []

        def vert(p_local, uv_px, n, y_local):
            j = chain[0] if cut is None or y_local < cut - 1e-6 else chain[1] if y_local < cut + 1e-6 else chain[2]
            model = (p_local[0] + pivot[0], p_local[1] + pivot[1], p_local[2] + pivot[2])
            pos.append(to_gltf(model))
            nrm.append((float(n[0]), float(-n[1]), float(-n[2])))
            tex.append((uv_px[0] / 64, uv_px[1] / 64))
            jnt.append((joints.index(j), 0, 0, 0))
            return len(pos) - 1

        for n, vs in cube(uv, origin, size, grow):
            if n[1] != 0 or cut is None:
                y = vs[0][0][1]
                quads.append([vert(p, t, n, y) for p, t in vs])
                continue
            # Side face: v0, v1 at min y; v3, v2 at max y. Split at the bend (y = cut), edges 0->3 and 1->2.
            (p0, t0), (p1, t1), (p2, t2), (p3, t3) = vs
            lo, hi = p0[1], p3[1]
            for a, b in ((lo, cut), (cut, hi)):
                ta, tb = (a - lo) / (hi - lo), (b - lo) / (hi - lo)
                ring = [(lerp(p0, p3, ta), lerp(t0, t3, ta), a), (lerp(p1, p2, ta), lerp(t1, t2, ta), a),
                        (lerp(p1, p2, tb), lerp(t1, t2, tb), b), (lerp(p0, p3, tb), lerp(t0, t3, tb), b)]
                ys = [a, a, b, b]
                quads.append([vert(p, t, n, y) for (p, t, _), y in zip(ring, ys)])
        tris = [i for q in quads for i in (q[0], q[1], q[2], q[0], q[2], q[3])]
        primitives.append({'attributes': {
            'POSITION': g.accessor(pos, 'VEC3', 'f', 5126, 34962, bounds=True),
            'NORMAL': g.accessor(nrm, 'VEC3', 'f', 5126, 34962),
            'TEXCOORD_0': g.accessor(tex, 'VEC2', 'f', 5126, 34962),
            'JOINTS_0': g.accessor(jnt, 'VEC4', 'H', 5123, 34962),
            'WEIGHTS_0': g.accessor([(1.0, 0.0, 0.0, 0.0)] * len(pos), 'VEC4', 'f', 5126, 34962)},
            'indices': g.accessor(tris, 'SCALAR', 'H', 5123, 34963), 'material': 0,
            'extras': {'part': name}})
    g.doc['meshes'].append({'name': 'player', 'primitives': primitives})
    nodes.append({'name': 'player_mesh', 'mesh': 0, 'skin': 0})
    nodes[0]['children'].append(len(nodes) - 1)
    return g.encode()


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest='command', required=True)
    sub.add_parser('body', help='jointed player body GLBs (wide, slim)')
    args = ap.parse_args()
    if args.command == 'body':
        OUT.mkdir(parents=True, exist_ok=True)
        for slim in (False, True):
            path = OUT / f'player_{"slim" if slim else "wide"}.glb'
            path.write_bytes(body(slim))
            print(path)


if __name__ == '__main__':
    main()
