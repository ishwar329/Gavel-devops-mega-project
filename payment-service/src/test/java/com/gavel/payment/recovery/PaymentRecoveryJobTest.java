package com.gavel.payment.recovery;

import com.gavel.payment.model.Payment;
import com.gavel.payment.repository.PaymentRepository;
import com.gavel.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentRecoveryJobTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentRecoveryJob paymentRecoveryJob;

    // -----------------------------------------------------------------------
    // recover
    // -----------------------------------------------------------------------

    @Test
    void recover_noStuckPayments_returnsEarly() {
        when(paymentRepository.scanStuck(any(Instant.class))).thenReturn(List.of());

        paymentRecoveryJob.recover();

        verify(paymentService, never()).processPayment(anyString());
        verify(paymentService, never()).abandonPayment(anyString(), anyString());
    }

    @Test
    void recover_retryCountAtMax_abandonsPayment() {
        Payment stuck = buildStuckPayment("pay-1", 1); // MAX_RETRIES = 1
        when(paymentRepository.scanStuck(any(Instant.class))).thenReturn(List.of(stuck));

        paymentRecoveryJob.recover();

        verify(paymentService).abandonPayment("pay-1", "payment could not be completed after retries");
        verify(paymentService, never()).processPayment(anyString());
        verify(paymentRepository, never()).incrementRetryCount(anyString());
    }

    @Test
    void recover_retryCountAboveMax_abandonsPayment() {
        Payment stuck = buildStuckPayment("pay-1", 5); // well above MAX_RETRIES
        when(paymentRepository.scanStuck(any(Instant.class))).thenReturn(List.of(stuck));

        paymentRecoveryJob.recover();

        verify(paymentService).abandonPayment("pay-1", "payment could not be completed after retries");
        verify(paymentService, never()).processPayment(anyString());
    }

    @Test
    void recover_retryCountBelowMax_incrementsAndRetries() {
        Payment stuck = buildStuckPayment("pay-1", 0); // below MAX_RETRIES
        when(paymentRepository.scanStuck(any(Instant.class))).thenReturn(List.of(stuck));

        paymentRecoveryJob.recover();

        verify(paymentRepository).incrementRetryCount("pay-1");
        verify(paymentService).processPayment("pay-1");
        verify(paymentService, never()).abandonPayment(anyString(), anyString());
    }

    @Test
    void recover_abandonThrows_catchesAndContinues() {
        Payment stuck1 = buildStuckPayment("pay-1", 1); // will abandon
        Payment stuck2 = buildStuckPayment("pay-2", 0); // will retry
        when(paymentRepository.scanStuck(any(Instant.class))).thenReturn(List.of(stuck1, stuck2));
        doThrow(new RuntimeException("abandon error"))
                .when(paymentService).abandonPayment("pay-1", "payment could not be completed after retries");

        paymentRecoveryJob.recover();

        // Despite exception on pay-1, pay-2 should still be processed
        verify(paymentRepository).incrementRetryCount("pay-2");
        verify(paymentService).processPayment("pay-2");
    }

    @Test
    void recover_processThrows_catchesAndContinues() {
        Payment stuck1 = buildStuckPayment("pay-1", 0); // will retry
        Payment stuck2 = buildStuckPayment("pay-2", 0); // will retry
        when(paymentRepository.scanStuck(any(Instant.class))).thenReturn(List.of(stuck1, stuck2));
        doThrow(new RuntimeException("process error"))
                .when(paymentService).processPayment("pay-1");

        paymentRecoveryJob.recover();

        // Both should be attempted
        verify(paymentRepository).incrementRetryCount("pay-1");
        verify(paymentRepository).incrementRetryCount("pay-2");
        verify(paymentService).processPayment("pay-1");
        verify(paymentService).processPayment("pay-2");
    }

    @Test
    void recover_mixedPayments_handlesEachCorrectly() {
        Payment abandoned = buildStuckPayment("pay-abandon", 2);
        Payment retried = buildStuckPayment("pay-retry", 0);
        when(paymentRepository.scanStuck(any(Instant.class))).thenReturn(List.of(abandoned, retried));

        paymentRecoveryJob.recover();

        verify(paymentService).abandonPayment("pay-abandon", "payment could not be completed after retries");
        verify(paymentRepository, never()).incrementRetryCount("pay-abandon");

        verify(paymentRepository).incrementRetryCount("pay-retry");
        verify(paymentService).processPayment("pay-retry");
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private Payment buildStuckPayment(String paymentId, int retryCount) {
        Payment p = new Payment();
        p.setPaymentId(paymentId);
        p.setAuctionId("a1");
        p.setUserId("u1");
        p.setItemId("item-1");
        p.setShopId("shop-1");
        p.setAmount(1000L);
        p.setStatus(Payment.STATUS_PROCESSING);
        p.setRetryCount(retryCount);
        p.setCreatedAt(Instant.now().toString());
        p.setUpdatedAt(Instant.now().toString());
        return p;
    }
}
