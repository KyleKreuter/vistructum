import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort

LABELS = ("ok", "hakenkreuz")


def load_test(data_dir):
    raw = np.load(Path(data_dir) / "test.npz")
    images = raw["images"].astype(np.float32) / 255.0
    flat = images.reshape(images.shape[0], -1)
    mean = flat.mean(axis=1).reshape(-1, 1, 1, 1)
    std = np.clip(flat.std(axis=1).reshape(-1, 1, 1, 1), 1e-3, None)
    images = ((images[:, None, :, :] - mean) / std).astype(np.float32)
    return images, raw["labels"].astype(np.int64), raw["subtypes"].astype(str)


def predict(session, images):
    output = session.get_outputs()[0].name
    name = session.get_inputs()[0].name
    preds = np.empty(len(images), dtype=np.int64)
    for i, image in enumerate(images):
        logits = session.run([output], {name: image[None]})[0]
        preds[i] = int(logits.argmax())
    return preds


def main():
    root = Path(__file__).resolve().parent
    test_name, tag_a, tag_b = sys.argv[1], sys.argv[2], sys.argv[3]
    images, labels, subtypes = load_test(root / test_name)
    predictions = {}
    for tag in (tag_a, tag_b):
        suite, name = tag.split("-", 1) if "-" in tag else ("", tag)
        model = root / f"models-{tag}" / ("bf-bin-1.onnx")
        session = ort.InferenceSession(str(model), providers=["CPUExecutionProvider"])
        predictions[tag] = predict(session, images)
    print(f"{'subtype':<16}{'n_hk':>7}{tag_a + '-R':>9}{tag_b + '-R':>9}{'delta':>8}")
    for subtype in sorted(set(subtypes[labels == 1])):
        mask = (labels == 1) & (subtypes == subtype)
        total = int(mask.sum())
        recalls = [(predictions[tag][mask] == 1).mean() for tag in (tag_a, tag_b)]
        print(f"{subtype:<16}{total:>7}{recalls[0]:>9.3f}{recalls[1]:>9.3f}"
              f"{recalls[1] - recalls[0]:>8.3f}")
    print(f"{'--- ok false-alarm-rate by subtype ---'}")
    print(f"{'subtype':<16}{'n_ok':>7}{tag_a + '-FA':>9}{tag_b + '-FA':>9}{'delta':>8}")
    for subtype in sorted(set(subtypes[labels == 0])):
        mask = (labels == 0) & (subtypes == subtype)
        total = int(mask.sum())
        rates = [(predictions[tag][mask] == 1).mean() for tag in (tag_a, tag_b)]
        print(f"{subtype:<16}{total:>7}{rates[0]:>9.3f}{rates[1]:>9.3f}"
              f"{rates[1] - rates[0]:>8.3f}")


if __name__ == "__main__":
    sys.exit(main())
