import json
import os
import subprocess
import sys
from pathlib import Path

import numpy as np
import pytest

from vistructum_ml.contract import GRID, KINDS

TRAIN_DIR = Path(__file__).resolve().parents[1]
SIDECAR_DIR = TRAIN_DIR.parent


def make_split(dir_path, name, kind, n_pos, n_neg, seed=0):
    channels = KINDS[kind].channels
    rng = np.random.default_rng(seed)
    n = n_pos + n_neg
    x = rng.integers(0, 256, size=(n, channels, GRID, GRID), dtype=np.uint8)
    y = np.array([1] * n_pos + [0] * n_neg, dtype=np.int64)
    subtype = np.array([f"pos-{i % 3}" for i in range(n_pos)] + [f"neg-{i % 3}" for i in range(n_neg)])
    seeds = np.arange(n, dtype=np.int64)
    np.savez(dir_path / f"{name}.npz", x=x, y=y, subtype=subtype, seed=seeds)


def make_dataset(dir_path, kind):
    dir_path.mkdir(parents=True, exist_ok=True)
    make_split(dir_path, "train", kind, 40, 40, seed=1)
    make_split(dir_path, "val", kind, 20, 80, seed=2)
    make_split(dir_path, "test", kind, 20, 80, seed=3)
    make_split(dir_path, "holdout", kind, 20, 80, seed=4)


def write_config(path, kind, data_dir):
    path.write_text(
        "\n".join(
            [
                f"kind: {kind}",
                f"data_dir: {data_dir}",
                "widths: [4, 8]",
                "epochs: 1",
                "batch: 16",
                "warmup_epochs: 0",
                "ema_decay: 0.0",
                "quantize: false",
                "num_threads: 1",
                "",
            ]
        )
    )


@pytest.mark.parametrize("kind", ["mask", "fullscan"])
def test_run_smoke(tmp_path, kind):
    data_dir = tmp_path / "data"
    make_dataset(data_dir, kind)
    config_path = tmp_path / "config.yaml"
    write_config(config_path, kind, data_dir)
    runs_dir = tmp_path / "runs"
    env = dict(os.environ)
    env["PYTHONPATH"] = str(SIDECAR_DIR)
    result = subprocess.run(
        [sys.executable, "run.py", "--config", str(config_path), "--runs-dir", str(runs_dir), "--allow-dirty"],
        cwd=TRAIN_DIR,
        capture_output=True,
        text=True,
        env=env,
        timeout=60,
        check=False,
    )
    assert result.returncode in (0, 1), result.stdout + result.stderr
    manifests = list(runs_dir.glob("*/manifest.json"))
    assert len(manifests) == 1
    manifest = json.loads(manifests[0].read_text())
    assert manifest["kind"] == kind
    assert manifest["config"] == "config"
    assert "commit" in manifest and isinstance(manifest["dirty"], bool)
    assert (runs_dir / manifests[0].parent.name / f"{kind}.onnx").exists()
