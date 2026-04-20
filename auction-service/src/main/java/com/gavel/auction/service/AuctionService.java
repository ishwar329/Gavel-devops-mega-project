package com.gavel.auction.service;

import com.gavel.auction.concurrency.BidException;
import com.gavel.auction.concurrency.BidPlacement;
import com.gavel.auction.concurrency.PessimisticStrategy;
import com.gavel.auction.events.AuctionEventPublisher;
import com.gavel.auction.model.*;
import com.gavel.auction.repository.AuctionRepository;
import com.gavel.shared.events.AuctionClosedEvent;
import com.gavel.shared.events.BidPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AuctionService {

    private static final Logger log = LoggerFactory.getLogger(AuctionService.class);

    private final AuctionRepository repo;
    private final AuctionEventPublisher publisher;
    private final MetricsCollector metrics;
    private final PessimisticStrategy pessimistic;
    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool();

    public AuctionService(AuctionRepository repo,
                          AuctionEventPublisher publisher,
                          MetricsCollector metrics,
                          PessimisticStrategy pessimistic) {
        this.repo = repo;
        this.publisher = publisher;
        this.metrics = metrics;
        this.pessimistic = pessimistic;
    }

    public Auction createAuction(CreateAuctionRequest req, String sellerId) {
        if (req.itemId() == null || req.itemId().isBlank()) {
            throw new ValidationException("item_id is required");
        }
        if (req.shopId() == null || req.shopId().isBlank()) {
            throw new ValidationException("shop_id is required");
        }
        if (req.duration() < 1 || req.duration() > 10080) {
            throw new ValidationException("duration must be between 1 and 10080 minutes");
        }
        if (req.startBid() < 0) {
            throw new ValidationException("start_bid must be non-negative");
        }
        if (req.maxPrice() > 0 && req.maxPrice() <= req.startBid()) {
            throw new ValidationException("max_price must be greater than start_bid");
        }
        if (req.pickupStart() == null || req.pickupStart().isBlank()) {
            throw new ValidationException("pickup_start is required");
        }
        if (req.pickupEnd() == null || req.pickupEnd().isBlank()) {
            throw new ValidationException("pickup_end is required");
        }

        Instant now = Instant.now();
        Instant startTime;
        String status;

        if (req.scheduledStart() != null && !req.scheduledStart().isBlank()) {
            startTime = Instant.parse(req.scheduledStart());
            if (startTime.isAfter(now)) {
                status = "PENDING";
            } else {
                status = "OPEN";
                startTime = now;
            }
        } else {
            startTime = now;
            status = "OPEN";
        }

        Instant endTime = startTime.plus(Duration.ofMinutes(req.duration()));

        Instant pickupStart = Instant.parse(req.pickupStart());
        Instant pickupEnd = Instant.parse(req.pickupEnd());

        if (pickupStart.isBefore(endTime)) {
            throw new ValidationException("pickup_start must be after auction end time");
        }
        if (pickupEnd.isBefore(pickupStart)) {
            throw new ValidationException("pickup_end must be after pickup_start");
        }

        int quantity = req.quantity() > 0 ? req.quantity() : 1;

        Auction auction = new Auction();
        auction.setAuctionId(UUID.randomUUID().toString());
        auction.setItemId(req.itemId());
        auction.setItemTitle(req.itemTitle() != null ? req.itemTitle() : "");
        auction.setSellerId(sellerId);
        auction.setShopId(req.shopId());
        auction.setShopName(req.shopName() != null ? req.shopName() : "");
        auction.setShopLat(req.shopLat());
        auction.setShopLng(req.shopLng());
        auction.setRetailPrice(req.retailPrice());
        auction.setMaxPrice(req.maxPrice());
        auction.setMinIncrement(req.minIncrement());
        auction.setQuantity(quantity);
        auction.setImageUrl(req.imageUrl() != null ? req.imageUrl() : "");
        auction.setShopLogoUrl(req.shopLogoUrl() != null ? req.shopLogoUrl() : "");
        auction.setDescription(req.description() != null ? req.description() : "");
        auction.setCategory(req.category() != null ? req.category() : "");
        auction.setPickupStart(pickupStart.toString());
        auction.setPickupEnd(pickupEnd.toString());
        auction.setStartTime(startTime.toString());
        auction.setEndTime(endTime.toString());
        auction.setCurrentHighest(req.startBid());
        auction.setBidCount(0);
        auction.setHighestBidder("");
        auction.setStatus(status);
        auction.setVersion(0);

        repo.create(auction);
        return auction;
    }

    public Auction getAuction(String auctionId) {
        Auction a = repo.getById(auctionId);
        if (a == null) throw new NotFoundException("auction not found");
        return a;
    }

    public List<Auction> listAuctions(String status) {
        return repo.list(status);
    }

    public List<Auction> listAuctionsByShop(String shopId) {
        return repo.listByShop(shopId);
    }

    public List<Auction> listAuctionsNear(double lat, double lng, double radiusKm) {
        List<String> shopIds = repo.getShopIdsNear(lat, lng, radiusKm);
        if (shopIds.isEmpty()) return Collections.emptyList();

        List<Auction> results = new ArrayList<>();
        for (String shopId : shopIds) {
            List<Auction> shopAuctions = repo.listByShop(shopId);
            for (Auction a : shopAuctions) {
                if ("OPEN".equals(a.getStatus()) || "PENDING".equals(a.getStatus())) {
                    results.add(a);
                }
            }
        }
        return results;
    }

    public BidResult placeBid(String auctionId, String userId, long amount) {
        Auction auction = repo.getById(auctionId);
        if (auction == null) {
            metrics.recordRejected();
            throw new NotFoundException("auction not found");
        }
        if (userId.equals(auction.getSellerId())) {
            metrics.recordRejected();
            throw new ForbiddenException("seller cannot bid on own auction");
        }

        Instant start = Instant.now();
        BidPlacement placement;
        try {
            placement = pessimistic.tryPlaceBid(auctionId, amount, userId);
        } catch (BidException e) {
            metrics.recordRejected();
            throw e;
        }

        Duration latency = Duration.between(start, Instant.now());
        metrics.recordSuccessful(latency);

        String bidId = UUID.randomUUID().toString();
        String previousBidder = auction.getHighestBidder();
        long previousHighest = auction.getCurrentHighest();

        Instant acceptedAt = Instant.now();
        BidPlacedEvent event = new BidPlacedEvent(
                auctionId, bidId, auction.getItemId(), auction.getItemTitle(),
                auction.getShopId(), auction.getShopName(), userId, amount,
                previousHighest, previousBidder != null ? previousBidder : "",
                acceptedAt.toString(), acceptedAt.toString()
        );

        try {
            publisher.publishBidPlaced(event);
        } catch (Exception e) {
            log.error("Failed to publish bid event: {}", e.getMessage());
        }

        asyncExecutor.submit(() -> repo.updateDynamoWinner(auctionId, userId, amount));

        return new BidResult(bidId, auctionId, amount, placement.newFloor(), "ACCEPTED");
    }

    public void closeAuction(String auctionId) {
        Auction auction = repo.getById(auctionId);
        if (auction == null) throw new NotFoundException("auction not found");

        AuctionRepository.CloseResult closeResult = repo.atomicCloseAndReadWinner(auctionId);
        Map<String, Long> winners = closeResult.winners();

        if (winners == null || winners.isEmpty()) {
            Map<String, Long> dynamoWinners = repo.getDynamoWinners(auctionId);
            if (dynamoWinners != null && !dynamoWinners.isEmpty()) {
                winners = dynamoWinners;
            }
        }

        String winnerId = "";
        long winningBid = 0;
        if (winners != null && !winners.isEmpty()) {
            var first = winners.entrySet().iterator().next();
            winnerId = first.getKey();
            winningBid = first.getValue();
        }

        AuctionClosedEvent event = new AuctionClosedEvent(
                auctionId, winnerId, winningBid, winners,
                auction.getQuantity(), auction.getItemId(), auction.getItemTitle(),
                auction.getShopId(), Instant.now().toString()
        );

        try {
            publisher.publishAuctionClosed(event);
        } catch (Exception e) {
            repo.rollbackClose(auctionId);
            throw new RuntimeException("Failed to publish auction closed event", e);
        }

        asyncExecutor.submit(() -> repo.persistClosedState(auctionId));
        asyncExecutor.submit(() -> repo.cleanupRedis(auctionId));
    }

    public void openAuction(String auctionId) {
        repo.open(auctionId);
    }

    public BidMetrics getMetrics() {
        return metrics.snapshot();
    }

    public void resetMetrics() {
        metrics.reset();
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String msg) { super(msg); }
    }

    public static class ValidationException extends RuntimeException {
        public ValidationException(String msg) { super(msg); }
    }

    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String msg) { super(msg); }
    }
}
