package com.gavel.shop.controller;

import com.gavel.shop.service.AiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiControllerTest {

    @Mock
    private AiService aiService;

    @Mock
    private Authentication auth;

    private AiController controller;

    @BeforeEach
    void setUp() {
        controller = new AiController(aiService);
    }

    private void mockSellerAuth() {
        when(auth.getDetails()).thenReturn(Map.of("role", "seller", "username", "seller1"));
    }

    private void mockBuyerAuth() {
        when(auth.getDetails()).thenReturn(Map.of("role", "buyer", "username", "buyer1"));
    }

    @Test
    void describe_buyerForbidden() {
        mockBuyerAuth();
        var request = new AiController.DescribeRequest("Pastry Box", "Bakery", 2800L);

        ResponseEntity<?> response = controller.describe(request, auth);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(aiService, never()).generateDescription(any(), any(), anyLong());
    }

    @Test
    void describe_notConfigured_returns503() {
        mockSellerAuth();
        when(aiService.isConfigured()).thenReturn(false);
        var request = new AiController.DescribeRequest("Pastry Box", "Bakery", 2800L);

        ResponseEntity<?> response = controller.describe(request, auth);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    void describe_blankTitle_returns400() {
        mockSellerAuth();
        when(aiService.isConfigured()).thenReturn(true);
        var request = new AiController.DescribeRequest("  ", "Bakery", 2800L);

        ResponseEntity<?> response = controller.describe(request, auth);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void describe_nullTitle_returns400() {
        mockSellerAuth();
        when(aiService.isConfigured()).thenReturn(true);
        var request = new AiController.DescribeRequest(null, "Bakery", 2800L);

        ResponseEntity<?> response = controller.describe(request, auth);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void describe_success_returnsDescription() {
        mockSellerAuth();
        when(aiService.isConfigured()).thenReturn(true);
        when(aiService.generateDescription("Pastry Box", "Bakery", 2800L))
                .thenReturn(Optional.of("A delightful assortment of fresh pastries."));
        var request = new AiController.DescribeRequest("Pastry Box", "Bakery", 2800L);

        ResponseEntity<?> response = controller.describe(request, auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("A delightful assortment of fresh pastries.", body.get("description"));
    }

    @Test
    void describe_aiReturnsEmpty_returns500() {
        mockSellerAuth();
        when(aiService.isConfigured()).thenReturn(true);
        when(aiService.generateDescription("Pastry Box", "Bakery", 2800L))
                .thenReturn(Optional.empty());
        var request = new AiController.DescribeRequest("Pastry Box", "Bakery", 2800L);

        ResponseEntity<?> response = controller.describe(request, auth);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void describe_nullRetailValue_defaultsToZero() {
        mockSellerAuth();
        when(aiService.isConfigured()).thenReturn(true);
        when(aiService.generateDescription("Bread Loaf", null, 0L))
                .thenReturn(Optional.of("Fresh bread."));
        var request = new AiController.DescribeRequest("Bread Loaf", null, null);

        ResponseEntity<?> response = controller.describe(request, auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(aiService).generateDescription("Bread Loaf", null, 0L);
    }
}
