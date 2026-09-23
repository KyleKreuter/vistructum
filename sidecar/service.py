import base64
import time
from dataclasses import dataclass

import numpy as np
from pydantic import BaseModel, Field

from vistructum_ml import detect, features
from vistructum_ml.contract import FULLSCAN, INPUT_NAME, MASK, OUTPUT_NAME, POSITIVE
from vistructum_ml.scene import Scene

BATCH_SIZE = 64
MAX_DETECTIONS = 20

REQUIRED_FIELDS = {
    MASK.name: ("modified",),
    FULLSCAN.name: ("blocks", "heights", "luminance"),
}


@dataclass
class LoadedModel:
    session: object
    kind: str
    version: str
    labels: tuple
    threshold: float
    feature_spec: str
    commit: str
    path: str
    min_votes: int

    def version_info(self):
        return {
            "model_version": self.version,
            "labels": list(self.labels),
            "threshold": self.threshold,
            "feature_spec": self.feature_spec,
            "commit": self.commit,
            "min_votes": self.min_votes,
        }


class InferRequest(BaseModel):
    kind: str
    width: int = Field(ge=1, le=512)
    height: int = Field(ge=1, le=512)
    blocks: str | None = None
    heights: str | None = None
    luminance: str | None = None
    modified: str | None = None


class Detection(BaseModel):
    top: int
    left: int
    bottom: int
    right: int
    score: float
    votes: int


class InferResponse(BaseModel):
    kind: str
    model_version: str
    threshold: float
    windows: int
    max_score: float
    flagged: bool
    detections: list[Detection]
    elapsed_ms: float
    min_votes: int


def decode_int16(data_b64, count, name):
    if data_b64 is None:
        return np.zeros(count, dtype=np.int16)
    raw = base64.b64decode(data_b64)
    arr = np.frombuffer(raw, dtype="<i2")
    if arr.size != count:
        raise ValueError(f"{name} has {arr.size} int16 values, expected {count}")
    return arr


def decode_uint8(data_b64, count, name):
    if data_b64 is None:
        return np.zeros(count, dtype=np.uint8)
    raw = base64.b64decode(data_b64)
    arr = np.frombuffer(raw, dtype=np.uint8)
    if arr.size != count:
        raise ValueError(f"{name} has {arr.size} uint8 values, expected {count}")
    return arr


def build_scene(payload):
    count = payload.width * payload.height
    shape = (payload.height, payload.width)
    required = REQUIRED_FIELDS.get(payload.kind, ())
    for name in required:
        if getattr(payload, name) is None:
            raise ValueError(f"{name!r} is required for kind {payload.kind!r}")
    blocks = decode_int16(payload.blocks, count, "blocks").astype(np.int32).reshape(shape)
    heights = decode_int16(payload.heights, count, "heights").astype(np.int32).reshape(shape)
    luminance = decode_uint8(payload.luminance, count, "luminance").reshape(shape)
    modified = decode_uint8(payload.modified, count, "modified")
    if not np.all((modified == 0) | (modified == 1)):
        raise ValueError("modified must contain only 0 or 1")
    return Scene(blocks, heights, luminance, modified.reshape(shape).astype(bool))


def run_batches(session, batch):
    n = batch.shape[0]
    scores = np.empty(n, dtype=np.float64)
    for i in range(0, n, BATCH_SIZE):
        chunk = batch[i:i + BATCH_SIZE]
        outputs = session.run([OUTPUT_NAME], {INPUT_NAME: chunk})
        scores[i:i + chunk.shape[0]] = outputs[0][:, POSITIVE]
    return scores


def run_inference(model, scene):
    start = time.perf_counter()
    feats = features.extract(model.kind, scene)
    positions, batch = features.windows(model.kind, feats)
    scores = run_batches(model.session, batch)
    max_score = float(scores.max()) if scores.size else 0.0
    flagged_clusters = detect.flagged(positions, scores, model.threshold, model.min_votes)
    detections = [
        {key: cluster[key] for key in ("top", "left", "bottom", "right", "score", "votes")}
        for cluster in flagged_clusters[:MAX_DETECTIONS]
    ]
    elapsed_ms = (time.perf_counter() - start) * 1000.0
    return {
        "kind": model.kind,
        "model_version": model.version,
        "threshold": model.threshold,
        "windows": int(batch.shape[0]),
        "max_score": max_score,
        "flagged": len(flagged_clusters) > 0,
        "detections": detections,
        "elapsed_ms": elapsed_ms,
        "min_votes": model.min_votes,
    }
