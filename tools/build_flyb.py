#!/usr/bin/env python3
"""
Build the compact FLYB connectome binary consumed by the mod from the raw neuPrint cache
produced by tools/fetch_neuprint.py.

FLYB v1 layout (all little-endian; whole file gzip-compressed):
  char[4] "FLYB", u32 version=1, u32 nNeurons, u32 nEdges, u32 nRetina
  str16 dataset                         (str16 = u16 byteLen + utf8)
  str32 metaJson                        (u32 byteLen + utf8; provenance + stats)
  string tables (u16 count, then count x str16), index 0 is always "" (missing):
      types, superclasses, classes, subclasses, nts, sides, dimorphisms, fruDsx, neuromeres, nerves
  neuron arrays (struct of arrays, n = nNeurons, sorted by bodyId):
      i64[n] bodyId
      i32[n] typeIdx
      u8[n]  superclassIdx, u8[n] classIdx, u16[n] subclassIdx
      u8[n]  ntIdx, i8[n] ntSign          (+1 excitatory, -1 inhibitory, 0 unknown/neuromodulatory)
      u8[n]  sideIdx
      i8[n]  hex1, i8[n] hex2             (-1 when absent; medulla column coordinates)
      u8[n]  dimorphismIdx, u8[n] fruDsxIdx, u8[n] neuromereIdx, u8[n] nerveIdx
      f32[3n] soma x,y,z (raw neuPrint voxel coordinates; NaN when absent)
      i32[n] preSynapses, i32[n] postSynapses
  edges as CSR over presynaptic neuron index:
      i32[n+1] rowPtr, i32[nEdges] postIdx, u16[nEdges] weight (synapse count, clipped to 65535)
  retina table (nRetina entries): i32 neuronIdx, u8 sideIdx, i8 hex1, i8 hex2, u8 kind
      kind: 0 = R1-R6, 1 = R7, 2 = R8, 3 = L1, 4 = L2, 5 = L3

Usage:
  python tools/build_flyb.py                           # data/raw/male-cns_v1.0 -> src/main/resources/connectome/malecns-v1.0.flyb.gz
  python tools/build_flyb.py --min-weight 10 --out data/cache/test.flyb.gz
  python tools/build_flyb.py --exclude-superclass ol_intrinsic,visual_projection   # smaller brain-only build
"""
import argparse
import gzip
import io
import json
import math
import os
import struct
import sys
import time
from collections import Counter, defaultdict
from datetime import datetime, timezone

import numpy as np
import pandas as pd

# Sign convention of Shiu et al. 2024 (Nature): GABA and glutamate inhibitory; every other neuron excitatory
# ("Neurons predicted to be dopaminergic, octopaminergic or serotonergic are assigned to the excitatory category").
# Histamine (photoreceptors, T1) is inhibitory via histamine-gated chloride channels on L1-L3.
DEFAULT_NT_SIGNS = {
    "acetylcholine": 1,
    "glutamate": -1,     # adult Drosophila glutamate acts mostly through GluCl -> inhibitory
    "gaba": -1,
    "histamine": -1,     # photoreceptors -> lamina (sign-inverting)
    "dopamine": 1,
    "octopamine": 1,
    "serotonin": 1,
    "unclear": 1,
    "": 1,
}

KNOWN_NTS = ["acetylcholine", "glutamate", "gaba", "histamine", "dopamine", "octopamine", "serotonin", "unclear"]
RETINA_KINDS = {"R1-R6": 0, "R7": 1, "R8": 2, "L1": 3, "L2": 4, "L3": 5}


def s16(b, text):
    data = text.encode("utf-8")
    b.write(struct.pack("<H", len(data)))
    b.write(data)


def s32(b, text):
    data = text.encode("utf-8")
    b.write(struct.pack("<I", len(data)))
    b.write(data)


def make_table(values):
    """Return (table list with '' at index 0, dict value->index)."""
    uniq = sorted({v for v in values if isinstance(v, str) and v != ""})
    table = [""] + uniq
    return table, {v: i for i, v in enumerate(table)}


def write_table(b, table):
    if len(table) > 65535:
        raise ValueError("string table too large")
    b.write(struct.pack("<H", len(table)))
    for t in table:
        s16(b, t)


