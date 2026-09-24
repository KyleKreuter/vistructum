import export as export_mod
import numpy as np
import onnxruntime as ort
import pytest
import torch
from model import ExportNet, SymbolNet

from vistructum_ml import scoring
from vistructum_ml.contract import GRID, INPUT_NAME, KINDS
from vistructum_ml.metadata import read_session_metadata, validate


def make_split(dir_path, name, kind, n_pos, n_neg, seed=0):
    channels = KINDS[kind].channels
    rng = np.random.default_rng(seed)
    n = n_pos + n_neg
    x = rng.integers(0, 256, size=(n, channels, GRID, GRID), dtype=np.uint8)
    y = np.array([1] * n_pos + [0] * n_neg, dtype=np.int64)
    subtype = np.array([f"pos-{i % 3}" for i in range(n_pos)] + [f"neg-{i % 3}" for i in range(n_neg)])
    seeds = np.arange(n, dtype=np.int64)
    np.savez(dir_path / f"{name}.npz", x=x, y=y, subtype=subtype, seed=seeds)


def build_checkpoint(tmp_path, kind, widths=(8, 16)):
    torch.manual_seed(0)
    model = SymbolNet(kind, list(widths), 0.0)
    payload = {
        "model": model.state_dict(),
        "cfg": {"kind": kind, "widths": list(widths), "dropout": 0.0, "data_dir": str(tmp_path / "data"), "quantize": True},
        "threshold": 0.5,
        "metrics": {"val_loss": 0.1},
    }
    ckpt_path = tmp_path / "model.pt"
    torch.save(payload, ckpt_path)
    return ckpt_path, model


@pytest.mark.parametrize("kind", ["mask", "fullscan"])
def test_export_parity_and_metadata(tmp_path, kind):
    ckpt_path, model = build_checkpoint(tmp_path, kind)
    onnx_path = tmp_path / f"{kind}.onnx"
    export_mod.export_checkpoint(ckpt_path, onnx_path, commit="0" * 40, quantize=False)

    session = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    meta = validate(read_session_metadata(session))
    assert meta["kind"] == kind

    input_meta = session.get_inputs()[0]
    assert input_meta.name == INPUT_NAME
    assert "uint8" in input_meta.type

    rng = np.random.default_rng(0)
    channels = KINDS[kind].channels
    x = rng.integers(0, 256, size=(32, channels, GRID, GRID), dtype=np.uint8)
    onnx_probs = session.run(None, {INPUT_NAME: x})[0]

    model.eval()
    with torch.no_grad():
        torch_probs = torch.softmax(model(torch.from_numpy(x)), dim=1).numpy()

    assert np.abs(onnx_probs - torch_probs).max() < 1e-4


@pytest.mark.parametrize("kind", ["mask", "fullscan"])
def test_export_checkpoint_with_quantization_stays_valid(tmp_path, kind):
    data_dir = tmp_path / "data"
    data_dir.mkdir()
    make_split(data_dir, "val", kind, 100, 400, seed=1)
    ckpt_path, _ = build_checkpoint(tmp_path, kind)
    onnx_path = tmp_path / f"{kind}.onnx"
    result = export_mod.export_checkpoint(ckpt_path, onnx_path, commit="1" * 40, quantize=True)
    disagreement = result["quantize"]["disagreement"]
    assert disagreement is not None
    assert result["quantize"]["applied"] == (disagreement <= export_mod.QUANTIZE_MAX_DISAGREEMENT)
    meta = export_mod.validate_export(onnx_path)
    assert meta["kind"] == kind


@pytest.mark.parametrize("kind", ["mask", "fullscan"])
def test_quantized_decisions_agree_on_random_inputs(tmp_path, kind):
    from onnxruntime.quantization import QuantType, quantize_dynamic

    ckpt_path, _ = build_checkpoint(tmp_path, kind)
    fp32_path = tmp_path / f"{kind}-fp32.onnx"
    export_mod.export_checkpoint(ckpt_path, fp32_path, commit="2" * 40, quantize=False)
    int8_path = tmp_path / f"{kind}-int8.onnx"
    quantize_dynamic(str(fp32_path), str(int8_path), weight_type=QuantType.QUInt8)

    rng = np.random.default_rng(3)
    channels = KINDS[kind].channels
    high = 2 if kind == "mask" else 256
    x = rng.integers(0, high, size=(500, channels, GRID, GRID), dtype=np.uint8)
    sess32 = ort.InferenceSession(str(fp32_path), providers=["CPUExecutionProvider"])
    sess8 = ort.InferenceSession(str(int8_path), providers=["CPUExecutionProvider"])
    p32 = sess32.run(None, {INPUT_NAME: x})[0]
    p8 = sess8.run(None, {INPUT_NAME: x})[0]
    assert np.abs(p32 - p8).max() < 0.02


def test_tta_scoring_averages_all_eight_d4_views(tmp_path):
    torch.manual_seed(1)
    model = SymbolNet("fullscan", [8, 16], 0.0).eval()
    onnx_path = tmp_path / "plain.onnx"
    export_mod.export_onnx(model, "fullscan", onnx_path)
    session = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    x = np.random.default_rng(0).integers(0, 256, size=(5, KINDS["fullscan"].channels, GRID, GRID), dtype=np.uint8)
    scores = scoring.all_views(session, x, batch_size=16)

    views = [np.rot90(v, k, axes=(2, 3)) for v in (x, x[:, :, :, ::-1]) for k in range(4)]
    with torch.no_grad():
        expected = np.mean([torch.softmax(model(torch.from_numpy(v.copy())), dim=1).numpy() for v in views], axis=0)
        reference = ExportNet(model, tta=True)(torch.from_numpy(x)).numpy()
    assert np.abs(scores - expected[:, 1]).max() < 1e-4
    assert np.abs(scores - reference[:, 1]).max() < 1e-4
    turned = np.ascontiguousarray(np.rot90(x, 1, axes=(2, 3)))
    assert np.abs(scoring.all_views(session, turned) - scores).max() < 1e-4


def test_export_writes_tta_policy_into_metadata(tmp_path):
    ckpt_path, _ = build_checkpoint(tmp_path, "fullscan")
    payload = torch.load(ckpt_path, weights_only=False)
    payload["cfg"]["tta"] = True
    torch.save(payload, ckpt_path)
    onnx_path = tmp_path / "fullscan.onnx"
    export_mod.export_checkpoint(ckpt_path, onnx_path, commit="abc", quantize=False)
    session = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    meta = validate(read_session_metadata(session))
    assert meta["tta"] is True and meta["prefilter"] is None
    x = np.zeros((3, KINDS["fullscan"].channels, GRID, GRID), dtype=np.uint8)
    assert session.run(None, {INPUT_NAME: x})[0].shape == (3, 2)
