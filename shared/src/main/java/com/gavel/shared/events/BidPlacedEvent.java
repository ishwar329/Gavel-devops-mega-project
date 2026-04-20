package com.gavel.shared.events;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BidPlacedEvent(
        @JsonProperty("auction_id") String auctionId,
        @JsonProperty("bid_id") String bidId,
        @JsonProperty("item_id") String itemId,
        @JsonProperty("item_title") String itemTitle,
        @JsonProperty("shop_id") String shopId,
        @JsonProperty("shop_name") String shopName,
        @JsonProperty("user_id") String userId,
        @JsonProperty("amount") long amount,
        @JsonProperty("previous_highest") long previousHighest,
        @JsonProperty("previous_bidder") String previousBidder,
        @JsonProperty("bid_accepted_at") String bidAcceptedAt,
        @JsonProperty("timestamp") String timestamp
) {}
