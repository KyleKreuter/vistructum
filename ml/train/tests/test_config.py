import pytest
from config import load_config


def test_defaults_roundtrip(tmp_path):
    path = tmp_path / "c.yaml"
    path.write_text("kind: mask\ndata_dir: d\nout_dir: o\n")
    cfg = load_config(path)
    assert cfg.kind == "mask"
    assert cfg.widths == [16, 32, 64]
    assert cfg.optimizer == "adamw"


def test_unknown_key_rejected(tmp_path):
    path = tmp_path / "c.yaml"
    path.write_text("kind: mask\nbogus: 1\n")
    with pytest.raises(ValueError):
        load_config(path)


def test_cli_override(tmp_path):
    path = tmp_path / "c.yaml"
    path.write_text("kind: mask\nepochs: 5\n")
    cfg = load_config(path, ["epochs=3", "augment_d4=false", "widths=8,16"])
    assert cfg.epochs == 3
    assert cfg.augment_d4 is False
    assert cfg.widths == [8, 16]


def test_unknown_kind_rejected(tmp_path):
    path = tmp_path / "c.yaml"
    path.write_text("kind: nope\n")
    with pytest.raises(ValueError):
        load_config(path)


def test_unknown_override_key_rejected(tmp_path):
    path = tmp_path / "c.yaml"
    path.write_text("kind: mask\n")
    with pytest.raises(ValueError):
        load_config(path, ["bogus=1"])


def test_malformed_override_rejected(tmp_path):
    path = tmp_path / "c.yaml"
    path.write_text("kind: mask\n")
    with pytest.raises(ValueError):
        load_config(path, ["epochs"])


def test_unknown_optimizer_rejected(tmp_path):
    path = tmp_path / "c.yaml"
    path.write_text("kind: mask\noptimizer: sgd\n")
    with pytest.raises(ValueError):
        load_config(path)
