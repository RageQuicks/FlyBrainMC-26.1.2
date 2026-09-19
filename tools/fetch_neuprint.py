#!/usr/bin/env python3
"""
Fetch the male Drosophila CNS connectome (neuPrint dataset male-cns:v1.0) into a local raw cache.

Anonymous Cypher access to https://neuprint.janelia.org/api/custom/custom works without a token
(verified 2026-09-03). If you have a token (neuprint.janelia.org -> Account), set
NEUPRINT_APPLICATION_CREDENTIALS or pass --token; it is sent as a Bearer header.

Outputs (in --out, default data/raw/<dataset>/):
  neurons.csv.gz      one row per Neuron node with the columns we need
  edges/chunk_*.csv   raw per-chunk edge lists (resumable)
  edges.csv.gz        merged pre,post,weight (weight = synapse count), weight >= --min-weight
  fetch_meta.json     provenance (dataset, thresholds, counts, timestamps)

Usage:
  python tools/fetch_neuprint.py                       # male-cns:v1.0, weight >= 5
  python tools/fetch_neuprint.py --min-weight 1        # every synaptic connection (25.9M rows, slower)
  python tools/fetch_neuprint.py --workers 8
"""
import argparse
import csv
import gzip
import json
import os
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone

import requests

SERVER = "https://neuprint.janelia.org"
UA = "fruit-fly-minecraft/0.1 (connectome fetch; https://github.com/blendi-remade/fly-brain-minecraft)"

NEURON_COLUMNS = [
    ("bodyId", "n.bodyId"),
    ("type", "n.type"),
    ("instance", "n.instance"),
    ("superclass", "n.superclass"),
    ("class", "n.class"),
    ("subclass", "n.subclass"),
    ("consensusNt", "n.consensusNt"),
    ("predictedNt", "n.predictedNt"),
    ("predictedNtConfidence", "n.predictedNtConfidence"),
    ("celltypePredictedNt", "n.celltypePredictedNt"),
    ("somaSide", "n.somaSide"),
    ("rootSide", "n.rootSide"),
    ("somaX", "n.somaLocation.x"),
    ("somaY", "n.somaLocation.y"),
    ("somaZ", "n.somaLocation.z"),
    ("hex1", "n.assignedOlHex1"),
    ("hex2", "n.assignedOlHex2"),
    ("dimorphism", "n.dimorphism"),
    ("fruDsx", "n.fruDsx"),
    ("somaNeuromere", "n.somaNeuromere"),
    ("entryNerve", "n.entryNerve"),
    ("exitNerve", "n.exitNerve"),
    ("status", "n.status"),
    ("pre", "n.pre"),
    ("post", "n.post"),
    ("upstream", "n.upstream"),
    ("downstream", "n.downstream"),
    ("synweight", "n.synweight"),
    ("size", "n.size"),
    ("flywireType", "n.flywireType"),
    ("mancType", "n.mancType"),
    ("hemibrainType", "n.hemibrainType"),
    ("synonyms", "n.synonyms"),
]


class NeuPrint:
    def __init__(self, dataset, token=None, timeout=900):
        self.dataset = dataset
        self.timeout = timeout
        self.sess = requests.Session()
        self.sess.headers["User-Agent"] = UA
        self.sess.headers["Content-Type"] = "application/json"
        if token:
            self.sess.headers["Authorization"] = "Bearer " + token

    def cypher(self, q, retries=6):
        delay = 2.0
        for attempt in range(retries):
            try:
                r = self.sess.post(SERVER + "/api/custom/custom",
                                   data=json.dumps({"cypher": q, "dataset": self.dataset}),
                                   timeout=self.timeout)
                if r.status_code == 200:
                    d = r.json()
                    if "data" not in d:
                        raise RuntimeError("neuPrint error: %s" % json.dumps(d)[:500])
                    return d["columns"], d["data"]
                raise RuntimeError("HTTP %d: %s" % (r.status_code, r.text[:300]))
            except (requests.RequestException, RuntimeError, ValueError) as e:
                if attempt == retries - 1:
                    raise
                sys.stderr.write("  retry %d after error: %s\n" % (attempt + 1, str(e)[:200]))
                time.sleep(delay)
                delay = min(delay * 2, 60)


