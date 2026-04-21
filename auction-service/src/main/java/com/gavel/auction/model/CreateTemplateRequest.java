package com.gavel.auction.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateTemplateRequest(
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
        @JsonProperty("duration_minutes") int durationMinutes,
        @JsonProperty("start_bid") long startBid,
        @JsonProperty("pickup_offset_minutes") int pickupOffsetMinutes,
        @JsonProperty("pickup_window_minutes") int pickupWindowMinutes,
        @JsonProperty("schedule_type") String scheduleType,
        @JsonProperty("schedule_days") String scheduleDays,
        @JsonProperty("schedule_time") String scheduleTime
) {}
