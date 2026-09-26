import argparse
import json
from dataclasses import asdict
from pathlib import Path

import torch
from config import add_config_args, load_config
from model import SymbolNet
from torch import nn

from data import d4_augment, findings_split, load_split, make_loader
from vistructum_ml.gates import GATES, metrics_at, select_threshold


class ModelEMA:
    def __init__(self, model, decay):
        self.decay = decay
        self.shadow = {k: v.cpu().clone() for k, v in model.state_dict().items() if v.is_floating_point()}

    def update(self, model):
        for k, v in model.state_dict().items():
            if k in self.shadow:
                self.shadow[k].mul_(self.decay).add_(v.detach().cpu(), alpha=1.0 - self.decay)

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
    def __init__(self, gamma, weight=None):
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


def build_optimizer(model, cfg):
    cls = torch.optim.AdamW if cfg.optimizer == "adamw" else torch.optim.Adam
    return cls(model.parameters(), lr=cfg.lr, weight_decay=cfg.weight_decay)


def build_scheduler(optimizer, cfg):
    if cfg.warmup_epochs > 0:
        warmup = torch.optim.lr_scheduler.LinearLR(optimizer, start_factor=0.1, total_iters=cfg.warmup_epochs)
        cosine = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, max(1, cfg.epochs - cfg.warmup_epochs))
        return torch.optim.lr_scheduler.SequentialLR(optimizer, [warmup, cosine], [cfg.warmup_epochs])
    return torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, max(1, cfg.epochs))


def build_criterion(cfg, device):
    weight = torch.tensor([1.0, cfg.pos_weight], dtype=torch.float32, device=device)
    if cfg.focal_gamma > 0:
        return FocalLoss(cfg.focal_gamma, weight)
    return nn.CrossEntropyLoss(weight=weight, label_smoothing=cfg.label_smoothing)


@torch.no_grad()
def evaluate_split(model, x, y, device, batch=512):
    model.eval()
    criterion = nn.CrossEntropyLoss(reduction="sum")
    probs = []
    total_loss = 0.0
    for i in range(0, len(y), batch):
        xb = x[i:i + batch].to(device)
        yb = y[i:i + batch].to(device)
        logits = model(xb)
        total_loss += float(criterion(logits, yb))
        probs.append(torch.softmax(logits, dim=1)[:, 1].cpu())
    scores = torch.cat(probs).numpy()
    return scores, total_loss / len(y)


def candidate_better(candidate, best):
    if best is None:
        return True
    if candidate["recall"] is not None and best["recall"] is None:
        return True
    if candidate["recall"] is None and best["recall"] is not None:
        return False
    if candidate["recall"] is not None:
        if candidate["recall"] != best["recall"]:
            return candidate["recall"] > best["recall"]
        return candidate["val_loss"] < best["val_loss"]
    return candidate["val_loss"] < best["val_loss"]


