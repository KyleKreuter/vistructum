import json
import sys
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
from eval_recall import LABELS, load_test, model_path, parse_selection

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


def cam_centroid(cam):
    total = cam.sum()
    if total <= 0:
        return cam.shape[0] / 2 - 0.5, cam.shape[1] / 2 - 0.5
    rows = (cam.sum(axis=1) * np.arange(cam.shape[0])).sum() / total
    cols = (cam.sum(axis=0) * np.arange(cam.shape[1])).sum() / total
    return rows, cols


def self_check():
    blank = np.zeros((1, 64, 64), dtype=np.float32)
    assert blank.shape == (1, 64, 64)
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


if __name__ == "__main__":
    sys.exit(main())
