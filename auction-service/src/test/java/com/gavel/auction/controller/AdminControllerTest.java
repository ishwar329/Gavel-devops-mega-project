package com.gavel.auction.controller;

import com.gavel.auction.model.BidMetrics;
import com.gavel.auction.service.AuctionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock private AuctionService auctionService;
    private AdminController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminController(auctionService);
    }

    @Test
    void getMetrics_returnsOkWithMetrics() {
        BidMetrics metrics = new BidMetrics(100, 80, 20, 12.5, 25.0, 50.0, 0);
        when(auctionService.getMetrics()).thenReturn(metrics);

        ResponseEntity<BidMetrics> response = controller.getMetrics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(metrics, response.getBody());
    }

    @Test
    void getMetrics_emptyMetrics_returnsOk() {
        BidMetrics metrics = new BidMetrics(0, 0, 0, 0, 0, 0, 0);
        when(auctionService.getMetrics()).thenReturn(metrics);

        ResponseEntity<BidMetrics> response = controller.getMetrics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().totalBids());
    }

    @Test
    void resetMetrics_returnsOkWithMessage() {
        ResponseEntity<?> response = controller.resetMetrics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(auctionService).resetMetrics();

        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("metrics reset", body.get("message"));
    }
}
