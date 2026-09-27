import io
import json
import os
import subprocess
import sys
import tarfile
import tempfile
from pathlib import Path

import modal
from bootstrap import combine

TRAIN_DIR = Path(__file__).resolve().parent
REMOTE_BOOTSTRAP = "/root/bootstrap.py"
REMOTE_OUTPUT = Path("/root/output")
TIMEOUT_HOURS = 6
CPUS = 8

image = (modal.Image.debian_slim(python_version="3.12")
         .apt_install("git")
         .pip_install_from_requirements(str(TRAIN_DIR / "requirements.txt"))
         .add_local_file(TRAIN_DIR / "bootstrap.py", REMOTE_BOOTSTRAP))
app = modal.App("vistructum-train", image=image)


def log_name(config):
    return f"modal-{Path(config).stem}.log"


@app.function(gpu="T4", cpu=float(CPUS), memory=32768, timeout=TIMEOUT_HOURS * 3600)
def train(ref, config):
    REMOTE_OUTPUT.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, VISTRUCTUM_REF=ref, VISTRUCTUM_CONFIGS=config, VISTRUCTUM_OUTPUT=str(REMOTE_OUTPUT),
               VISTRUCTUM_SCRATCH="/tmp/vistructum", VISTRUCTUM_CPUS=str(CPUS))
    with open(REMOTE_OUTPUT / log_name(config), "w") as log:
        subprocess.run([sys.executable, REMOTE_BOOTSTRAP], env=env, stdout=log, stderr=subprocess.STDOUT, check=False)
    buffer = io.BytesIO()
    with tarfile.open(fileobj=buffer, mode="w:gz") as tar:
        tar.add(REMOTE_OUTPUT, arcname=".")
    return buffer.getvalue()


@app.local_entrypoint()
def main(configs: str, ref: str, out: str):
    names = [config.strip() for config in configs.split(",") if config.strip()]
    work = Path(tempfile.mkdtemp(prefix="vistructum-modal-"))
    parts = {}
    results = train.starmap([(ref, config) for config in names], return_exceptions=True)
    for config, result in zip(names, results, strict=True):
        part_dir = work / Path(config).stem
        part_dir.mkdir(parents=True)
        if isinstance(result, BaseException):
            (part_dir / log_name(config)).write_text(f"modal container failed: {result!r}\n")
        else:
            with tarfile.open(fileobj=io.BytesIO(result)) as tar:
                tar.extractall(part_dir, filter="data")
        parts[config] = part_dir
    summary = combine(parts, Path(out), ref)
    print(json.dumps(summary, indent=2), flush=True)
