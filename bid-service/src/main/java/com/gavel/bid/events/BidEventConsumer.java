package com.gavel.bid.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.bid.model.Bid;
import com.gavel.bid.service.BidService;
import com.gavel.shared.events.AuctionClosedEvent;
import com.gavel.shared.events.BidPlacedEvent;
import com.gavel.shared.kafka.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class BidEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(BidEventConsumer.class);

    private final BidService bidService;
    private final ObjectMapper objectMapper;

    public BidEventConsumer(BidService bidService, ObjectMapper objectMapper) {
        this.bidService = bidService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.BID_PLACED, groupId = "bid-service")
    public void onBidPlaced(String payload) {
        handleBidPlaced(payload);
    }

    @KafkaListener(topics = Topics.AUCTION_CLOSED, groupId = "bid-service")
    public void onAuctionClosed(String payload) {
        handleAuctionClosed(payload);
    }

    boolean handleBidPlaced(String payload) {
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

    boolean handleAuctionClosed(String payload) {
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
}
