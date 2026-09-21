import argparse
import json
import time
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, TensorDataset

GRID = 64
LABELS = ("ok", "hakenkreuz")
MODEL_VERSION = "bf-bin-1"
PRECISION_TARGET = 0.95
MAX_MODEL_BYTES = 500 * 1024
MAX_INFER_SECONDS = 0.01
EPOCHS = 20
BATCH = 128
LEARNING_RATE = 1e-3
SEED = 42


class SymbolNet(nn.Module):
    def __init__(self):
        super().__init__()
        self.features = nn.Sequential(
            nn.Conv2d(1, 16, 3, padding=1),
            nn.ReLU(),
            nn.MaxPool2d(2),
            nn.Conv2d(16, 16, 3, padding=1),
            nn.ReLU(),
            nn.MaxPool2d(2),
            nn.Conv2d(16, 16, 3, padding=1),
            nn.ReLU(),
        )
        self.head = nn.Sequential(
            nn.Flatten(),
            nn.Linear(4096, 64),
            nn.ReLU(),
            nn.Linear(64, len(LABELS)),
        )

    def forward(self, x):
        return self.head(self.features(x))


class SymbolNetV2(nn.Module):
    def __init__(self):
        super().__init__()
        self.features = nn.Sequential(
            nn.Conv2d(1, 16, 3, padding=1),
            nn.BatchNorm2d(16),
            nn.Hardswish(),
            nn.MaxPool2d(2),
            nn.Conv2d(16, 32, 3, padding=1),
            nn.BatchNorm2d(32),
            nn.Hardswish(),
            nn.MaxPool2d(2),
            nn.Conv2d(32, 64, 3, padding=1),
            nn.BatchNorm2d(64),
            nn.Hardswish(),
        )
        self.head = nn.Linear(64, len(LABELS))

    def forward(self, x):
        return self.head(self.features(x).mean(dim=(2, 3)))


class SymbolNetV3(nn.Module):
    def __init__(self):
        super().__init__()
        self.features = nn.Sequential(
            nn.Conv2d(1, 32, 3, padding=1),
            nn.BatchNorm2d(32),
            nn.Hardswish(),
            nn.MaxPool2d(2),
            nn.Conv2d(32, 64, 3, padding=1),
            nn.BatchNorm2d(64),
            nn.Hardswish(),
            nn.MaxPool2d(2),
            nn.Conv2d(64, 128, 3, padding=1),
            nn.BatchNorm2d(128),
            nn.Hardswish(),
        )
        self.head = nn.Linear(128, len(LABELS))

    def forward(self, x):
        return self.head(self.features(x).mean(dim=(2, 3)))


def standardize(images):
    flat = images.reshape(images.shape[0], -1)
    mean = flat.mean(dim=1).reshape(-1, 1, 1, 1)
    std = flat.std(dim=1).reshape(-1, 1, 1, 1).clamp_min(1e-3)
    return (images - mean) / std


def load_split(data_dir, name):
    raw = np.load(Path(data_dir) / f"{name}.npz")
    images = standardize(torch.from_numpy(raw["images"]).unsqueeze(1).float().div(255.0))
    labels = torch.from_numpy(raw["labels"]).long()
    return TensorDataset(images, labels)


class ModelEMA:
    def __init__(self, model, decay):
        self.decay = decay
        self.shadow = {k: v.cpu().clone() for k, v in model.state_dict().items() if v.is_floating_point()}

    def update(self, model):
        for k, v in model.state_dict().items():
            if k in self.shadow:
                self.shadow[k].mul_(self.decay).add_(v.cpu(), alpha=1.0 - self.decay)

    def apply_to(self, model):
        state = model.state_dict()
        backup = {k: v.cpu().clone() for k, v in state.items() if k in self.shadow}
        merged = dict(state)
        merged.update(self.shadow)
        model.load_state_dict(merged)
        return backup

    def restore(self, model, backup):
        state = model.state_dict()
        merged = dict(state)
        merged.update(backup)
        model.load_state_dict(merged)


class FocalLoss(nn.Module):
    def __init__(self, gamma=2.0, weight=None):
        super().__init__()
        self.gamma = gamma
        self.weight = weight

    def forward(self, logits, targets):
        log_probs = torch.log_softmax(logits, dim=1)
        gathered = log_probs.gather(1, targets.unsqueeze(1)).squeeze(1)
        losses = (1.0 - gathered.exp()) ** self.gamma * -gathered
        if self.weight is not None:
            losses = losses * self.weight[targets]
        return losses.mean()


def precision_per_label(predictions, targets):
    scores = []
    for label in range(len(LABELS)):
        predicted = predictions == label
        denom = int(predicted.sum())
        if denom == 0:
            scores.append(1.0)
        else:
            scores.append(float(((predictions[predicted] == targets[predicted]).sum()) / denom))
    return scores


