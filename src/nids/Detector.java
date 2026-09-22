package nids;

import java.util.*;
import java.util.stream.IntStream;

/** Immutable trained pipeline: inference does not access a flow's label. */
public final class Detector {
    public static final List<String> MODEL_NAMES = List.of("Decision tree", "Random forest", "Linear SVM", "Anomaly", "Signature", "Hybrid", "Ensemble");
    public record Result(Flow flow, double score, String signature, String explanation, Map<String, Double> scores) {
        public boolean alert(double threshold) { return score >= threshold; }
    }
    private final Models.Scaler scaler;
    private final Models.Tree tree;
    private final Models.Forest forest;
    private final Models.Svm svm;
    private final Models.Anomaly anomaly;
    private final Set<String> trainingFeatures;
    public final int trainingCount;
    public final int duplicatesRemoved;

    public Detector(List<Flow> input) {
        if (input.stream().anyMatch(f -> f.label().equals("UNKNOWN"))) throw new IllegalArgumentException("Training requires known labels");
        // Deduplicate by features, not ID. Reject conflicting ground truth.
        Map<String, Flow> unique = new LinkedHashMap<>();
        for (Flow f : input) {
            String key = Arrays.toString(f.features());
            Flow previous = unique.putIfAbsent(key, f);
            if (previous != null && previous.attack() != f.attack()) throw new IllegalArgumentException("Conflicting labels for identical training features");
        }
        List<Flow> rows = List.copyOf(unique.values());
        trainingFeatures = Set.copyOf(unique.keySet());
        long normals = rows.stream().filter(f -> !f.attack()).count();
        if (normals < 10 || rows.size() - normals < 10) throw new IllegalArgumentException("Training requires at least 10 distinct NORMAL and 10 attack flows");
        trainingCount = rows.size(); duplicatesRemoved = input.size() - rows.size();
        double[][] raw = rows.stream().map(Flow::features).toArray(double[][]::new);
        scaler = new Models.Scaler(raw);
        double[][] x = Arrays.stream(raw).map(scaler::apply).toArray(double[][]::new);
        int[] y = rows.stream().mapToInt(f -> f.attack() ? 1 : 0).toArray();
        tree = new Models.Tree(x, y, IntStream.range(0, x.length).toArray(), Flow.FEATURES.length, 42);
        forest = new Models.Forest(x, y);
        svm = new Models.Svm(x, y);
        anomaly = new Models.Anomaly(rows.stream().filter(f -> !f.attack()).map(Flow::features).toArray(double[][]::new));
    }
    public static String signature(Flow f) {
        if (f.packetsPerSecond() >= 1000 && f.synRatio() >= .75) return "SYN flood pattern";
        if (f.destinationPorts() >= 35 && f.synRatio() >= .4) return "Port scan pattern";
        if (f.failedLogins() >= 10) return "Repeated login failures";
        if (f.bytes() >= 2_000_000 && f.durationMs() >= 1500) return "Large transfer pattern";
        return "No rule matched";
    }
    public void requireDisjoint(List<Flow> flows) {
        if (flows.stream().anyMatch(f -> trainingFeatures.contains(Arrays.toString(f.features()))))
            throw new IllegalArgumentException("Training and evaluation overlap by feature values. Choose a separate test CSV.");
    }
    public Result predict(Flow f) {
        double[] raw = f.features(), x = scaler.apply(raw);
        String rule = signature(f);
        double sig = rule.equals("No rule matched") ? 0 : 1;
        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put("Decision tree", tree.score(x)); scores.put("Random forest", forest.score(x));
        scores.put("Linear SVM", svm.score(x)); scores.put("Anomaly", anomaly.score(raw)); scores.put("Signature", sig);
        double learned = .6 * scores.get("Random forest") + .2 * scores.get("Decision tree") + .2 * scores.get("Linear SVM");
        scores.put("Hybrid", Math.max(sig * .95, scores.get("Anomaly")));
        double ensemble = Math.max(sig * .95, .85 * learned + .15 * scores.get("Anomaly"));
        scores.put("Ensemble", ensemble);
        return new Result(f, ensemble, rule, rule + "; " + anomaly.explanation(raw), Collections.unmodifiableMap(scores));
    }
    public Map<String, Double> importance() {
        Map<String, Double> values = new LinkedHashMap<>();
        IntStream.range(0, forest.importance.length).boxed().sorted((a, b) -> Double.compare(forest.importance[b], forest.importance[a]))
            .forEach(i -> values.put(Flow.FEATURES[i], forest.importance[i]));
        return Collections.unmodifiableMap(values);
    }
}
