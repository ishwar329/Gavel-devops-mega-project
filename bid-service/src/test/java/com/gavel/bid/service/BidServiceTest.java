package com.gavel.bid.service;

import com.gavel.bid.model.Bid;
import com.gavel.bid.repository.BidRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    @Mock
    private BidRepository bidRepository;

    @InjectMocks
    private BidService bidService;

    // --- recordBid ---

    @Test
    void recordBid_callsRepositoryMethodsInCorrectOrder() {
        Bid bid = new Bid("bid-1", "auction-1", "user-1", "Organic Bread",
                "shop-1", "Daily Bakery", 500L, Instant.now(), "ACCEPTED");

        bidService.recordBid(bid);

        InOrder inOrder = inOrder(bidRepository);
        inOrder.verify(bidRepository).markUserPreviousBids("auction-1", "user-1", "bid-1");
        inOrder.verify(bidRepository).create(bid);
        inOrder.verify(bidRepository).markOutbid("auction-1", "bid-1");
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void recordBid_passesCorrectAuctionIdAndUserId() {
        Bid bid = new Bid("bid-99", "auction-42", "user-7", "Sushi Platter",
                "shop-5", "Ocean Fresh", 1200L, Instant.parse("2026-04-20T10:00:00Z"), "ACCEPTED");

        bidService.recordBid(bid);

        verify(bidRepository).markUserPreviousBids("auction-42", "user-7", "bid-99");
        verify(bidRepository).create(bid);
        verify(bidRepository).markOutbid("auction-42", "bid-99");
    }

    @Test
    void recordBid_passesExactBidObjectToCreate() {
        Bid bid = new Bid("bid-abc", "auction-xyz", "user-123", "Leftover Cake",
                "shop-10", "Sweet Tooth", 300L, Instant.now(), "ACCEPTED");

        bidService.recordBid(bid);

        verify(bidRepository).create(same(bid));
    }

    // --- markWinnerBid ---

    @Test
    void markWinnerBid_delegatesToRepository() {
        bidService.markWinnerBid("auction-1", "user-1");

        verify(bidRepository).markWon("auction-1", "user-1");
        verifyNoMoreInteractions(bidRepository);
    }

    @Test
    void markWinnerBid_passesCorrectArguments() {
        bidService.markWinnerBid("auction-55", "winner-9");

        verify(bidRepository).markWon("auction-55", "winner-9");
    }

    // --- getAuctionBids ---

    @Test
    void getAuctionBids_returnsResultFromRepository() {
        Bid bid1 = new Bid("bid-1", "auction-1", "user-1", "Item A",
                "shop-1", "Shop A", 100L, Instant.now(), "ACCEPTED");
        Bid bid2 = new Bid("bid-2", "auction-1", "user-2", "Item A",
                "shop-1", "Shop A", 200L, Instant.now(), "OUTBID");
        List<Bid> expected = List.of(bid1, bid2);
        when(bidRepository.getByAuction("auction-1")).thenReturn(expected);

        List<Bid> result = bidService.getAuctionBids("auction-1");

        assertThat(result).isSameAs(expected);
        verify(bidRepository).getByAuction("auction-1");
    }

    @Test
    void getAuctionBids_returnsEmptyListWhenNoBids() {
        when(bidRepository.getByAuction("auction-empty")).thenReturn(Collections.emptyList());

        List<Bid> result = bidService.getAuctionBids("auction-empty");

        assertThat(result).isEmpty();
    }

    @Test
    void getAuctionBids_delegatesCorrectAuctionId() {
        when(bidRepository.getByAuction("auction-777")).thenReturn(List.of());

        bidService.getAuctionBids("auction-777");

        verify(bidRepository).getByAuction("auction-777");
        verifyNoMoreInteractions(bidRepository);
    }

    // --- getUserBids ---

    @Test
    void getUserBids_returnsResultFromRepository() {
        Bid bid1 = new Bid("bid-10", "auction-5", "user-3", "Item X",
                "shop-2", "Shop B", 750L, Instant.now(), "ACCEPTED");
        Bid bid2 = new Bid("bid-11", "auction-6", "user-3", "Item Y",
                "shop-3", "Shop C", 400L, Instant.now(), "WON");
        List<Bid> expected = List.of(bid1, bid2);
        when(bidRepository.getByUser("user-3")).thenReturn(expected);

        List<Bid> result = bidService.getUserBids("user-3");

        assertThat(result).isSameAs(expected);
        verify(bidRepository).getByUser("user-3");
    }

    @Test
    void getUserBids_returnsEmptyListWhenNoBids() {
        when(bidRepository.getByUser("user-nobody")).thenReturn(Collections.emptyList());

        List<Bid> result = bidService.getUserBids("user-nobody");

        assertThat(result).isEmpty();
    }

    @Test
    void getUserBids_delegatesCorrectUserId() {
        when(bidRepository.getByUser("user-42")).thenReturn(List.of());

        bidService.getUserBids("user-42");

        verify(bidRepository).getByUser("user-42");
        verifyNoMoreInteractions(bidRepository);
    }
}
