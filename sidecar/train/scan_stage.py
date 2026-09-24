import argparse
import json
import os
import sys
from pathlib import Path

from run import scan_stage


def main():
    parser = argparse.ArgumentParser(description="run the scan stage (calibrate, scan, scan-holdout) for a run that was "
                                                 "trained with --skip-scan, and complete its manifest")
    parser.add_argument("run_dir", help="a runs/<config>-<sha> directory with manifest.json and <kind>.onnx")
    parser.add_argument("--workers", type=int, default=os.cpu_count() or 4)
    args = parser.parse_args()
    run_dir = Path(args.run_dir)
    manifest_path = run_dir / "manifest.json"
    manifest = json.loads(manifest_path.read_text())
    cfg = manifest["config_values"]
    manifest["scan"] = scan_stage(run_dir / f"{manifest['kind']}.onnx", cfg, run_dir, args.workers)
    manifest["scan_pending"] = False
    manifest_path.write_text(json.dumps(manifest, indent=2))
    verdict = manifest["scan"]["scan"]["verdict"]
    print(json.dumps({"run": run_dir.name, "verdict": verdict, "failures": manifest["scan"]["scan"]["failures"]}))
    return 0 if verdict == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
