package com.gavel.auction.concurrency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PessimisticStrategyTest {

    @Mock private StringRedisTemplate redisTemplate;
    private PessimisticStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new PessimisticStrategy(redisTemplate);
    }

    @SuppressWarnings("unchecked")
    private void stubLuaResult(List<String> result) {
        doReturn(result).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any());
    }

    @Test
    void tryPlaceBid_success_returnsBidPlacement() {
        stubLuaResult(List.of("5", "prev-user", "600"));

        BidPlacement result = strategy.tryPlaceBid("a-1", 600, "user-1");

        assertEquals(5, result.newVersion());
        assertEquals("prev-user", result.evictedBidder());
        assertEquals(600, result.newFloor());
    }

    @Test
    void tryPlaceBid_notFound_throwsBidException() {
        stubLuaResult(List.of("-1", "", "0"));

        BidException ex = assertThrows(BidException.class,
                () -> strategy.tryPlaceBid("a-1", 100, "user-1"));
        assertEquals(BidException.NOT_FOUND, ex.getCode());
    }

    @Test
    void tryPlaceBid_notOpen_throwsBidException() {
        stubLuaResult(List.of("-2", "", "0"));

        BidException ex = assertThrows(BidException.class,
                () -> strategy.tryPlaceBid("a-1", 100, "user-1"));
        assertEquals(BidException.NOT_OPEN, ex.getCode());
    }

    @Test
    void tryPlaceBid_bidTooLow_throwsWithThreshold() {
        stubLuaResult(List.of("-3", "", "500"));

        BidException ex = assertThrows(BidException.class,
                () -> strategy.tryPlaceBid("a-1", 100, "user-1"));
        assertEquals(BidException.BID_TOO_LOW, ex.getCode());
        assertEquals(500, ex.getThreshold());
    }

    @Test
    void tryPlaceBid_exceedsMax_throwsBidException() {
        stubLuaResult(List.of("-4", "", "1000"));

        BidException ex = assertThrows(BidException.class,
                () -> strategy.tryPlaceBid("a-1", 2000, "user-1"));
        assertEquals(BidException.EXCEEDS_MAX, ex.getCode());
    }

    @Test
    void tryPlaceBid_incrementTooSmall_throwsBidException() {
        stubLuaResult(List.of("-5", "", "500"));

        BidException ex = assertThrows(BidException.class,
                () -> strategy.tryPlaceBid("a-1", 501, "user-1"));
        assertEquals(BidException.INCREMENT_TOO_SMALL, ex.getCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    void tryPlaceBid_nullResult_throwsNotFound() {
        doReturn(null).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any());

        BidException ex = assertThrows(BidException.class,
                () -> strategy.tryPlaceBid("a-1", 100, "user-1"));
        assertEquals(BidException.NOT_FOUND, ex.getCode());
    }

    @Test
    void tryPlaceBid_shortResult_throwsNotFound() {
        stubLuaResult(List.of("1"));

        BidException ex = assertThrows(BidException.class,
                () -> strategy.tryPlaceBid("a-1", 100, "user-1"));
        assertEquals(BidException.NOT_FOUND, ex.getCode());
    }
}
