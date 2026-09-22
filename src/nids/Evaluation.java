package nids;

import java.util.*;

public final class Evaluation {
    private Evaluation() {}
    public record Metrics(int tp, int tn, int fp, int fn, double auc) {
        public double accuracy() { return divide(tp + tn, tp + tn + fp + fn); }
        public double precision() { return divide(tp, tp + fp); }
        public double recall() { return divide(tp, tp + fn); }
        public double f1() { return divide(2 * tp, 2 * tp + fp + fn); }
        public double falsePositiveRate() { return divide(fp, fp + tn); }
        private static double divide(double a, double b) { return b == 0 ? Double.NaN : a / b; }
    }
    public static Map<String, Metrics> evaluate(List<Detector.Result> results, double threshold) {
        validateThreshold(threshold);
        List<Detector.Result> labeled = results.stream().filter(r -> !r.flow().label().equals("UNKNOWN")).toList();
        Map<String, Metrics> metrics = new LinkedHashMap<>();
        for (String name : Detector.MODEL_NAMES) {
            int tp = 0, tn = 0, fp = 0, fn = 0;
            for (var r : labeled) {
                boolean predicted = r.scores().get(name) >= threshold;
                if (r.flow().attack()) { if (predicted) tp++; else fn++; }
                else { if (predicted) fp++; else tn++; }
            }
            // Rank-based ROC AUC with average ranks for tied scores: O(n log n).
            List<Detector.Result> sorted = new ArrayList<>(labeled);
            sorted.sort(Comparator.comparingDouble(r -> r.scores().get(name)));
            double positiveRanks = 0;
            for (int start = 0; start < sorted.size();) {
                int end = start + 1;
                while (end < sorted.size() && Double.compare(sorted.get(start).scores().get(name), sorted.get(end).scores().get(name)) == 0) end++;
                double rank = (start + 1 + end) / 2.0;
                for (int i = start; i < end; i++) if (sorted.get(i).flow().attack()) positiveRanks += rank;
                start = end;
            }
            double positives = tp + fn, negatives = tn + fp;
            double auc = positives == 0 || negatives == 0 ? Double.NaN : (positiveRanks - positives * (positives + 1) / 2) / (positives * negatives);
            metrics.put(name, new Metrics(tp, tn, fp, fn, auc));
        }
        return metrics;
    }
    public static void validateThreshold(double t) {
        if (!Double.isFinite(t) || t <= 0 || t >= 1) throw new IllegalArgumentException("Threshold must be greater than 0 and less than 1");
    }
    public static String report(List<Detector.Result> results, double threshold) {
        StringBuilder s = new StringBuilder("AI-POWERED NETWORK INTRUSION DETECTION | JAVA DEMO\n");
        long known = results.stream().filter(r -> !r.flow().label().equals("UNKNOWN")).count();
        s.append(String.format(Locale.ROOT, "Flows: %d | Labeled: %d | Threshold: %.2f\nScores are uncalibrated; metrics apply only to these labeled flows.\n\n", results.size(), known, threshold));
        s.append(String.format("%-16s %9s %9s %9s %9s %9s %9s%n", "Model", "Accuracy", "Precision", "Recall", "F1", "ROC AUC", "FPR"));
        for (var e : evaluate(results, threshold).entrySet()) {
            Metrics m = e.getValue();
            s.append(String.format(Locale.ROOT, "%-16s %9s %9s %9s %9s %9s %9s%n", e.getKey(), percent(m.accuracy()), percent(m.precision()), percent(m.recall()), percent(m.f1()), decimal(m.auc()), percent(m.falsePositiveRate())));
        }
        Metrics m = evaluate(results, threshold).get("Ensemble");
        s.append(String.format("%nEnsemble confusion matrix (attack = positive): TP=%d TN=%d FP=%d FN=%d%n", m.tp(), m.tn(), m.fp(), m.fn()));
        return s.toString();
    }
    public static String percent(double x) { return Double.isNaN(x) ? "N/A" : String.format(Locale.ROOT, "%.2f%%", 100 * x); }
    public static String decimal(double x) { return Double.isNaN(x) ? "N/A" : String.format(Locale.ROOT, "%.3f", x); }
}
