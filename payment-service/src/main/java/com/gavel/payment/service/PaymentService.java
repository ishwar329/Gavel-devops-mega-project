package com.gavel.payment.service;

import com.gavel.payment.events.PaymentEventPublisher;
import com.gavel.payment.model.Payment;
import com.gavel.payment.model.PaymentResponse;
import com.gavel.payment.repository.PaymentRepository;
import com.gavel.shared.events.AuctionClosedEvent;
import com.gavel.shared.events.PaymentFailedEvent;
import com.gavel.shared.events.PaymentProcessedEvent;
import com.gavel.shared.events.RefundProcessedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher eventPublisher;

    public PaymentService(PaymentRepository paymentRepository, PaymentEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    public void initiatePayment(AuctionClosedEvent event) {
        Map<String, Long> winners = new HashMap<>();
        if (event.winners() != null && !event.winners().isEmpty()) {
            winners.putAll(event.winners());
        } else if (event.winnerId() != null && !event.winnerId().isEmpty()) {
            winners.put(event.winnerId(), event.winningBid());
        }

        if (winners.isEmpty()) {
            log.info("Auction {} has no winner, skipping payment", event.auctionId());
            return;
        }

        Set<String> alreadyPaid = paymentRepository.getUserIdsWithPaymentForAuction(event.auctionId());

        Map<String, Long> pending = new HashMap<>();
        for (var entry : winners.entrySet()) {
            if (!alreadyPaid.contains(entry.getKey())) {
                pending.put(entry.getKey(), entry.getValue());
            }
        }

        if (pending.isEmpty()) {
            log.info("Auction {} — all {} winner(s) already have payments, skipping",
                    event.auctionId(), winners.size());
            return;
        }

        String now = Instant.now().toString();
        for (var entry : pending.entrySet()) {
            String bidderId = entry.getKey();
            long amount = entry.getValue();

            Payment payment = new Payment();
            payment.setPaymentId(UUID.randomUUID().toString());
            payment.setAuctionId(event.auctionId());
            payment.setUserId(bidderId);
            payment.setItemId(event.itemId());
            payment.setShopId(event.shopId());
            payment.setAmount(amount);
            payment.setStatus(Payment.STATUS_PENDING);
            payment.setCreatedAt(now);
            payment.setUpdatedAt(now);

            try {
                paymentRepository.create(payment);
                log.info("Created payment {} for auction {} bidder {} (amount={})",
                        payment.getPaymentId(), event.auctionId(), bidderId, amount);
                processPayment(payment.getPaymentId());
            } catch (PaymentRepository.PaymentAlreadyExistsException e) {
                log.info("Payment already exists for auction {} bidder {}, skipping",
                        event.auctionId(), bidderId);
            } catch (Exception e) {
                log.error("Failed to process payment {} for bidder {}: {}",
                        payment.getPaymentId(), bidderId, e.getMessage());
            }
        }
    }

    public void processPayment(String paymentId) {
        Payment payment = paymentRepository.getById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (!Payment.STATUS_PENDING.equals(payment.getStatus())
                && !Payment.STATUS_PROCESSING.equals(payment.getStatus())) {
            throw new InvalidPaymentStatusException(payment.getStatus());
        }

        if (Payment.STATUS_PENDING.equals(payment.getStatus())) {
            paymentRepository.updateStatus(paymentId, Payment.STATUS_PROCESSING, "");
        }

        String decision = payment.getGatewayDecision();
        if (decision == null || decision.isEmpty()) {
            decision = ThreadLocalRandom.current().nextInt(10) < 9 ? "success" : "failed";
            paymentRepository.setGatewayDecision(paymentId, decision);
        }

        if ("success".equals(decision)) {
            paymentRepository.updateStatus(paymentId, Payment.STATUS_COMPLETED, "");
            log.info("Payment {} completed", paymentId);
            eventPublisher.publishPaymentProcessed(new PaymentProcessedEvent(
                    paymentId,
                    payment.getAuctionId(),
                    payment.getUserId(),
                    payment.getAmount(),
                    Instant.now().toString()
            ));
        } else {
            String reason = "payment gateway declined";
            paymentRepository.updateStatus(paymentId, Payment.STATUS_FAILED, reason);
            log.info("Payment {} failed ({})", paymentId, reason);
            eventPublisher.publishPaymentFailed(new PaymentFailedEvent(
                    paymentId,
                    payment.getAuctionId(),
                    payment.getUserId(),
                    payment.getAmount(),
                    reason,
                    Instant.now().toString()
            ));
        }
    }

    public void refundPayment(String paymentId) {
        Payment payment = paymentRepository.getById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (!Payment.STATUS_COMPLETED.equals(payment.getStatus())
                && !Payment.STATUS_FAILED.equals(payment.getStatus())) {
            throw new InvalidPaymentStatusException(payment.getStatus());
        }

        paymentRepository.updateStatus(paymentId, Payment.STATUS_REFUNDED, "");
        log.info("Payment {} refunded", paymentId);

        eventPublisher.publishRefundProcessed(new RefundProcessedEvent(
                paymentId,
                payment.getAuctionId(),
                payment.getUserId(),
                payment.getAmount(),
                Instant.now().toString()
        ));
    }

    public void abandonPayment(String paymentId, String reason) {
        Payment payment = paymentRepository.getById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        paymentRepository.updateStatus(paymentId, Payment.STATUS_FAILED, reason);
        log.info("Payment {} abandoned after max retries ({})", paymentId, reason);

        eventPublisher.publishPaymentFailed(new PaymentFailedEvent(
                paymentId,
                payment.getAuctionId(),
                payment.getUserId(),
                payment.getAmount(),
                reason,
                Instant.now().toString()
        ));
    }

    public PaymentResponse getPayment(String paymentId) {
        Payment payment = paymentRepository.getById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        return PaymentResponse.from(payment);
    }

    public PaymentResponse getPaymentByAuction(String auctionId) {
        Payment payment = paymentRepository.getByAuctionId(auctionId)
                .orElseThrow(() -> new PaymentNotFoundException("auction:" + auctionId));
        return PaymentResponse.from(payment);
    }

    public List<PaymentResponse> getUserPayments(String userId) {
        return paymentRepository.getByUserId(userId).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    public static class PaymentNotFoundException extends RuntimeException {
        public PaymentNotFoundException(String id) {
            super("Payment not found: " + id);
        }
    }

    public static class InvalidPaymentStatusException extends RuntimeException {
        public InvalidPaymentStatusException(String status) {
            super("Invalid payment status for this operation: " + status);
        }
    }
}
