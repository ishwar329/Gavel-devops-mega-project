package com.gavel.auction.service;

import com.gavel.auction.model.BidMetrics;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class MetricsCollector {

    private final Object lock = new Object();
    private long successful = 0;
    private long rejected = 0;
    private long violations = 0;
    private final List<Double> latencies = new ArrayList<>();

    public void recordSuccessful(Duration latency) {
        synchronized (lock) {
            successful++;
            latencies.add(latency.toNanos() / 1_000_000.0);
        }
    }

    public void recordRejected() {
        synchronized (lock) {
            rejected++;
        }
    }

    public void recordViolation() {
        synchronized (lock) {
            violations++;
        }
    }

    public BidMetrics snapshot() {
        synchronized (lock) {
            long total = successful + rejected;
            if (latencies.isEmpty()) {
                return new BidMetrics(total, successful, rejected, 0, 0, 0, violations);
            }

            double[] sorted = latencies.stream().mapToDouble(d -> d).sorted().toArray();
            double sum = 0;
            for (double v : sorted) sum += v;
            double avg = Math.round(sum / sorted.length * 100.0) / 100.0;
            double p95 = sorted[(int) Math.floor(sorted.length * 0.95)];
            double p99 = sorted[(int) Math.floor(sorted.length * 0.99)];

            return new BidMetrics(total, successful, rejected, avg, p95, p99, violations);
        }
    }

    public void reset() {
        synchronized (lock) {
            successful = 0;
            rejected = 0;
            violations = 0;
            latencies.clear();
        }
    }
}
