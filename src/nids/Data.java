package nids;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class Data {
    private Data() {}
    /** Independent seeded synthetic samples; not any public benchmark dataset. */
    public static List<Flow> generate(int count, long seed) {
        Random r = new Random(seed);
        List<Flow> rows = new ArrayList<>();
        String[] labels = {"NORMAL", "NORMAL", "NORMAL", "NORMAL", "DOS", "PORT_SCAN", "BRUTE_FORCE", "EXFILTRATION"};
        for (int i = 0; i < count; i++) {
            String label = labels[r.nextInt(labels.length)];
            String protocol = r.nextDouble() < .78 ? "TCP" : r.nextBoolean() ? "UDP" : "ICMP";
            double duration = log(r, 500, 1.2), bytes = log(r, 10000, 1.5), rate = log(r, 45, 1.0);
            double syn = r.nextDouble() * .35, failures = r.nextDouble() < .1 ? r.nextInt(5) : 0, ports = 1 + r.nextInt(6);
            switch (label) {
                case "DOS" -> { rate = log(r, 1200, .75); syn = .45 + r.nextDouble() * .55; duration = log(r, 80, .8); }
                case "PORT_SCAN" -> { ports = log(r, 45, .65); bytes = log(r, 1600, 1); syn = .3 + r.nextDouble() * .6; }
                case "BRUTE_FORCE" -> { failures = log(r, 12, .7); protocol = "TCP"; }
                case "EXFILTRATION" -> { bytes = log(r, 1800000, 1.1); duration = log(r, 2500, 1); }
                default -> { // Benign bursts produce realistic ambiguity and false alarms.
                    if (r.nextDouble() < .09) rate *= 20;
                    if (r.nextDouble() < .06) bytes *= 100;
                }
            }
            rows.add(new Flow("F" + seed + "-" + (i + 1), protocol, round(duration), round(bytes), round(rate), round(syn), round(failures), round(ports), label));
        }
        return List.copyOf(rows);
    }
    private static double log(Random r, double median, double sigma) { return median * Math.exp(r.nextGaussian() * sigma); }
    private static double round(double x) { return Math.rint(x * 1000) / 1000; }
    public static List<Flow> read(Path path) throws IOException {
        if (Files.size(path) > 10_000_000) throw new IllegalArgumentException("CSV exceeds the 10 MB demo limit");
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).replace("\uFEFF", "").equals(Flow.HEADER)) throw new IllegalArgumentException("Expected header: " + Flow.HEADER);
        List<Flow> rows = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            try {
                String[] s = lines.get(i).split(",", -1);
                if (s.length != 9) throw new IllegalArgumentException("Expected 9 columns; quoted fields are not supported");
                for (int j = 0; j < s.length; j++) s[j] = s[j].trim();
                Flow f = new Flow(s[0], s[1], Double.parseDouble(s[2]), Double.parseDouble(s[3]), Double.parseDouble(s[4]),
                    Double.parseDouble(s[5]), Double.parseDouble(s[6]), Double.parseDouble(s[7]), s[8].isEmpty() ? "UNKNOWN" : s[8]);
                if (!ids.add(f.id())) throw new IllegalArgumentException("Duplicate flow ID: " + f.id());
                rows.add(f);
                if (rows.size() > 10000) throw new IllegalArgumentException("At most 10,000 rows are supported");
            } catch (IllegalArgumentException e) { throw new IllegalArgumentException("CSV line " + (i + 1) + ": " + e.getMessage(), e); }
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("CSV contains no flows");
        return List.copyOf(rows);
    }
    public static void write(Path path, List<Flow> rows) throws IOException {
        if (path.toAbsolutePath().getParent() != null) Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, Flow.HEADER + "\n" + String.join("\n", rows.stream().map(Flow::csv).toList()) + "\n", StandardCharsets.UTF_8);
    }
}
