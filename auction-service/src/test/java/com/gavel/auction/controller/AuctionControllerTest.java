package com.gavel.auction.controller;

import com.gavel.auction.concurrency.BidException;
import com.gavel.auction.model.Auction;
import com.gavel.auction.model.BidResult;
import com.gavel.auction.service.AuctionService;
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
class AuctionControllerTest {

    @Mock private AuctionService auctionService;
    @Mock private Authentication authentication;

    private AuctionController controller;

    @BeforeEach
    void setUp() {
        controller = new AuctionController(auctionService);
    }

    @Test
    void createAuction_nonSeller_returnsForbidden() {
        when(authentication.getDetails()).thenReturn(Map.of("role", "buyer"));

        ResponseEntity<?> response = controller.createAuction(null, authentication);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void createAuction_seller_returnsCreated() {
        when(authentication.getDetails()).thenReturn(Map.of("role", "seller"));
        when(authentication.getPrincipal()).thenReturn("seller-1");
        Auction auction = new Auction();
        auction.setAuctionId("a-1");
        when(auctionService.createAuction(any(), eq("seller-1"))).thenReturn(auction);

        ResponseEntity<?> response = controller.createAuction(null, authentication);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void getAuction_found_returnsOk() {
        Auction auction = new Auction();
        auction.setAuctionId("a-1");
        when(auctionService.getAuction("a-1")).thenReturn(auction);

        ResponseEntity<?> response = controller.getAuction("a-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getAuction_notFound_returns404() {
        when(auctionService.getAuction("a-1"))
                .thenThrow(new AuctionService.NotFoundException("auction not found"));

        ResponseEntity<?> response = controller.getAuction("a-1");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void listAuctions_withStatus_filtersResults() {
        when(auctionService.listAuctions("OPEN")).thenReturn(List.of(new Auction()));

        ResponseEntity<?> response = controller.listAuctions("OPEN", null, null, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void listAuctions_withGeo_usesNearbySearch() {
        when(auctionService.listAuctionsNear(40.0, -74.0, 5.0)).thenReturn(List.of());

        ResponseEntity<?> response = controller.listAuctions(null, 40.0, -74.0, 5.0, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(auctionService).listAuctionsNear(40.0, -74.0, 5.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAuctions_withSearchQuery_filtersResults() {
        Auction matching = new Auction();
        matching.setItemTitle("Fresh Bread");
        Auction nonMatching = new Auction();
        nonMatching.setItemTitle("Laptop");
        when(auctionService.listAuctions(null)).thenReturn(List.of(matching, nonMatching));

        ResponseEntity<?> response = controller.listAuctions(null, null, null, null, "bread");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, List<Auction>> body = (Map<String, List<Auction>>) response.getBody();
        assertEquals(1, body.get("auctions").size());
    }

    @Test
    void placeBid_success_returnsCreated() {
        when(authentication.getPrincipal()).thenReturn("user-1");
        BidResult result = new BidResult("b-1", "a-1", 600, 600, "ACCEPTED");
        when(auctionService.placeBid("a-1", "user-1", 600)).thenReturn(result);

        var request = new com.gavel.auction.model.PlaceBidRequest(600);
        ResponseEntity<?> response = controller.placeBid("a-1", request, authentication);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void placeBid_bidTooLow_returnsBadRequest() {
        when(authentication.getPrincipal()).thenReturn("user-1");
        when(auctionService.placeBid("a-1", "user-1", 100))
                .thenThrow(new BidException(BidException.BID_TOO_LOW, "bid must be higher", 500));

        var request = new com.gavel.auction.model.PlaceBidRequest(100);
        ResponseEntity<?> response = controller.placeBid("a-1", request, authentication);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void placeBid_auctionNotOpen_returnsConflict() {
        when(authentication.getPrincipal()).thenReturn("user-1");
        when(auctionService.placeBid("a-1", "user-1", 600))
                .thenThrow(new BidException(BidException.NOT_OPEN, "auction not open", 0));

        var request = new com.gavel.auction.model.PlaceBidRequest(600);
        ResponseEntity<?> response = controller.placeBid("a-1", request, authentication);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void closeAuction_bySeller_returnsOk() {
        when(authentication.getPrincipal()).thenReturn("seller-1");
        Auction auction = new Auction();
        auction.setSellerId("seller-1");
        when(auctionService.getAuction("a-1")).thenReturn(auction);

        ResponseEntity<?> response = controller.closeAuction("a-1", authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(auctionService).closeAuction("a-1");
    }

    @Test
    void closeAuction_byNonSeller_returnsForbidden() {
        when(authentication.getPrincipal()).thenReturn("other-user");
        Auction auction = new Auction();
        auction.setSellerId("seller-1");
        when(auctionService.getAuction("a-1")).thenReturn(auction);

        ResponseEntity<?> response = controller.closeAuction("a-1", authentication);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(auctionService, never()).closeAuction(anyString());
    }

    @Test
    void closeAuction_notFound_returns404() {
        when(authentication.getPrincipal()).thenReturn("user-1");
        when(auctionService.getAuction("a-1"))
                .thenThrow(new AuctionService.NotFoundException("auction not found"));

        ResponseEntity<?> response = controller.closeAuction("a-1", authentication);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void closeAuction_internalError_returns500() {
        when(authentication.getPrincipal()).thenReturn("seller-1");
        Auction auction = new Auction();
        auction.setSellerId("seller-1");
        when(auctionService.getAuction("a-1")).thenReturn(auction);
        doThrow(new RuntimeException("redis down")).when(auctionService).closeAuction("a-1");

        ResponseEntity<?> response = controller.closeAuction("a-1", authentication);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void createAuction_validationError_returnsBadRequest() {
        when(authentication.getDetails()).thenReturn(Map.of("role", "seller"));
        when(authentication.getPrincipal()).thenReturn("seller-1");
        when(auctionService.createAuction(any(), eq("seller-1")))
                .thenThrow(new AuctionService.ValidationException("item_id is required"));

        ResponseEntity<?> response = controller.createAuction(null, authentication);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void listShopAuctions_returnsOk() {
        when(auctionService.listAuctionsByShop("shop-1")).thenReturn(List.of(new Auction()));

        ResponseEntity<?> response = controller.listShopAuctions("shop-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void placeBid_notFound_returns404() {
        when(authentication.getPrincipal()).thenReturn("user-1");
        when(auctionService.placeBid("a-1", "user-1", 600))
                .thenThrow(new AuctionService.NotFoundException("auction not found"));

        var request = new com.gavel.auction.model.PlaceBidRequest(600);
        ResponseEntity<?> response = controller.placeBid("a-1", request, authentication);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void placeBid_forbidden_returns403() {
        when(authentication.getPrincipal()).thenReturn("seller-1");
        when(auctionService.placeBid("a-1", "seller-1", 600))
                .thenThrow(new AuctionService.ForbiddenException("seller cannot bid on own auction"));

        var request = new com.gavel.auction.model.PlaceBidRequest(600);
        ResponseEntity<?> response = controller.placeBid("a-1", request, authentication);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void placeBid_exceedsMax_returnsBadRequest() {
        when(authentication.getPrincipal()).thenReturn("user-1");
        when(auctionService.placeBid("a-1", "user-1", 9999))
                .thenThrow(new BidException(BidException.EXCEEDS_MAX, "bid exceeds max price", 5000));

        var request = new com.gavel.auction.model.PlaceBidRequest(9999);
        ResponseEntity<?> response = controller.placeBid("a-1", request, authentication);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void placeBid_incrementTooSmall_returnsBadRequest() {
        when(authentication.getPrincipal()).thenReturn("user-1");
        when(auctionService.placeBid("a-1", "user-1", 501))
                .thenThrow(new BidException(BidException.INCREMENT_TOO_SMALL, "bid increment too small", 500));

        var request = new com.gavel.auction.model.PlaceBidRequest(501);
        ResponseEntity<?> response = controller.placeBid("a-1", request, authentication);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void placeBid_bidNotFound_returns404() {
        when(authentication.getPrincipal()).thenReturn("user-1");
        when(auctionService.placeBid("a-1", "user-1", 100))
                .thenThrow(new BidException(BidException.NOT_FOUND, "auction not found", 0));

        var request = new com.gavel.auction.model.PlaceBidRequest(100);
        ResponseEntity<?> response = controller.placeBid("a-1", request, authentication);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAuctions_noParams_returnsAll() {
        when(auctionService.listAuctions(null)).thenReturn(List.of(new Auction()));

        ResponseEntity<?> response = controller.listAuctions(null, null, null, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, List<Auction>> body = (Map<String, List<Auction>>) response.getBody();
        assertEquals(1, body.get("auctions").size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAuctions_searchByDescription_filtersResults() {
        Auction matching = new Auction();
        matching.setDescription("Fresh organic apples");
        Auction nonMatching = new Auction();
        nonMatching.setDescription("Electronics sale");
        when(auctionService.listAuctions(null)).thenReturn(List.of(matching, nonMatching));

        ResponseEntity<?> response = controller.listAuctions(null, null, null, null, "organic");

        Map<String, List<Auction>> body = (Map<String, List<Auction>>) response.getBody();
        assertEquals(1, body.get("auctions").size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAuctions_searchByShopName_filtersResults() {
        Auction matching = new Auction();
        matching.setShopName("Green Bakery");
        Auction nonMatching = new Auction();
        nonMatching.setShopName("Tech Store");
        when(auctionService.listAuctions(null)).thenReturn(List.of(matching, nonMatching));

        ResponseEntity<?> response = controller.listAuctions(null, null, null, null, "bakery");

        Map<String, List<Auction>> body = (Map<String, List<Auction>>) response.getBody();
        assertEquals(1, body.get("auctions").size());
    }
}
