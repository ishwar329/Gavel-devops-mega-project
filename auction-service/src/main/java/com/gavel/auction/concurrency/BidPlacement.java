package com.gavel.auction.concurrency;

public record BidPlacement(
        long newVersion,
        String evictedBidder,
        long newFloor
) {}
