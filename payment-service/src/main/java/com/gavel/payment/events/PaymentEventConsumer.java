package com.gavel.payment.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.payment.service.PaymentService;
import com.gavel.shared.events.AuctionClosedEvent;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private static final String STREAM_AUCTION_CLOSED = "auction:closed";
    private static final String CONSUMER_GROUP = "payment-service";
    private static final Duration PENDING_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration RECLAIM_INTERVAL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;
    private final String consumerId = UUID.randomUUID().toString();
    private final int numWorkers;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public PaymentEventConsumer(StringRedisTemplate redisTemplate,
                                PaymentService paymentService,
                                ObjectMapper objectMapper,
                                @Value("${payment.consumer.workers:10}") int numWorkers) {
        this.redisTemplate = redisTemplate;
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
        this.numWorkers = numWorkers;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        running.set(true);
        executor.submit(this::runStream);
        log.info("Payment event consumer started (stream={}, group={}, consumer={}, workers={})",
                STREAM_AUCTION_CLOSED, CONSUMER_GROUP, consumerId, numWorkers);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Payment event consumer stopped");
    }

    @SuppressWarnings("unchecked")
    private void runStream() {
        ensureConsumerGroup();
        Semaphore semaphore = new Semaphore(numWorkers);

        executor.submit(() -> reclaimLoop(semaphore));

        while (running.get()) {
            try {
                List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                        Consumer.from(CONSUMER_GROUP, consumerId),
                        StreamReadOptions.empty().count(numWorkers).block(Duration.ofSeconds(2)),
                        StreamOffset.create(STREAM_AUCTION_CLOSED, ReadOffset.lastConsumed())
                );
                if (records != null) {
                    for (MapRecord<String, Object, Object> record : records) {
                        dispatch(record, semaphore);
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.error("Error reading stream {}: {}", STREAM_AUCTION_CLOSED, e.getMessage());
                    sleep(1000);
                }
            }
        }
        log.info("Payment consumer stream loop exited");
    }

    private void dispatch(MapRecord<String, Object, Object> record, Semaphore semaphore) {
        Object payloadObj = record.getValue().get("payload");
        if (payloadObj == null) {
            log.warn("Missing payload in message {} on {}, discarding", record.getId(), STREAM_AUCTION_CLOSED);
            redisTemplate.opsForStream().acknowledge(STREAM_AUCTION_CLOSED, CONSUMER_GROUP, record.getId());
            return;
        }
        String payload = payloadObj.toString();

        AuctionClosedEvent event;
        try {
            event = objectMapper.readValue(payload, AuctionClosedEvent.class);
        } catch (Exception e) {
            log.error("Unmarshal error in message {} (discarding): {}", record.getId(), e.getMessage());
            redisTemplate.opsForStream().acknowledge(STREAM_AUCTION_CLOSED, CONSUMER_GROUP, record.getId());
            return;
        }

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        executor.submit(() -> {
            try {
                paymentService.initiatePayment(event);
                redisTemplate.opsForStream().acknowledge(STREAM_AUCTION_CLOSED, CONSUMER_GROUP, record.getId());
            } catch (Exception e) {
                log.error("Initiate payment error for auction {}: {}", event.auctionId(), e.getMessage());
            } finally {
                semaphore.release();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void reclaimLoop(Semaphore semaphore) {
        while (running.get()) {
            sleep(RECLAIM_INTERVAL.toMillis());
            if (!running.get()) break;

            try {
                List<MapRecord<String, Object, Object>> pending = redisTemplate.opsForStream().read(
                        Consumer.from(CONSUMER_GROUP, consumerId),
                        StreamReadOptions.empty().count(numWorkers),
                        StreamOffset.create(STREAM_AUCTION_CLOSED, ReadOffset.from("0"))
                );
                if (pending != null) {
                    for (MapRecord<String, Object, Object> record : pending) {
                        log.info("Reclaiming pending message {} on {}", record.getId(), STREAM_AUCTION_CLOSED);
                        dispatch(record, semaphore);
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.error("Reclaim error on {}: {}", STREAM_AUCTION_CLOSED, e.getMessage());
                }
            }
        }
    }

    private void ensureConsumerGroup() {
        try {
            redisTemplate.opsForStream().createGroup(STREAM_AUCTION_CLOSED, ReadOffset.from("$"), CONSUMER_GROUP);
        } catch (Exception e) {
            log.debug("Consumer group {} may already exist: {}", CONSUMER_GROUP, e.getMessage());
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
