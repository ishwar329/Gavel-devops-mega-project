package com.gavel.auction.controller;

import com.gavel.auction.model.AuctionTemplate;
import com.gavel.auction.service.AuctionService;
import com.gavel.auction.service.AuctionTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateControllerTest {

    @Mock private AuctionTemplateService templateService;
    @Mock private Authentication authentication;

    private TemplateController controller;

    @BeforeEach
    void setUp() {
        controller = new TemplateController(templateService);
    }

    @Test
    void createTemplate_nonSeller_returnsForbidden() {
        when(authentication.getDetails()).thenReturn(Map.of("role", "buyer"));

        ResponseEntity<?> response = controller.createTemplate(null, authentication);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void createTemplate_seller_returnsCreated() {
        when(authentication.getDetails()).thenReturn(Map.of("role", "seller"));
        when(authentication.getPrincipal()).thenReturn("seller-1");
        AuctionTemplate template = new AuctionTemplate();
        template.setTemplateId("t-1");
        when(templateService.createTemplate(any(), eq("seller-1"))).thenReturn(template);

        ResponseEntity<?> response = controller.createTemplate(null, authentication);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void createTemplate_validationError_returnsBadRequest() {
        when(authentication.getDetails()).thenReturn(Map.of("role", "seller"));
        when(authentication.getPrincipal()).thenReturn("seller-1");
        when(templateService.createTemplate(any(), eq("seller-1")))
                .thenThrow(new AuctionService.ValidationException("item_id is required"));

        ResponseEntity<?> response = controller.createTemplate(null, authentication);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void getTemplate_found_returnsOk() {
        AuctionTemplate template = new AuctionTemplate();
        template.setTemplateId("t-1");
        when(templateService.getTemplate("t-1")).thenReturn(template);

        ResponseEntity<?> response = controller.getTemplate("t-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getTemplate_notFound_returns404() {
        when(templateService.getTemplate("t-1"))
                .thenThrow(new AuctionService.NotFoundException("template not found"));

        ResponseEntity<?> response = controller.getTemplate("t-1");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listShopTemplates_returnsOk() {
        when(templateService.listByShop("shop-1")).thenReturn(List.of(new AuctionTemplate()));

        ResponseEntity<?> response = controller.listShopTemplates("shop-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, List<AuctionTemplate>> body = (Map<String, List<AuctionTemplate>>) response.getBody();
        assertEquals(1, body.get("templates").size());
    }

    @Test
    void toggleActive_success_returnsOk() {
        when(authentication.getPrincipal()).thenReturn("seller-1");

        ResponseEntity<?> response = controller.toggleActive("t-1", Map.of("active", false), authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(templateService).toggleActive("t-1", "seller-1", false);
    }

    @Test
    void toggleActive_missingField_returnsBadRequest() {
        when(authentication.getPrincipal()).thenReturn("seller-1");

        ResponseEntity<?> response = controller.toggleActive("t-1", Map.of(), authentication);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void toggleActive_notFound_returns404() {
        when(authentication.getPrincipal()).thenReturn("seller-1");
        doThrow(new AuctionService.NotFoundException("template not found"))
                .when(templateService).toggleActive("t-1", "seller-1", true);

        ResponseEntity<?> response = controller.toggleActive("t-1", Map.of("active", true), authentication);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void toggleActive_notOwner_returnsForbidden() {
        when(authentication.getPrincipal()).thenReturn("other-user");
        doThrow(new AuctionService.ForbiddenException("not your template"))
                .when(templateService).toggleActive("t-1", "other-user", true);

        ResponseEntity<?> response = controller.toggleActive("t-1", Map.of("active", true), authentication);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void deleteTemplate_success_returnsOk() {
        when(authentication.getPrincipal()).thenReturn("seller-1");

        ResponseEntity<?> response = controller.deleteTemplate("t-1", authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(templateService).deleteTemplate("t-1", "seller-1");
    }

    @Test
    void deleteTemplate_notFound_returns404() {
        when(authentication.getPrincipal()).thenReturn("seller-1");
        doThrow(new AuctionService.NotFoundException("template not found"))
                .when(templateService).deleteTemplate("t-1", "seller-1");

        ResponseEntity<?> response = controller.deleteTemplate("t-1", authentication);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void deleteTemplate_notOwner_returnsForbidden() {
        when(authentication.getPrincipal()).thenReturn("other-user");
        doThrow(new AuctionService.ForbiddenException("not your template"))
                .when(templateService).deleteTemplate("t-1", "other-user");

        ResponseEntity<?> response = controller.deleteTemplate("t-1", authentication);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void createTemplate_nonMapDetails_returnsForbidden() {
        when(authentication.getDetails()).thenReturn("not-a-map");

        ResponseEntity<?> response = controller.createTemplate(null, authentication);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