def fit(model, cfg, device, checkpoint_path, resume, log_every=1):
    gate = GATES[cfg.kind]
    torch.manual_seed(cfg.seed)
    generator = torch.Generator().manual_seed(cfg.seed)
    train_loader = make_loader(cfg.data_dir, "train", cfg.batch, shuffle=True, generator=generator,
                               extra=findings_split(cfg, "train"), repeat=cfg.findings_repeat)
    val_x, val_y = load_split(cfg.data_dir, "val")
    optimizer = build_optimizer(model, cfg)
    scheduler = build_scheduler(optimizer, cfg)
    criterion = build_criterion(cfg, device)
    ema = ModelEMA(model, cfg.ema_decay) if cfg.ema_decay > 0 else None
    best = None
    best_state = None
    best_threshold = 0.5
    start_epoch = 0
    if resume and checkpoint_path.exists():
        payload = torch.load(checkpoint_path, map_location=device, weights_only=False)
        model.load_state_dict(payload["model"])
        optimizer.load_state_dict(payload["optimizer"])
        scheduler.load_state_dict(payload["scheduler"])
        torch.set_rng_state(payload["rng_state"])
        generator.set_state(payload["generator_state"])
        best = payload["best"]
        best_state = payload["best_state"]
        best_threshold = payload["best_threshold"]
        if ema is not None and payload["ema_shadow"] is not None:
            ema.shadow = payload["ema_shadow"]
        start_epoch = payload["epoch"] + 1
    for epoch in range(start_epoch, cfg.epochs):
        model.train()
        train_loss = torch.zeros((), device=device)
        for images, labels in train_loader:
            images = images.to(device)
            if cfg.augment_d4:
                images = d4_augment(images, generator)
            labels = labels.to(device)
            optimizer.zero_grad()
            loss = criterion(model(images), labels)
            loss.backward()
            optimizer.step()
            train_loss += loss.detach()
            if ema is not None:
                ema.update(model)
        scheduler.step()
        if ema is not None:
            backup = ema.apply_to(model)
            scores, val_loss = evaluate_split(model, val_x, val_y, device)
            ema.restore(model, backup)
        else:
            scores, val_loss = evaluate_split(model, val_x, val_y, device)
        val_labels = val_y.numpy()
        threshold = select_threshold(scores, val_labels, gate)
        recall = metrics_at(scores, val_labels, threshold)["recall"] if threshold is not None else None
        candidate = {"recall": recall, "val_loss": val_loss}
        state = {k: v.detach().cpu().clone() for k, v in model.state_dict().items()}
        if ema is not None:
            state.update({k: v.clone() for k, v in ema.shadow.items()})
        if candidate_better(candidate, best):
            best = candidate
            best_state = state
            best_threshold = threshold if threshold is not None else 0.5
        if (epoch + 1) % log_every == 0 or epoch + 1 == cfg.epochs:
            print(json.dumps({"epoch": epoch + 1, "train_loss": round(train_loss.item() / max(1, len(train_loader)), 5),
                              "val_loss": val_loss, "recall": recall, "threshold": threshold}), flush=True)
        torch.save(
            {
                "epoch": epoch,
                "model": {k: v.detach().cpu().clone() for k, v in model.state_dict().items()},
                "optimizer": optimizer.state_dict(),
                "scheduler": scheduler.state_dict(),
                "rng_state": torch.get_rng_state(),
                "generator_state": generator.get_state(),
                "best": best,
                "best_state": best_state,
                "best_threshold": best_threshold,
                "ema_shadow": ema.shadow if ema is not None else None,
            },
            checkpoint_path,
        )
    model.load_state_dict(best_state)
    return model, best_threshold, best


def train_model(cfg, resume=False):
    torch.set_num_threads(max(1, cfg.num_threads))
    device = cfg.device
    if device == "cuda" and not torch.cuda.is_available():
        raise SystemExit("device cuda requested but torch.cuda.is_available() is False")
    if device == "mps" and not torch.backends.mps.is_available():
        raise SystemExit("device mps requested but torch.backends.mps.is_available() is False")
    out_dir = Path(cfg.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    model = SymbolNet(cfg.kind, cfg.widths, cfg.dropout, cfg.depths).to(device)
    checkpoint_path = out_dir / "checkpoint.pt"
    model, threshold, best = fit(model, cfg, device, checkpoint_path, resume)
    model.cpu()
    return model, threshold, best


def main():
    parser = argparse.ArgumentParser()
    add_config_args(parser)
    parser.add_argument("--resume", action="store_true")
    args = parser.parse_args()
    cfg = load_config(args.config, args.set)
    model, threshold, best = train_model(cfg, resume=args.resume)
    out_dir = Path(cfg.out_dir)
    checkpoint_path = out_dir / "model.pt"
    torch.save({"model": model.state_dict(), "cfg": asdict(cfg), "threshold": threshold, "metrics": best}, checkpoint_path)
    print(json.dumps({"checkpoint": str(checkpoint_path), "threshold": threshold, "best": best}, indent=2))


if __name__ == "__main__":
    main()
