package com.gavel.notification.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.notification.hub.NotificationHub;
import com.gavel.notification.model.StoredNotification;
import com.gavel.shared.events.AuctionClosedEvent;
import com.gavel.shared.events.BidPlacedEvent;
import com.gavel.shared.kafka.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class NotificationStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationStreamConsumer.class);

    private final NotificationHub hub;
    private final ObjectMapper objectMapper;

    public NotificationStreamConsumer(NotificationHub hub, ObjectMapper objectMapper) {
        this.hub = hub;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.BID_PLACED, groupId = "notification-service")
    public void onBidPlaced(String payload) {
        handleBidPlaced(payload);
    }

    @KafkaListener(topics = Topics.AUCTION_CLOSED, groupId = "notification-service")
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

        Map<String, Object> broadcastMsg = new LinkedHashMap<>();
        broadcastMsg.put("type", "bid_placed");
        broadcastMsg.put("auction_id", event.auctionId());
        broadcastMsg.put("user_id", event.userId());
        broadcastMsg.put("amount", event.amount());
        broadcastMsg.put("previous_bidder", event.previousBidder());
        broadcastMsg.put("item_title", event.itemTitle());
        broadcastMsg.put("message", String.format("You've been outbid on %s! Current: $%.2f",
                event.itemTitle(), event.amount() / 100.0));
        broadcastMsg.put("bid_accepted_at", event.bidAcceptedAt());
        broadcastMsg.put("timestamp", event.timestamp());

        hub.broadcast(event.auctionId(), broadcastMsg, event.bidAcceptedAt());

        if (event.previousBidder() != null && !event.previousBidder().isEmpty()) {
            StoredNotification notification = new StoredNotification(
                    "outbid:" + event.auctionId(),
                    "outbid",
                    event.auctionId(),
                    event.itemTitle(),
                    String.format("You've been outbid on %s! Current: $%.2f",
                            event.itemTitle(), event.amount() / 100.0),
                    "/auction/" + event.auctionId(),
                    event.amount(),
                    Instant.now().toEpochMilli(),
                    false
            );
            hub.storeAndPushNotification(event.previousBidder(), notification);
        }

        return true;
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
        long winningBid = event.winningBid();

        if (winners != null && !winners.isEmpty()) {
            for (Map.Entry<String, Long> entry : winners.entrySet()) {
                winningBid = entry.getValue();
                singleWinnerId = entry.getKey();
            }
        }

        Map<String, Object> broadcastMsg = new LinkedHashMap<>();
        broadcastMsg.put("type", "auction_closed");
        broadcastMsg.put("auction_id", event.auctionId());
        broadcastMsg.put("winner_id", singleWinnerId != null ? singleWinnerId : "");
        broadcastMsg.put("winning_bid", winningBid);
        broadcastMsg.put("message", String.format("Auction closed! Winning bid: $%.2f", winningBid / 100.0));
        broadcastMsg.put("closed_at", event.closedAt());

        hub.broadcast(event.auctionId(), broadcastMsg, null);

        if (winners != null && !winners.isEmpty()) {
            for (Map.Entry<String, Long> entry : winners.entrySet()) {
                pushWonNotification(event, entry.getKey(), entry.getValue());
            }
        } else if (singleWinnerId != null && !singleWinnerId.isEmpty()) {
            pushWonNotification(event, singleWinnerId, winningBid);
        }

        return true;
    }

    private void pushWonNotification(AuctionClosedEvent event, String winnerId, long amount) {
        StoredNotification notification = new StoredNotification(
                "won:" + event.auctionId(),
                "won",
                event.auctionId(),
                event.itemTitle(),
                String.format("You won the auction for %s! Winning bid: $%.2f",
                        event.itemTitle(), amount / 100.0),
                "/auction/" + event.auctionId(),
                amount,
                Instant.now().toEpochMilli(),
                false
        );
        hub.storeAndPushNotification(winnerId, notification);
    }
}
