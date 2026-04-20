package com.gavel.payment.controller;

import com.gavel.payment.model.PaymentResponse;
import com.gavel.payment.service.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // -----------------------------------------------------------------------
    // getPayment
    // -----------------------------------------------------------------------

    @Test
    void getPayment_returnsOkWithResponse() {
        PaymentResponse response = buildResponse("pay-1", "a1", "u1", 5000L, "completed");
        when(paymentService.getPayment("pay-1")).thenReturn(response);

        ResponseEntity<PaymentResponse> result = paymentController.getPayment("pay-1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("pay-1", result.getBody().paymentId());
        assertEquals(5000L, result.getBody().amount());
    }

    // -----------------------------------------------------------------------
    // getUserPayments
    // -----------------------------------------------------------------------

    @Test
    void getUserPayments_callerMatchesUserId_returnsOk() {
        setAuthenticatedUser("u1");
        List<PaymentResponse> payments = List.of(
                buildResponse("pay-1", "a1", "u1", 1000L, "completed"),
                buildResponse("pay-2", "a2", "u1", 2000L, "pending")
        );
        when(paymentService.getUserPayments("u1")).thenReturn(payments);

        ResponseEntity<List<PaymentResponse>> result = paymentController.getUserPayments("u1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(2, result.getBody().size());
    }

    @Test
    void getUserPayments_callerDoesNotMatchUserId_returnsForbidden() {
        setAuthenticatedUser("other-user");

        ResponseEntity<List<PaymentResponse>> result = paymentController.getUserPayments("u1");

        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
        assertNull(result.getBody());
        verify(paymentService, never()).getUserPayments(anyString());
    }

    // -----------------------------------------------------------------------
    // getAuctionPayment
    // -----------------------------------------------------------------------

    @Test
    void getAuctionPayment_returnsOk() {
        PaymentResponse response = buildResponse("pay-1", "a1", "u1", 3000L, "pending");
        when(paymentService.getPaymentByAuction("a1")).thenReturn(response);

        ResponseEntity<PaymentResponse> result = paymentController.getAuctionPayment("a1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("a1", result.getBody().auctionId());
    }

    // -----------------------------------------------------------------------
    // processPayment
    // -----------------------------------------------------------------------

    @Test
    void processPayment_returnsOkWithMessage() {
        doNothing().when(paymentService).processPayment("pay-1");

        ResponseEntity<Map<String, String>> result = paymentController.processPayment("pay-1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("payment processing initiated", result.getBody().get("message"));
        verify(paymentService).processPayment("pay-1");
    }

    // -----------------------------------------------------------------------
    // refundPayment
    // -----------------------------------------------------------------------

    @Test
    void refundPayment_returnsOkWithMessage() {
        doNothing().when(paymentService).refundPayment("pay-1");

        ResponseEntity<Map<String, String>> result = paymentController.refundPayment("pay-1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("refund processed", result.getBody().get("message"));
        verify(paymentService).refundPayment("pay-1");
    }

    // -----------------------------------------------------------------------
    // Exception handlers
    // -----------------------------------------------------------------------

    @Test
    void handleNotFound_returns404WithError() {
        PaymentService.PaymentNotFoundException ex =
                new PaymentService.PaymentNotFoundException("pay-99");

        ResponseEntity<Map<String, String>> result = paymentController.handleNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
        assertNotNull(result.getBody());
        assertTrue(result.getBody().get("error").contains("pay-99"));
    }

    @Test
    void handleInvalidStatus_returns422WithError() {
        PaymentService.InvalidPaymentStatusException ex =
                new PaymentService.InvalidPaymentStatusException("refunded");

        ResponseEntity<Map<String, String>> result = paymentController.handleInvalidStatus(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, result.getStatusCode());
        assertNotNull(result.getBody());
        assertTrue(result.getBody().get("error").contains("refunded"));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void setAuthenticatedUser(String username) {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(username, "password");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private PaymentResponse buildResponse(String paymentId, String auctionId, String userId,
                                           long amount, String status) {
        return new PaymentResponse(paymentId, auctionId, userId, "item-1", "shop-1",
                amount, status, null, "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z");
    }
}
