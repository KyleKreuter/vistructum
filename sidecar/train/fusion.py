import json
import sys
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
from eval_recall import LABELS, load_test, model_path, parse_selection

FORBIDDEN = (1,)
FEATURE_OUTPUT = "/features/features.10/HardSwish_output_0"


def softmax(logits):
    shifted = logits - logits.max(axis=-1, keepdims=True)
    exponential = np.exp(shifted)
    return exponential / exponential.sum(axis=-1, keepdims=True)


def tta_views(image):
    views = []
    for turns in range(4):
        rotated = np.rot90(image, turns).copy()
        views.append(rotated)
        views.append(np.fliplr(rotated).copy())
    return views


def predict_window(session, image):
    output = session.get_outputs()[0].name
    name = session.get_inputs()[0].name
    probs = []
    for view in tta_views(image[0]):
        logits = session.run([output], {name: view[None][None]})[0]
        probs.append(softmax(logits.astype(np.float64))[0])
    return np.mean(probs, axis=0)


def predict_single(session, image):
    output = session.get_outputs()[0].name
    name = session.get_inputs()[0].name
    logits = session.run([output], {name: image[None]})[0]
    return softmax(logits.astype(np.float64))[0]


def fuse_or(window_probs, thresholds):
    fused = np.max(window_probs, axis=0)
    for index in FORBIDDEN:
        if fused[index] >= thresholds[index]:
            return index, float(fused[index])
    return 0, float(fused[0])


def fuse_majority(window_probs, thresholds):
    votes = [int(row.argmax()) for row in window_probs]
    counts = np.bincount(votes, minlength=len(LABELS))
    winner = int(counts.argmax())
    confidence = float(np.mean([row[winner] for row in window_probs]))
    if confidence < thresholds[winner]:
        return 0, float(np.mean([row[0] for row in window_probs]))
    return winner, confidence


def batched_session(model):
    loaded = onnx.load(str(model))
    dim = loaded.graph.input[0].type.tensor_type.shape.dim[0]
    dim.dim_param = "batch"
    return ort.InferenceSession(loaded.SerializeToString(),
                                providers=["CPUExecutionProvider"])


def predict_batch(session, images):
    output = session.get_outputs()[0].name
    name = session.get_inputs()[0].name
    logits = session.run([output], {name: np.stack(images)})[0]
    return softmax(logits.astype(np.float64))


def cam_session(model):
    loaded = onnx.load(str(model))
    weights = onnx.numpy_helper.to_array(
        next(i for i in loaded.graph.initializer
             if i.name == "head.weight_quantized")).astype(np.float64)
    scale = float(onnx.numpy_helper.to_array(
        next(i for i in loaded.graph.initializer
             if i.name == "head.weight_scale")))
    zero = float(onnx.numpy_helper.to_array(
        next(i for i in loaded.graph.initializer
             if i.name == "head.weight_zero_point")))
    head = (weights - zero) * scale
    session = ort.InferenceSession(_serialized_with_features(str(model)),
                                   providers=["CPUExecutionProvider"])
    return session, head


def _serialized_with_features(model):
    loaded = onnx.load(model)
    graph = loaded.graph
    if FEATURE_OUTPUT not in [output.name for output in graph.output]:
        feature = next(value for value in graph.value_info
                       if value.name == FEATURE_OUTPUT)
        output = onnx.helper.make_tensor_value_info(
            FEATURE_OUTPUT, feature.type.tensor_type.elem_type, None)
        graph.output.append(output)
    return loaded.SerializeToString()


def cam_heatmap(session, head, image, class_index):
    output = session.get_outputs()[0].name
    name = session.get_inputs()[0].name
    names = [o.name for o in session.get_outputs()]
    scores_name = names[0] if names[0] != FEATURE_OUTPUT else names[1]
    feature_name = names[1] if names[0] != FEATURE_OUTPUT else names[0]
    scores, features = session.run([scores_name, feature_name],
                                   {name: image[None]})
    probs = softmax(scores.astype(np.float64))[0]
    maps = features[0].astype(np.float64)
    cam = np.maximum((maps * head[:, class_index][:, None, None]).sum(axis=0), 0.0)
    peak = cam.max()
    if peak > 0:
        cam = cam / peak
    return cam, probs


def cam_recenter(image, cam):
    mask = cam >= 0.5 * cam.max() if cam.max() > 0 else np.zeros_like(cam, bool)
    if not mask.any():
        return image
    rows, cols = np.nonzero(mask)
    grid = image.shape[1]
    factor = grid / cam.shape[0]
    shift_r = int(round(grid / 2 - (rows.mean() + 0.5) * factor))
    shift_c = int(round(grid / 2 - (cols.mean() + 0.5) * factor))
    moved = np.roll(image, (shift_r, shift_c), axis=(1, 2))
    edge = float(np.median(image))
    if shift_r > 0:
        moved[:, :shift_r, :] = edge
    elif shift_r < 0:
        moved[:, shift_r:, :] = edge
    if shift_c > 0:
        moved[:, :, :shift_c] = edge
    elif shift_c < 0:
        moved[:, :, shift_c:] = edge
    return moved


