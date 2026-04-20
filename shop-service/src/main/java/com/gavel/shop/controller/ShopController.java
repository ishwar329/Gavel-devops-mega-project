package com.gavel.shop.controller;

import com.gavel.shop.model.*;
import com.gavel.shop.service.ShopService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class ShopController {

    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @PostMapping("/shops")
    public ResponseEntity<?> createShop(@Valid @RequestBody CreateShopRequest request,
                                        Authentication auth) {
        if (!"seller".equals(getRole(auth))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "sellers only"));
        }
        Shop shop = shopService.createShop(request, getUserId(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(shop);
    }

    @PutMapping("/shops/{shopId}")
    public ResponseEntity<?> updateShop(@PathVariable String shopId,
                                        @RequestBody UpdateShopRequest request,
                                        Authentication auth) {
        if (!"seller".equals(getRole(auth))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "sellers only"));
        }
        Shop shop = shopService.updateShop(shopId, request, getUserId(auth));
        return ResponseEntity.ok(shop);
    }

    @GetMapping("/shops/{shopId}")
    public ResponseEntity<?> getShop(@PathVariable String shopId) {
        Shop shop = shopService.getShop(shopId);
        return ResponseEntity.ok(shop);
    }

    @GetMapping("/items/{itemId}")
    public ResponseEntity<?> getItem(@PathVariable String itemId) {
        Item item = shopService.getItem(itemId);
        return ResponseEntity.ok(item);
    }

    @PostMapping("/shops/{shopId}/items")
    public ResponseEntity<?> createItem(@PathVariable String shopId,
                                        @Valid @RequestBody CreateItemRequest request,
                                        Authentication auth) {
        if (!"seller".equals(getRole(auth))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "sellers only"));
        }
        Item item = shopService.createItem(shopId, request, getUserId(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    @GetMapping("/shops/{shopId}/items")
    public ResponseEntity<?> listItems(@PathVariable String shopId) {
        return ResponseEntity.ok(Map.of("items", shopService.listItems(shopId)));
    }

    @GetMapping("/sellers/{userId}/shops")
    public ResponseEntity<?> listSellerShops(@PathVariable String userId) {
        return ResponseEntity.ok(Map.of("shops", shopService.listSellerShops(userId)));
    }

    @PostMapping("/shops/{shopId}/reviews")
    public ResponseEntity<?> createReview(@PathVariable String shopId,
                                          @Valid @RequestBody CreateReviewRequest request,
                                          @RequestHeader(value = "Authorization", required = false) String authHeader,
                                          Authentication auth) {
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        Review review = shopService.createReview(shopId, request,
                getUserId(auth), getUsername(auth), token);
        return ResponseEntity.status(HttpStatus.CREATED).body(review);
    }

    @GetMapping("/shops/{shopId}/reviews")
    public ResponseEntity<?> listReviews(@PathVariable String shopId) {
        return ResponseEntity.ok(shopService.listReviews(shopId));
    }

    @PostMapping("/shops/{shopId}/reviews/{reviewId}/reply")
    public ResponseEntity<?> replyToReview(@PathVariable String shopId,
                                           @PathVariable String reviewId,
                                           @Valid @RequestBody ReplyRequest request,
                                           Authentication auth) {
        if (!"seller".equals(getRole(auth))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "sellers only"));
        }
        Review review = shopService.replyToReview(shopId, reviewId, request.reply(), getUserId(auth));
        return ResponseEntity.ok(review);
    }

    private String getUserId(Authentication auth) {
        return (String) auth.getPrincipal();
    }

    @SuppressWarnings("unchecked")
    private String getRole(Authentication auth) {
        Map<String, String> details = (Map<String, String>) auth.getDetails();
        return details.get("role");
    }

    @SuppressWarnings("unchecked")
    private String getUsername(Authentication auth) {
        Map<String, String> details = (Map<String, String>) auth.getDetails();
        return details.get("username");
    }
}
