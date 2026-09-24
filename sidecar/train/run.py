import argparse
import hashlib
import json
import subprocess
from dataclasses import asdict
from pathlib import Path

import evaluate as evaluate_mod
import export as export_mod
import scan_eval
import torch
from config import add_config_args, load_config

import train as train_mod


def git_info():
    sha = subprocess.check_output(["git", "rev-parse", "HEAD"]).decode().strip()
    dirty = bool(subprocess.check_output(["git", "status", "--porcelain"]).decode().strip())
    return sha, dirty


def data_manifest_hash(data_dir):
    manifest_path = Path(data_dir) / "manifest.json"
    if not manifest_path.exists():
        return None
    return hashlib.sha256(manifest_path.read_bytes()).hexdigest()


def scan_stage(onnx_path, cfg, run_dir, workers=None):
    """calibrate threshold/min_votes on the calib areas (written into the ONNX), then evaluate on scan and the shifted
    scan-holdout; returns the reports without their sweeps and writes the full ones next to the model"""
    workers = workers or cfg["scan_workers"]
    scan = {"calib": scan_eval.run(onnx_path, "calib", cfg["scan_negatives"], cfg["scan_positives"], cfg["seed"],
                                   workers, write=True)}
    for scan_name, holdout in (("scan", False), ("scan-holdout", True)):
        scan[scan_name] = scan_eval.run(onnx_path, "scan", cfg["scan_negatives"], cfg["scan_positives"],
                                        cfg["seed"] + holdout, workers, holdout=holdout)
    for scan_name, entry in scan.items():
        (Path(run_dir) / f"{scan_name}.json").write_text(json.dumps(entry, indent=2))
    return {key: {k: v for k, v in entry.items() if k != "sweep"} for key, entry in scan.items()}


def main():
    parser = argparse.ArgumentParser()
    add_config_args(parser)
    parser.add_argument("--runs-dir", default="runs")
    parser.add_argument("--allow-dirty", action="store_true")
    parser.add_argument("--resume", action="store_true")
    parser.add_argument("--splits", nargs="+", default=["val", "test", "holdout"])
    parser.add_argument("--no-gate", action="store_true", help="write the report but do not fail on a test verdict FAIL")
    parser.add_argument("--skip-scan", action="store_true",
                        help="leave the CPU-bound scan stage for scan_stage.py, e.g. to free the Kaggle GPUs sooner")
    args = parser.parse_args()
    cfg = load_config(args.config, args.set)
    sha, dirty = git_info()
    if dirty and not args.allow_dirty:
        raise SystemExit("git tree is dirty, commit or pass --allow-dirty")
    name = Path(args.config).stem
    run_dir = Path(args.runs_dir) / f"{name}-{sha[:8]}"
    run_dir.mkdir(parents=True, exist_ok=True)
    cfg.out_dir = str(run_dir)

    model, threshold, best = train_mod.train_model(cfg, resume=args.resume)
    checkpoint_path = run_dir / "model.pt"
    torch.save({"model": model.state_dict(), "cfg": asdict(cfg), "threshold": threshold, "metrics": best}, checkpoint_path)

    onnx_path = run_dir / f"{cfg.kind}.onnx"
    export_result = export_mod.export_checkpoint(checkpoint_path, onnx_path, commit=sha, quantize=cfg.quantize)

    session, meta = evaluate_mod.load_session(onnx_path)
    report = evaluate_mod.build_report(session, meta, cfg.data_dir, args.splits)
    report["model_bytes"] = onnx_path.stat().st_size
    scan_wanted = cfg.scan_negatives > 0
    scan = scan_stage(onnx_path, asdict(cfg), run_dir) if scan_wanted and not args.skip_scan else {}

    manifest = {
        "config": name,
        "kind": cfg.kind,
        "commit": sha,
        "dirty": dirty,
        "config_values": asdict(cfg),
        "data_manifest_sha256": data_manifest_hash(cfg.data_dir),
        "export": export_result,
        "metrics": report,
        "scan": scan,
        "scan_pending": scan_wanted and not scan,
    }
    (run_dir / "manifest.json").write_text(json.dumps(manifest, indent=2))
    print(json.dumps(manifest, indent=2))
    if manifest["scan_pending"]:
        return
    verdict = scan["scan"]["verdict"] if scan else report["splits"].get("test", {}).get("verdict")
    if not args.no_gate and verdict == "FAIL":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