def predict_cam(session, head, image, thresholds, band=(0.1, 0.9)):
    plain = predict_single(session, image)
    score = float(plain[FORBIDDEN[0]])
    if score < band[0]:
        return 0, float(plain[0])
    if score >= band[1]:
        return FORBIDDEN[0], score
    cam, _ = cam_heatmap(session, head, image, FORBIDDEN[0])
    moved = cam_recenter(image, cam)
    second = predict_single(session, moved)
    if float(second[FORBIDDEN[0]]) >= thresholds[FORBIDDEN[0]]:
        return FORBIDDEN[0], float(second[FORBIDDEN[0]])
    return 0, float(second[0])


def cam_centroid(cam):
    total = cam.sum()
    if total <= 0:
        return cam.shape[0] / 2 - 0.5, cam.shape[1] / 2 - 0.5
    rows = (cam.sum(axis=1) * np.arange(cam.shape[0])).sum() / total
    cols = (cam.sum(axis=0) * np.arange(cam.shape[1])).sum() / total
    return rows, cols


def nearest_center(centers, point):
    best, best_distance = 0, None
    for index, center in enumerate(centers):
        distance = (center[0] - point[0]) ** 2 + (center[1] - point[1]) ** 2
        if best_distance is None or distance < best_distance:
            best, best_distance = index, distance
    return best


def predict_knm_grid(session, head, images, positions, thresholds, band=(0.1, 0.9)):
    singles = [predict_single(session, image) for image in images]
    size = images[0].shape[-1]
    centers = [(top + size / 2, left + size / 2) for top, left in positions]
    adjusted = [row.copy() for row in singles]
    for index, image in enumerate(images):
        score = float(singles[index][FORBIDDEN[0]])
        if score < band[0] or score >= band[1]:
            continue
        cam, _ = cam_heatmap(session, head, image, FORBIDDEN[0])
        rows, cols = cam_centroid(cam)
        factor = size / cam.shape[0]
        top, left = positions[index]
        point = (top + (rows + 0.5) * factor, left + (cols + 0.5) * factor)
        other = nearest_center(centers, point)
        if float(singles[other][FORBIDDEN[0]]) > score:
            adjusted[index] = singles[other]
    label, confidence = fuse_or(np.array(adjusted), thresholds)
    return label, confidence, adjusted


def self_check():
    single = np.array([[0.80, 0.20]])
    assert fuse_or(single, (0, 0.85))[0] == 0
    assert fuse_or(single, (0, 0.15))[0] == 1
    split = np.array([[0.90, 0.10],
                      [0.20, 0.80],
                      [0.85, 0.15],
                      [0.90, 0.10]])
    assert fuse_or(split, (0, 0.5))[0] == 1
    assert fuse_majority(split, (0, 0.5))[0] == 0
    assert fuse_majority(split, (0, 0.1))[0] == 0
    blank = np.zeros((1, 64, 64), dtype=np.float32)
    assert cam_recenter(blank, np.zeros((16, 16))).shape == blank.shape
    hot = np.zeros((16, 16))
    hot[12:, 12:] = 1.0
    moved = cam_recenter(np.ones((1, 64, 64), dtype=np.float32), hot)
    assert moved.shape == (1, 64, 64)
    assert nearest_center([(0, 0), (0, 64), (64, 0)], (5, 60)) == 1
    rows, cols = cam_centroid(np.zeros((8, 8)))
    assert (rows, cols) == (3.5, 3.5)
    print("fusion self-check ok")


def main():
    self_check()
    root = Path(__file__).resolve().parent
    tags, test_sets, _ = parse_selection(sys.argv[1:])
    tests = {name: load_test(root / name) for name in test_sets}
    print("exp | testsatz | modus | klasse | recall | precision")
    for tag in tags:
        model = model_path(root, tag)
        if not model.is_file():
            continue
        session = ort.InferenceSession(str(model), providers=["CPUExecutionProvider"])
        cam, head = cam_session(model)
        for name, (images, labels) in tests.items():
            for mode, predictor in (("single", predict_single), ("tta", predict_window)):
                preds = [int(predictor(session, image).argmax()) for image in images]
                for index, label in enumerate(LABELS):
                    true_positives = sum(1 for t, p in zip(labels, preds)
                                         if int(t) == index and p == index)
                    actual = sum(1 for t in labels if int(t) == index)
                    predicted = sum(1 for p in preds if p == index)
                    recall = true_positives / actual if actual else 0.0
                    precision = true_positives / predicted if predicted else 0.0
                    print(f"{tag} | {name} | {mode} | {label} | "
                          f"{recall:.3f} | {precision:.3f}")
            preds = [predict_cam(cam, head, image, (0, 0.5))[0] for image in images]
            for index, label in enumerate(LABELS):
                true_positives = sum(1 for t, p in zip(labels, preds)
                                     if int(t) == index and p == index)
                actual = sum(1 for t in labels if int(t) == index)
                predicted = sum(1 for p in preds if p == index)
                recall = true_positives / actual if actual else 0.0
                precision = true_positives / predicted if predicted else 0.0
                print(f"{tag} | {name} | cam | {label} | "
                      f"{recall:.3f} | {precision:.3f}")


if __name__ == "__main__":
    sys.exit(main())
