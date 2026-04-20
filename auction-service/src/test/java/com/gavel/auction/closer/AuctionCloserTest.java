package com.gavel.auction.closer;

import com.gavel.auction.model.Auction;
import com.gavel.auction.service.AuctionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionCloserTest {

    @Mock private AuctionService auctionService;
    private AuctionCloser closer;

    @BeforeEach
    void setUp() {
        closer = new AuctionCloser(auctionService);
    }

    @Test
    void checkExpiredAndPending_closesExpiredAuctions() {
        Auction expired = new Auction();
        expired.setAuctionId("a-1");
        expired.setEndTime(Instant.now().minus(1, ChronoUnit.HOURS).toString());

        when(auctionService.listAuctions("OPEN")).thenReturn(List.of(expired));
        when(auctionService.listAuctions("PENDING")).thenReturn(List.of());

        closer.checkExpiredAndPending();

        verify(auctionService).closeAuction("a-1");
    }

    @Test
    void checkExpiredAndPending_doesNotCloseActiveAuctions() {
        Auction active = new Auction();
        active.setAuctionId("a-2");
        active.setEndTime(Instant.now().plus(1, ChronoUnit.HOURS).toString());

        when(auctionService.listAuctions("OPEN")).thenReturn(List.of(active));
        when(auctionService.listAuctions("PENDING")).thenReturn(List.of());

        closer.checkExpiredAndPending();

        verify(auctionService, never()).closeAuction(anyString());
    }

    @Test
    void checkExpiredAndPending_opensPendingAuctionsPastStartTime() {
        Auction pending = new Auction();
        pending.setAuctionId("a-3");
        pending.setStartTime(Instant.now().minus(5, ChronoUnit.MINUTES).toString());

        when(auctionService.listAuctions("OPEN")).thenReturn(List.of());
        when(auctionService.listAuctions("PENDING")).thenReturn(List.of(pending));

        closer.checkExpiredAndPending();

        verify(auctionService).openAuction("a-3");
    }

    @Test
    void checkExpiredAndPending_doesNotOpenFuturePending() {
        Auction pending = new Auction();
        pending.setAuctionId("a-4");
        pending.setStartTime(Instant.now().plus(1, ChronoUnit.HOURS).toString());

        when(auctionService.listAuctions("OPEN")).thenReturn(List.of());
        when(auctionService.listAuctions("PENDING")).thenReturn(List.of(pending));

        closer.checkExpiredAndPending();

        verify(auctionService, never()).openAuction(anyString());
    }

    @Test
    void checkExpiredAndPending_handlesExceptionGracefully() {
        when(auctionService.listAuctions("OPEN")).thenThrow(new RuntimeException("redis down"));
        when(auctionService.listAuctions("PENDING")).thenReturn(List.of());

        closer.checkExpiredAndPending();

        verify(auctionService, never()).closeAuction(anyString());
    }
}
