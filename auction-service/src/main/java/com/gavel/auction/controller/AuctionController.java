package com.gavel.auction.controller;

import com.gavel.auction.concurrency.BidException;
import com.gavel.auction.model.*;
import com.gavel.auction.service.AuctionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class AuctionController {

    private final AuctionService auctionService;

    public AuctionController(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @PostMapping("/auctions")
    public ResponseEntity<?> createAuction(@RequestBody CreateAuctionRequest request,
                                           Authentication authentication) {
        String role = getRole(authentication);
        if (!"seller".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "only sellers can create auctions"));
        }

        String sellerId = (String) authentication.getPrincipal();
        try {
            Auction auction = auctionService.createAuction(request, sellerId);
            return ResponseEntity.status(HttpStatus.CREATED).body(auction);
        } catch (AuctionService.ValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/auctions/{id}")
    public ResponseEntity<?> getAuction(@PathVariable String id) {
        try {
            Auction auction = auctionService.getAuction(id);
            return ResponseEntity.ok(auction);
        } catch (AuctionService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/auctions")
    public ResponseEntity<?> listAuctions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(name = "radius_km", required = false) Double radiusKm,
            @RequestParam(required = false) String q) {

        List<Auction> auctions;
        if (lat != null && lng != null && radiusKm != null) {
            auctions = auctionService.listAuctionsNear(lat, lng, radiusKm);
        } else {
            auctions = auctionService.listAuctions(status);
        }

        if (q != null && !q.isBlank()) {
            String query = q.toLowerCase();
            auctions = auctions.stream().filter(a ->
                    (a.getItemTitle() != null && a.getItemTitle().toLowerCase().contains(query)) ||
                    (a.getDescription() != null && a.getDescription().toLowerCase().contains(query)) ||
                    (a.getShopName() != null && a.getShopName().toLowerCase().contains(query))
            ).toList();
        }

        return ResponseEntity.ok(Map.of("auctions", auctions));
    }

    @GetMapping("/shops/{shopId}/auctions")
    public ResponseEntity<?> listShopAuctions(@PathVariable String shopId) {
        List<Auction> auctions = auctionService.listAuctionsByShop(shopId);
        return ResponseEntity.ok(Map.of("auctions", auctions));
    }

    @PostMapping("/auctions/{id}/bid")
    public ResponseEntity<?> placeBid(@PathVariable String id,
                                      @RequestBody PlaceBidRequest request,
                                      Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        try {
            BidResult result = auctionService.placeBid(id, userId, request.amount());
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (AuctionService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (AuctionService.ForbiddenException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (BidException e) {
            return switch (e.getCode()) {
                case BidException.NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", e.getMessage()));
                case BidException.NOT_OPEN -> ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", e.getMessage()));
                case BidException.BID_TOO_LOW, BidException.EXCEEDS_MAX, BidException.INCREMENT_TOO_SMALL ->
                        ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "threshold", e.getThreshold()));
                default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", e.getMessage()));
            };
        }
    }

    @PostMapping("/auctions/{id}/close")
    public ResponseEntity<?> closeAuction(@PathVariable String id, Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        try {
            Auction auction = auctionService.getAuction(id);
            if (!userId.equals(auction.getSellerId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "only the seller can close this auction"));
            }
            auctionService.closeAuction(id);
            return ResponseEntity.ok(Map.of("message", "auction closed"));
        } catch (AuctionService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private String getRole(Authentication authentication) {
        if (authentication.getDetails() instanceof Map) {
            Map<String, String> details = (Map<String, String>) authentication.getDetails();
            return details.getOrDefault("role", "");
        }
        return "";
    }
}
