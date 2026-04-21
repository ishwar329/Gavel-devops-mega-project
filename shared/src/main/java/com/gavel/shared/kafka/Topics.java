package com.gavel.shared.kafka;

public final class Topics {

    public static final String BID_PLACED = "bid.placed";
    public static final String AUCTION_CLOSED = "auction.closed";
    public static final String PAYMENT_PROCESSED = "payment.processed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String REFUND_PROCESSED = "refund.processed";

    private Topics() {}
}
