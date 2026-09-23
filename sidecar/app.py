import logging
import os
from contextlib import asynccontextmanager
from pathlib import Path

import onnxruntime as ort
from fastapi import FastAPI, HTTPException, Response, status

from service import InferRequest, InferResponse, LoadedModel, build_scene, run_inference
from vistructum_ml import metadata as vmeta
from vistructum_ml.contract import KINDS, META_COMMIT, META_FEATURE_SPEC

logger = logging.getLogger("vistructum.sidecar")


def load_models(model_dir):
    models = {}
    threads = int(os.environ.get("ORT_THREADS", "1"))
    options = ort.SessionOptions()
    options.intra_op_num_threads = threads
    options.inter_op_num_threads = 1
    for path in sorted(Path(model_dir).glob("*.onnx")):
        try:
            session = ort.InferenceSession(str(path), sess_options=options, providers=["CPUExecutionProvider"])
            raw_meta = vmeta.read_session_metadata(session)
            info = vmeta.validate(raw_meta)
        except Exception as exc:  # noqa: BLE001
            logger.error("skipping invalid model %s: %s", path, exc)
            continue
        kind = info["kind"]
        if kind in models:
            raise RuntimeError(f"duplicate model kind {kind!r}: {models[kind].path} and {path}")
        models[kind] = LoadedModel(
            session=session,
            kind=kind,
            version=info["version"],
            labels=info["labels"],
            threshold=info["threshold"],
            feature_spec=raw_meta.get(META_FEATURE_SPEC, ""),
            commit=raw_meta.get(META_COMMIT, ""),
            path=str(path),
        )
    return models


@asynccontextmanager
async def lifespan(app: FastAPI):
    model_dir = os.environ.get("MODEL_DIR", "/models")
    app.state.models = load_models(model_dir)
    yield
    app.state.models = {}


app = FastAPI(title="vistructum-sidecar", lifespan=lifespan)


@app.get("/health")
def health(response: Response):
    models = getattr(app.state, "models", {})
    loaded = {name: name in models for name in KINDS}
    loaded_count = sum(loaded.values())
    if loaded_count == 0:
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
        return {"status": "degraded", "models": loaded}
    response.status_code = status.HTTP_200_OK
    return {"status": "ok" if loaded_count == len(loaded) else "degraded", "models": loaded}


@app.get("/version")
def version():
    models = getattr(app.state, "models", {})
    return {kind: model.version_info() for kind, model in models.items()}


@app.post("/infer", response_model=InferResponse)
def infer(payload: InferRequest):
    models = getattr(app.state, "models", {})
    model = models.get(payload.kind)
    if model is None:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, f"model for kind {payload.kind!r} not loaded")
    try:
        scene = build_scene(payload)
    except ValueError as exc:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, str(exc))
    return run_inference(model, scene)
