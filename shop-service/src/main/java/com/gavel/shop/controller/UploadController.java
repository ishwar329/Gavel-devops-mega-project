package com.gavel.shop.controller;

import com.gavel.shop.service.ShopService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
public class UploadController {

    private final ShopService shopService;

    public UploadController(ShopService shopService) {
        this.shopService = shopService;
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/uploads")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file,
                                         Authentication auth) {
        Map<String, String> details = (Map<String, String>) auth.getDetails();
        if (!"seller".equals(details.get("role"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "sellers only"));
        }
        try {
            String url = shopService.uploadImage(
                    file.getContentType(),
                    file.getInputStream(),
                    file.getSize());
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "failed to read file"));
        }
    }
}
