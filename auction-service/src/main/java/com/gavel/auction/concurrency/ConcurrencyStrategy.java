package com.gavel.auction.concurrency;

public interface ConcurrencyStrategy {

    BidPlacement tryPlaceBid(String auctionId, long amount, String bidderId);

    String name();
}
