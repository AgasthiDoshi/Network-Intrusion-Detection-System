package nids;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class Export {
    private Export() {}
    public static void results(Path path, List<Detector.Result> results, double threshold) throws IOException {
        StringBuilder csv = new StringBuilder("id,protocol,ground_truth,prediction,ensemble_score,signature,explanation\n");
        for (var r : results) csv.append(String.join(",", r.flow().id(), r.flow().protocol(), r.flow().label(), r.alert(threshold) ? "ALERT" : "NORMAL",
            String.format(Locale.ROOT, "%.6f", r.score()), quote(r.signature()), quote(r.explanation()))).append('\n');
        if (path.toAbsolutePath().getParent() != null) Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }
    private static String quote(String s) { return "\"" + s.replace("\"", "\"\"") + "\""; }
}
