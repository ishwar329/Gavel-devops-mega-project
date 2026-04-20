package com.gavel.auction.concurrency;

public class BidException extends RuntimeException {

    private final String code;
    private final long threshold;

    public BidException(String code, String message, long threshold) {
        super(message);
        this.code = code;
        this.threshold = threshold;
    }

    public String getCode() { return code; }
    public long getThreshold() { return threshold; }

    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String NOT_OPEN = "NOT_OPEN";
    public static final String BID_TOO_LOW = "BID_TOO_LOW";
    public static final String EXCEEDS_MAX = "EXCEEDS_MAX";
    public static final String INCREMENT_TOO_SMALL = "INCREMENT_TOO_SMALL";
}
