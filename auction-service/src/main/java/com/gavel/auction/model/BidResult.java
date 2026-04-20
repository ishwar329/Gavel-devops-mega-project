package com.gavel.auction.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BidResult(
        @JsonProperty("bid_id") String bidId,
        @JsonProperty("auction_id") String auctionId,
        @JsonProperty("amount") long amount,
        @JsonProperty("new_highest_bid") long newHighestBid,
        @JsonProperty("status") String status
) {}
