import argparse
import json
import subprocess
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
import torch
from data import load_split
from model import ExportNet, SymbolNet

from vistructum_ml.contract import (
    FEATURE_SPEC,
    GRID,
    INPUT_NAME,
    KINDS,
    LABELS,
    META_COMMIT,
    META_CONFIG,
    META_FEATURE_SPEC,
    META_KIND,
    META_LABELS,
    META_METRICS,
    META_THRESHOLD,
    META_VERSION,
    OUTPUT_NAME,
)
from vistructum_ml.metadata import read_session_metadata, validate

QUANTIZE_MAX_DISAGREEMENT = 0.001
OPSET = 17


def git_commit():
    return subprocess.check_output(["git", "rev-parse", "HEAD"]).decode().strip()


def export_onnx(model, kind, out_path, opset=OPSET):
    model.eval()
    wrapper = ExportNet(model)
    spec = KINDS[kind]
    dummy = torch.zeros(1, spec.channels, GRID, GRID, dtype=torch.uint8)
    torch.onnx.export(
        wrapper,
        dummy,
        str(out_path),
        input_names=[INPUT_NAME],
        output_names=[OUTPUT_NAME],
        dynamic_axes={INPUT_NAME: {0: "batch"}, OUTPUT_NAME: {0: "batch"}},
        opset_version=opset,
        dynamo=False,
    )


def add_metadata(onnx_path, kind, threshold, commit, cfg_dict, metrics_dict):
    model = onnx.load(str(onnx_path))
    spec = KINDS[kind]
    props = {
        META_VERSION: spec.version,
        META_KIND: kind,
        META_LABELS: json.dumps(list(LABELS)),
        META_FEATURE_SPEC: FEATURE_SPEC,
        META_THRESHOLD: f"{threshold:.4f}",
        META_COMMIT: commit,
        META_CONFIG: json.dumps(cfg_dict),
        META_METRICS: json.dumps(metrics_dict or {}),
    }
    del model.metadata_props[:]
    for key, value in props.items():
        entry = model.metadata_props.add()
        entry.key = key
        entry.value = value
    onnx.save(model, str(onnx_path))


def validate_export(onnx_path):
    session = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    return validate(read_session_metadata(session))


def _scores(session, name, x, batch=256):
    # batched: one 12k-sample run of the wide fullscan net needs tens of GB of activations
    return np.concatenate([session.run([OUTPUT_NAME], {name: x[i:i + batch]})[0][:, 1] for i in range(0, len(x), batch)])


def maybe_quantize(onnx_path, threshold, data_dir):
    from onnxruntime.quantization import QuantType, quantize_dynamic

    tmp_path = Path(str(onnx_path) + ".int8.tmp")
    quantize_dynamic(str(onnx_path), str(tmp_path), weight_type=QuantType.QUInt8)
    val_x, _ = load_split(data_dir, "val")
    x = val_x.numpy()
    sess32 = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    sess8 = ort.InferenceSession(str(tmp_path), providers=["CPUExecutionProvider"])
    name = sess32.get_inputs()[0].name
    p32 = _scores(sess32, name, x)
    p8 = _scores(sess8, name, x)
    disagreement = float(((p32 >= threshold) != (p8 >= threshold)).mean())
    if disagreement <= QUANTIZE_MAX_DISAGREEMENT:
        tmp_path.replace(onnx_path)
        return True, disagreement
    tmp_path.unlink(missing_ok=True)
    return False, disagreement


def export_checkpoint(checkpoint_path, onnx_path, commit=None, quantize=None, opset=OPSET):
    payload = torch.load(checkpoint_path, map_location="cpu", weights_only=False)
    cfg_dict = payload["cfg"]
    kind = cfg_dict["kind"]
    model = SymbolNet(kind, cfg_dict["widths"], cfg_dict.get("dropout", 0.0))
    model.load_state_dict(payload["model"])
    threshold = payload["threshold"]
    commit = commit or git_commit()
    quantize = cfg_dict.get("quantize", True) if quantize is None else quantize
    export_onnx(model, kind, onnx_path, opset)
    add_metadata(onnx_path, kind, threshold, commit, cfg_dict, payload.get("metrics"))
    validate_export(onnx_path)
    quantize_info = {"applied": False, "disagreement": None}
    if quantize:
        applied, disagreement = maybe_quantize(onnx_path, threshold, cfg_dict["data_dir"])
        quantize_info = {"applied": applied, "disagreement": disagreement}
    return {"onnx_path": str(onnx_path), "kind": kind, "threshold": threshold, "commit": commit, "quantize": quantize_info}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("checkpoint")
    parser.add_argument("out")
    parser.add_argument("--opset", type=int, default=OPSET)
    parser.add_argument("--no-quantize", action="store_true")
    args = parser.parse_args()
    result = export_checkpoint(args.checkpoint, args.out, quantize=not args.no_quantize, opset=args.opset)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
