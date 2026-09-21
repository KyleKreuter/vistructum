import json
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort

LABELS = ("ok", "hakenkreuz")
DEFAULT_EXPERIMENTS = ("a", "b", "c", "d", "e", "f", "g", "h", "i", "j")
DEFAULT_TEST_SETS = ("data", "data-exp-c")


def parse_selection(argv):
    if not argv:
        return (tuple(f"exp9:{name}" for name in DEFAULT_EXPERIMENTS),
                DEFAULT_TEST_SETS, "recall-report.json")
    if "--" in argv:
        cut = argv.index("--")
        exps, sets = argv[:cut], argv[cut + 1:]
    else:
        exps, sets = argv, DEFAULT_TEST_SETS
    tagged = tuple(item if ":" in item else f"exp9:{item}" for item in exps)
    return tagged, tuple(sets) or DEFAULT_TEST_SETS, f"recall-report-{sets[0]}.json"


def model_path(root, tag):
    suite, name = tag.split(":", 1)
    if suite == "exp9":
        return root / f"models-exp-{name}" / "bf-mc-1.onnx"
    return root / f"models-{suite}-{name}" / "bf-bin-1.onnx"


def load_test(data_dir):
    raw = np.load(Path(data_dir) / "test.npz")
    images = raw["images"].astype(np.float32) / 255.0
    flat = images.reshape(images.shape[0], -1)
    mean = flat.mean(axis=1).reshape(-1, 1, 1, 1)
    std = flat.std(axis=1).reshape(-1, 1, 1, 1)
    std = np.clip(std, 1e-3, None)
    images = ((images[:, None, :, :] - mean) / std).astype(np.float32)
    return images, raw["labels"].astype(np.int64)


def confusion_matrix(session, images, labels):
    output = session.get_outputs()[0].name
    name = session.get_inputs()[0].name
    preds = []
    for image in images:
        logits = session.run([output], {name: image[None]})[0]
        preds.append(int(logits.argmax()))
    matrix = np.zeros((2, 2), dtype=np.int64)
    for true, pred in zip(labels, preds):
        matrix[int(true), int(pred)] += 1
    return matrix


def main():
    root = Path(__file__).resolve().parent
    tags, test_sets, report_name = parse_selection(sys.argv[1:])
    tests = {name: load_test(root / name) for name in test_sets}
    report = {}
    for tag in tags:
        model = model_path(root, tag)
        if not model.is_file():
            continue
        session = ort.InferenceSession(str(model), providers=["CPUExecutionProvider"])
        report[tag] = {}
        for name, (images, labels) in tests.items():
            matrix = confusion_matrix(session, images, labels)
            per_class = {}
            for index, label in enumerate(LABELS):
                true_positives = int(matrix[index, index])
                actual = int(matrix[index, :].sum())
                predicted = int(matrix[:, index].sum())
                per_class[label] = {
                    "recall": true_positives / actual if actual else 0.0,
                    "precision": true_positives / predicted if predicted else 0.0,
                    "missed": actual - true_positives,
                    "total": actual,
                }
            report[tag][name] = per_class
    print("exp | testsatz | klasse | recall | precision | verpasst/total")
    for tag in tags:
        if tag not in report:
            continue
        for name in test_sets:
            for label in LABELS:
                cell = report[tag][name][label]
                print(f"{tag} | {name} | {label} | {cell['recall']:.3f} | "
                      f"{cell['precision']:.3f} | {cell['missed']}/{cell['total']}")
    (root / report_name).write_text(json.dumps(report, indent=2))


if __name__ == "__main__":
    sys.exit(main())
