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

    @GetMapping("/strategy")
    public ResponseEntity<?> getStrategy() {
        return ResponseEntity.ok(Map.of("strategy", auctionService.getStrategy()));
    }

    @PutMapping("/strategy")
    public ResponseEntity<?> setStrategy(@RequestBody Map<String, String> body) {
        String strategy = body.get("strategy");
        if (strategy == null || (!strategy.equals("pessimistic") && !strategy.equals("optimistic") && !strategy.equals("queue"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid strategy"));
        }
        auctionService.setStrategy(strategy);
        return ResponseEntity.ok(Map.of("strategy", strategy));
    }
}
