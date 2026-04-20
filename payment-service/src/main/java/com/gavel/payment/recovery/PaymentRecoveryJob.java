package com.gavel.payment.recovery;

import com.gavel.payment.model.Payment;
import com.gavel.payment.repository.PaymentRepository;
import com.gavel.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class PaymentRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentRecoveryJob.class);

    private static final Duration STUCK_THRESHOLD = Duration.ofMinutes(5);
    private static final int MAX_RETRIES = 1;

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    public PaymentRecoveryJob(PaymentRepository paymentRepository, PaymentService paymentService) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
    }

    @Scheduled(fixedRate = 120000)
    public void recover() {
        Instant cutoff = Instant.now().minus(STUCK_THRESHOLD);
        List<Payment> stuck = paymentRepository.scanStuck(cutoff);

        if (stuck.isEmpty()) return;

        log.info("Payment recovery: found {} stuck payment(s)", stuck.size());
        for (Payment payment : stuck) {
            if (payment.getRetryCount() >= MAX_RETRIES) {
                log.info("Payment recovery: abandoning payment {} after {} attempt(s)",
                        payment.getPaymentId(), payment.getRetryCount());
                try {
                    paymentService.abandonPayment(payment.getPaymentId(),
                            "payment could not be completed after retries");
                } catch (Exception e) {
                    log.error("Payment recovery: abandon failed for {}: {}",
                            payment.getPaymentId(), e.getMessage());
                }
                continue;
            }

            paymentRepository.incrementRetryCount(payment.getPaymentId());
            log.info("Payment recovery: retrying payment {} (attempt {}/{}, status={})",
                    payment.getPaymentId(), payment.getRetryCount() + 1, MAX_RETRIES, payment.getStatus());
            try {
                paymentService.processPayment(payment.getPaymentId());
            } catch (Exception e) {
                log.error("Payment recovery: retry failed for {}: {}",
                        payment.getPaymentId(), e.getMessage());
            }
        }
    }
}
