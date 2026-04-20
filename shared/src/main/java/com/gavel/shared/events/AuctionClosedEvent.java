package com.gavel.shared.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record AuctionClosedEvent(
        @JsonProperty("auction_id") String auctionId,
        @JsonProperty("winner_id") String winnerId,
        @JsonProperty("winning_bid") long winningBid,
        @JsonProperty("winners") Map<String, Long> winners,
        @JsonProperty("quantity") int quantity,
        @JsonProperty("item_id") String itemId,
        @JsonProperty("item_title") String itemTitle,
        @JsonProperty("shop_id") String shopId,
        @JsonProperty("closed_at") String closedAt
) {}
