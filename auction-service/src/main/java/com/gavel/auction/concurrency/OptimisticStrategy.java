package com.gavel.auction.concurrency;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OptimisticStrategy implements ConcurrencyStrategy {

    private static final int MAX_RETRIES = 3;
    private static final long[] BACKOFF_MS = {10, 20, 40};

    private final StringRedisTemplate redisTemplate;

    public OptimisticStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    @SuppressWarnings("unchecked")
    public BidPlacement tryPlaceBid(String auctionId, long amount, String bidderId) {
        String hashKey = "auction:" + auctionId;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                List<Object> results = redisTemplate.execute(new SessionCallback<List<Object>>() {
                    @Override
                    public List<Object> execute(RedisOperations operations) throws DataAccessException {
                        operations.watch(hashKey);

                        Object statusObj = operations.opsForHash().get(hashKey, "status");
                        if (statusObj == null) {
                            operations.unwatch();
                            throw new BidException(BidException.NOT_FOUND, "auction not found", 0);
                        }
                        String status = statusObj.toString();
                        if (!"OPEN".equals(status)) {
                            operations.unwatch();
                            throw new BidException(BidException.NOT_OPEN, "auction not open", 0);
                        }

                        Object maxPriceObj = operations.opsForHash().get(hashKey, "max_price");
                        long maxPrice = maxPriceObj != null ? Long.parseLong(maxPriceObj.toString()) : 0;
                        if (maxPrice > 0 && amount > maxPrice) {
                            operations.unwatch();
                            throw new BidException(BidException.EXCEEDS_MAX, "bid exceeds max price", maxPrice);
                        }

                        Object currentObj = operations.opsForHash().get(hashKey, "current_highest");
                        long current = currentObj != null ? Long.parseLong(currentObj.toString()) : 0;
                        if (amount <= current) {
                            operations.unwatch();
                            throw new BidException(BidException.BID_TOO_LOW, "bid must be higher", current);
                        }

                        Object minIncrObj = operations.opsForHash().get(hashKey, "min_increment");
                        long minIncrement = minIncrObj != null ? Long.parseLong(minIncrObj.toString()) : 0;
                        if (minIncrement > 0 && current > 0 && amount < current + minIncrement) {
                            operations.unwatch();
                            throw new BidException(BidException.INCREMENT_TOO_SMALL,
                                    "bid increment too small", current + minIncrement);
                        }

                        Object versionObj = operations.opsForHash().get(hashKey, "version");
                        long version = versionObj != null ? Long.parseLong(versionObj.toString()) : 0;
                        long newVersion = version + 1;

                        operations.multi();
                        operations.opsForHash().put(hashKey, "current_highest", String.valueOf(amount));
                        operations.opsForHash().put(hashKey, "highest_bidder", bidderId);
                        operations.opsForHash().put(hashKey, "version", String.valueOf(newVersion));
                        operations.opsForHash().increment(hashKey, "bid_count", 1);

                        return operations.exec();
                    }
                });

                if (results != null && !results.isEmpty()) {
                    long newVersion = Long.parseLong(
                            redisTemplate.opsForHash().get(hashKey, "version").toString());
                    return new BidPlacement(newVersion, "", amount);
                }
            } catch (BidException e) {
                throw e;
            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(BACKOFF_MS[attempt]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry", ie);
                    }
                }
            }
        }
        throw new RuntimeException("Optimistic lock failed after " + MAX_RETRIES + " retries");
    }

    @Override
    public String name() {
        return "optimistic";
    }
}
