package nids;

import java.util.*;

/** Small transparent implementations for teaching; not optimized ML libraries. */
public final class Models {
    private Models() {}
    public interface Model { double score(double[] x); }

    public static final class Scaler {
        final double[] mean, sd;
        public Scaler(double[][] x) {
            mean = new double[x[0].length]; sd = new double[mean.length];
            for (double[] row : x) for (int j = 0; j < mean.length; j++) mean[j] += row[j] / x.length;
            for (double[] row : x) for (int j = 0; j < mean.length; j++) sd[j] += Math.pow(row[j] - mean[j], 2) / x.length;
            for (int j = 0; j < mean.length; j++) sd[j] = Math.max(1e-6, Math.sqrt(sd[j]));
        }
        public double[] apply(double[] x) {
            double[] z = new double[x.length];
            for (int j = 0; j < x.length; j++) z[j] = (x[j] - mean[j]) / sd[j];
            return z;
        }
    }

    public static final class Tree implements Model {
        private record Node(int feature, double threshold, double probability, Node left, Node right) {}
        private final Node root;
        public final double[] importance;
        private final Random random;
        private final int candidateFeatures;
        public Tree(double[][] x, int[] y, int[] rows, int candidates, long seed) {
            importance = new double[x[0].length]; random = new Random(seed); candidateFeatures = candidates;
            root = grow(x, y, rows, 0);
        }
        private Node grow(double[][] x, int[] y, int[] rows, int depth) {
            double sum = 0; for (int i : rows) sum += y[i];
            double p = sum / rows.length;
            if (depth >= 7 || rows.length < 12 || p == 0 || p == 1) return new Node(-1, 0, p, null, null);
            List<Integer> features = new ArrayList<>();
            for (int j = 0; j < x[0].length; j++) features.add(j);
            Collections.shuffle(features, random);
            double best = 0, threshold = 0; int feature = -1;
            for (int f : features.subList(0, Math.min(candidateFeatures, features.size()))) {
                double[] values = new double[rows.length];
                for (int i = 0; i < rows.length; i++) values[i] = x[rows[i]][f];
                Arrays.sort(values);
                for (int q = 1; q < 20; q++) {
                    double t = values[Math.min(values.length - 1, values.length * q / 20)];
                    int n = 0, positives = 0;
                    for (int i : rows) if (x[i][f] <= t) { n++; positives += y[i]; }
                    if (n < 4 || rows.length - n < 4) continue;
                    double gain = gini(p) - (n * gini((double) positives / n) + (rows.length - n) * gini((sum - positives) / (rows.length - n))) / rows.length;
                    if (gain > best) { best = gain; feature = f; threshold = t; }
                }
            }
            if (feature == -1) return new Node(-1, 0, p, null, null);
            final int f = feature; final double t = threshold;
            int[] left = Arrays.stream(rows).filter(i -> x[i][f] <= t).toArray();
            int[] right = Arrays.stream(rows).filter(i -> x[i][f] > t).toArray();
            importance[f] += best * rows.length;
            return new Node(f, t, p, grow(x, y, left, depth + 1), grow(x, y, right, depth + 1));
        }
        private static double gini(double p) { return 2 * p * (1 - p); }
        public double score(double[] x) {
            Node n = root;
            while (n.feature >= 0) n = x[n.feature] <= n.threshold ? n.left : n.right;
            return n.probability;
        }
    }

    public static final class Forest implements Model {
        private final List<Tree> trees = new ArrayList<>();
        public final double[] importance;
        public Forest(double[][] x, int[] y) {
            importance = new double[x[0].length]; Random r = new Random(42);
            for (int t = 0; t < 31; t++) {
                int[] rows = new int[x.length];
                for (int i = 0; i < rows.length; i++) rows[i] = r.nextInt(x.length);
                Tree tree = new Tree(x, y, rows, 3, r.nextLong()); trees.add(tree);
                for (int j = 0; j < importance.length; j++) importance[j] += tree.importance[j];
            }
            double sum = Arrays.stream(importance).sum();
            if (sum > 0) for (int j = 0; j < importance.length; j++) importance[j] /= sum;
        }
        public double score(double[] x) { return trees.stream().mapToDouble(t -> t.score(x)).average().orElseThrow(); }
    }

    /** Linear soft-margin SVM, deterministic SGD on hinge loss with L2 penalty. */
    public static final class Svm implements Model {
        private final double[] weights;
        private double bias;
        public Svm(double[][] x, int[] y) {
            weights = new double[x[0].length];
            List<Integer> order = new ArrayList<>(); for (int i = 0; i < x.length; i++) order.add(i);
            Random random = new Random(73);
            for (int epoch = 0; epoch < 160; epoch++) {
                Collections.shuffle(order, random); double rate = .02 / (1 + epoch * .03);
                for (int i : order) {
                    double label = y[i] == 1 ? 1 : -1, margin = label * dot(x[i]);
                    for (int j = 0; j < weights.length; j++) weights[j] = weights[j] * (1 - rate * .005) + (margin < 1 ? rate * label * x[i][j] : 0);
                    if (margin < 1) bias += rate * label;
                }
            }
        }
        private double dot(double[] x) { double s = bias; for (int j = 0; j < x.length; j++) s += weights[j] * x[j]; return s; }
        // Monotonic display score, not a calibrated probability.
        public double score(double[] x) { return 1 / (1 + Math.exp(-Math.max(-30, Math.min(30, dot(x))))); }
    }

    /** Maximum standardized deviation from training NORMAL flows (continuous features only). */
    public static final class Anomaly implements Model {
        final Scaler normal;
        public Anomaly(double[][] normalRows) { normal = new Scaler(normalRows); }
        public double deviation(double[] x) {
            double[] z = normal.apply(x); double max = 0;
            for (int j = 0; j < 6; j++) max = Math.max(max, Math.abs(z[j]));
            return max;
        }
        public double score(double[] x) { return Math.min(1, deviation(x) / 6); }
        public String explanation(double[] x) {
            double[] z = normal.apply(x); int best = 0;
            for (int j = 1; j < 6; j++) if (Math.abs(z[j]) > Math.abs(z[best])) best = j;
            return String.format(Locale.ROOT, "%s is %.2f standard deviations from the normal baseline", Flow.FEATURES[best], Math.abs(z[best]));
        }
    }
}