def fetch_neurons(np_, out_csv, page=20000):
    ret = ", ".join("%s AS %s" % (expr, name) for name, expr in NEURON_COLUMNS)
    rows_total = 0
    skip = 0
    with gzip.open(out_csv, "wt", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow([name for name, _ in NEURON_COLUMNS])
        while True:
            q = "MATCH (n:Neuron) WITH n ORDER BY n.bodyId SKIP %d LIMIT %d RETURN %s" % (skip, page, ret)
            t0 = time.time()
            cols, data = np_.cypher(q)
            for row in data:
                w.writerow(["" if v is None else v for v in row])
            rows_total += len(data)
            print("  neurons: %d rows (+%d in %.1fs)" % (rows_total, len(data), time.time() - t0), flush=True)
            if len(data) < page:
                break
            skip += page
    return rows_total


def plan_chunks(neurons_csv, target_syn=250000, max_ids=400):
    """Group presynaptic bodyIds into chunks with roughly equal downstream synapse counts."""
    ids = []
    with gzip.open(neurons_csv, "rt", encoding="utf-8") as f:
        r = csv.DictReader(f)
        for row in r:
            ds = int(float(row["downstream"] or 0))
            ids.append((int(row["bodyId"]), ds))
    ids.sort()
    chunks, cur, acc = [], [], 0
    for bid, ds in ids:
        if ds <= 0:
            continue  # no outputs -> never a presynaptic partner
        cur.append(bid)
        acc += ds
        if acc >= target_syn or len(cur) >= max_ids:
            chunks.append(cur)
            cur, acc = [], 0
    if cur:
        chunks.append(cur)
    return chunks


def fetch_edge_chunk(np_, ids, min_weight, path):
    q = ("MATCH (a:Neuron)-[e:ConnectsTo]->(b:Neuron) WHERE a.bodyId IN [%s] AND e.weight >= %d "
         "RETURN a.bodyId AS pre, b.bodyId AS post, e.weight AS w" % (",".join(map(str, ids)), min_weight))
    cols, data = np_.cypher(q)
    tmp = path + ".tmp"
    with open(tmp, "w", newline="") as f:
        w = csv.writer(f)
        for row in data:
            w.writerow(row)
    os.replace(tmp, path)
    return len(data)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dataset", default="male-cns:v1.0")
    ap.add_argument("--out", default=None, help="output dir (default data/raw/<dataset>)")
    ap.add_argument("--min-weight", type=int, default=5, help="min synapse count per connection (default 5)")
    ap.add_argument("--workers", type=int, default=6)
    ap.add_argument("--token", default=os.environ.get("NEUPRINT_APPLICATION_CREDENTIALS"))
    ap.add_argument("--skip-neurons", action="store_true")
    ap.add_argument("--skip-edges", action="store_true")
    args = ap.parse_args()

    out = args.out or os.path.join("data", "raw", args.dataset.replace(":", "_"))
    os.makedirs(os.path.join(out, "edges"), exist_ok=True)
    np_ = NeuPrint(args.dataset, args.token)
    meta_path = os.path.join(out, "fetch_meta.json")
    meta = {"dataset": args.dataset, "server": SERVER, "min_weight": args.min_weight,
            "started": datetime.now(timezone.utc).isoformat()}

    neurons_csv = os.path.join(out, "neurons.csv.gz")
    if not args.skip_neurons or not os.path.exists(neurons_csv):
        print("Fetching neurons from %s ..." % args.dataset, flush=True)
        n = fetch_neurons(np_, neurons_csv)
        meta["neurons"] = n
        print("neurons done: %d" % n, flush=True)

    if not args.skip_edges:
        chunks = plan_chunks(neurons_csv)
        print("Fetching edges (weight >= %d) in %d chunks with %d workers ..." % (
            args.min_weight, len(chunks), args.workers), flush=True)
        todo = []
        for i, ids in enumerate(chunks):
            p = os.path.join(out, "edges", "chunk_%04d.csv" % i)
            if not os.path.exists(p):
                todo.append((i, ids, p))
        print("  %d chunks already cached, %d to fetch" % (len(chunks) - len(todo), len(todo)), flush=True)
        t0 = time.time()
        done = 0
        rows = 0
        with ThreadPoolExecutor(max_workers=args.workers) as ex:
            futs = {ex.submit(fetch_edge_chunk, np_, ids, args.min_weight, p): (i, p) for i, ids, p in todo}
            for fut in as_completed(futs):
                i, p = futs[fut]
                try:
                    n = fut.result()
                except Exception as e:
                    print("  chunk %d FAILED: %s" % (i, e), flush=True)
                    continue
                done += 1
                rows += n
                if done % 10 == 0 or done == len(todo):
                    el = time.time() - t0
                    print("  chunks %d/%d, %d rows, %.0fs elapsed, ETA %.0fs" % (
                        done, len(todo), rows, el, el / done * (len(todo) - done)), flush=True)
        missing = [i for i in range(len(chunks))
                   if not os.path.exists(os.path.join(out, "edges", "chunk_%04d.csv" % i))]
        if missing:
            print("WARNING: %d chunks missing (%s...). Re-run to resume." % (len(missing), missing[:10]))
            sys.exit(2)
        merged = os.path.join(out, "edges.csv.gz")
        print("Merging %d chunks -> %s" % (len(chunks), merged), flush=True)
        total = 0
        syn = 0
        with gzip.open(merged, "wt", newline="") as f:
            w = csv.writer(f)
            w.writerow(["pre", "post", "weight"])
            for i in range(len(chunks)):
                with open(os.path.join(out, "edges", "chunk_%04d.csv" % i), newline="") as cf:
                    for row in csv.reader(cf):
                        w.writerow(row)
                        total += 1
                        syn += int(row[2])
        meta["edges"] = total
        meta["synapses_in_edges"] = syn
        print("edges done: %d connections, %d synapses" % (total, syn), flush=True)

    meta["finished"] = datetime.now(timezone.utc).isoformat()
    with open(meta_path, "w") as f:
        json.dump(meta, f, indent=2)
    print("wrote", meta_path)


if __name__ == "__main__":
    main()
