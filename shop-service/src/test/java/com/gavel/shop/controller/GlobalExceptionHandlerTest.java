package com.gavel.shop.controller;

import com.gavel.shop.service.ShopService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleNotFound_returns404() {
        ResponseEntity<?> response = handler.handleNotFound(new NotFoundException());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertBody(response, "error", "not found");
    }

    @Test
    void handleForbidden_returns403() {
        ResponseEntity<?> response = handler.handleForbidden(new ForbiddenException());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertBody(response, "error", "forbidden");
    }

    @Test
    void handleInvalidInput_returns400WithMessage() {
        ResponseEntity<?> response = handler.handleInvalidInput(new InvalidInputException("bad coords"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertBody(response, "error", "bad coords");
    }

    @Test
    void handlePaymentNotCompleted_returns403() {
        ResponseEntity<?> response = handler.handlePaymentNotCompleted(new PaymentNotCompletedException());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertBody(response, "error", "payment not completed for this auction");
    }

    @Test
    void handleAlreadyReviewed_returns409() {
        ResponseEntity<?> response = handler.handleAlreadyReviewed(new AlreadyReviewedException());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertBody(response, "error", "you have already reviewed this auction");
    }

    @Test
    void handleFileTooLarge_returns400() {
        ResponseEntity<?> response = handler.handleFileTooLarge(new FileTooLargeException());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertBody(response, "error", "file exceeds 5MB limit");
    }

    @Test
    void handleInvalidFileType_returns400() {
        ResponseEntity<?> response = handler.handleInvalidFileType(new InvalidFileTypeException());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertBody(response, "error", "only JPEG, PNG, WebP, and GIF files are allowed");
    }

    @SuppressWarnings("unchecked")
    private void assertBody(ResponseEntity<?> response, String key, String value) {
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry(key, value);
    }
}
