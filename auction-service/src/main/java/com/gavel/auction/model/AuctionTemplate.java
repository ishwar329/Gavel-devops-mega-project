package com.gavel.auction.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AuctionTemplate {

    @JsonProperty("template_id")
    private String templateId;
    @JsonProperty("seller_id")
    private String sellerId;
    @JsonProperty("shop_id")
    private String shopId;
    @JsonProperty("item_id")
    private String itemId;
    @JsonProperty("item_title")
    private String itemTitle;
    @JsonProperty("shop_name")
    private String shopName;
    @JsonProperty("shop_lat")
    private double shopLat;
    @JsonProperty("shop_lng")
    private double shopLng;
    @JsonProperty("retail_price")
    private long retailPrice;
    @JsonProperty("max_price")
    private long maxPrice;
    @JsonProperty("min_increment")
    private long minIncrement;
    @JsonProperty("quantity")
    private int quantity;
    @JsonProperty("image_url")
    private String imageUrl;
    @JsonProperty("shop_logo_url")
    private String shopLogoUrl;
    @JsonProperty("description")
    private String description;
    @JsonProperty("category")
    private String category;
    @JsonProperty("duration_minutes")
    private int durationMinutes;
    @JsonProperty("start_bid")
    private long startBid;
    @JsonProperty("pickup_offset_minutes")
    private int pickupOffsetMinutes;
    @JsonProperty("pickup_window_minutes")
    private int pickupWindowMinutes;
    @JsonProperty("schedule_type")
    private String scheduleType;
    @JsonProperty("schedule_days")
    private String scheduleDays;
    @JsonProperty("schedule_time")
    private String scheduleTime;
    @JsonProperty("active")
    private boolean active;
    @JsonProperty("created_at")
    private String createdAt;
    @JsonProperty("next_run_at")
    private String nextRunAt;

    public AuctionTemplate() {}

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getItemTitle() { return itemTitle; }
    public void setItemTitle(String itemTitle) { this.itemTitle = itemTitle; }
    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }
    public double getShopLat() { return shopLat; }
    public void setShopLat(double shopLat) { this.shopLat = shopLat; }
    public double getShopLng() { return shopLng; }
    public void setShopLng(double shopLng) { this.shopLng = shopLng; }
    public long getRetailPrice() { return retailPrice; }
    public void setRetailPrice(long retailPrice) { this.retailPrice = retailPrice; }
    public long getMaxPrice() { return maxPrice; }
    public void setMaxPrice(long maxPrice) { this.maxPrice = maxPrice; }
    public long getMinIncrement() { return minIncrement; }
    public void setMinIncrement(long minIncrement) { this.minIncrement = minIncrement; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getShopLogoUrl() { return shopLogoUrl; }
    public void setShopLogoUrl(String shopLogoUrl) { this.shopLogoUrl = shopLogoUrl; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public long getStartBid() { return startBid; }
    public void setStartBid(long startBid) { this.startBid = startBid; }
    public int getPickupOffsetMinutes() { return pickupOffsetMinutes; }
    public void setPickupOffsetMinutes(int pickupOffsetMinutes) { this.pickupOffsetMinutes = pickupOffsetMinutes; }
    public int getPickupWindowMinutes() { return pickupWindowMinutes; }
    public void setPickupWindowMinutes(int pickupWindowMinutes) { this.pickupWindowMinutes = pickupWindowMinutes; }
    public String getScheduleType() { return scheduleType; }
    public void setScheduleType(String scheduleType) { this.scheduleType = scheduleType; }
    public String getScheduleDays() { return scheduleDays; }
    public void setScheduleDays(String scheduleDays) { this.scheduleDays = scheduleDays; }
    public String getScheduleTime() { return scheduleTime; }
    public void setScheduleTime(String scheduleTime) { this.scheduleTime = scheduleTime; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(String nextRunAt) { this.nextRunAt = nextRunAt; }
}
