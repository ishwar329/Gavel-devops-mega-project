package com.gavel.payment.service;

import com.gavel.payment.events.PaymentEventPublisher;
import com.gavel.payment.model.Payment;
import com.gavel.payment.model.PaymentResponse;
import com.gavel.payment.repository.PaymentRepository;
import com.gavel.shared.events.AuctionClosedEvent;
import com.gavel.shared.events.PaymentFailedEvent;
import com.gavel.shared.events.PaymentProcessedEvent;
import com.gavel.shared.events.RefundProcessedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentEventPublisher eventPublisher;

    @InjectMocks
    private PaymentService paymentService;

    // -----------------------------------------------------------------------
    // initiatePayment
    // -----------------------------------------------------------------------

    @Test
    void initiatePayment_noWinnersMapAndNoSingleWinner_skips() {
        AuctionClosedEvent event = new AuctionClosedEvent(
                "auction-1", null, 0L, null, 1, "item-1", "Title", "shop-1", Instant.now().toString());

        paymentService.initiatePayment(event);

        verify(paymentRepository, never()).create(any());
    }

    @Test
    void initiatePayment_emptyWinnersMapAndEmptySingleWinner_skips() {
        AuctionClosedEvent event = new AuctionClosedEvent(
                "auction-1", "", 0L, Map.of(), 1, "item-1", "Title", "shop-1", Instant.now().toString());

        paymentService.initiatePayment(event);

        verify(paymentRepository, never()).create(any());
    }

    @Test
    void initiatePayment_winnersFromMap_createsPaymentAndProcesses() {
        Map<String, Long> winners = Map.of("user-a", 5000L, "user-b", 3000L);
        AuctionClosedEvent event = new AuctionClosedEvent(
                "auction-1", null, 0L, winners, 2, "item-1", "Title", "shop-1", Instant.now().toString());
        when(paymentRepository.getUserIdsWithPaymentForAuction("auction-1")).thenReturn(Set.of());

        // processPayment will be called internally, so mock getById for those calls
        when(paymentRepository.getById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            Payment p = buildPayment(id, "auction-1", "user-a", 5000L, Payment.STATUS_PENDING);
            p.setGatewayDecision("success");
            return Optional.of(p);
        });

        paymentService.initiatePayment(event);

        verify(paymentRepository, times(2)).create(any(Payment.class));
    }

    @Test
    void initiatePayment_singleWinnerNoMap_createsPayment() {
        AuctionClosedEvent event = new AuctionClosedEvent(
                "auction-1", "user-a", 5000L, null, 1, "item-1", "Title", "shop-1", Instant.now().toString());
        when(paymentRepository.getUserIdsWithPaymentForAuction("auction-1")).thenReturn(Set.of());

        Payment pending = buildPayment("pay-1", "auction-1", "user-a", 5000L, Payment.STATUS_PENDING);
        pending.setGatewayDecision("success");
        when(paymentRepository.getById(anyString())).thenReturn(Optional.of(pending));

        paymentService.initiatePayment(event);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).create(captor.capture());
        Payment created = captor.getValue();
        assertEquals("auction-1", created.getAuctionId());
        assertEquals("user-a", created.getUserId());
        assertEquals(5000L, created.getAmount());
        assertEquals(Payment.STATUS_PENDING, created.getStatus());
        assertEquals("item-1", created.getItemId());
        assertEquals("shop-1", created.getShopId());
        assertNotNull(created.getPaymentId());
        assertNotNull(created.getCreatedAt());
    }

    @Test
    void initiatePayment_alreadyPaidUser_skipped() {
        Map<String, Long> winners = new HashMap<>();
        winners.put("user-a", 5000L);
        winners.put("user-b", 3000L);
        AuctionClosedEvent event = new AuctionClosedEvent(
                "auction-1", null, 0L, winners, 2, "item-1", "Title", "shop-1", Instant.now().toString());
        when(paymentRepository.getUserIdsWithPaymentForAuction("auction-1")).thenReturn(Set.of("user-a"));

        // Only user-b will create a payment
        Payment pending = buildPayment("pay-1", "auction-1", "user-b", 3000L, Payment.STATUS_PENDING);
        pending.setGatewayDecision("success");
        when(paymentRepository.getById(anyString())).thenReturn(Optional.of(pending));

        paymentService.initiatePayment(event);

        // Only one payment created (for user-b)
        verify(paymentRepository, times(1)).create(any(Payment.class));
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).create(captor.capture());
        assertEquals("user-b", captor.getValue().getUserId());
    }

    @Test
    void initiatePayment_allAlreadyPaid_nothingCreated() {
        Map<String, Long> winners = Map.of("user-a", 5000L);
        AuctionClosedEvent event = new AuctionClosedEvent(
                "auction-1", null, 0L, winners, 1, "item-1", "Title", "shop-1", Instant.now().toString());
        when(paymentRepository.getUserIdsWithPaymentForAuction("auction-1")).thenReturn(Set.of("user-a"));

        paymentService.initiatePayment(event);

        verify(paymentRepository, never()).create(any());
    }

    @Test
    void initiatePayment_paymentAlreadyExistsException_logsAndContinues() {
        Map<String, Long> winners = new LinkedHashMap<>();
        winners.put("user-a", 5000L);
        winners.put("user-b", 3000L);
        AuctionClosedEvent event = new AuctionClosedEvent(
                "auction-1", null, 0L, winners, 2, "item-1", "Title", "shop-1", Instant.now().toString());
        when(paymentRepository.getUserIdsWithPaymentForAuction("auction-1")).thenReturn(Set.of());

        // First create throws PaymentAlreadyExistsException, second succeeds
        doThrow(new PaymentRepository.PaymentAlreadyExistsException("pay-x"))
                .doNothing()
                .when(paymentRepository).create(any(Payment.class));

        Payment pending = buildPayment("pay-2", "auction-1", "user-b", 3000L, Payment.STATUS_PENDING);
        pending.setGatewayDecision("success");
        when(paymentRepository.getById(anyString())).thenReturn(Optional.of(pending));

        // Should not throw
        assertDoesNotThrow(() -> paymentService.initiatePayment(event));
        verify(paymentRepository, times(2)).create(any());
    }

    // -----------------------------------------------------------------------
    // processPayment
    // -----------------------------------------------------------------------

    @Test
    void processPayment_notFound_throwsPaymentNotFoundException() {
        when(paymentRepository.getById("pay-missing")).thenReturn(Optional.empty());

        assertThrows(PaymentService.PaymentNotFoundException.class,
                () -> paymentService.processPayment("pay-missing"));
    }

    @Test
    void processPayment_statusCompleted_throwsInvalidStatus() {
        Payment payment = buildPayment("pay-1", "a1", "u1", 100L, Payment.STATUS_COMPLETED);
        when(paymentRepository.getById("pay-1")).thenReturn(Optional.of(payment));

        assertThrows(PaymentService.InvalidPaymentStatusException.class,
                () -> paymentService.processPayment("pay-1"));
    }

    @Test
    void processPayment_statusRefunded_throwsInvalidStatus() {
        Payment payment = buildPayment("pay-1", "a1", "u1", 100L, Payment.STATUS_REFUNDED);
        when(paymentRepository.getById("pay-1")).thenReturn(Optional.of(payment));

        assertThrows(PaymentService.InvalidPaymentStatusException.class,
                () -> paymentService.processPayment("pay-1"));
    }

    @Test
    void processPayment_statusFailed_throwsInvalidStatus() {
        Payment payment = buildPayment("pay-1", "a1", "u1", 100L, Payment.STATUS_FAILED);
        when(paymentRepository.getById("pay-1")).thenReturn(Optional.of(payment));

        assertThrows(PaymentService.InvalidPaymentStatusException.class,
                () -> paymentService.processPayment("pay-1"));
    }

    @Test
    void processPayment_pendingWithSuccessDecision_completesAndPublishes() {
        Payment payment = buildPayment("pay-1", "a1", "u1", 2500L, Payment.STATUS_PENDING);
        payment.setGatewayDecision("success");
        when(paymentRepository.getById("pay-1")).thenReturn(Optional.of(payment));

        paymentService.processPayment("pay-1");

        // Should transition to processing first, since status was pending
        verify(paymentRepository).updateStatus("pay-1", Payment.STATUS_PROCESSING, "");
        // Then complete
        verify(paymentRepository).updateStatus("pay-1", Payment.STATUS_COMPLETED, "");
        verify(eventPublisher).publishPaymentProcessed(any(PaymentProcessedEvent.class));
        verify(eventPublisher, never()).publishPaymentFailed(any());
    }

    @Test
    void processPayment_processingWithSuccessDecision_completesWithoutReprocessing() {
        Payment payment = buildPayment("pay-1", "a1", "u1", 2500L, Payment.STATUS_PROCESSING);
        payment.setGatewayDecision("success");
        when(paymentRepository.getById("pay-1")).thenReturn(Optional.of(payment));

        paymentService.processPayment("pay-1");

        // Should NOT call updateStatus to processing since already processing
        verify(paymentRepository, never()).updateStatus("pay-1", Payment.STATUS_PROCESSING, "");
        verify(paymentRepository).updateStatus("pay-1", Payment.STATUS_COMPLETED, "");
        verify(eventPublisher).publishPaymentProcessed(any(PaymentProcessedEvent.class));
    }

    @Test
    void processPayment_pendingWithFailedDecision_failsAndPublishes() {
        Payment payment = buildPayment("pay-1", "a1", "u1", 1200L, Payment.STATUS_PENDING);
        payment.setGatewayDecision("failed");
        when(paymentRepository.getById("pay-1")).thenReturn(Optional.of(payment));

        paymentService.processPayment("pay-1");

        verify(paymentRepository).updateStatus("pay-1", Payment.STATUS_PROCESSING, "");
        verify(paymentRepository).updateStatus("pay-1", Payment.STATUS_FAILED, "payment gateway declined");

        ArgumentCaptor<PaymentFailedEvent> captor = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(eventPublisher).publishPaymentFailed(captor.capture());
        assertEquals("pay-1", captor.getValue().paymentId());
        assertEquals("a1", captor.getValue().auctionId());
        assertEquals("u1", captor.getValue().userId());
        assertEquals(1200L, captor.getValue().amount());
        assertEquals("payment gateway declined", captor.getValue().reason());
    }

    @Test
    void processPayment_noGatewayDecision_setsRandomDecisionAndProcesses() {
        Payment payment = buildPayment("pay-1", "a1", "u1", 800L, Payment.STATUS_PENDING);
        payment.setGatewayDecision(null);
        when(paymentRepository.getById("pay-1")).thenReturn(Optional.of(payment));

        paymentService.processPayment("pay-1");

        // Should set a gateway decision (random, either "success" or "failed")
        verify(paymentRepository).setGatewayDecision(eq("pay-1"), anyString());
        // Should update to processing
        verify(paymentRepository).updateStatus("pay-1", Payment.STATUS_PROCESSING, "");
        // Should reach either completed or failed path
        verify(paymentRepository, atLeastOnce()).updateStatus(eq("pay-1"), argThat(
                status -> status.equals(Payment.STATUS_COMPLETED) || status.equals(Payment.STATUS_FAILED)
        ), anyString());
    }

    @Test
    void processPayment_emptyGatewayDecision_setsRandomDecision() {
        Payment payment = buildPayment("pay-1", "a1", "u1", 800L, Payment.STATUS_PENDING);
        payment.setGatewayDecision("");
        when(paymentRepository.getById("pay-1")).thenReturn(Optional.of(payment));

        paymentService.processPayment("pay-1");

        verify(paymentRepository).setGatewayDecision(eq("pay-1"), anyString());
    }

    @Test
    void processPayment_successDecision_publishesCorrectEvent() {
        Payment payment = buildPayment("pay-1", "a1", "u1", 4200L, Payment.STATUS_PROCESSING);
        payment.setGatewayDecision("success");
        when(paymentRepository.getById("pay-1")).thenReturn(Optional.of(payment));

        paymentService.processPayment("pay-1");

        ArgumentCaptor<PaymentProcessedEvent> captor = ArgumentCaptor.forClass(PaymentProcessedEvent.class);
        verify(eventPublisher).publishPaymentProcessed(captor.capture());
        PaymentProcessedEvent evt = captor.getValue();
        assertEquals("pay-1", evt.paymentId());
        assertEquals("a1", evt.auctionId());
        assertEquals("u1", evt.userId());
        assertEquals(4200L, evt.amount());
        assertNotNull(evt.processedAt());
    }

    // -----------------------------------------------------------------------
    // refundPayment
    // -----------------------------------------------------------------------

    @Test
    void refundPayment_notFound_throwsPaymentNotFoundException() {
        when(paymentRepository.getById("pay-missing")).thenReturn(Optional.empty());

        assertThrows(PaymentService.PaymentNotFoundException.class,
                () -> paymentService.refundPayment("pay-missing"));
    }

    @Test
    void refundPayment_statusPending_throwsInvalidStatus() {
        Payment payment = buildPayment("pay-1", "a1", "u1", 100L, Payment.STATUS_PENDING);
        when(paymentRepository.getById("pay-1")).thenReturn(Optional.of(payment));

        assertThrows(PaymentService.InvalidPaymentStatusException.class,
                () -> paymentService.refundPayment("pay-1"));
    }

    @Test
    void refundPayment_statusProcessing_throwsInvalidStatus() {
        Payment payment = buildPayment("pay-1", "a1", "u1", 100L, Payment.STATUS_PROCESSING);
        when(paymentRepository.getById("pay-1")).thenReturn(Optional.of(payment));

        assertThrows(PaymentService.InvalidPaymentStatusException.class,
                () -> paymentService.refundPayment("pay-1"));
    }

    @Test
    void refundPayment_statusRefunded_throwsInvalidStatus() {
        Payment payment = buildPayment("pay-1", "a1", "u1", 100L, Payment.STATUS_REFUNDED);
        when(paymentRepository.getById("pay-1")).thenReturn(Optional.of(payment));

        assertThrows(PaymentService.InvalidPaymentStatusException.class,
                () -> paymentService.refundPayment("pay-1"));
    }

    @Test
    void refundPayment_completedStatus_refundsAndPublishes() {
        Payment payment = buildPayment("pay-1", "a1", "u1", 3000L, Payment.STATUS_COMPLETED);
        when(paymentRepository.getById("pay-1")).thenReturn(Optional.of(payment));

        paymentService.refundPayment("pay-1");

        verify(paymentRepository).updateStatus("pay-1", Payment.STATUS_REFUNDED, "");

        ArgumentCaptor<RefundProcessedEvent> captor = ArgumentCaptor.forClass(RefundProcessedEvent.class);
        verify(eventPublisher).publishRefundProcessed(captor.capture());
        assertEquals("pay-1", captor.getValue().paymentId());
        assertEquals("a1", captor.getValue().auctionId());
        assertEquals("u1", captor.getValue().userId());
        assertEquals(3000L, captor.getValue().amount());
        assertNotNull(captor.getValue().refundedAt());
    }

    @Test
    void refundPayment_failedStatus_refundsAndPublishes() {
        Payment payment = buildPayment("pay-1", "a1", "u1", 1500L, Payment.STATUS_FAILED);
        when(paymentRepository.getById("pay-1")).thenReturn(Optional.of(payment));

        paymentService.refundPayment("pay-1");

        verify(paymentRepository).updateStatus("pay-1", Payment.STATUS_REFUNDED, "");
        verify(eventPublisher).publishRefundProcessed(any(RefundProcessedEvent.class));
    }

    // -----------------------------------------------------------------------
    // abandonPayment
    // -----------------------------------------------------------------------

    @Test
    void abandonPayment_notFound_throwsPaymentNotFoundException() {
        when(paymentRepository.getById("pay-missing")).thenReturn(Optional.empty());

        assertThrows(PaymentService.PaymentNotFoundException.class,
                () -> paymentService.abandonPayment("pay-missing", "test reason"));
    }

    @Test
    void abandonPayment_success_updatesToFailedAndPublishes() {
        Payment payment = buildPayment("pay-1", "a1", "u1", 2000L, Payment.STATUS_PROCESSING);
        when(paymentRepository.getById("pay-1")).thenReturn(Optional.of(payment));

        paymentService.abandonPayment("pay-1", "max retries exceeded");

        verify(paymentRepository).updateStatus("pay-1", Payment.STATUS_FAILED, "max retries exceeded");

        ArgumentCaptor<PaymentFailedEvent> captor = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(eventPublisher).publishPaymentFailed(captor.capture());
        assertEquals("pay-1", captor.getValue().paymentId());
        assertEquals("a1", captor.getValue().auctionId());
        assertEquals("u1", captor.getValue().userId());
        assertEquals(2000L, captor.getValue().amount());
        assertEquals("max retries exceeded", captor.getValue().reason());
    }

    // -----------------------------------------------------------------------
    // getPayment / getPaymentByAuction / getUserPayments
    // -----------------------------------------------------------------------

    @Test
    void getPayment_found_returnsPaymentResponse() {
        Payment payment = buildPayment("pay-1", "a1", "u1", 500L, Payment.STATUS_COMPLETED);
        when(paymentRepository.getById("pay-1")).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPayment("pay-1");

        assertEquals("pay-1", response.paymentId());
        assertEquals("a1", response.auctionId());
        assertEquals("u1", response.userId());
        assertEquals(500L, response.amount());
        assertEquals(Payment.STATUS_COMPLETED, response.status());
    }

    @Test
    void getPayment_notFound_throwsPaymentNotFoundException() {
        when(paymentRepository.getById("pay-missing")).thenReturn(Optional.empty());

        assertThrows(PaymentService.PaymentNotFoundException.class,
                () -> paymentService.getPayment("pay-missing"));
    }

    @Test
    void getPaymentByAuction_found_returnsPaymentResponse() {
        Payment payment = buildPayment("pay-1", "a1", "u1", 700L, Payment.STATUS_PENDING);
        when(paymentRepository.getByAuctionId("a1")).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentByAuction("a1");

        assertEquals("pay-1", response.paymentId());
        assertEquals("a1", response.auctionId());
    }

    @Test
    void getPaymentByAuction_notFound_throwsPaymentNotFoundException() {
        when(paymentRepository.getByAuctionId("a-missing")).thenReturn(Optional.empty());

        assertThrows(PaymentService.PaymentNotFoundException.class,
                () -> paymentService.getPaymentByAuction("a-missing"));
    }

    @Test
    void getUserPayments_returnsListOfResponses() {
        Payment p1 = buildPayment("pay-1", "a1", "u1", 100L, Payment.STATUS_COMPLETED);
        Payment p2 = buildPayment("pay-2", "a2", "u1", 200L, Payment.STATUS_PENDING);
        when(paymentRepository.getByUserId("u1")).thenReturn(List.of(p1, p2));

        List<PaymentResponse> result = paymentService.getUserPayments("u1");

        assertEquals(2, result.size());
        assertEquals("pay-1", result.get(0).paymentId());
        assertEquals("pay-2", result.get(1).paymentId());
    }

    @Test
    void getUserPayments_emptyList_returnsEmpty() {
        when(paymentRepository.getByUserId("u-none")).thenReturn(List.of());

        List<PaymentResponse> result = paymentService.getUserPayments("u-none");

        assertTrue(result.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private Payment buildPayment(String paymentId, String auctionId, String userId, long amount, String status) {
        Payment p = new Payment();
        p.setPaymentId(paymentId);
        p.setAuctionId(auctionId);
        p.setUserId(userId);
        p.setItemId("item-1");
        p.setShopId("shop-1");
        p.setAmount(amount);
        p.setStatus(status);
        p.setCreatedAt(Instant.now().toString());
        p.setUpdatedAt(Instant.now().toString());
        return p;
    }
}
