package com.gavel.notification.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StoredNotification(
        @JsonProperty("id") String id,
        @JsonProperty("type") String type,
        @JsonProperty("auction_id") String auctionId,
        @JsonProperty("item_title") String itemTitle,
        @JsonProperty("message") String message,
        @JsonProperty("link") String link,
        @JsonProperty("amount") long amount,
        @JsonProperty("created_at") long createdAt,
        @JsonProperty("read") boolean read
) {
    public StoredNotification withRead(boolean read) {
        return new StoredNotification(id, type, auctionId, itemTitle, message, link, amount, createdAt, read);
    }
}
