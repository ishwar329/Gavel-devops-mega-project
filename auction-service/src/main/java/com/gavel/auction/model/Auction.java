package com.gavel.auction.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

public class Auction {

    @JsonProperty("auction_id")
    private String auctionId;
    @JsonProperty("item_id")
    private String itemId;
    @JsonProperty("item_title")
    private String itemTitle;
    @JsonProperty("seller_id")
    private String sellerId;
    @JsonProperty("shop_id")
    private String shopId;
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
    @JsonProperty("pickup_start")
    private String pickupStart;
    @JsonProperty("pickup_end")
    private String pickupEnd;
    @JsonProperty("start_time")
    private String startTime;
    @JsonProperty("end_time")
    private String endTime;
    @JsonProperty("current_highest")
    private long currentHighest;
    @JsonProperty("bid_count")
    private long bidCount;
    @JsonProperty("highest_bidder")
    private String highestBidder;
    @JsonProperty("status")
    private String status;
    @JsonProperty("version")
    private long version;
    @JsonProperty("winners")
    private Map<String, Long> winners;

    public Auction() {}

    public String getAuctionId() { return auctionId; }
    public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getItemTitle() { return itemTitle; }
    public void setItemTitle(String itemTitle) { this.itemTitle = itemTitle; }
    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }
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
    public String getPickupStart() { return pickupStart; }
    public void setPickupStart(String pickupStart) { this.pickupStart = pickupStart; }
    public String getPickupEnd() { return pickupEnd; }
    public void setPickupEnd(String pickupEnd) { this.pickupEnd = pickupEnd; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public long getCurrentHighest() { return currentHighest; }
    public void setCurrentHighest(long currentHighest) { this.currentHighest = currentHighest; }
    public long getBidCount() { return bidCount; }
    public void setBidCount(long bidCount) { this.bidCount = bidCount; }
    public String getHighestBidder() { return highestBidder; }
    public void setHighestBidder(String highestBidder) { this.highestBidder = highestBidder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Map<String, Long> getWinners() { return winners; }
    public void setWinners(Map<String, Long> winners) { this.winners = winners; }
}
