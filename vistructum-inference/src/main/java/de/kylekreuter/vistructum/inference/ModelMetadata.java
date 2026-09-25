package de.kylekreuter.vistructum.inference;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.List;
import java.util.Map;

final class ModelMetadata {

    private ModelMetadata() {
    }

    static ModelInfo validate(Map<String, String> meta, String sha256) throws ModelException {
        List<String> missing = Contract.REQUIRED_META.stream().filter(key -> !meta.containsKey(key)).toList();
        if (!missing.isEmpty()) {
            throw new ModelException("model metadata missing: " + String.join(", ", missing));
        }
        String kind = meta.get(Contract.META_KIND);
        if (ModelKind.byId(kind).isEmpty()) {
            throw new ModelException("unknown model kind '" + kind + "'");
        }
        String featureSpec = meta.get(Contract.META_FEATURE_SPEC);
        if (!Contract.FEATURE_SPEC.equals(featureSpec)) {
            throw new ModelException("feature spec '" + featureSpec + "' != '" + Contract.FEATURE_SPEC + "'");
        }
        List<String> labels = labels(meta.get(Contract.META_LABELS));
        if (!Contract.LABELS.equals(labels)) {
            throw new ModelException("labels " + labels + " != " + Contract.LABELS);
        }
        double threshold = number(meta.get(Contract.META_THRESHOLD), "threshold");
        if (!(threshold > 0.0 && threshold < 1.0)) {
            throw new ModelException("threshold " + threshold + " outside (0, 1)");
        }
        int minVotes = (int) number(meta.getOrDefault(Contract.META_MIN_VOTES, "1"), "min_votes");
        if (minVotes < 1) {
            throw new ModelException("min_votes " + minVotes + " < 1");
        }
        String tta = meta.getOrDefault(Contract.META_TTA, "0");
        if (!tta.equals("0") && !tta.equals("1")) {
            throw new ModelException("tta '" + tta + "' is neither '0' nor '1'");
        }
        Double prefilter = null;
        if (meta.containsKey(Contract.META_PREFILTER)) {
            prefilter = number(meta.get(Contract.META_PREFILTER), "prefilter");
            if (!tta.equals("1")) {
                throw new ModelException("a prefilter needs tta");
            }
            if (!(prefilter > 0.0 && prefilter <= threshold)) {
                throw new ModelException("prefilter " + prefilter + " outside (0, threshold " + threshold + "]");
            }
        }
        return new ModelInfo(kind, meta.get(Contract.META_VERSION), labels, threshold, featureSpec,
                meta.getOrDefault(Contract.META_COMMIT, ""), minVotes, tta.equals("1"), prefilter, sha256);
    }

    private static List<String> labels(String json) throws ModelException {
        try {
            return JsonParser.parseString(json).getAsJsonArray().asList().stream()
                    .map(element -> element.getAsString()).toList();
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException e) {
            throw new ModelException("labels '" + json + "' are not a JSON string array", e);
        }
    }

    private static double number(String value, String name) throws ModelException {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new ModelException(name + " '" + value + "' is not a number", e);
        }
    }
}
