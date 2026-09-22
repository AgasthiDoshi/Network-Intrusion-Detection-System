package nids;

/** One aggregated connection. Labels are evaluation metadata, never model inputs. */
public record Flow(String id, String protocol, double durationMs, double bytes,
                   double packetsPerSecond, double synRatio, double failedLogins,
                   double destinationPorts, String label) {
    public static final String HEADER = "id,protocol,duration_ms,bytes,packets_per_second,syn_ratio,failed_logins,destination_ports,label";
    public static final String[] FEATURES = {"log duration", "log bytes", "log packets/s", "SYN ratio", "log failed logins", "log destination ports", "TCP", "UDP", "ICMP"};
    public Flow {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("ID must contain 1-64 letters, digits, underscores or hyphens");
        if (!java.util.Set.of("TCP", "UDP", "ICMP").contains(protocol)) throw new IllegalArgumentException("Protocol must be TCP, UDP or ICMP");
        for (double v : new double[]{durationMs, bytes, packetsPerSecond, synRatio, failedLogins, destinationPorts})
            if (!Double.isFinite(v) || v < 0 || v > 1e12) throw new IllegalArgumentException("Features must be finite numbers between 0 and 1e12");
        if (synRatio > 1) throw new IllegalArgumentException("SYN ratio must be between 0 and 1");
        if (!java.util.Set.of("NORMAL", "DOS", "PORT_SCAN", "BRUTE_FORCE", "EXFILTRATION", "UNKNOWN").contains(label))
            throw new IllegalArgumentException("Unsupported label: " + label);
    }
    public double[] features() {
        return new double[]{Math.log1p(durationMs), Math.log1p(bytes), Math.log1p(packetsPerSecond), synRatio,
            Math.log1p(failedLogins), Math.log1p(destinationPorts), protocol.equals("TCP") ? 1 : 0,
            protocol.equals("UDP") ? 1 : 0, protocol.equals("ICMP") ? 1 : 0};
    }
    public boolean attack() { return !label.equals("NORMAL") && !label.equals("UNKNOWN"); }
    public String csv() {
        return String.join(",", id, protocol, Double.toString(durationMs), Double.toString(bytes), Double.toString(packetsPerSecond),
            Double.toString(synRatio), Double.toString(failedLogins), Double.toString(destinationPorts), label);
    }
}
