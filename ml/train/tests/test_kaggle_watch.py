import argparse
import json
import subprocess

import kaggle_watch


def _answer(monkeypatch, text):
    monkeypatch.setattr(kaggle_watch, "kaggle", lambda *args: subprocess.CompletedProcess(args, 0, text, ""))
    return kaggle_watch.status("kaggle", "owner/kernel")[0]


def test_status_reads_the_kernel_state(monkeypatch):
    text = 'owner/kernel has status "KernelWorkerStatus.COMPLETE"'
    assert _answer(monkeypatch, text) == "complete"


def test_api_errors_are_not_mistaken_for_a_failed_kernel(monkeypatch):
    text = "503 Server Error: Service Unavailable for url: https://api.kaggle.com/v1/kernels"
    assert _answer(monkeypatch, text) == "unknown"


def _metrics(verdict):
    return {"verdict": verdict, "failures": [] if verdict == "PASS" else ["recall 0.2 < 0.6"],
            "metrics": {"precision": 0.99, "recall": 0.2, "fp_rate": 0.0001, "threshold": 0.98}}


def test_report_merges_scan_results_and_summarizes_every_split(tmp_path):
    out = tmp_path / "kaggle"
    run_dir = out / "runs" / "scan-v1-0b8abf31"
    run_dir.mkdir(parents=True)
    (out / "release").mkdir()
    (out / "release" / "summary.json").write_text(json.dumps({"ref": "0b8abf31" * 5, "exit_codes": {"c": 0}}))
    manifest = {"kind": "fullscan", "config": "scan-v1", "scan_pending": True, "scan": {},
                "metrics": {"splits": {"test": _metrics("FAIL"), "findings": _metrics("FAIL")}}}
    (run_dir / "manifest.json").write_text(json.dumps(manifest))
    (run_dir / "fullscan.onnx").write_bytes(b"trained")
    result = tmp_path / "scan-results" / "result-scan-v1-0b8abf31"
    result.mkdir(parents=True)
    scanned = manifest | {"scan_pending": False, "scan": {
        "calib": {"calibrated": {"threshold": 0.94, "min_votes": 3, "recall": 0.34, "false_flags": 4, "windows": 9}},
        "scan": {"verdict": "FAIL", "failures": ["recall 0.34 < 0.5"],
                 "at_model_threshold": {"threshold": 0.94, "min_votes": 3, "recall": 0.34, "false_flags": 8,
                                        "windows": 9}}}}
    (result / "manifest.json").write_text(json.dumps(scanned))
    (result / "fullscan.onnx").write_bytes(b"calibrated")

    args = argparse.Namespace(out=out, label="20260927-120000-0b8abf31", scan_results=tmp_path / "scan-results",
                              publish=False)
    assert kaggle_watch.run_report(args) == 0

    assert (run_dir / "fullscan.onnx").read_bytes() == b"calibrated"
    assert (out / "release" / "scan-v1.onnx").read_bytes() == b"calibrated"
    summary = json.loads((out / "release" / "summary.json").read_text())
    assert set(summary["models"]["scan-v1"]) == {"test", "findings"}
    text = (out / "report.md").read_text()
    assert "| scan-v1 | findings | FAIL | 0.98 |" in text
    assert "| scan-v1-0b8abf31 | scan | FAIL | 0.94 | 3 | 0.34 | 8/9 | recall 0.34 < 0.5 |" in text
    assert "scan stage missing" not in text


def test_report_marks_runs_without_scan_stage(tmp_path):
    out = tmp_path / "kaggle"
    run_dir = out / "runs" / "mask-v2-0b8abf31"
    run_dir.mkdir(parents=True)
    (run_dir / "manifest.json").write_text(json.dumps({"kind": "mask", "scan_pending": True}))
    assert "| mask-v2-0b8abf31 | – | scan stage missing |" in kaggle_watch.report(out, "label")
    assert "No `release/summary.json`" in kaggle_watch.report(out, "label")


def test_a_kernel_that_stays_queued_gives_up_on_kaggle(monkeypatch):
    clock = iter(range(0, 10_000, 300))
    monkeypatch.setattr(kaggle_watch.time, "time", lambda: next(clock))
    monkeypatch.setattr(kaggle_watch.time, "sleep", lambda seconds: None)
    monkeypatch.setattr(kaggle_watch, "status", lambda cmd, slug: ("queued", "queued"))
    assert kaggle_watch.wait("kaggle", "owner/kernel", 60, 5.5, queue_minutes=10) == "queue-timeout"


def test_a_running_kernel_is_not_cut_off_by_the_queue_limit(monkeypatch):
    clock = iter(range(0, 100_000, 300))
    states = iter(["queued", "running", "running", "running", "complete"])
    monkeypatch.setattr(kaggle_watch.time, "time", lambda: next(clock))
    monkeypatch.setattr(kaggle_watch.time, "sleep", lambda seconds: None)
    monkeypatch.setattr(kaggle_watch, "status", lambda cmd, slug: (next(states), ""))
    assert kaggle_watch.wait("kaggle", "owner/kernel", 60, 5.5, queue_minutes=10) == "complete"


def test_handoff_without_summary_reports_an_error(tmp_path, monkeypatch):
    monkeypatch.delenv("GITHUB_OUTPUT", raising=False)
    assert kaggle_watch.run_handoff(argparse.Namespace(out=tmp_path)) == 1
