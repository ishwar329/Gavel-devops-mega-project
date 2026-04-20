package com.gavel.bid.controller;

import com.gavel.bid.model.Bid;
import com.gavel.bid.service.BidService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BidControllerTest {

    @Mock
    private BidService bidService;

    @InjectMocks
    private BidController bidController;

    // --- getAuctionBids ---

    @Test
    void getAuctionBids_returnsOkWithBidsMap() {
        Bid bid1 = new Bid("bid-1", "auction-1", "user-1", "Fresh Pastries",
                "shop-1", "Baker's Delight", 500L, Instant.now(), "ACCEPTED");
        Bid bid2 = new Bid("bid-2", "auction-1", "user-2", "Fresh Pastries",
                "shop-1", "Baker's Delight", 600L, Instant.now(), "OUTBID");
        List<Bid> bids = List.of(bid1, bid2);
        when(bidService.getAuctionBids("auction-1")).thenReturn(bids);

        ResponseEntity<Map<String, List<Bid>>> response = bidController.getAuctionBids("auction-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("bids");
        assertThat(response.getBody().get("bids")).hasSize(2);
        assertThat(response.getBody().get("bids")).containsExactly(bid1, bid2);
    }

    @Test
    void getAuctionBids_returnsOkWithEmptyList() {
        when(bidService.getAuctionBids("auction-empty")).thenReturn(Collections.emptyList());

        ResponseEntity<Map<String, List<Bid>>> response = bidController.getAuctionBids("auction-empty");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("bids")).isEmpty();
    }

    @Test
    void getAuctionBids_delegatesCorrectIdToService() {
        when(bidService.getAuctionBids("auction-99")).thenReturn(List.of());

        bidController.getAuctionBids("auction-99");

        verify(bidService).getAuctionBids("auction-99");
        verifyNoMoreInteractions(bidService);
    }

    @Test
    void getAuctionBids_bodyContainsOnlyBidsKey() {
        when(bidService.getAuctionBids("auction-1")).thenReturn(List.of());

        ResponseEntity<Map<String, List<Bid>>> response = bidController.getAuctionBids("auction-1");

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()).containsOnlyKeys("bids");
    }

    // --- getUserBids ---

    @Test
    void getUserBids_returnsOkWithBidsMap() {
        Bid bid1 = new Bid("bid-10", "auction-5", "user-7", "Veggie Box",
                "shop-3", "Green Grocer", 300L, Instant.now(), "ACCEPTED");
        Bid bid2 = new Bid("bid-11", "auction-6", "user-7", "Fruit Basket",
                "shop-4", "Farm Fresh", 250L, Instant.now(), "WON");
        Bid bid3 = new Bid("bid-12", "auction-7", "user-7", "Deli Platter",
                "shop-5", "Gourmet Go", 800L, Instant.now(), "OUTBID");
        List<Bid> bids = List.of(bid1, bid2, bid3);
        when(bidService.getUserBids("user-7")).thenReturn(bids);

        ResponseEntity<Map<String, List<Bid>>> response = bidController.getUserBids("user-7");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("bids");
        assertThat(response.getBody().get("bids")).hasSize(3);
        assertThat(response.getBody().get("bids")).containsExactly(bid1, bid2, bid3);
    }

    @Test
    void getUserBids_returnsOkWithEmptyList() {
        when(bidService.getUserBids("user-nobody")).thenReturn(Collections.emptyList());

        ResponseEntity<Map<String, List<Bid>>> response = bidController.getUserBids("user-nobody");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("bids")).isEmpty();
    }

    @Test
    void getUserBids_delegatesCorrectUserIdToService() {
        when(bidService.getUserBids("user-42")).thenReturn(List.of());

        bidController.getUserBids("user-42");

        verify(bidService).getUserBids("user-42");
        verifyNoMoreInteractions(bidService);
    }

    @Test
    void getUserBids_bodyContainsOnlyBidsKey() {
        when(bidService.getUserBids("user-1")).thenReturn(List.of());

        ResponseEntity<Map<String, List<Bid>>> response = bidController.getUserBids("user-1");

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()).containsOnlyKeys("bids");
    }
}
