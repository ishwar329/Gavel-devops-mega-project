package com.gavel.shared.events;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReviewCreatedEvent(
        @JsonProperty("review_id") String reviewId,
        @JsonProperty("shop_id") String shopId,
        @JsonProperty("shop_name") String shopName,
        @JsonProperty("reviewer_username") String reviewerUsername,
        @JsonProperty("auction_id") String auctionId,
        @JsonProperty("rating") int rating,
        @JsonProperty("comment") String comment
) {}
