package nids;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import javax.imageio.ImageIO;

/** Native Java Swing UI. The same component tree is used for the app and screenshots. */
public final class Dashboard extends JPanel {
    private static final Color BG = new Color(15, 23, 35), PANEL = new Color(23, 34, 49), TEXT = new Color(229, 237, 245), MUTED = new Color(154, 174, 194), ACCENT = new Color(74, 219, 177);
    private Detector detector;
    private List<Flow> flows;
    private List<Detector.Result> results;
    private double threshold = .5;
    private int visibleCount;
    private final JLabel summary = new JLabel(), status = new JLabel("Ready. Select a flow to inspect its detection evidence.");
    private final JTextArea detail = new JTextArea(), metrics = new JTextArea();
    private final DefaultTableModel tableModel = new DefaultTableModel(new String[]{"FLOW ID", "PROTOCOL", "ACTUAL", "DECISION", "SCORE", "SIGNATURE EVIDENCE"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    final JTable table = new JTable(tableModel);
    final JButton replayButton = new JButton("Replay traffic"), importButton = new JButton("Import CSV"), trainButton = new JButton("Retrain from CSV"), exportButton = new JButton("Export results");
    final JCheckBox alertsOnly = new JCheckBox("Alerts only");
    final JSlider sensitivity = new JSlider(10, 90, 50);
    final javax.swing.Timer replay;
    private final List<Detector.Result> displayed = new ArrayList<>();

    public Dashboard(Detector detector, List<Flow> flows) {
        detector.requireDisjoint(flows);
        this.detector = detector; this.flows = flows;
        results = flows.stream().map(detector::predict).toList(); visibleCount = results.size();
        setLayout(new BorderLayout(0, 22)); setBackground(BG); setBorder(new EmptyBorder(28, 32, 22, 32));
        JPanel heading = panel(new BorderLayout(0, 14), BG);
        JLabel title = new JLabel("Network intrusion detection"); title.setForeground(TEXT); title.setFont(new Font("SansSerif", Font.BOLD, 30));
        JLabel subtitle = new JLabel("JAVA RESEARCH DEMO   /   Hybrid signatures + anomaly detection + learned ensemble");
        subtitle.setForeground(MUTED); subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        JPanel labels = panel(new GridLayout(2, 1, 0, 7), BG); labels.add(title); labels.add(subtitle); heading.add(labels, BorderLayout.NORTH);
        JPanel toolbar = panel(new FlowLayout(FlowLayout.LEFT, 10, 0), BG);
        for (JButton b : List.of(replayButton, importButton, trainButton, exportButton)) { styleButton(b); toolbar.add(b); }
        heading.add(toolbar, BorderLayout.CENTER);
        summary.setForeground(ACCENT); summary.setFont(new Font("SansSerif", Font.BOLD, 17)); summary.setBorder(new EmptyBorder(10, 0, 0, 0)); heading.add(summary, BorderLayout.SOUTH);
        add(heading, BorderLayout.NORTH);

        JPanel body = panel(new BorderLayout(0, 16), BG);
        JPanel controls = panel(new FlowLayout(FlowLayout.LEFT, 14, 0), BG);
        JLabel thresholdLabel = new JLabel("Alert threshold: 0.50"); thresholdLabel.setForeground(TEXT);
        sensitivity.setPreferredSize(new Dimension(180, 28)); sensitivity.setBackground(BG); sensitivity.setToolTipText("Lower thresholds flag more flows. This changes demo evaluation, not a held-out benchmark claim.");
        alertsOnly.setBackground(BG); alertsOnly.setForeground(TEXT);
        controls.add(newLabel("TRAFFIC ANALYSIS", MUTED, 13)); controls.add(alertsOnly); controls.add(thresholdLabel); controls.add(sensitivity);
        body.add(controls, BorderLayout.NORTH);

        table.setBackground(PANEL); table.setForeground(TEXT); table.setGridColor(new Color(42, 56, 73)); table.setRowHeight(32); table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.setSelectionBackground(new Color(38, 70, 83)); table.setSelectionForeground(Color.WHITE); table.setAutoCreateRowSorter(true);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11)); table.getTableHeader().setBackground(new Color(32, 47, 65)); table.getTableHeader().setForeground(TEXT);
        table.getTableHeader().setPreferredSize(new Dimension(100, 34)); table.setFillsViewportHeight(true); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        int[] widths = {125, 90, 115, 100, 70, 255}; for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean selected, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t, v, selected, focus, row, col);
                setBorder(new EmptyBorder(0, 10, 0, 6));
                if (!selected) { setBackground(row % 2 == 0 ? PANEL : new Color(26, 39, 55)); setForeground(col == 3 ? ("ALERT".equals(v) ? new Color(255, 165, 135) : ACCENT) : TEXT); }
                return this;
            }
        });
        JScrollPane trafficScroll = new JScrollPane(table); trafficScroll.setColumnHeaderView(table.getTableHeader()); trafficScroll.setBorder(BorderFactory.createLineBorder(new Color(42, 56, 73))); trafficScroll.getViewport().setBackground(PANEL);
        body.add(trafficScroll, BorderLayout.CENTER);
        detail.setEditable(false); detail.setLineWrap(true); detail.setWrapStyleWord(true); detail.setBackground(PANEL); detail.setForeground(TEXT); detail.setFont(new Font("Monospaced", Font.PLAIN, 12)); detail.setBorder(new EmptyBorder(12, 14, 12, 14));
        JScrollPane detailScroll = new JScrollPane(detail); detailScroll.setBorder(null); detailScroll.setPreferredSize(new Dimension(100, 145)); body.add(detailScroll, BorderLayout.SOUTH);

        JPanel side = panel(new BorderLayout(0, 12), PANEL); side.setBorder(new EmptyBorder(18, 18, 18, 18)); side.setPreferredSize(new Dimension(285, 500));
        side.add(newLabel("MODEL EVALUATION", ACCENT, 14), BorderLayout.NORTH);
        metrics.setEditable(false); metrics.setBackground(PANEL); metrics.setForeground(TEXT); metrics.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane metricsScroll = new JScrollPane(metrics); metricsScroll.setBorder(null); side.add(metricsScroll, BorderLayout.CENTER);
        JPanel center = panel(new BorderLayout(22, 0), BG); center.add(body, BorderLayout.CENTER); center.add(side, BorderLayout.EAST); add(center, BorderLayout.CENTER);
        status.setForeground(MUTED); status.setFont(new Font("SansSerif", Font.PLAIN, 12)); add(status, BorderLayout.SOUTH);

        replay = new javax.swing.Timer(120, e -> { visibleCount = Math.min(visibleCount + 3, results.size()); refresh(); if (visibleCount == results.size()) stopReplay(); });
        replayButton.addActionListener(e -> { if (replay.isRunning()) stopReplay(); else { if (visibleCount == results.size()) visibleCount = 0; replayButton.setText("Pause replay"); replay.start(); refresh(); } });
        alertsOnly.addActionListener(e -> refresh());
        sensitivity.addChangeListener(e -> { threshold = sensitivity.getValue() / 100.0; thresholdLabel.setText(String.format(Locale.ROOT, "Alert threshold: %.2f", threshold)); refresh(); });
        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showSelected(); });
        importButton.addActionListener(e -> load(false)); trainButton.addActionListener(e -> load(true));
        exportButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(); chooser.setSelectedFile(new java.io.File("predictions.csv"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                Path path = chooser.getSelectedFile().toPath();
                if (Files.exists(path) && JOptionPane.showConfirmDialog(this, "Replace existing file?", "Export", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
                try { Export.results(path, results.subList(0, visibleCount), threshold); status.setText("Exported " + visibleCount + " analyzed flows to " + path); }
                catch (Exception ex) { error(ex); }
            }
        });
        refresh(); if (table.getRowCount() > 0) table.setRowSelectionInterval(0, 0);
    }
    private void load(boolean train) {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path path = chooser.getSelectedFile().toPath(); stopReplay(); setBusy(true);
        status.setText(train ? "Training Java models..." : "Reading and scoring CSV...");
        new SwingWorker<Object, Void>() {
            @Override protected Object doInBackground() throws Exception {
                List<Flow> loaded = Data.read(path);
                if (train) { Detector next = new Detector(loaded); next.requireDisjoint(flows); return next; }
                detector.requireDisjoint(loaded); return loaded;
            }
            @SuppressWarnings("unchecked")
            @Override protected void done() {
                try {
                    Object value = get(); if (train) detector = (Detector) value; else flows = (List<Flow>) value;
                    results = flows.stream().map(detector::predict).toList(); visibleCount = results.size(); refresh();
                    status.setText((train ? "Retrained from " : "Imported ") + path.getFileName() + ". Evaluation is on the currently loaded traffic; use a separate test file.");
                } catch (Exception ex) { error(ex.getCause() instanceof Exception cause ? cause : ex); }
                finally { setBusy(false); }
            }
        }.execute();
    }
    private void setBusy(boolean busy) { for (JButton b : List.of(importButton, trainButton, replayButton, exportButton)) b.setEnabled(!busy); }
    private void error(Exception ex) { status.setText("Operation failed; previous data retained."); JOptionPane.showMessageDialog(this, ex.getMessage(), "Unable to complete operation", JOptionPane.ERROR_MESSAGE); }
    void stopReplay() { replay.stop(); replayButton.setText(visibleCount < results.size() ? "Resume replay" : "Replay traffic"); }
    private void refresh() {
        String selectedId = null;
        if (table.getSelectedRow() >= 0) selectedId = displayed.get(table.convertRowIndexToModel(table.getSelectedRow())).flow().id();
        tableModel.setRowCount(0); displayed.clear();
        List<Detector.Result> current = results.subList(0, visibleCount);
        for (var r : current) if (!alertsOnly.isSelected() || r.alert(threshold)) {
            displayed.add(r); tableModel.addRow(new Object[]{r.flow().id(), r.flow().protocol(), r.flow().label(), r.alert(threshold) ? "ALERT" : "NORMAL", String.format(Locale.ROOT, "%.3f", r.score()), r.signature()});
        }
        long alerts = current.stream().filter(r -> r.alert(threshold)).count();
        summary.setText(visibleCount + " flows analyzed     /     " + alerts + " alerts     /     " + detector.trainingCount + " training samples");
        var all = Evaluation.evaluate(current, threshold); var ensemble = all.get("Ensemble");
        StringBuilder s = new StringBuilder("Current labeled traffic only\n\nENSEMBLE\n");
        s.append("Accuracy   ").append(Evaluation.percent(ensemble.accuracy())).append("\nPrecision  ").append(Evaluation.percent(ensemble.precision())).append("\nRecall     ").append(Evaluation.percent(ensemble.recall())).append("\nF1         ").append(Evaluation.percent(ensemble.f1())).append("\nROC AUC    ").append(Evaluation.decimal(ensemble.auc())).append("\n\nCONFUSION MATRIX\n");
        s.append(String.format("TP %-5d FP %d\nFN %-5d TN %d\n\nMODEL ACCURACY\n", ensemble.tp(), ensemble.fp(), ensemble.fn(), ensemble.tn()));
        all.forEach((name, m) -> s.append(String.format("%-14s %s\n", name, Evaluation.percent(m.accuracy()))));
        s.append("\nFOREST FEATURE IMPORTANCE\n"); detector.importance().entrySet().stream().limit(4).forEach(e -> s.append(String.format("%-17s %s\n", e.getKey(), Evaluation.percent(e.getValue()))));
        s.append("\nScores are not calibrated\nprobabilities. Replay uses\nCSV flows, not live packets."); metrics.setText(s.toString()); metrics.setCaretPosition(0);
        detail.setText("Select a flow for signature evidence, anomaly explanation and individual model scores.");
        if (selectedId != null) for (int i = 0; i < displayed.size(); i++) if (displayed.get(i).flow().id().equals(selectedId)) { int view = table.convertRowIndexToView(i); table.setRowSelectionInterval(view, view); break; }
    }
    private void showSelected() {
        int row = table.getSelectedRow(); if (row < 0) return;
        var r = displayed.get(table.convertRowIndexToModel(row));
        StringBuilder s = new StringBuilder("DETECTION EVIDENCE  /  " + r.flow().id() + "\n" + r.explanation() + "\n");
        s.append(String.format(Locale.ROOT, "Bytes: %.0f | Packets/s: %.1f | SYN ratio: %.3f | Failed logins: %.0f | Ports: %.0f\n", r.flow().bytes(), r.flow().packetsPerSecond(), r.flow().synRatio(), r.flow().failedLogins(), r.flow().destinationPorts()));
        r.scores().forEach((name, value) -> s.append(name).append(": ").append(String.format(Locale.ROOT, "%.3f", value)).append("   "));
        detail.setText(s.toString()); detail.setCaretPosition(0);
    }
    private static JPanel panel(LayoutManager layout, Color color) { JPanel p = new JPanel(layout); p.setBackground(color); return p; }
    private static JLabel newLabel(String text, Color color, int size) { JLabel l = new JLabel(text); l.setForeground(color); l.setFont(new Font("SansSerif", Font.BOLD, size)); return l; }
    private static void styleButton(JButton b) { b.setFont(new Font("SansSerif", Font.BOLD, 12)); b.setBackground(new Color(35, 58, 75)); b.setForeground(TEXT); b.setFocusPainted(true); b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(63, 92, 108)), new EmptyBorder(9, 14, 9, 14))); }
    public static void screenshot(Dashboard dashboard, Path path) throws Exception {
        dashboard.setSize(1440, 940); layoutTree(dashboard);
        BufferedImage image = new BufferedImage(1440, 940, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON); graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); dashboard.printAll(graphics); graphics.dispose();
        Files.createDirectories(path.toAbsolutePath().getParent()); ImageIO.write(image, "png", path.toFile());
    }
    private static void layoutTree(Container c) { c.doLayout(); for (Component child : c.getComponents()) if (child instanceof Container nested) layoutTree(nested); }
}
