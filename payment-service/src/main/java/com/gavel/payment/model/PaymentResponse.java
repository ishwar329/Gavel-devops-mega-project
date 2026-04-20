package com.gavel.payment.model;

public record PaymentResponse(
        String paymentId,
        String auctionId,
        String userId,
        String itemId,
        String shopId,
        long amount,
        String status,
        String failReason,
        String createdAt,
        String updatedAt
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getPaymentId(),
                p.getAuctionId(),
                p.getUserId(),
                p.getItemId(),
                p.getShopId(),
                p.getAmount(),
                p.getStatus(),
                p.getFailReason(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
