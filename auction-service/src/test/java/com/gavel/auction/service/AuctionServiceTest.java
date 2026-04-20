package com.gavel.auction.service;

import com.gavel.auction.concurrency.BidException;
import com.gavel.auction.concurrency.BidPlacement;
import com.gavel.auction.concurrency.PessimisticStrategy;
import com.gavel.auction.events.AuctionEventPublisher;
import com.gavel.auction.model.Auction;
import com.gavel.auction.model.BidResult;
import com.gavel.auction.model.CreateAuctionRequest;
import com.gavel.auction.repository.AuctionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    @Mock private AuctionRepository repo;
    @Mock private AuctionEventPublisher publisher;
    @Mock private MetricsCollector metrics;
    @Mock private PessimisticStrategy pessimistic;

    private AuctionService service;

    @BeforeEach
    void setUp() {
        service = new AuctionService(repo, publisher, metrics, pessimistic);
    }

    private CreateAuctionRequest validRequest() {
        Instant now = Instant.now();
        Instant pickupStart = now.plus(2, ChronoUnit.HOURS);
        Instant pickupEnd = pickupStart.plus(1, ChronoUnit.HOURS);
        return new CreateAuctionRequest(
                "item-1", "Test Item", "shop-1", "Test Shop",
                40.7128, -74.0060, 1000, 5000, 100, 1,
                "http://img.com/1.jpg", "http://img.com/logo.jpg",
                "A test item", "food", 60, 500, null,
                pickupStart.toString(), pickupEnd.toString(), 0
        );
    }

    @Test
    void createAuction_validRequest_createsAndPersists() {
        CreateAuctionRequest req = validRequest();

        Auction result = service.createAuction(req, "seller-1");

        assertNotNull(result.getAuctionId());
        assertEquals("item-1", result.getItemId());
        assertEquals("seller-1", result.getSellerId());
        assertEquals("shop-1", result.getShopId());
        assertEquals("OPEN", result.getStatus());
        assertEquals(500, result.getCurrentHighest());
        assertEquals(0, result.getBidCount());
        verify(repo).create(any(Auction.class));
    }

    @Test
    void createAuction_missingItemId_throws() {
        Instant now = Instant.now();
        Instant pickupStart = now.plus(2, ChronoUnit.HOURS);
        Instant pickupEnd = pickupStart.plus(1, ChronoUnit.HOURS);
        CreateAuctionRequest req = new CreateAuctionRequest(
                "", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 60, 0, null,
                pickupStart.toString(), pickupEnd.toString(), 0
        );

        var ex = assertThrows(AuctionService.ValidationException.class,
                () -> service.createAuction(req, "seller-1"));
        assertEquals("item_id is required", ex.getMessage());
    }

    @Test
    void createAuction_missingShopId_throws() {
        Instant now = Instant.now();
        Instant pickupStart = now.plus(2, ChronoUnit.HOURS);
        Instant pickupEnd = pickupStart.plus(1, ChronoUnit.HOURS);
        CreateAuctionRequest req = new CreateAuctionRequest(
                "item-1", "Title", "", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 60, 0, null,
                pickupStart.toString(), pickupEnd.toString(), 0
        );

        var ex = assertThrows(AuctionService.ValidationException.class,
                () -> service.createAuction(req, "seller-1"));
        assertEquals("shop_id is required", ex.getMessage());
    }

    @Test
    void createAuction_invalidDuration_throws() {
        Instant now = Instant.now();
        Instant pickupStart = now.plus(200, ChronoUnit.HOURS);
        Instant pickupEnd = pickupStart.plus(1, ChronoUnit.HOURS);
        CreateAuctionRequest req = new CreateAuctionRequest(
                "item-1", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 0, 0, null,
                pickupStart.toString(), pickupEnd.toString(), 0
        );

        assertThrows(AuctionService.ValidationException.class,
                () -> service.createAuction(req, "seller-1"));
    }

    @Test
    void createAuction_negativeStartBid_throws() {
        Instant now = Instant.now();
        Instant pickupStart = now.plus(2, ChronoUnit.HOURS);
        Instant pickupEnd = pickupStart.plus(1, ChronoUnit.HOURS);
        CreateAuctionRequest req = new CreateAuctionRequest(
                "item-1", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 60, -1, null,
                pickupStart.toString(), pickupEnd.toString(), 0
        );

        var ex = assertThrows(AuctionService.ValidationException.class,
                () -> service.createAuction(req, "seller-1"));
        assertEquals("start_bid must be non-negative", ex.getMessage());
    }

    @Test
    void createAuction_maxPriceLessThanStartBid_throws() {
        Instant now = Instant.now();
        Instant pickupStart = now.plus(2, ChronoUnit.HOURS);
        Instant pickupEnd = pickupStart.plus(1, ChronoUnit.HOURS);
        CreateAuctionRequest req = new CreateAuctionRequest(
                "item-1", "Title", "shop-1", "Shop", 0, 0, 0, 50, 0, 1,
                null, null, null, null, 60, 100, null,
                pickupStart.toString(), pickupEnd.toString(), 0
        );

        var ex = assertThrows(AuctionService.ValidationException.class,
                () -> service.createAuction(req, "seller-1"));
        assertEquals("max_price must be greater than start_bid", ex.getMessage());
    }

    @Test
    void createAuction_scheduledInFuture_statusPending() {
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant pickupStart = future.plus(2, ChronoUnit.HOURS);
        Instant pickupEnd = pickupStart.plus(1, ChronoUnit.HOURS);
        CreateAuctionRequest req = new CreateAuctionRequest(
                "item-1", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 60, 0, future.toString(),
                pickupStart.toString(), pickupEnd.toString(), 0
        );

        Auction result = service.createAuction(req, "seller-1");
        assertEquals("PENDING", result.getStatus());
    }

    @Test
    void createAuction_pickupBeforeEnd_throws() {
        Instant now = Instant.now();
        Instant pickupStart = now.plus(30, ChronoUnit.MINUTES);
        Instant pickupEnd = pickupStart.plus(1, ChronoUnit.HOURS);
        CreateAuctionRequest req = new CreateAuctionRequest(
                "item-1", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 60, 0, null,
                pickupStart.toString(), pickupEnd.toString(), 0
        );

        var ex = assertThrows(AuctionService.ValidationException.class,
                () -> service.createAuction(req, "seller-1"));
        assertEquals("pickup_start must be after auction end time", ex.getMessage());
    }

    @Test
    void getAuction_exists_returnsAuction() {
        Auction auction = new Auction();
        auction.setAuctionId("a-1");
        when(repo.getById("a-1")).thenReturn(auction);

        Auction result = service.getAuction("a-1");
        assertEquals("a-1", result.getAuctionId());
    }

    @Test
    void getAuction_notFound_throws() {
        when(repo.getById("a-1")).thenReturn(null);

        assertThrows(AuctionService.NotFoundException.class,
                () -> service.getAuction("a-1"));
    }

    @Test
    void listAuctions_delegatesToRepo() {
        List<Auction> expected = List.of(new Auction());
        when(repo.list("OPEN")).thenReturn(expected);

        List<Auction> result = service.listAuctions("OPEN");
        assertEquals(expected, result);
    }

    @Test
    void listAuctionsByShop_delegatesToRepo() {
        List<Auction> expected = List.of(new Auction());
        when(repo.listByShop("shop-1")).thenReturn(expected);

        List<Auction> result = service.listAuctionsByShop("shop-1");
        assertEquals(expected, result);
    }

    @Test
    void listAuctionsNear_noShopsFound_returnsEmpty() {
        when(repo.getShopIdsNear(40.0, -74.0, 5.0)).thenReturn(Collections.emptyList());

        List<Auction> result = service.listAuctionsNear(40.0, -74.0, 5.0);
        assertTrue(result.isEmpty());
    }

    @Test
    void listAuctionsNear_filtersNonOpenAuctions() {
        when(repo.getShopIdsNear(40.0, -74.0, 5.0)).thenReturn(List.of("shop-1"));

        Auction open = new Auction();
        open.setStatus("OPEN");
        Auction closed = new Auction();
        closed.setStatus("CLOSED");
        when(repo.listByShop("shop-1")).thenReturn(List.of(open, closed));

        List<Auction> result = service.listAuctionsNear(40.0, -74.0, 5.0);
        assertEquals(1, result.size());
        assertEquals("OPEN", result.get(0).getStatus());
    }

    @Test
    void placeBid_auctionNotFound_throws() {
        when(repo.getById("a-1")).thenReturn(null);

        assertThrows(AuctionService.NotFoundException.class,
                () -> service.placeBid("a-1", "user-1", 100));
        verify(metrics).recordRejected();
    }

    @Test
    void placeBid_sellerBidsOnOwn_throws() {
        Auction auction = new Auction();
        auction.setSellerId("seller-1");
        when(repo.getById("a-1")).thenReturn(auction);

        assertThrows(AuctionService.ForbiddenException.class,
                () -> service.placeBid("a-1", "seller-1", 100));
        verify(metrics).recordRejected();
    }

    @Test
    void placeBid_success_returnsResult() {
        Auction auction = new Auction();
        auction.setSellerId("seller-1");
        auction.setHighestBidder("prev-user");
        auction.setCurrentHighest(500);
        auction.setItemId("item-1");
        auction.setItemTitle("Item");
        auction.setShopId("shop-1");
        auction.setShopName("Shop");
        when(repo.getById("a-1")).thenReturn(auction);
        when(pessimistic.tryPlaceBid("a-1", 600, "user-1"))
                .thenReturn(new BidPlacement(2, "prev-user", 600));

        BidResult result = service.placeBid("a-1", "user-1", 600);

        assertNotNull(result.bidId());
        assertEquals("a-1", result.auctionId());
        assertEquals(600, result.amount());
        assertEquals("ACCEPTED", result.status());
        verify(metrics).recordSuccessful(any());
        verify(publisher).publishBidPlaced(any());
    }

    @Test
    void placeBid_bidException_propagates() {
        Auction auction = new Auction();
        auction.setSellerId("seller-1");
        when(repo.getById("a-1")).thenReturn(auction);
        when(pessimistic.tryPlaceBid("a-1", 100, "user-1"))
                .thenThrow(new BidException(BidException.BID_TOO_LOW, "bid must be higher", 500));

        assertThrows(BidException.class,
                () -> service.placeBid("a-1", "user-1", 100));
        verify(metrics).recordRejected();
    }

    @Test
    void closeAuction_notFound_throws() {
        when(repo.getById("a-1")).thenReturn(null);

        assertThrows(AuctionService.NotFoundException.class,
                () -> service.closeAuction("a-1"));
    }

    @Test
    void closeAuction_success_publishesEvent() {
        Auction auction = new Auction();
        auction.setAuctionId("a-1");
        auction.setQuantity(1);
        auction.setItemId("item-1");
        auction.setItemTitle("Item");
        auction.setShopId("shop-1");
        when(repo.getById("a-1")).thenReturn(auction);
        when(repo.atomicCloseAndReadWinner("a-1"))
                .thenReturn(new AuctionRepository.CloseResult(Map.of("winner-1", 500L), null));

        service.closeAuction("a-1");

        verify(publisher).publishAuctionClosed(any());
    }

    @Test
    void closeAuction_publishFails_rollsBack() {
        Auction auction = new Auction();
        auction.setAuctionId("a-1");
        auction.setQuantity(1);
        auction.setItemId("item-1");
        auction.setItemTitle("Item");
        auction.setShopId("shop-1");
        when(repo.getById("a-1")).thenReturn(auction);
        when(repo.atomicCloseAndReadWinner("a-1"))
                .thenReturn(new AuctionRepository.CloseResult(Map.of("winner-1", 500L), null));
        doThrow(new RuntimeException("publish failed")).when(publisher).publishAuctionClosed(any());

        assertThrows(RuntimeException.class, () -> service.closeAuction("a-1"));
        verify(repo).rollbackClose("a-1");
    }

    @Test
    void closeAuction_noRedisWinners_fallsToDynamo() {
        Auction auction = new Auction();
        auction.setAuctionId("a-1");
        auction.setQuantity(1);
        auction.setItemId("item-1");
        auction.setItemTitle("Item");
        auction.setShopId("shop-1");
        when(repo.getById("a-1")).thenReturn(auction);
        when(repo.atomicCloseAndReadWinner("a-1"))
                .thenReturn(new AuctionRepository.CloseResult(null, null));
        when(repo.getDynamoWinners("a-1")).thenReturn(Map.of("dynamo-winner", 300L));

        service.closeAuction("a-1");

        verify(repo).getDynamoWinners("a-1");
        verify(publisher).publishAuctionClosed(any());
    }

    @Test
    void openAuction_delegatesToRepo() {
        service.openAuction("a-1");
        verify(repo).open("a-1");
    }

    @Test
    void createAuction_missingPickupStart_throws() {
        CreateAuctionRequest req = new CreateAuctionRequest(
                "item-1", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 60, 0, null,
                null, "2026-01-01T12:00:00Z", 0
        );

        var ex = assertThrows(AuctionService.ValidationException.class,
                () -> service.createAuction(req, "seller-1"));
        assertEquals("pickup_start is required", ex.getMessage());
    }

    @Test
    void createAuction_missingPickupEnd_throws() {
        Instant now = Instant.now();
        Instant pickupStart = now.plus(2, ChronoUnit.HOURS);
        CreateAuctionRequest req = new CreateAuctionRequest(
                "item-1", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 60, 0, null,
                pickupStart.toString(), null, 0
        );

        var ex = assertThrows(AuctionService.ValidationException.class,
                () -> service.createAuction(req, "seller-1"));
        assertEquals("pickup_end is required", ex.getMessage());
    }

    @Test
    void createAuction_pickupEndBeforePickupStart_throws() {
        Instant now = Instant.now();
        Instant pickupStart = now.plus(3, ChronoUnit.HOURS);
        Instant pickupEnd = now.plus(2, ChronoUnit.HOURS);
        CreateAuctionRequest req = new CreateAuctionRequest(
                "item-1", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 60, 0, null,
                pickupStart.toString(), pickupEnd.toString(), 0
        );

        var ex = assertThrows(AuctionService.ValidationException.class,
                () -> service.createAuction(req, "seller-1"));
        assertEquals("pickup_end must be after pickup_start", ex.getMessage());
    }

    @Test
    void createAuction_scheduledInPast_treatsAsOpen() {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant pickupStart = Instant.now().plus(2, ChronoUnit.HOURS);
        Instant pickupEnd = pickupStart.plus(1, ChronoUnit.HOURS);
        CreateAuctionRequest req = new CreateAuctionRequest(
                "item-1", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 60, 0, past.toString(),
                pickupStart.toString(), pickupEnd.toString(), 0
        );

        Auction result = service.createAuction(req, "seller-1");
        assertEquals("OPEN", result.getStatus());
    }

    @Test
    void createAuction_durationMinutesFallback_usesAlternateField() {
        Instant now = Instant.now();
        Instant pickupStart = now.plus(2, ChronoUnit.HOURS);
        Instant pickupEnd = pickupStart.plus(1, ChronoUnit.HOURS);
        CreateAuctionRequest req = new CreateAuctionRequest(
                "item-1", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 0, 0, null,
                pickupStart.toString(), pickupEnd.toString(), 60
        );

        Auction result = service.createAuction(req, "seller-1");
        assertNotNull(result.getAuctionId());
        assertEquals("OPEN", result.getStatus());
    }

    @Test
    void createAuction_quantityZero_defaultsToOne() {
        Instant now = Instant.now();
        Instant pickupStart = now.plus(2, ChronoUnit.HOURS);
        Instant pickupEnd = pickupStart.plus(1, ChronoUnit.HOURS);
        CreateAuctionRequest req = new CreateAuctionRequest(
                "item-1", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 0,
                null, null, null, null, 60, 0, null,
                pickupStart.toString(), pickupEnd.toString(), 0
        );

        Auction result = service.createAuction(req, "seller-1");
        assertEquals(1, result.getQuantity());
    }

    @Test
    void createAuction_durationTooLarge_throws() {
        Instant now = Instant.now();
        Instant pickupStart = now.plus(500, ChronoUnit.HOURS);
        Instant pickupEnd = pickupStart.plus(1, ChronoUnit.HOURS);
        CreateAuctionRequest req = new CreateAuctionRequest(
                "item-1", "Title", "shop-1", "Shop", 0, 0, 0, 0, 0, 1,
                null, null, null, null, 20000, 0, null,
                pickupStart.toString(), pickupEnd.toString(), 0
        );

        var ex = assertThrows(AuctionService.ValidationException.class,
                () -> service.createAuction(req, "seller-1"));
        assertEquals("duration must be between 1 and 10080 minutes", ex.getMessage());
    }

    @Test
    void listAuctionsNear_includesPendingAuctions() {
        when(repo.getShopIdsNear(40.0, -74.0, 5.0)).thenReturn(List.of("shop-1"));

        Auction open = new Auction();
        open.setStatus("OPEN");
        Auction pending = new Auction();
        pending.setStatus("PENDING");
        Auction closed = new Auction();
        closed.setStatus("CLOSED");
        when(repo.listByShop("shop-1")).thenReturn(List.of(open, pending, closed));

        List<Auction> result = service.listAuctionsNear(40.0, -74.0, 5.0);
        assertEquals(2, result.size());
    }

    @Test
    void getMetrics_delegatesToCollector() {
        service.getMetrics();
        verify(metrics).snapshot();
    }

    @Test
    void resetMetrics_delegatesToCollector() {
        service.resetMetrics();
        verify(metrics).reset();
    }

    @Test
    void placeBid_publishFailure_stillReturnsBidResult() {
        Auction auction = new Auction();
        auction.setSellerId("seller-1");
        auction.setHighestBidder("");
        auction.setCurrentHighest(0);
        auction.setItemId("item-1");
        auction.setItemTitle("Item");
        auction.setShopId("shop-1");
        auction.setShopName("Shop");
        when(repo.getById("a-1")).thenReturn(auction);
        when(pessimistic.tryPlaceBid("a-1", 100, "user-1"))
                .thenReturn(new BidPlacement(1, "", 100));
        doThrow(new RuntimeException("publish failed")).when(publisher).publishBidPlaced(any());

        BidResult result = service.placeBid("a-1", "user-1", 100);

        assertNotNull(result);
        assertEquals("ACCEPTED", result.status());
    }

    @Test
    void closeAuction_noWinnersAnywhere_publishesWithEmptyWinner() {
        Auction auction = new Auction();
        auction.setAuctionId("a-1");
        auction.setQuantity(1);
        auction.setItemId("item-1");
        auction.setItemTitle("Item");
        auction.setShopId("shop-1");
        when(repo.getById("a-1")).thenReturn(auction);
        when(repo.atomicCloseAndReadWinner("a-1"))
                .thenReturn(new AuctionRepository.CloseResult(null, null));
        when(repo.getDynamoWinners("a-1")).thenReturn(Map.of());

        service.closeAuction("a-1");

        verify(publisher).publishAuctionClosed(any());
    }

    @Test
    void closeAuction_emptyRedisWinners_fallsToDynamo() {
        Auction auction = new Auction();
        auction.setAuctionId("a-1");
        auction.setQuantity(1);
        auction.setItemId("item-1");
        auction.setItemTitle("Item");
        auction.setShopId("shop-1");
        when(repo.getById("a-1")).thenReturn(auction);
        when(repo.atomicCloseAndReadWinner("a-1"))
                .thenReturn(new AuctionRepository.CloseResult(Map.of(), null));
        when(repo.getDynamoWinners("a-1")).thenReturn(Map.of("user-1", 300L));

        service.closeAuction("a-1");

        verify(repo).getDynamoWinners("a-1");
        verify(publisher).publishAuctionClosed(any());
    }
}
