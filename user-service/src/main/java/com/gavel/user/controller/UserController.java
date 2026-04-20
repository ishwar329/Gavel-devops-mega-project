package com.gavel.user.controller;

import com.gavel.user.model.*;
import com.gavel.user.service.UserService;
import com.gavel.user.service.UserService.RegisterResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.Map;

@RestController
public class UserController {

    private final UserService userService;
    private final RestClient restClient;

    public UserController(UserService userService,
                          @Value("${bid.service.url:http://bid:8084}") String bidServiceUrl) {
        this.userService = userService;
        this.restClient = RestClient.builder().baseUrl(bidServiceUrl).build();
    }

    @PostMapping("/users")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResult result = userService.register(request);
        if (result.isSuccess()) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("user_id", result.userId(), "role", result.role()));
        }
        return switch (result.error()) {
            case USERNAME_MISMATCH -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "username_mismatch", "existing_username", result.existingUsername()));
            case EMAIL_TAKEN -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "email already registered"));
            case ALREADY_SELLER -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "account is already a seller"));
            case INVALID_CREDENTIALS -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "incorrect password for existing account"));
        };
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        String token = userService.login(request);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "invalid email or password"));
        }
        return ResponseEntity.ok(Map.of("token", token));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<?> getProfile(@PathVariable String userId) {
        return userService.getProfile(userId)
                .map(u -> ResponseEntity.ok((Object) u))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "user not found")));
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<?> updateProfile(@PathVariable String userId,
                                           @Valid @RequestBody UpdateProfileRequest request,
                                           Authentication auth) {
        if (!userId.equals(auth.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "cannot update another user's profile"));
        }
        if (userService.updateProfile(userId, request)) {
            return ResponseEntity.ok(Map.of("ok", true));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "user not found"));
    }

    @GetMapping("/users/{userId}/bids")
    public ResponseEntity<?> getBids(@PathVariable String userId,
                                     @RequestHeader("Authorization") String authHeader) {
        try {
            String body = restClient.get()
                    .uri("/users/{userId}/bids", userId)
                    .header("Authorization", authHeader)
                    .retrieve()
                    .body(String.class);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "bid service unavailable"));
        }
    }

    @GetMapping("/users/{userId}/watchlist")
    public ResponseEntity<?> getWatchlist(@PathVariable String userId, Authentication auth) {
        if (!userId.equals(auth.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        var ids = userService.getWatchlist(userId);
        return ResponseEntity.ok(Map.of("auction_ids", ids));
    }

    @PostMapping("/users/{userId}/watchlist/{auctionId}")
    public ResponseEntity<?> addToWatchlist(@PathVariable String userId,
                                            @PathVariable String auctionId,
                                            Authentication auth) {
        if (!userId.equals(auth.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        if (userService.addToWatchlist(userId, auctionId)) {
            return ResponseEntity.ok(Map.of("ok", true));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "user not found"));
    }

    @DeleteMapping("/users/{userId}/watchlist/{auctionId}")
    public ResponseEntity<?> removeFromWatchlist(@PathVariable String userId,
                                                 @PathVariable String auctionId,
                                                 Authentication auth) {
        if (!userId.equals(auth.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
        userService.removeFromWatchlist(userId, auctionId);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
