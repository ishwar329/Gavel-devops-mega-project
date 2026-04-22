package com.gavel.shared.events;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ItemCreatedEvent(
        @JsonProperty("item_id") String itemId,
        @JsonProperty("shop_id") String shopId,
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("retail_value") long retailValue,
        @JsonProperty("category") String category,
        @JsonProperty("image_url") String imageUrl
) {}
