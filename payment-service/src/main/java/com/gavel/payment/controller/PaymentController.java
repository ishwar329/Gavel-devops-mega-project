package com.gavel.payment.controller;

import com.gavel.payment.model.PaymentResponse;
import com.gavel.payment.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/payments/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String id) {
        PaymentResponse response = paymentService.getPayment(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/{userId}/payments")
    public ResponseEntity<List<PaymentResponse>> getUserPayments(@PathVariable String userId) {
        String callerId = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!callerId.equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<PaymentResponse> payments = paymentService.getUserPayments(userId);
        return ResponseEntity.ok(payments);
    }

    @GetMapping("/auctions/{auctionId}/payment")
    public ResponseEntity<PaymentResponse> getAuctionPayment(@PathVariable String auctionId) {
        PaymentResponse response = paymentService.getPaymentByAuction(auctionId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/admin/payments/{id}/process")
    public ResponseEntity<Map<String, String>> processPayment(@PathVariable String id) {
        paymentService.processPayment(id);
        return ResponseEntity.ok(Map.of("message", "payment processing initiated"));
    }

    @PostMapping("/admin/payments/{id}/refund")
    public ResponseEntity<Map<String, String>> refundPayment(@PathVariable String id) {
        paymentService.refundPayment(id);
        return ResponseEntity.ok(Map.of("message", "refund processed"));
    }

    @ExceptionHandler(PaymentService.PaymentNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(PaymentService.PaymentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(PaymentService.InvalidPaymentStatusException.class)
    public ResponseEntity<Map<String, String>> handleInvalidStatus(PaymentService.InvalidPaymentStatusException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
    }
}
