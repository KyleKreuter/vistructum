import json

import bootstrap


def _part(root, config, code, run=None):
    part = root / config
    (part / "release").mkdir(parents=True)
    (part / "release" / "summary.json").write_text(json.dumps({"exit_codes": {f"configs/{config}.yaml": code}}))
    (part / f"modal-{config}.log").write_text("log")
    if run:
        run_dir = part / "runs" / run
        run_dir.mkdir(parents=True)
        manifest = {"kind": "mask", "config": config, "metrics": {"splits": {"test": {
            "verdict": "PASS", "failures": [], "metrics": {"precision": 1.0, "recall": 0.9, "fp_rate": 0.0,
                                                           "threshold": 0.9}}}}}
        (run_dir / "manifest.json").write_text(json.dumps(manifest))
        (run_dir / "mask.onnx").write_bytes(b"model")
    return part


def test_combine_merges_one_output_per_config(tmp_path):
    parts = {"configs/mask-v2.yaml": _part(tmp_path / "parts", "mask-v2", 0, run="mask-v2-abc"),
             "configs/scan-v1.yaml": tmp_path / "parts" / "missing"}
    out = tmp_path / "out"
    summary = bootstrap.combine(parts, out, "a" * 40)
    assert summary["exit_codes"] == {"configs/mask-v2.yaml": 0, "configs/scan-v1.yaml": -1}
    assert summary["models"]["mask-v2"]["test"]["verdict"] == "PASS"
    assert (out / "runs" / "mask-v2-abc" / "mask.onnx").is_file()
    assert (out / "release" / "mask-v2.onnx").read_bytes() == b"model"
    assert (out / "modal-mask-v2.log").is_file()
    assert json.loads((out / "release" / "summary.json").read_text()) == summary
