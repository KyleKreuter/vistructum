import argparse
import json
import os
import pickle
import sys
import time
from pathlib import Path

import scan_eval

PHASES = ("calib", "scan")


def plan(cfg, phase):
    if phase == "calib":
        return {"calib": ("calib", cfg["seed"], False)}
    return {"scan": ("scan", cfg["seed"], False), "scan-holdout": ("scan", cfg["seed"] + 1, True)}


def collect_phase(onnx_path, cfg, phase, workers, shard=(0, 1)):
    kind = scan_eval.model_kind(onnx_path)["kind"]
    parts = {}
    for name, (split, seed, holdout) in plan(cfg, phase).items():
        started = time.time()
        results = scan_eval.collect(onnx_path, kind, seed, split, cfg["scan_negatives"], cfg["scan_positives"],
                                    workers, holdout, full=phase == "calib", shard=shard)
        parts[name] = {"results": results, "seconds": time.time() - started}
    return parts


def merge_phase(onnx_path, cfg, run_dir, phase, shards):
    reports = {}
    for name, (split, seed, holdout) in plan(cfg, phase).items():
        results = scan_eval.interleave([shard[name]["results"] for shard in shards])
        seconds = sum(shard[name]["seconds"] for shard in shards)
        reports[name] = scan_eval.summarize(onnx_path, results, split, cfg["scan_negatives"], cfg["scan_positives"],
                                            seed, holdout, phase == "calib", seconds)
        (Path(run_dir) / f"{name}.json").write_text(json.dumps(reports[name], indent=2))
    return reports


def without_sweep(reports):
    return {name: {k: v for k, v in report.items() if k != "sweep"} for name, report in reports.items()}


def scan_stage(onnx_path, cfg, run_dir, workers=None):
    workers = workers or cfg["scan_workers"]
    reports = {}
    for phase in PHASES:
        reports |= merge_phase(onnx_path, cfg, run_dir, phase, [collect_phase(onnx_path, cfg, phase, workers)])
    return without_sweep(reports)


def load_run(run_dir):
    manifest = json.loads((run_dir / "manifest.json").read_text())
    return manifest, run_dir / f"{manifest['kind']}.onnx", manifest["config_values"]


def complete_manifest(run_dir, manifest, scan):
    manifest["scan"] = scan
    manifest["scan_pending"] = False
    (run_dir / "manifest.json").write_text(json.dumps(manifest, indent=2))
    verdict = scan["scan"]["verdict"]
    print(json.dumps({"run": run_dir.name, "verdict": verdict, "failures": scan["scan"]["failures"]}))
    return 0 if verdict == "PASS" else 1


def parse_shard(text):
    index, count = (int(part) for part in text.split("/"))
    if not 0 <= index < count:
        raise argparse.ArgumentTypeError(f"shard must be INDEX/COUNT with 0 <= INDEX < COUNT, got {text}")
    return index, count


def run_all(args):
    manifest, onnx_path, cfg = load_run(args.run_dir)
    return complete_manifest(args.run_dir, manifest, scan_stage(onnx_path, cfg, args.run_dir, args.workers))


def run_collect(args):
    _, onnx_path, cfg = load_run(args.run_dir)
    parts = collect_phase(onnx_path, cfg, args.phase, args.workers, args.shard)
    args.out.write_bytes(pickle.dumps({"phase": args.phase, "shard": args.shard, "parts": parts}))
    return 0


def run_merge(args):
    manifest, onnx_path, cfg = load_run(args.run_dir)
    payloads = sorted((pickle.loads(path.read_bytes()) for path in args.shards), key=lambda payload: payload["shard"])
    shards = [tuple(payload["shard"]) for payload in payloads]
    count = shards[0][1]
    if {payload["phase"] for payload in payloads} != {args.phase} or shards != [(i, count) for i in range(count)]:
        raise SystemExit(f"need exactly one {args.phase} shard per index 0..COUNT-1, got {shards}")
    reports = merge_phase(onnx_path, cfg, args.run_dir, args.phase, [payload["parts"] for payload in payloads])
    if args.phase == "calib":
        return 0 if reports["calib"]["calibrated"] is not None else 1
    calib = json.loads((args.run_dir / "calib.json").read_text())
    return complete_manifest(args.run_dir, manifest, without_sweep({"calib": calib} | reports))


def main():
    parser = argparse.ArgumentParser(description="run the scan stage (calibrate, scan, scan-holdout) for a run that was "
                                                 "trained with --skip-scan, whole or split into shards")
    commands = parser.add_subparsers(dest="command", required=True)
    whole = commands.add_parser("all", help="run every phase here and complete the manifest")
    collect = commands.add_parser("collect", help="score one shard of one phase and write it to --out")
    merge = commands.add_parser("merge", help="merge all shards of one phase; calib writes the calibration into the "
                                              "model, scan completes the manifest")
    for command in (whole, collect, merge):
        command.add_argument("run_dir", type=Path, help="a runs/<config>-<sha> directory with manifest.json and "
                                                        "<kind>.onnx")
    for command in (whole, collect):
        command.add_argument("--workers", type=int, default=os.cpu_count() or 4)
    for command in (collect, merge):
        command.add_argument("--phase", choices=PHASES, required=True)
    collect.add_argument("--shard", type=parse_shard, default=(0, 1), help="INDEX/COUNT, e.g. 3/16")
    collect.add_argument("--out", type=Path, required=True)
    merge.add_argument("shards", type=Path, nargs="+")
    args = parser.parse_args()
    return {"all": run_all, "collect": run_collect, "merge": run_merge}[args.command](args)


if __name__ == "__main__":
    sys.exit(main())
