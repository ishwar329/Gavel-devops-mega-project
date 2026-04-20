package com.gavel.bid.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.bid.model.Bid;
import com.gavel.bid.service.BidService;
import com.gavel.shared.events.AuctionClosedEvent;
import com.gavel.shared.events.BidPlacedEvent;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class BidEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(BidEventConsumer.class);

    private static final String STREAM_BID_PLACED = "bid:placed";
    private static final String STREAM_AUCTION_CLOSED = "auction:closed";
    private static final String CONSUMER_GROUP = "bid-service";
    private static final Duration PENDING_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration RECLAIM_INTERVAL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;
    private final BidService bidService;
    private final ObjectMapper objectMapper;
    private final String consumerId = UUID.randomUUID().toString();
    private final int numWorkers;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public BidEventConsumer(StringRedisTemplate redisTemplate,
                            BidService bidService,
                            ObjectMapper objectMapper,
                            @Value("${bid.consumer.workers:10}") int numWorkers) {
        this.redisTemplate = redisTemplate;
        this.bidService = bidService;
        this.objectMapper = objectMapper;
        this.numWorkers = numWorkers;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        running.set(true);
        executor.submit(() -> runStream(STREAM_BID_PLACED));
        executor.submit(() -> runStream(STREAM_AUCTION_CLOSED));
        log.info("Bid event consumer started (streams={}, {}, group={}, consumer={}, workers={})",
                STREAM_BID_PLACED, STREAM_AUCTION_CLOSED, CONSUMER_GROUP, consumerId, numWorkers);
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
        log.info("Bid event consumer stopped");
    }

    @SuppressWarnings("unchecked")
    private void runStream(String stream) {
        ensureConsumerGroup(stream);

        Semaphore semaphore = new Semaphore(numWorkers);

        executor.submit(() -> reclaimLoop(stream, semaphore));

        while (running.get()) {
            try {
                List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                        Consumer.from(CONSUMER_GROUP, consumerId),
                        StreamReadOptions.empty().count(numWorkers).block(Duration.ofSeconds(2)),
                        StreamOffset.create(stream, ReadOffset.lastConsumed())
                );
                if (records != null) {
                    for (MapRecord<String, Object, Object> record : records) {
                        dispatch(stream, record, semaphore);
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.error("Error reading stream {}: {}", stream, e.getMessage());
                    sleep(1000);
                }
            }
        }
        log.info("Stream {} consumer loop exited", stream);
    }

    private void dispatch(String stream, MapRecord<String, Object, Object> record, Semaphore semaphore) {
        Object payloadObj = record.getValue().get("payload");
        if (payloadObj == null) {
            log.warn("Missing payload in message {} on {}, discarding", record.getId(), stream);
            redisTemplate.opsForStream().acknowledge(stream, CONSUMER_GROUP, record.getId());
            return;
        }
        String payload = payloadObj.toString();

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        executor.submit(() -> {
            try {
                boolean success = handleMessage(stream, payload);
                if (success) {
                    redisTemplate.opsForStream().acknowledge(stream, CONSUMER_GROUP, record.getId());
                }
            } catch (Exception e) {
                log.error("Handler error on {} message {}: {}", stream, record.getId(), e.getMessage());
            } finally {
                semaphore.release();
            }
        });
    }

    private boolean handleMessage(String stream, String payload) {
        if (STREAM_BID_PLACED.equals(stream)) {
            return handleBidPlaced(payload);
        } else if (STREAM_AUCTION_CLOSED.equals(stream)) {
            return handleAuctionClosed(payload);
        }
        return true;
    }

    private boolean handleBidPlaced(String payload) {
        BidPlacedEvent event;
        try {
            event = objectMapper.readValue(payload, BidPlacedEvent.class);
        } catch (Exception e) {
            log.error("Unmarshal bid_placed error (discarding): {}", e.getMessage());
            return true;
        }

        Instant timestamp;
        try {
            timestamp = Instant.parse(event.timestamp());
        } catch (Exception e) {
            timestamp = Instant.now();
        }

        Bid bid = new Bid(
                event.bidId(),
                event.auctionId(),
                event.userId(),
                event.itemTitle(),
                event.shopId(),
                event.shopName(),
                event.amount(),
                timestamp,
                "ACCEPTED"
        );

        try {
            bidService.recordBid(bid);
            return true;
        } catch (Exception e) {
            log.error("Failed to record bid {}: {}", event.bidId(), e.getMessage());
            return false;
        }
    }

    private boolean handleAuctionClosed(String payload) {
        AuctionClosedEvent event;
        try {
            event = objectMapper.readValue(payload, AuctionClosedEvent.class);
        } catch (Exception e) {
            log.error("Unmarshal auction_closed error (discarding): {}", e.getMessage());
            return true;
        }

        Map<String, Long> winners = event.winners();
        String singleWinnerId = event.winnerId();

        if ((winners == null || winners.isEmpty()) && (singleWinnerId == null || singleWinnerId.isEmpty())) {
            log.info("Auction {} closed with no winner", event.auctionId());
            return true;
        }

        boolean allSucceeded = true;
        if (winners != null && !winners.isEmpty()) {
            for (String winnerId : winners.keySet()) {
                try {
                    bidService.markWinnerBid(event.auctionId(), winnerId);
                    log.info("Marked winning bid for auction {}, winner {}", event.auctionId(), winnerId);
                } catch (Exception e) {
                    log.error("Failed to mark winner bid for auction {}, winner {}: {}",
                            event.auctionId(), winnerId, e.getMessage());
                    allSucceeded = false;
                }
            }
        } else {
            try {
                bidService.markWinnerBid(event.auctionId(), singleWinnerId);
                log.info("Marked winning bid for auction {}, winner {}", event.auctionId(), singleWinnerId);
            } catch (Exception e) {
                log.error("Failed to mark winner bid for auction {}, winner {}: {}",
                        event.auctionId(), singleWinnerId, e.getMessage());
                allSucceeded = false;
            }
        }
        return allSucceeded;
    }

    @SuppressWarnings("unchecked")
    private void reclaimLoop(String stream, Semaphore semaphore) {
        while (running.get()) {
            sleep(RECLAIM_INTERVAL.toMillis());
            if (!running.get()) break;

            try {
                List<MapRecord<String, Object, Object>> pending = redisTemplate.opsForStream().read(
                        Consumer.from(CONSUMER_GROUP, consumerId),
                        StreamReadOptions.empty().count(numWorkers),
                        StreamOffset.create(stream, ReadOffset.from("0"))
                );
                if (pending != null) {
                    for (MapRecord<String, Object, Object> record : pending) {
                        log.info("Reclaiming pending message {} on {}", record.getId(), stream);
                        dispatch(stream, record, semaphore);
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.error("Reclaim error on {}: {}", stream, e.getMessage());
                }
            }
        }
    }

    private void ensureConsumerGroup(String stream) {
        try {
            redisTemplate.opsForStream().createGroup(stream, ReadOffset.from("$"), CONSUMER_GROUP);
        } catch (Exception e) {
            log.debug("Consumer group {} may already exist for {}: {}", CONSUMER_GROUP, stream, e.getMessage());
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
