package nids;

import java.nio.file.*;
import java.util.*;
import javax.swing.SwingUtilities;

/** Dependency-free regression suite. Throws on failure even without Java assertions enabled. */
public final class SelfTest {
    private static int checks;
    private SelfTest() {}
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable action, String message) { checks++; try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError(message); }
    private static Detector.Result result(String id, boolean attack, double score) {
        Flow f = new Flow(id, "TCP", 1, 1, 1, 0, 0, 1, attack ? "DOS" : "NORMAL");
        Map<String, Double> scores = new LinkedHashMap<>(); Detector.MODEL_NAMES.forEach(name -> scores.put(name, score));
        return new Detector.Result(f, score, "test", "test", scores);
    }
    public static void run() throws Exception {
        checks = 0;
        List<Flow> train = Data.generate(1200, 42), test = Data.generate(400, 2025);
        check(train.equals(Data.generate(1200, 42)), "Synthetic generation must be repeatable");
        Set<String> trainKeys = new HashSet<>(); train.forEach(f -> trainKeys.add(Arrays.toString(f.features())));
        check(test.stream().noneMatch(f -> trainKeys.contains(Arrays.toString(f.features()))), "Train/test must be disjoint");
        Detector detector = new Detector(train);
        rejects(() -> detector.requireDisjoint(train), "Overlapping evaluation data rejected");
        List<Detector.Result> predictions = test.stream().map(detector::predict).toList();
        for (var p : predictions) for (double value : p.scores().values()) check(Double.isFinite(value) && value >= 0 && value <= 1, "Scores must be finite in [0,1]");
        Flow original = test.get(0);
        Flow relabeled = new Flow("NEW_ID", original.protocol(), original.durationMs(), original.bytes(), original.packetsPerSecond(), original.synRatio(), original.failedLogins(), original.destinationPorts(), "UNKNOWN");
        check(detector.predict(original).scores().equals(detector.predict(relabeled).scores()), "Inference must not use ground truth or ID");
        var m = Evaluation.evaluate(List.of(result("a", true, .9), result("b", true, .2), result("c", false, .7), result("d", false, .1)), .5).get("Ensemble");
        check(m.tp() == 1 && m.tn() == 1 && m.fp() == 1 && m.fn() == 1, "Confusion matrix hand check");
        check(m.accuracy() == .5 && m.precision() == .5 && m.recall() == .5 && m.f1() == .5, "Metric hand check");
        check(m.auc() == .75, "ROC AUC hand check");
        check(Evaluation.evaluate(List.of(result("a", true, .5), result("b", false, .5)), .5).get("Ensemble").auc() == .5, "Tied AUC scores");
        check(Double.isNaN(Evaluation.evaluate(List.of(result("a", false, .1)), .5).get("Ensemble").auc()), "Single-class AUC undefined");
        check(Double.isNaN(Evaluation.evaluate(List.of(), .5).get("Ensemble").accuracy()), "Empty evaluation is N/A");
        check(Double.isNaN(Evaluation.evaluate(List.of(detector.predict(relabeled)), .5).get("Ensemble").accuracy()), "Unknown labels excluded");
        long low = predictions.stream().filter(r -> r.alert(.3)).count(), high = predictions.stream().filter(r -> r.alert(.8)).count();
        check(low >= high, "Raising threshold cannot increase alerts");
        rejects(() -> Evaluation.validateThreshold(Double.NaN), "NaN threshold rejected");
        rejects(() -> Evaluation.validateThreshold(1), "Out-of-range threshold rejected");
        rejects(() -> new Flow("bad", "TCP", 0, Double.POSITIVE_INFINITY, 0, 0, 0, 1, "NORMAL"), "Non-finite features rejected");
        rejects(() -> new Flow("bad", "TCP", 0, 0, 0, 1.1, 0, 1, "NORMAL"), "Invalid SYN ratio rejected");
        rejects(() -> new Detector(List.of(relabeled)), "Unknown training label rejected");
        rejects(() -> new Detector(train.stream().filter(f -> !f.attack()).toList()), "Single-class training rejected");
        Flow flood = new Flow("flood", "TCP", 10, 10000, 2000, .9, 0, 1, "UNKNOWN");
        check(detector.predict(flood).score() >= .95, "Known flood signature elevates score");
        Path dir = Files.createTempDirectory("nids-tests-");
        try {
            Path csv = dir.resolve("flows.csv"); Data.write(csv, test); check(Data.read(csv).equals(test), "CSV round trip");
            Files.writeString(csv, Flow.HEADER + "\n" + test.get(0).csv() + "\n" + test.get(0).csv());
            try { Data.read(csv); throw new AssertionError("Duplicate IDs must fail"); } catch (IllegalArgumentException expected) { checks++; }
            Files.writeString(csv, "wrong,header\n");
            try { Data.read(csv); throw new AssertionError("Invalid header must fail"); } catch (IllegalArgumentException expected) { checks++; }
            Files.writeString(csv, Flow.HEADER + "\nabc,TCP,NaN,1,1,0,0,1,NORMAL\n");
            try { Data.read(csv); throw new AssertionError("NaN CSV must fail"); } catch (IllegalArgumentException expected) { check(expected.getMessage().contains("line 2"), "Parser errors include row"); }
            Export.results(dir.resolve("results.csv"), predictions, .5);
            check(Files.readAllLines(dir.resolve("results.csv")).size() == 401, "CSV export includes every flow");
            SwingUtilities.invokeAndWait(() -> {
                Dashboard dashboard = new Dashboard(detector, test);
                check(dashboard.table.getRowCount() == 400, "UI loads all flows");
                dashboard.alertsOnly.doClick(); int alerts = dashboard.table.getRowCount();
                check(alerts == predictions.stream().filter(r -> r.alert(.5)).count(), "UI alert filter matches predictions");
                dashboard.sensitivity.setValue(80); check(dashboard.table.getRowCount() <= alerts, "UI threshold changes decisions");
                dashboard.alertsOnly.doClick(); dashboard.table.getRowSorter().toggleSortOrder(4); dashboard.table.setRowSelectionInterval(0, 0);
                check(dashboard.table.getSelectedRow() == 0, "UI supports sorted row inspection");
                dashboard.replayButton.doClick(); check(dashboard.replay.isRunning(), "Replay starts");
                dashboard.replay.getActionListeners()[0].actionPerformed(null); check(dashboard.table.getRowCount() == 3, "Replay processes flows incrementally");
                dashboard.replayButton.doClick(); check(!dashboard.replay.isRunning(), "Replay pauses");
                dashboard.replayButton.doClick(); dashboard.replay.getActionListeners()[0].actionPerformed(null);
                check(dashboard.table.getRowCount() == 6, "Replay resumes without losing progress"); dashboard.stopReplay();
                try { Dashboard.screenshot(dashboard, dir.resolve("ui.png")); } catch (Exception ex) { throw new RuntimeException(ex); }
                check(Files.exists(dir.resolve("ui.png")), "Swing rendering succeeds");
            });
        } finally {
            try (var paths = Files.list(dir)) { for (Path p : paths.toList()) Files.deleteIfExists(p); }
            Files.deleteIfExists(dir);
        }
        System.out.println("PASS: " + checks + " checks (models, label isolation, metrics, CSV, Swing actions and rendering)");
    }
}
