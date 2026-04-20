package com.gavel.bid.controller;

import com.gavel.bid.model.Bid;
import com.gavel.bid.service.BidService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class BidController {

    private final BidService bidService;

    public BidController(BidService bidService) {
        this.bidService = bidService;
    }

    @GetMapping("/auctions/{id}/bids")
    public ResponseEntity<Map<String, List<Bid>>> getAuctionBids(@PathVariable String id) {
        List<Bid> bids = bidService.getAuctionBids(id);
        return ResponseEntity.ok(Map.of("bids", bids));
    }

    @GetMapping("/users/{userId}/bids")
    public ResponseEntity<Map<String, List<Bid>>> getUserBids(@PathVariable String userId) {
        List<Bid> bids = bidService.getUserBids(userId);
        return ResponseEntity.ok(Map.of("bids", bids));
    }
}
