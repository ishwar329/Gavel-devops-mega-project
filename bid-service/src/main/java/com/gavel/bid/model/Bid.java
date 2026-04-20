package com.gavel.bid.model;

import java.time.Instant;

public class Bid {

    private String bidId;
    private String auctionId;
    private String userId;
    private String itemTitle;
    private String shopId;
    private String shopName;
    private long amount;
    private Instant timestamp;
    private String status;

    public Bid() {}

    public Bid(String bidId, String auctionId, String userId, String itemTitle,
               String shopId, String shopName, long amount, Instant timestamp, String status) {
        this.bidId = bidId;
        this.auctionId = auctionId;
        this.userId = userId;
        this.itemTitle = itemTitle;
        this.shopId = shopId;
        this.shopName = shopName;
        this.amount = amount;
        this.timestamp = timestamp;
        this.status = status;
    }

    public String getBidId() { return bidId; }
    public void setBidId(String bidId) { this.bidId = bidId; }

    public String getAuctionId() { return auctionId; }
    public void setAuctionId(String auctionId) { this.auctionId = auctionId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getItemTitle() { return itemTitle; }
    public void setItemTitle(String itemTitle) { this.itemTitle = itemTitle; }

    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
