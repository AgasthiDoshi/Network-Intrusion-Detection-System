package nids;

import javax.swing.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Main {
    private Main() {}
    public static void main(String[] args) {
        try { run(args); }
        catch (Exception ex) { System.err.println("Error: " + ex.getMessage()); System.exit(1); }
    }
    private static void run(String[] args) throws Exception {
        String command = args.length == 0 ? "gui" : args[0];
        if (command.equals("help") || command.equals("--help")) {
            System.out.println("Usage: java -jar build/nids-demo.jar [gui|evaluate|generate|screenshot|test] [options]\nOptions: --train FILE --input FILE --output DIR --threshold 0.5\nDefault: synthetic train seed 42 (1200 rows), test seed 2025 (400 rows).\nGUI import uses the documented CSV schema. No network capture or traffic generation."); return;
        }
        if (!Set.of("gui", "evaluate", "generate", "screenshot", "test").contains(command)) throw new IllegalArgumentException("Unknown command: " + command + ". Run help.");
        Map<String, String> options = new HashMap<>();
        for (int i = 1; i < args.length; i += 2) {
            if (!Set.of("--train", "--input", "--output", "--threshold").contains(args[i]) || i + 1 >= args.length) throw new IllegalArgumentException("Invalid or missing option: " + args[i]);
            if (options.put(args[i], args[i + 1]) != null) throw new IllegalArgumentException("Duplicate option: " + args[i]);
        }
        double threshold = Double.parseDouble(options.getOrDefault("--threshold", ".5")); Evaluation.validateThreshold(threshold);
        Path output = Path.of(options.getOrDefault("--output", "output"));
        if (command.equals("test")) { SelfTest.run(); return; }
        if (command.equals("generate")) { Data.write(output.resolve("train.csv"), Data.generate(1200, 42)); Data.write(output.resolve("test.csv"), Data.generate(400, 2025)); System.out.println("Generated synthetic train.csv and test.csv in " + output); return; }
        List<Flow> train = options.containsKey("--train") ? Data.read(Path.of(options.get("--train"))) : Data.generate(1200, 42);
        List<Flow> test = options.containsKey("--input") ? Data.read(Path.of(options.get("--input"))) : Data.generate(400, 2025);
        // Disallow identical feature rows across train and evaluation files.
        Set<String> trainingFeatures = new HashSet<>(); for (Flow f : train) trainingFeatures.add(Arrays.toString(f.features()));
        if (test.stream().anyMatch(f -> trainingFeatures.contains(Arrays.toString(f.features())))) throw new IllegalArgumentException("Training and test data overlap by feature values. Use disjoint files.");
        Detector detector = new Detector(train);
        if (command.equals("evaluate")) {
            List<Detector.Result> results = test.stream().map(detector::predict).toList();
            String provenance = "Training: " + options.getOrDefault("--train", "synthetic seed 42") + " (" + detector.trainingCount + " unique rows)\nEvaluation: " + options.getOrDefault("--input", "synthetic seed 2025") + "\n\n";
            String report = provenance + Evaluation.report(results, threshold);
            Files.createDirectories(output); Files.writeString(output.resolve("metrics.txt"), report, StandardCharsets.UTF_8);
            Export.results(output.resolve("predictions.csv"), results, threshold); System.out.print(report); return;
        }
        if (threshold != .5) throw new IllegalArgumentException("--threshold is for evaluate; use the slider in the GUI");
        SwingUtilities.invokeAndWait(() -> {
            Dashboard dashboard = new Dashboard(detector, test);
            if (command.equals("screenshot")) {
                try { Dashboard.screenshot(dashboard, output.resolve("dashboard.png")); System.out.println("Rendered actual Swing components to " + output.resolve("dashboard.png")); }
                catch (Exception e) { throw new RuntimeException(e); }
            } else {
                JFrame frame = new JFrame("AI-powered Network Intrusion Detection | Java Demo"); frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE); frame.setContentPane(dashboard);
                frame.setSize(1440, 940); frame.setMinimumSize(new java.awt.Dimension(1150, 800)); frame.setLocationRelativeTo(null); frame.setVisible(true);
            }
        });
    }
}
