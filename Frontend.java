import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/** Minimal desktop interface for the saved model. */
public final class Frontend extends JFrame {
    private final Backend backend;
    private List<Backend.Result> results = List.of();
    private final DefaultTableModel rows = new DefaultTableModel(
        new String[] { "ID", "Protocol", "Ground truth", "Decision", "Score" }, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JLabel summary = new JLabel("Open a CSV file to analyze network flows.");
    private final JLabel source = new JLabel("Example synthetic flows - use Open CSV for your own data.");
    private final JSlider threshold = new JSlider(10, 90, 50);
    private final JCheckBox alertsOnly = new JCheckBox("Alerts only");

    public Frontend(Path model) throws Exception {
        super("Network Intrusion Detection");
        backend = new Backend(model);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(960, 620);
        setLocationRelativeTo(null);

        JButton open = new JButton("Open CSV");
        JButton export = new JButton("Export predictions");
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        tools.add(open);
        tools.add(export);
        tools.add(new JLabel("Alert threshold"));
        tools.add(threshold);
        tools.add(alertsOnly);
        JPanel header = new JPanel(new BorderLayout());
        header.add(tools, BorderLayout.NORTH);
        header.add(source, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        JTable table = new JTable(rows);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(26);
        add(new JScrollPane(table), BorderLayout.CENTER);
        summary.setFont(new Font("SansSerif", Font.PLAIN, 13));
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        footer.add(summary);
        add(footer, BorderLayout.SOUTH);

        open.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            try {
                results = Backend.readCsv(chooser.getSelectedFile().toPath()).stream().map(backend::predict).toList();
                source.setText("Loaded: " + chooser.getSelectedFile().getName());
                refresh();
            } catch (Exception ex) { showError(ex); }
        });
        export.addActionListener(event -> {
            if (results.isEmpty()) { JOptionPane.showMessageDialog(this, "Open a CSV first."); return; }
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new java.io.File("predictions.csv"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path file = chooser.getSelectedFile().toPath();
            if (java.nio.file.Files.exists(file) && JOptionPane.showConfirmDialog(this,
                "Replace existing file?", "Export", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            try { Backend.export(file, results, threshold.getValue() / 100.0); }
            catch (Exception ex) { showError(ex); }
        });
        threshold.addChangeListener(event -> refresh());
        alertsOnly.addActionListener(event -> refresh());
        results = exampleFlows().stream().map(backend::predict).toList();
        refresh();
    }

    private static List<Backend.Flow> exampleFlows() {
        return List.of(
            new Backend.Flow("normal-web", "TCP", 600, 12000, 42, .1, 0, 2, "NORMAL"),
            new Backend.Flow("syn-flood", "TCP", 60, 20000, 2500, .92, 0, 2, "DOS"),
            new Backend.Flow("port-scan", "TCP", 120, 900, 220, .72, 0, 75, "PORT_SCAN"),
            new Backend.Flow("login-failures", "TCP", 1500, 8000, 30, .15, 24, 1, "BRUTE_FORCE")
        );
    }

    private void refresh() {
        rows.setRowCount(0);
        double cutoff = threshold.getValue() / 100.0;
        for (Backend.Result result : results) {
            if (alertsOnly.isSelected() && !result.alert(cutoff)) continue;
            rows.addRow(new Object[] { result.flow().id(), result.flow().protocol(), result.flow().label(),
                result.alert(cutoff) ? "ALERT" : "NORMAL", String.format(Locale.ROOT, "%.3f", result.score()) });
        }
        summary.setText("Flows: " + results.size() + "  Showing: " + rows.getRowCount() + "  |  "
            + Backend.metrics(results, cutoff));
    }

    private void showError(Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        if (args.length > 1) { System.err.println("Usage: java Frontend [model.txt]"); System.exit(1); }
        Path model = Path.of(args.length == 1 ? args[0] : "model.txt");
        SwingUtilities.invokeLater(() -> {
            try { new Frontend(model).setVisible(true); }
            catch (Exception ex) {
                JOptionPane.showMessageDialog(null, ex.getMessage(), "Model error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
