import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** CSV validation, saved decision-tree inference, and command-line evaluation. */
public final class Backend {
    static final String HEADER = "id,protocol,duration_ms,bytes,packets_per_second,syn_ratio,failed_logins,destination_ports,label";
    static final String OUTPUT_HEADER = "id,protocol,ground_truth,prediction,score";
    private final List<Node> nodes;

    record Flow(String id, String protocol, double duration, double bytes, double rate,
                double syn, double failures, double ports, String label) {
        double[] features() {
            return new double[] { Math.log1p(duration), Math.log1p(bytes), Math.log1p(rate), syn,
                Math.log1p(failures), Math.log1p(ports), protocol.equals("TCP") ? 1 : 0,
                protocol.equals("UDP") ? 1 : 0, protocol.equals("ICMP") ? 1 : 0 };
        }
        boolean labeled() { return !label.equals("UNKNOWN"); }
        boolean attack() { return labeled() && !label.equals("NORMAL"); }
    }

    record Node(int feature, double threshold, int left, int right, double score) {}
    record Result(Flow flow, double score) {
        boolean alert(double threshold) { return score >= threshold; }
    }

    public Backend(Path modelFile) throws IOException {
        List<String> lines = Files.readAllLines(modelFile, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).equals("NIDS_TREE_V1"))
            throw new IllegalArgumentException("Unsupported model format: " + modelFile);
        List<Node> parsed = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split(",", -1);
            if (parts.length != 5) throw new IllegalArgumentException("Invalid model line " + (i + 1));
            try {
                Node node = new Node(Integer.parseInt(parts[0]), Double.parseDouble(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), Double.parseDouble(parts[4]));
                if (node.feature < -1 || node.feature > 8 || !Double.isFinite(node.threshold)
                    || !Double.isFinite(node.score) || node.score < 0 || node.score > 1)
                    throw new IllegalArgumentException("Invalid model line " + (i + 1));
                parsed.add(node);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid model line " + (i + 1), ex);
            }
        }
        if (parsed.isEmpty()) throw new IllegalArgumentException("Model has no nodes");
        for (int i = 0; i < parsed.size(); i++) {
            Node n = parsed.get(i);
            if (n.feature >= 0 && (n.left <= i || n.right <= i || n.left >= parsed.size() || n.right >= parsed.size()))
                throw new IllegalArgumentException("Invalid model child at node " + i);
        }
        nodes = List.copyOf(parsed);
    }

    Result predict(Flow flow) {
        double[] features = flow.features();
        int index = 0;
        for (int steps = 0; steps < nodes.size(); steps++) {
            Node node = nodes.get(index);
            if (node.feature < 0) return new Result(flow, node.score);
            index = features[node.feature] <= node.threshold ? node.left : node.right;
        }
        throw new IllegalStateException("Model contains a cycle");
    }

    static List<Flow> readCsv(Path file) throws IOException {
        if (Files.size(file) > 10_000_000) throw new IllegalArgumentException("CSV exceeds 10 MB");
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).replace("\uFEFF", "").equals(HEADER))
            throw new IllegalArgumentException("Expected CSV header: " + HEADER);
        List<Flow> flows = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            try {
                String[] p = lines.get(i).split(",", -1);
                if (p.length != 9) throw new IllegalArgumentException("Expected 9 columns; quoted values are unsupported");
                for (int j = 0; j < p.length; j++) p[j] = p[j].trim();
                if (!p[0].matches("[A-Za-z0-9_-]{1,64}") || !ids.add(p[0]))
                    throw new IllegalArgumentException("Invalid or duplicate flow ID");
                if (!Set.of("TCP", "UDP", "ICMP").contains(p[1])) throw new IllegalArgumentException("Invalid protocol");
                String label = p[8].isEmpty() ? "UNKNOWN" : p[8];
                if (!Set.of("NORMAL", "DOS", "PORT_SCAN", "BRUTE_FORCE", "EXFILTRATION", "UNKNOWN").contains(label))
                    throw new IllegalArgumentException("Invalid label");
                double[] v = new double[6];
                for (int j = 0; j < 6; j++) {
                    v[j] = Double.parseDouble(p[j + 2]);
                    if (!Double.isFinite(v[j]) || v[j] < 0 || v[j] > 1e12)
                        throw new IllegalArgumentException("Features must be finite and nonnegative");
                }
                if (v[3] > 1) throw new IllegalArgumentException("SYN ratio must be at most 1");
                flows.add(new Flow(p[0], p[1], v[0], v[1], v[2], v[3], v[4], v[5], label));
                if (flows.size() > 10_000) throw new IllegalArgumentException("CSV exceeds 10,000 flows");
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("CSV line " + (i + 1) + ": " + ex.getMessage(), ex);
            }
        }
        if (flows.isEmpty()) throw new IllegalArgumentException("CSV contains no flows");
        return List.copyOf(flows);
    }

    static void export(Path file, List<Result> results, double threshold) throws IOException {
        StringBuilder csv = new StringBuilder(OUTPUT_HEADER).append('\n');
        for (Result result : results) {
            Flow flow = result.flow();
            csv.append(flow.id()).append(',').append(flow.protocol()).append(',').append(flow.label()).append(',')
                .append(result.alert(threshold) ? "ALERT" : "NORMAL").append(',')
                .append(String.format(Locale.ROOT, "%.6f", result.score())).append('\n');
        }
        Files.writeString(file, csv, StandardCharsets.UTF_8);
    }

    static String metrics(List<Result> results, double threshold) {
        int tp = 0, fp = 0, tn = 0, fn = 0;
        for (Result result : results) {
            if (!result.flow().labeled()) continue;
            if (result.alert(threshold)) { if (result.flow().attack()) tp++; else fp++; }
            else { if (result.flow().attack()) fn++; else tn++; }
        }
        int known = tp + fp + tn + fn;
        if (known == 0) return "No labeled flows; metrics unavailable.";
        double accuracy = (double) (tp + tn) / known;
        double precision = tp + fp == 0 ? 0 : (double) tp / (tp + fp);
        double recall = tp + fn == 0 ? 0 : (double) tp / (tp + fn);
        double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
        return String.format(Locale.ROOT,
            "Labeled: %d  Accuracy: %.2f%%  Precision: %.2f%%  Recall: %.2f%%  F1: %.2f%%  TP: %d  FP: %d  TN: %d  FN: %d",
            known, accuracy * 100, precision * 100, recall * 100, f1 * 100, tp, fp, tn, fn);
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 4 || args[0].equals("--help")) {
            System.out.println("Usage: java Backend <input.csv> [model.txt] [output.csv] [threshold]\nDefault model: model.txt; default threshold: 0.5");
            return;
        }
        try {
            double threshold = args.length >= 4 ? Double.parseDouble(args[3]) : .5;
            if (!Double.isFinite(threshold) || threshold <= 0 || threshold >= 1)
                throw new IllegalArgumentException("Threshold must be between 0 and 1");
            Backend backend = new Backend(Path.of(args.length >= 2 ? args[1] : "model.txt"));
            List<Result> results = readCsv(Path.of(args[0])).stream().map(backend::predict).toList();
            for (Result result : results)
                System.out.printf(Locale.ROOT, "%s: %s (score %.3f)%n", result.flow().id(),
                    result.alert(threshold) ? "ALERT" : "NORMAL", result.score());
            System.out.println(metrics(results, threshold));
            if (args.length >= 3) export(Path.of(args[2]), results, threshold);
        } catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
            System.exit(1);
        }
    }
}
