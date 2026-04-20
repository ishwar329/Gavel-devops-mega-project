package com.gavel.shared.events;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RefundProcessedEvent(
        @JsonProperty("payment_id") String paymentId,
        @JsonProperty("auction_id") String auctionId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("amount") long amount,
        @JsonProperty("refunded_at") String refundedAt
) {}
