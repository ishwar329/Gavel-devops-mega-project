package com.gavel.auction.concurrency;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class PessimisticStrategy {

    private final StringRedisTemplate redisTemplate;
    private final String luaScript;

    public PessimisticStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        try {
            this.luaScript = new ClassPathResource("lua/place_bid.lua")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load place_bid.lua", e);
        }
    }

    @SuppressWarnings("unchecked")
    public BidPlacement tryPlaceBid(String auctionId, long amount, String bidderId) {
        String hashKey = "auction:" + auctionId;
        String bidsKey = "auction:" + auctionId + ":bids";

        DefaultRedisScript<List> script = new DefaultRedisScript<>(luaScript, List.class);
        List<String> result = redisTemplate.execute(script,
                List.of(hashKey, bidsKey),
                String.valueOf(amount), bidderId);

        if (result == null || result.size() < 3) {
            throw new BidException(BidException.NOT_FOUND, "auction not found", 0);
        }

        String code = result.get(0).toString();
        String evicted = result.get(1).toString();
        long floor = Long.parseLong(result.get(2).toString().replace(".0", "").split("\\.")[0]);

        switch (code) {
            case "-1":
                throw new BidException(BidException.NOT_FOUND, "auction not found", 0);
            case "-2":
                throw new BidException(BidException.NOT_OPEN, "auction not open", 0);
            case "-3":
                throw new BidException(BidException.BID_TOO_LOW, "bid must be higher", floor);
            case "-4":
                throw new BidException(BidException.EXCEEDS_MAX, "bid exceeds max price", floor);
            case "-5":
                throw new BidException(BidException.INCREMENT_TOO_SMALL, "bid increment too small", floor);
        }

        long newVersion = Long.parseLong(code);
        return new BidPlacement(newVersion, evicted, floor);
    }

}
