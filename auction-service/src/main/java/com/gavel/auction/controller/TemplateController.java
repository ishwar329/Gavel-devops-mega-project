package com.gavel.auction.controller;

import com.gavel.auction.model.AuctionTemplate;
import com.gavel.auction.model.CreateTemplateRequest;
import com.gavel.auction.service.AuctionService;
import com.gavel.auction.service.AuctionTemplateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class TemplateController {

    private final AuctionTemplateService templateService;

    public TemplateController(AuctionTemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping("/templates")
    public ResponseEntity<?> createTemplate(@RequestBody CreateTemplateRequest request,
                                            Authentication authentication) {
        String role = getRole(authentication);
        if (!"seller".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "only sellers can create templates"));
        }
        String sellerId = (String) authentication.getPrincipal();
        try {
            AuctionTemplate template = templateService.createTemplate(request, sellerId);
            return ResponseEntity.status(HttpStatus.CREATED).body(template);
        } catch (AuctionService.ValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/templates/{id}")
    public ResponseEntity<?> getTemplate(@PathVariable String id) {
        try {
            AuctionTemplate template = templateService.getTemplate(id);
            return ResponseEntity.ok(template);
        } catch (AuctionService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/shops/{shopId}/templates")
    public ResponseEntity<?> listShopTemplates(@PathVariable String shopId) {
        List<AuctionTemplate> templates = templateService.listByShop(shopId);
        return ResponseEntity.ok(Map.of("templates", templates));
    }

    @PatchMapping("/templates/{id}/active")
    public ResponseEntity<?> toggleActive(@PathVariable String id,
                                          @RequestBody Map<String, Boolean> body,
                                          Authentication authentication) {
        String sellerId = (String) authentication.getPrincipal();
        Boolean active = body.get("active");
        if (active == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "active field is required"));
        }
        try {
            templateService.toggleActive(id, sellerId, active);
            return ResponseEntity.ok(Map.of("message", active ? "template activated" : "template paused"));
        } catch (AuctionService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (AuctionService.ForbiddenException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/templates/{id}")
    public ResponseEntity<?> deleteTemplate(@PathVariable String id,
                                            Authentication authentication) {
        String sellerId = (String) authentication.getPrincipal();
        try {
            templateService.deleteTemplate(id, sellerId);
            return ResponseEntity.ok(Map.of("message", "template deleted"));
        } catch (AuctionService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (AuctionService.ForbiddenException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
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
