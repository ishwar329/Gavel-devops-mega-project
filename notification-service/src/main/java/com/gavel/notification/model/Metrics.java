package com.gavel.notification.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Metrics(
        @JsonProperty("active_connections") long activeConnections,
        @JsonProperty("total_broadcasts") long totalBroadcasts,
        @JsonProperty("avg_delivery_latency_ms") double avgDeliveryLatencyMs,
        @JsonProperty("p99_delivery_latency_ms") double p99DeliveryLatencyMs
) {}
