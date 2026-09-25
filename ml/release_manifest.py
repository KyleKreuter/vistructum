import argparse
import hashlib
import json
import sys
from pathlib import Path

import onnxruntime as ort

MANIFEST = "models.json"


def entry(path: Path) -> dict:
    meta = ort.InferenceSession(str(path), providers=["CPUExecutionProvider"]).get_modelmeta().custom_metadata_map
    return {
        "kind": meta["vistructum.kind"],
        "file": path.name,
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "model_version": meta["vistructum.model_version"],
        "feature_spec": meta["vistructum.feature_spec"],
    }


def manifest(models_dir: Path) -> dict:
    paths = sorted(models_dir.glob("*.onnx"))
    if not paths:
        raise SystemExit(f"no models in {models_dir}")
    return {"models": [entry(path) for path in paths]}


def main(argv: list[str]) -> None:
    parser = argparse.ArgumentParser(description="Write the models.json manifest of a model release.")
    parser.add_argument("--models", type=Path, default=Path("models"))
    parser.add_argument("--out", type=Path, default=Path(MANIFEST))
    args = parser.parse_args(argv)
    args.out.write_text(json.dumps(manifest(args.models), indent=2) + "\n", encoding="utf-8")
    print(args.out.read_text(encoding="utf-8"))


if __name__ == "__main__":
    main(sys.argv[1:])