def resolve_nt(row, conf_threshold):
    nt = row["consensusNt"]
    if nt in KNOWN_NTS and nt != "unclear":
        return nt
    pnt = row["predictedNt"]
    conf = row["predictedNtConfidence"]
    if pnt in KNOWN_NTS and pnt != "unclear" and conf >= conf_threshold:
        return pnt
    cnt = row["celltypePredictedNt"]
    if cnt in KNOWN_NTS and cnt != "unclear":
        return cnt
    return "unclear"


def retina_kind(t):
    if t == "R1-R6":
        return 0
    if t.startswith("R7") and not t.startswith("R7R8"):
        return 1
    if t.startswith("R8"):
        return 2
    if t == "R7R8_unclear":
        return 1
    return -1


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--raw", default=os.path.join("data", "raw", "male-cns_v1.0"))
    ap.add_argument("--dataset", default="male-cns:v1.0")
    ap.add_argument("--out", default=os.path.join("src", "main", "resources", "connectome", "malecns-v1.0.flyb.gz"))
    ap.add_argument("--stats", default=os.path.join("docs", "connectome-stats.json"))
    ap.add_argument("--min-weight", type=int, default=5)
    ap.add_argument("--nt-conf", type=float, default=0.5, help="min predictedNt confidence used as fallback")
    ap.add_argument("--nt-signs", default="", help="override, e.g. 'unclear=1,dopamine=1'")
    ap.add_argument("--exclude-superclass", default="", help="comma list of superclasses to drop")
    ap.add_argument("--drop-orphans", action="store_true", help="drop neurons with no kept edges in either direction")
    ap.add_argument("--voxel-nm", type=float, default=8.0, help="assumed voxel size of somaLocation (recorded in meta only)")
    args = ap.parse_args()

    nt_signs = dict(DEFAULT_NT_SIGNS)
    if args.nt_signs:
        for kv in args.nt_signs.split(","):
            k, v = kv.split("=")
            nt_signs[k.strip()] = int(v)

    t0 = time.time()
    neurons_path = os.path.join(args.raw, "neurons.csv.gz")
    edges_path = os.path.join(args.raw, "edges.csv.gz")
    print("reading", neurons_path, flush=True)
    nd = pd.read_csv(neurons_path, dtype={
        "bodyId": "int64", "type": "string", "instance": "string", "superclass": "string", "class": "string",
        "subclass": "string", "consensusNt": "string", "predictedNt": "string", "celltypePredictedNt": "string",
        "somaSide": "string", "rootSide": "string", "dimorphism": "string", "fruDsx": "string",
        "somaNeuromere": "string", "entryNerve": "string", "exitNerve": "string", "status": "string",
        "flywireType": "string", "mancType": "string", "hemibrainType": "string", "synonyms": "string",
    }, keep_default_na=True)
    for c in ["type", "superclass", "class", "subclass", "consensusNt", "predictedNt", "celltypePredictedNt",
              "somaSide", "rootSide", "dimorphism", "fruDsx", "somaNeuromere", "entryNerve", "status"]:
        nd[c] = nd[c].fillna("").astype(str)
    nd["predictedNtConfidence"] = pd.to_numeric(nd["predictedNtConfidence"], errors="coerce").fillna(0.0)
    for c in ["pre", "post", "downstream", "upstream"]:
        nd[c] = pd.to_numeric(nd[c], errors="coerce").fillna(0).astype("int64")
    for c in ["hex1", "hex2"]:
        nd[c] = pd.to_numeric(nd[c], errors="coerce")
    for c in ["somaX", "somaY", "somaZ"]:
        nd[c] = pd.to_numeric(nd[c], errors="coerce")
    nd = nd.sort_values("bodyId").reset_index(drop=True)
    print("  %d neurons" % len(nd), flush=True)

    excl = {s.strip() for s in args.exclude_superclass.split(",") if s.strip()}
    if excl:
        before = len(nd)
        nd = nd[~nd["superclass"].isin(excl)].reset_index(drop=True)
        print("  excluded superclasses %s: %d -> %d neurons" % (sorted(excl), before, len(nd)), flush=True)

    print("reading", edges_path, flush=True)
    ed = pd.read_csv(edges_path, dtype={"pre": "int64", "post": "int64", "weight": "int32"})
    print("  %d edges raw" % len(ed), flush=True)
    ed = ed[ed["weight"] >= args.min_weight]
    body_to_idx = pd.Series(np.arange(len(nd), dtype=np.int64), index=nd["bodyId"].values)
    ed = ed[ed["pre"].isin(body_to_idx.index) & ed["post"].isin(body_to_idx.index)]
    ed = ed[ed["pre"] != ed["post"]]  # drop autapses for the point-neuron model
    print("  %d edges kept (weight >= %d, both endpoints present)" % (len(ed), args.min_weight), flush=True)

    if args.drop_orphans:
        connected = set(ed["pre"].unique()) | set(ed["post"].unique())
        before = len(nd)
        nd = nd[nd["bodyId"].isin(connected)].reset_index(drop=True)
        body_to_idx = pd.Series(np.arange(len(nd), dtype=np.int64), index=nd["bodyId"].values)
        print("  dropped orphans: %d -> %d neurons" % (before, len(nd)), flush=True)

    n = len(nd)
    pre_idx = body_to_idx.loc[ed["pre"].values].values.astype(np.int32)
    post_idx = body_to_idx.loc[ed["post"].values].values.astype(np.int32)
    weights = np.minimum(ed["weight"].values, 65535).astype(np.uint16)
    order = np.lexsort((post_idx, pre_idx))
    pre_idx, post_idx, weights = pre_idx[order], post_idx[order], weights[order]
    row_ptr = np.zeros(n + 1, dtype=np.int32)
    np.add.at(row_ptr, pre_idx + 1, 1)
    row_ptr = np.cumsum(row_ptr, dtype=np.int64).astype(np.int32)
    n_edges = len(post_idx)

    # --- neurotransmitter sign ---
    nts = [resolve_nt(r, args.nt_conf) for r in nd[["consensusNt", "predictedNt", "predictedNtConfidence",
                                                     "celltypePredictedNt"]].to_dict("records")]
    nd["nt"] = nts
    nd["ntSign"] = [nt_signs.get(x, 0) for x in nts]

    # --- side ---
    side = nd["somaSide"].where(nd["somaSide"] != "", nd["rootSide"])
    nd["side"] = side.fillna("")

    # --- tables ---
    tables = {}
    idx = {}
    for name, col in [("types", "type"), ("superclasses", "superclass"), ("classes", "class"),
                      ("subclasses", "subclass"), ("nts", "nt"), ("sides", "side"), ("dimorphisms", "dimorphism"),
                      ("fruDsx", "fruDsx"), ("neuromeres", "somaNeuromere"), ("nerves", "entryNerve")]:
        tables[name], idx[name] = make_table(nd[col].tolist())
    print("  tables:", {k: len(v) for k, v in tables.items()}, flush=True)

    # --- retina table: photoreceptors get the column of their strongest hex-bearing target ---
    hex1 = nd["hex1"].fillna(-1).astype(int).values
    hex2 = nd["hex2"].fillna(-1).astype(int).values
    has_hex = hex1 > 0
    type_arr = nd["type"].values
    side_arr = nd["side"].values
    retina = []
    photoreceptors = [i for i in range(n) if nd["superclass"].iat[i] == "ol_sensory" and retina_kind(type_arr[i]) >= 0]
    assigned = 0
    for i in photoreceptors:
        a, b = row_ptr[i], row_ptr[i + 1]
        votes = defaultdict(float)
        for k in range(a, b):
            j = post_idx[k]
            if has_hex[j]:
                votes[(side_arr[j], int(hex1[j]), int(hex2[j]))] += float(weights[k])
        if votes:
            (sd, h1, h2), _ = max(votes.items(), key=lambda kv: kv[1])
            retina.append((i, idx["sides"].get(sd, 0), h1, h2, retina_kind(type_arr[i])))
            assigned += 1
    for i in range(n):
        t = type_arr[i]
        if t in ("L1", "L2", "L3") and has_hex[i]:
            retina.append((i, idx["sides"].get(side_arr[i], 0), int(hex1[i]), int(hex2[i]), RETINA_KINDS[t]))
    print("  retina: %d/%d photoreceptors mapped to columns, %d entries total" % (assigned, len(photoreceptors), len(retina)), flush=True)

    # --- stats ---
    kept_syn = int(weights.astype(np.int64).sum())
    stats = {
        "dataset": args.dataset,
        "built": datetime.now(timezone.utc).isoformat(),
        "min_weight": args.min_weight,
        "nt_signs": nt_signs,
        "nt_conf_fallback": args.nt_conf,
        "excluded_superclasses": sorted(excl),
        "voxel_nm_assumed": args.voxel_nm,
        "neurons": n,
        "edges": int(n_edges),
        "synapses_in_edges": kept_syn,
        "neurons_with_soma": int(nd["somaX"].notna().sum()),
        "superclass_counts": dict(Counter(nd["superclass"].tolist())),
        "class_counts": dict(Counter(nd["class"].tolist())),
        "nt_counts": dict(Counter(nts)),
        "sign_counts": dict(Counter(int(x) for x in nd["ntSign"].tolist())),
        "retina_entries": len(retina),
        "retina_photoreceptors_mapped": assigned,
        "retina_by_kind": dict(Counter(k for *_, k in retina)),
        "retina_by_side": dict(Counter(tables["sides"][s] for _, s, *_ in retina)),
        "descending_neurons": int((nd["superclass"] == "descending_neuron").sum()),
        "motor_neurons": int(nd["superclass"].isin(["vnc_motor", "cb_motor"]).sum()),
    }
    meta_json = json.dumps({k: v for k, v in stats.items() if k not in ("class_counts",)}, separators=(",", ":"))

    # --- write ---
    b = io.BytesIO()
    b.write(b"FLYB")
    b.write(struct.pack("<IIII", 1, n, n_edges, len(retina)))
    s16(b, args.dataset)
    s32(b, meta_json)
    for name in ["types", "superclasses", "classes", "subclasses", "nts", "sides", "dimorphisms", "fruDsx",
                 "neuromeres", "nerves"]:
        write_table(b, tables[name])

    def col_idx(name, col, dtype):
        m = idx[name]
        return np.array([m.get(v, 0) for v in nd[col].tolist()], dtype=dtype)

    b.write(nd["bodyId"].values.astype("<i8").tobytes())
    b.write(col_idx("types", "type", "<i4").tobytes())
    b.write(col_idx("superclasses", "superclass", "u1").tobytes())
    b.write(col_idx("classes", "class", "u1").tobytes())
    b.write(col_idx("subclasses", "subclass", "<u2").tobytes())
    b.write(col_idx("nts", "nt", "u1").tobytes())
    b.write(nd["ntSign"].values.astype("i1").tobytes())
    b.write(col_idx("sides", "side", "u1").tobytes())
    b.write(np.clip(hex1, -1, 127).astype("i1").tobytes())
    b.write(np.clip(hex2, -1, 127).astype("i1").tobytes())
    b.write(col_idx("dimorphisms", "dimorphism", "u1").tobytes())
    b.write(col_idx("fruDsx", "fruDsx", "u1").tobytes())
    b.write(col_idx("neuromeres", "somaNeuromere", "u1").tobytes())
    b.write(col_idx("nerves", "entryNerve", "u1").tobytes())
    soma = np.stack([nd["somaX"].values, nd["somaY"].values, nd["somaZ"].values], axis=1).astype("<f4")
    b.write(soma.tobytes())
    b.write(nd["pre"].values.astype("<i4").tobytes())
    b.write(nd["post"].values.astype("<i4").tobytes())
    b.write(row_ptr.astype("<i4").tobytes())
    b.write(post_idx.astype("<i4").tobytes())
    b.write(weights.astype("<u2").tobytes())
    ret = np.zeros(len(retina), dtype=np.dtype([("idx", "<i4"), ("side", "u1"), ("h1", "i1"), ("h2", "i1"), ("kind", "u1")]))
    for k, (i, sd, h1, h2, kind) in enumerate(retina):
        ret[k] = (i, sd, h1, h2, kind)
    b.write(ret.tobytes())

    raw = b.getvalue()
    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    with gzip.open(args.out, "wb", compresslevel=9) as f:
        f.write(raw)
    stats["bytes_uncompressed"] = len(raw)
    stats["bytes_gzip"] = os.path.getsize(args.out)
    os.makedirs(os.path.dirname(args.stats) or ".", exist_ok=True)
    with open(args.stats, "w") as f:
        json.dump(stats, f, indent=2)
    print("wrote %s (%.1f MB raw, %.1f MB gzip) in %.1fs" % (
        args.out, len(raw) / 1e6, stats["bytes_gzip"] / 1e6, time.time() - t0), flush=True)
    print("stats ->", args.stats)


if __name__ == "__main__":
    main()