@torch.no_grad()
def evaluate(model, loader, device):
    model.eval()
    all_predictions = []
    all_targets = []
    for images, labels in loader:
        all_predictions.append(model(images.to(device)).argmax(dim=1).cpu())
        all_targets.append(labels)
    predictions = torch.cat(all_predictions)
    targets = torch.cat(all_targets)
    accuracy = float((predictions == targets).float().mean())
    return accuracy, precision_per_label(predictions, targets)


def fit(model, train_loader, val_loader, generator, epochs, optimizer_name, learning_rate, weight_decay, label_smoothing, use_focal, warmup_epochs, ema_decay, checkpoint_path, resume, log_every, stop_after, device):
    torch.manual_seed(SEED)
    if device == "cuda":
        torch.cuda.manual_seed_all(SEED)
    if optimizer_name == "adamw":
        optimizer = torch.optim.AdamW(model.parameters(), lr=learning_rate, weight_decay=weight_decay)
    else:
        optimizer = torch.optim.Adam(model.parameters(), lr=learning_rate, weight_decay=weight_decay)
    if warmup_epochs > 0:
        warmup = torch.optim.lr_scheduler.LinearLR(optimizer, start_factor=0.1, total_iters=warmup_epochs)
        cosine = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, max(1, epochs - warmup_epochs))
        scheduler = torch.optim.lr_scheduler.SequentialLR(optimizer, [warmup, cosine], [warmup_epochs])
    else:
        scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, epochs)
    criterion = FocalLoss() if use_focal else nn.CrossEntropyLoss(label_smoothing=label_smoothing)
    tracker = ModelEMA(model, ema_decay) if ema_decay > 0.0 else None
    best_state = None
    best_min_precision = -1.0
    start_epoch = 0
    if resume and checkpoint_path.exists():
        payload = torch.load(checkpoint_path, map_location=device, weights_only=False)
        model.load_state_dict(payload["model"])
        optimizer.load_state_dict(payload["optimizer"])
        scheduler.load_state_dict(payload["scheduler"])
        torch.set_rng_state(payload["rng_state"])
        generator.set_state(payload["generator_state"])
        best_state = payload["best_state"]
        best_min_precision = payload["best_min_precision"]
        if tracker is not None:
            tracker.shadow = payload["ema_shadow"]
        start_epoch = payload["epoch"] + 1
    limit = stop_after if stop_after > 0 else epochs
    for epoch in range(start_epoch, limit):
        model.train()
        for images, labels in train_loader:
            images = images.to(device)
            labels = labels.to(device)
            optimizer.zero_grad()
            criterion(model(images), labels).backward()
            optimizer.step()
            if tracker is not None:
                tracker.update(model)
        scheduler.step()
        if tracker is not None:
            backup = tracker.apply_to(model)
            _, precisions = evaluate(model, val_loader, device)
            tracker.restore(model, backup)
            candidate = {k: v.cpu().clone() for k, v in model.state_dict().items()}
            for k, v in tracker.shadow.items():
                candidate[k] = v.cpu().clone()
        else:
            _, precisions = evaluate(model, val_loader, device)
            candidate = {k: v.cpu().clone() for k, v in model.state_dict().items()}
        if min(precisions) > best_min_precision:
            best_min_precision = min(precisions)
            best_state = candidate
        if (epoch + 1) % log_every == 0 or epoch + 1 == limit:
            print(f"epoch {epoch + 1}/{epochs} val_min_precision {best_min_precision:.4f}", flush=True)
        torch.save(
            {
                "epoch": epoch,
                "model": {k: v.cpu().clone() for k, v in model.state_dict().items()},
                "optimizer": optimizer.state_dict(),
                "scheduler": scheduler.state_dict(),
                "rng_state": torch.get_rng_state(),
                "generator_state": generator.get_state(),
                "best_state": best_state,
                "best_min_precision": best_min_precision,
                "ema_shadow": tracker.shadow if tracker is not None else None,
            },
            checkpoint_path,
        )
    model.load_state_dict(best_state)
    return best_min_precision


def export_onnx(model, out_path):
    model.eval()
    dummy = torch.zeros(1, 1, GRID, GRID)
    torch.onnx.export(model, dummy, str(out_path), input_names=["grid"], output_names=["scores"], dynamic_axes={"grid": {0: "batch"}, "scores": {0: "batch"}}, dynamo=False)


def quantize(model_path, quantized_path):
    from onnxruntime.quantization import QuantType, quantize_dynamic

    quantize_dynamic(str(model_path), str(quantized_path), weight_type=QuantType.QUInt8)


