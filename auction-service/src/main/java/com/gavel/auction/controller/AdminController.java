package com.gavel.auction.controller;

import com.gavel.auction.model.BidMetrics;
import com.gavel.auction.service.AuctionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AuctionService auctionService;

    public AdminController(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @GetMapping("/metrics")
    public ResponseEntity<BidMetrics> getMetrics() {
        return ResponseEntity.ok(auctionService.getMetrics());
    }

    @PostMapping("/metrics/reset")
    public ResponseEntity<?> resetMetrics() {
        auctionService.resetMetrics();
        return ResponseEntity.ok(Map.of("message", "metrics reset"));
    }
}
