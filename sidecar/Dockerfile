FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 MODEL_DIR=/models

RUN groupadd --system sidecar && useradd --system --gid sidecar --home-dir /app sidecar

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY pyproject.toml .
COPY vistructum_ml ./vistructum_ml
COPY app.py service.py ./
RUN pip install --no-cache-dir --no-deps .

RUN mkdir -p /models && chown -R sidecar:sidecar /app /models
USER sidecar

VOLUME /models
EXPOSE 8000

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=2)"

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
