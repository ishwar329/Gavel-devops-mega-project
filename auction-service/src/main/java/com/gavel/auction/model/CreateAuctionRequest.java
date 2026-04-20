package com.gavel.auction.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateAuctionRequest(
        @JsonProperty("item_id") String itemId,
        @JsonProperty("item_title") String itemTitle,
        @JsonProperty("shop_id") String shopId,
        @JsonProperty("shop_name") String shopName,
        @JsonProperty("shop_lat") double shopLat,
        @JsonProperty("shop_lng") double shopLng,
        @JsonProperty("retail_price") long retailPrice,
        @JsonProperty("max_price") long maxPrice,
        @JsonProperty("min_increment") long minIncrement,
        @JsonProperty("quantity") int quantity,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("shop_logo_url") String shopLogoUrl,
        @JsonProperty("description") String description,
        @JsonProperty("category") String category,
        @JsonProperty("duration") int duration,
        @JsonProperty("start_bid") long startBid,
        @JsonProperty("scheduled_start") String scheduledStart,
        @JsonProperty("pickup_start") String pickupStart,
        @JsonProperty("pickup_end") String pickupEnd,
        @JsonProperty("duration_minutes") int durationMinutes
) {
    public int effectiveDuration() {
        return duration > 0 ? duration : durationMinutes;
    }
}
