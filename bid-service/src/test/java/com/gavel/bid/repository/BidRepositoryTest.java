package com.gavel.bid.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gavel.bid.model.Bid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BidRepositoryTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private ZSetOperations<String, String> zsetOps;

    private ObjectMapper objectMapper;
    private BidRepository repository;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForZSet()).thenReturn(zsetOps);
        repository = new BidRepository(redisTemplate, objectMapper);
    }

    private Bid makeBid(String bidId, String auctionId, String userId, long amount, String status) {
        return new Bid(bidId, auctionId, userId, "Item", "shop-1", "Shop", amount, Instant.now(), status);
    }

    @Test
    void create_storesInAllKeys() {
        Bid bid = makeBid("b-1", "a-1", "u-1", 500, "ACCEPTED");
        repository.create(bid);

        verify(valueOps).set(eq("bid:b-1"), anyString());
        verify(zsetOps).add(eq("bids:auction:a-1"), eq("b-1"), anyDouble());
        verify(zsetOps).add(eq("bids:user:u-1"), eq("b-1"), anyDouble());
    }

    @Test
    void getByAuction_emptySet_returnsEmptyList() {
        when(zsetOps.reverseRange("bids:auction:a-1", 0, -1)).thenReturn(null);
        List<Bid> result = repository.getByAuction("a-1");
        assertTrue(result.isEmpty());
    }

    @Test
    void getByAuction_withBids_returnsList() throws Exception {
        Bid bid = makeBid("b-1", "a-1", "u-1", 500, "ACCEPTED");
        String json = objectMapper.writeValueAsString(bid);

        when(zsetOps.reverseRange("bids:auction:a-1", 0, -1))
                .thenReturn(new LinkedHashSet<>(List.of("b-1")));
        when(valueOps.multiGet(List.of("bid:b-1"))).thenReturn(List.of(json));

        List<Bid> result = repository.getByAuction("a-1");
        assertEquals(1, result.size());
        assertEquals("b-1", result.get(0).getBidId());
    }

    @Test
    void getByUser_emptySet_returnsEmptyList() {
        when(zsetOps.reverseRange("bids:user:u-1", 0, -1)).thenReturn(null);
        List<Bid> result = repository.getByUser("u-1");
        assertTrue(result.isEmpty());
    }

    @Test
    void getByAuction_nullMultiGet_returnsEmptyList() {
        when(zsetOps.reverseRange("bids:auction:a-1", 0, -1))
                .thenReturn(new LinkedHashSet<>(List.of("b-1")));
        when(valueOps.multiGet(anyList())).thenReturn(null);

        List<Bid> result = repository.getByAuction("a-1");
        assertTrue(result.isEmpty());
    }

    @Test
    void markWon_setsWinnerStatus() throws Exception {
        Bid bid = makeBid("b-1", "a-1", "u-1", 500, "ACCEPTED");
        String json = objectMapper.writeValueAsString(bid);

        when(zsetOps.range("bids:auction:a-1", 0, -1))
                .thenReturn(new LinkedHashSet<>(List.of("b-1")));
        when(valueOps.get("bid:b-1")).thenReturn(json);

        repository.markWon("a-1", "u-1");

        verify(valueOps).set(eq("bid:b-1"), argThat(s -> s.contains("WON")));
    }

    @Test
    void markWon_nullIds_returnsEarly() {
        when(zsetOps.range("bids:auction:a-1", 0, -1)).thenReturn(null);
        repository.markWon("a-1", "u-1");
        verify(valueOps, never()).get(anyString());
    }

    @Test
    void markWon_nonMatchingUser_doesNotUpdate() throws Exception {
        Bid bid = makeBid("b-1", "a-1", "u-2", 500, "ACCEPTED");
        String json = objectMapper.writeValueAsString(bid);

        when(zsetOps.range("bids:auction:a-1", 0, -1))
                .thenReturn(new LinkedHashSet<>(List.of("b-1")));
        when(valueOps.get("bid:b-1")).thenReturn(json);

        repository.markWon("a-1", "u-1");

        verify(valueOps, never()).set(eq("bid:b-1"), argThat(s -> s.contains("WON")));
    }

    @Test
    void markOutbid_setsOutbidStatus() throws Exception {
        Bid bid = makeBid("b-1", "a-1", "u-1", 500, "ACCEPTED");
        String json = objectMapper.writeValueAsString(bid);

        when(zsetOps.range("bids:auction:a-1", 0, -1))
                .thenReturn(new LinkedHashSet<>(List.of("b-1", "b-2")));
        when(valueOps.get("bid:b-1")).thenReturn(json);

        repository.markOutbid("a-1", "b-2");

        verify(valueOps).set(eq("bid:b-1"), argThat(s -> s.contains("OUTBID")));
    }

    @Test
    void markOutbid_skipsExcludedBid() throws Exception {
        when(zsetOps.range("bids:auction:a-1", 0, -1))
                .thenReturn(new LinkedHashSet<>(List.of("b-1")));

        repository.markOutbid("a-1", "b-1");

        verify(valueOps, never()).get("bid:b-1");
    }

    @Test
    void markOutbid_nullIds_returnsEarly() {
        when(zsetOps.range("bids:auction:a-1", 0, -1)).thenReturn(null);
        repository.markOutbid("a-1", "b-1");
        verify(valueOps, never()).get(anyString());
    }

    @Test
    void markUserPreviousBids_marksOnlyMatchingUserBids() throws Exception {
        Bid bid1 = makeBid("b-1", "a-1", "u-1", 400, "ACCEPTED");
        Bid bid2 = makeBid("b-2", "a-1", "u-2", 500, "ACCEPTED");
        String json1 = objectMapper.writeValueAsString(bid1);
        String json2 = objectMapper.writeValueAsString(bid2);

        when(zsetOps.range("bids:auction:a-1", 0, -1))
                .thenReturn(new LinkedHashSet<>(List.of("b-1", "b-2", "b-3")));
        when(valueOps.get("bid:b-1")).thenReturn(json1);
        when(valueOps.get("bid:b-2")).thenReturn(json2);

        repository.markUserPreviousBids("a-1", "u-1", "b-3");

        verify(valueOps).set(eq("bid:b-1"), argThat(s -> s.contains("OUTBID")));
        verify(valueOps, never()).set(eq("bid:b-2"), argThat(s -> s.contains("OUTBID")));
    }

    @Test
    void markUserPreviousBids_nullRaw_skips() throws Exception {
        when(zsetOps.range("bids:auction:a-1", 0, -1))
                .thenReturn(new LinkedHashSet<>(List.of("b-1")));
        when(valueOps.get("bid:b-1")).thenReturn(null);

        repository.markUserPreviousBids("a-1", "u-1", "b-2");

        verify(valueOps, never()).set(eq("bid:b-1"), anyString());
    }
}
