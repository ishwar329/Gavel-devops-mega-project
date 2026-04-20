package com.gavel.auction.closer;

import com.gavel.auction.model.Auction;
import com.gavel.auction.service.AuctionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class AuctionCloser {

    private static final Logger log = LoggerFactory.getLogger(AuctionCloser.class);

    private final AuctionService auctionService;

    public AuctionCloser(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @Scheduled(fixedRate = 1000)
    public void checkExpiredAndPending() {
        checkExpired();
        checkPending();
    }

    private void checkExpired() {
        try {
            List<Auction> openAuctions = auctionService.listAuctions("OPEN");
            Instant now = Instant.now();
            for (Auction a : openAuctions) {
                try {
                    Instant endTime = Instant.parse(a.getEndTime());
                    if (endTime.isBefore(now)) {
                        auctionService.closeAuction(a.getAuctionId());
                        log.info("Auto-closed expired auction {}", a.getAuctionId());
                    }
                } catch (Exception e) {
                    log.error("Error closing auction {}: {}", a.getAuctionId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error in expired auction check: {}", e.getMessage());
        }
    }

    private void checkPending() {
        try {
            List<Auction> pendingAuctions = auctionService.listAuctions("PENDING");
            Instant now = Instant.now();
            for (Auction a : pendingAuctions) {
                try {
                    Instant startTime = Instant.parse(a.getStartTime());
                    if (!startTime.isAfter(now)) {
                        auctionService.openAuction(a.getAuctionId());
                        log.info("Opened pending auction {}", a.getAuctionId());
                    }
                } catch (Exception e) {
                    log.error("Error opening auction {}: {}", a.getAuctionId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error in pending auction check: {}", e.getMessage());
        }
    }
}
