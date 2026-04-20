package com.gavel.auction.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BidMetrics(
        @JsonProperty("total_bids") long totalBids,
        @JsonProperty("successful_bids") long successfulBids,
        @JsonProperty("rejected_bids") long rejectedBids,
        @JsonProperty("avg_latency_ms") double avgLatencyMs,
        @JsonProperty("p95_latency_ms") double p95LatencyMs,
        @JsonProperty("p99_latency_ms") double p99LatencyMs,
        @JsonProperty("consistency_violations") long consistencyViolations
) {}
