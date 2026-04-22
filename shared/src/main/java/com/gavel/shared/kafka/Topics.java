package com.gavel.shared.kafka;

public final class Topics {

    public static final String BID_PLACED = "bid.placed";
    public static final String AUCTION_CLOSED = "auction.closed";
    public static final String PAYMENT_PROCESSED = "payment.processed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String REFUND_PROCESSED = "refund.processed";
    public static final String ITEM_CREATED = "item.created";
    public static final String REVIEW_CREATED = "review.created";

    private Topics() {}
}
