package com.gavel.auction.service;

import com.gavel.auction.model.BidMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class MetricsCollectorTest {

    private MetricsCollector collector;

    @BeforeEach
    void setUp() {
        collector = new MetricsCollector();
    }

    @Test
    void snapshot_empty_returnsZeros() {
        BidMetrics m = collector.snapshot();

        assertEquals(0, m.totalBids());
        assertEquals(0, m.successfulBids());
        assertEquals(0, m.rejectedBids());
        assertEquals(0, m.avgLatencyMs());
        assertEquals(0, m.p95LatencyMs());
        assertEquals(0, m.p99LatencyMs());
        assertEquals(0, m.consistencyViolations());
    }

    @Test
    void recordSuccessful_incrementsCountAndTracksLatency() {
        collector.recordSuccessful(Duration.ofMillis(10));
        collector.recordSuccessful(Duration.ofMillis(20));

        BidMetrics m = collector.snapshot();
        assertEquals(2, m.successfulBids());
        assertEquals(2, m.totalBids());
        assertEquals(0, m.rejectedBids());
        assertTrue(m.avgLatencyMs() > 0);
    }

    @Test
    void recordRejected_incrementsRejectedCount() {
        collector.recordRejected();
        collector.recordRejected();
        collector.recordRejected();

        BidMetrics m = collector.snapshot();
        assertEquals(0, m.successfulBids());
        assertEquals(3, m.rejectedBids());
        assertEquals(3, m.totalBids());
    }

    @Test
    void recordViolation_incrementsViolationCount() {
        collector.recordViolation();
        collector.recordViolation();

        BidMetrics m = collector.snapshot();
        assertEquals(2, m.consistencyViolations());
    }

    @Test
    void snapshot_mixedCounts_calculatesCorrectly() {
        collector.recordSuccessful(Duration.ofMillis(5));
        collector.recordSuccessful(Duration.ofMillis(15));
        collector.recordRejected();
        collector.recordViolation();

        BidMetrics m = collector.snapshot();
        assertEquals(3, m.totalBids());
        assertEquals(2, m.successfulBids());
        assertEquals(1, m.rejectedBids());
        assertEquals(1, m.consistencyViolations());
    }

    @Test
    void snapshot_percentiles_computedFromSortedLatencies() {
        for (int i = 1; i <= 100; i++) {
            collector.recordSuccessful(Duration.ofMillis(i));
        }

        BidMetrics m = collector.snapshot();
        assertEquals(100, m.successfulBids());
        assertTrue(m.p95LatencyMs() >= 95);
        assertTrue(m.p99LatencyMs() >= 99);
        assertTrue(m.avgLatencyMs() > 40 && m.avgLatencyMs() < 60);
    }

    @Test
    void reset_clearsAllState() {
        collector.recordSuccessful(Duration.ofMillis(10));
        collector.recordRejected();
        collector.recordViolation();

        collector.reset();

        BidMetrics m = collector.snapshot();
        assertEquals(0, m.totalBids());
        assertEquals(0, m.successfulBids());
        assertEquals(0, m.rejectedBids());
        assertEquals(0, m.consistencyViolations());
        assertEquals(0, m.avgLatencyMs());
    }

    @Test
    void snapshot_singleLatency_allPercentilesEqual() {
        collector.recordSuccessful(Duration.ofMillis(42));

        BidMetrics m = collector.snapshot();
        assertEquals(1, m.successfulBids());
        assertEquals(m.avgLatencyMs(), m.p95LatencyMs(), 0.01);
        assertEquals(m.avgLatencyMs(), m.p99LatencyMs(), 0.01);
    }
}
