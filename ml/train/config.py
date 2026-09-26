import argparse
from dataclasses import dataclass, field, fields
from pathlib import Path

import yaml

from vistructum_ml.contract import KINDS


@dataclass
class TrainConfig:
    kind: str = "mask"
    data_dir: str = "data"
    out_dir: str = "out"
    widths: list = field(default_factory=lambda: [16, 32, 64])
    depths: list = field(default_factory=list)
    dropout: float = 0.0
    epochs: int = 30
    batch: int = 128
    optimizer: str = "adamw"
    lr: float = 1e-3
    weight_decay: float = 1e-4
    warmup_epochs: int = 2
    label_smoothing: float = 0.0
    focal_gamma: float = 0.0
    ema_decay: float = 0.0
    pos_weight: float = 1.0
    augment_d4: bool = True
    device: str = "cpu"
    seed: int = 42
    num_threads: int = 4
    quantize: bool = True
    tta: bool = False
    scan_negatives: int = 0
    scan_positives: int = 0
    scan_workers: int = 4
    gen_train: int = 60000
    gen_val: int = 12500
    gen_test: int = 12500
    gen_holdout: int = 12500
    findings_dir: str = ""
    findings_repeat: int = 1


VALID_KEYS = {f.name for f in fields(TrainConfig)}


def _coerce(value, current):
    if isinstance(current, bool):
        return value.strip().lower() in ("1", "true", "yes", "on")
    if isinstance(current, list):
        return [int(v) for v in value.split(",") if v.strip()]
    if isinstance(current, int):
        return int(value)
    if isinstance(current, float):
        return float(value)
    return value


def _apply_overrides(cfg, overrides):
    for item in overrides or []:
        key, sep, value = item.partition("=")
        if not sep:
            raise ValueError(f"malformed override {item!r}, expected key=value")
        if key not in VALID_KEYS:
            raise ValueError(f"unknown override key {key!r}")
        setattr(cfg, key, _coerce(value, getattr(cfg, key)))
    return cfg


def load_config(path, overrides=None):
    raw = yaml.safe_load(Path(path).read_text()) or {}
    unknown = set(raw) - VALID_KEYS
    if unknown:
        raise ValueError(f"unknown config keys: {sorted(unknown)}")
    cfg = TrainConfig(**raw)
    _apply_overrides(cfg, overrides)
    if cfg.kind not in KINDS:
        raise ValueError(f"unknown kind {cfg.kind!r}, expected one of {sorted(KINDS)}")
    if cfg.optimizer not in ("adam", "adamw"):
        raise ValueError(f"unknown optimizer {cfg.optimizer!r}")
    if cfg.findings_repeat < 1:
        raise ValueError("findings_repeat must be at least 1")
    if cfg.device not in ("cpu", "cuda", "mps"):
        raise ValueError(f"unknown device {cfg.device!r}")
    return cfg


def add_config_args(parser):
    parser.add_argument("--config", required=True)
    parser.add_argument("--set", action="append", default=[])
    return parser


def parse_args(argv=None):
    parser = argparse.ArgumentParser()
    add_config_args(parser)
    args = parser.parse_args(argv)
    return load_config(args.config, args.set)