def benchmark(quantized_path, runs=100):
    import onnxruntime

    session = onnxruntime.InferenceSession(str(quantized_path), providers=["CPUExecutionProvider"])
    sample = np.zeros((1, 1, GRID, GRID), dtype=np.float32)
    start = time.perf_counter()
    for _ in range(runs):
        session.run(None, {"grid": sample})
    return (time.perf_counter() - start) / runs


def train(data_dir, out_dir, epochs, arch, batch, optimizer_name, learning_rate, weight_decay, label_smoothing, use_focal, warmup_epochs, ema_decay, resume, log_every, stop_after, device):
    data_dir = Path(data_dir)
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    if device == "cuda" and not torch.cuda.is_available():
        raise SystemExit("device cuda requested but torch.cuda.is_available() is False")
    if device == "mps" and not torch.backends.mps.is_available():
        raise SystemExit("device mps requested but torch.backends.mps.is_available() is False")
    torch.manual_seed(SEED)
    generator = torch.Generator().manual_seed(SEED)
    checkpoint_path = out_dir / "checkpoint.pt"
    train_loader = DataLoader(load_split(data_dir, "train"), batch_size=batch, shuffle=True, generator=generator)
    val_loader = DataLoader(load_split(data_dir, "val"), batch_size=batch)
    test_loader = DataLoader(load_split(data_dir, "test"), batch_size=batch)

    model = {"v3": SymbolNetV3, "v2": SymbolNetV2}.get(arch, SymbolNet)()
    model.to(device)
    param_count = sum(p.numel() for p in model.parameters())
    best_val_worst = fit(model, train_loader, val_loader, generator, epochs, optimizer_name, learning_rate, weight_decay, label_smoothing, use_focal, warmup_epochs, ema_decay, checkpoint_path, resume, log_every, stop_after, device)
    if stop_after > 0:
        return None
    test_accuracy, test_precisions = evaluate(model, test_loader, device)

    model.cpu()

    model_path = out_dir / f"{MODEL_VERSION}.onnx"
    quantized_path = out_dir / f"{MODEL_VERSION}-int8.onnx"
    export_onnx(model, model_path)
    quantize(model_path, quantized_path)
    model_path.unlink()
    quantized_path.rename(model_path)
    checkpoint_path.unlink(missing_ok=True)

    model_bytes = model_path.stat().st_size
    infer_seconds = benchmark(model_path)
    metrics = {
        "model_version": MODEL_VERSION,
        "labels": list(LABELS),
        "params": param_count,
        "arch": arch,
        "optimizer": optimizer_name,
        "learning_rate": learning_rate,
        "weight_decay": weight_decay,
        "label_smoothing": label_smoothing,
        "use_focal": use_focal,
        "warmup_epochs": warmup_epochs,
        "ema_decay": ema_decay,
        "batch": batch,
        "epochs": epochs,
        "seed": SEED,
        "device": device,
        "best_val_min_precision": best_val_worst,
        "test_accuracy": test_accuracy,
        "test_precision": {label: score for label, score in zip(LABELS, test_precisions)},
        "model_bytes": model_bytes,
        "infer_seconds_mean": infer_seconds,
        "precision_target": PRECISION_TARGET,
    }
    (out_dir / f"{MODEL_VERSION}-metrics.json").write_text(json.dumps(metrics, indent=2))
    print(json.dumps(metrics, indent=2))
    gates_hold = min(test_precisions) >= PRECISION_TARGET and model_bytes < MAX_MODEL_BYTES and infer_seconds < MAX_INFER_SECONDS
    if not gates_hold:
        raise SystemExit(1)
    return metrics


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("data", nargs="?", default="data")
    parser.add_argument("target", nargs="?", default="models")
    parser.add_argument("rounds", nargs="?", type=int, default=EPOCHS)
    parser.add_argument("--arch", choices=("v1", "v2", "v3"), default="v1")
    parser.add_argument("--batch", type=int, default=BATCH)
    parser.add_argument("--optimizer", choices=("adam", "adamw"), default="adam")
    parser.add_argument("--lr", type=float, default=LEARNING_RATE)
    parser.add_argument("--weight-decay", type=float, default=0.0)
    parser.add_argument("--label-smoothing", type=float, default=0.0)
    parser.add_argument("--no-focal", action="store_true")
    parser.add_argument("--warmup", type=int, default=0)
    parser.add_argument("--ema", type=float, default=0.0)
    parser.add_argument("--resume", action="store_true")
    parser.add_argument("--log-every", type=int, default=10)
    parser.add_argument("--stop-after", type=int, default=0)
    parser.add_argument("--device", choices=("cpu", "cuda", "mps"), default="cpu")
    args = parser.parse_args()
    train(args.data, args.target, args.rounds, args.arch, args.batch, args.optimizer, args.lr, args.weight_decay, args.label_smoothing, not args.no_focal, args.warmup, args.ema, args.resume, args.log_every, args.stop_after, args.device)
