from fastapi import FastAPI, Response, status

app = FastAPI(title="vistructum-sidecar")

MODEL_VERSION = "bf-bin-1"
LABELS = ["ok", "hakenkreuz"]


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/version")
def version():
    return {"model_version": MODEL_VERSION, "labels": LABELS}


@app.post("/infer", status_code=status.HTTP_501_NOT_IMPLEMENTED)
def infer(response: Response):
    response.status_code = status.HTTP_501_NOT_IMPLEMENTED
    return {"detail": "model not loaded"}
