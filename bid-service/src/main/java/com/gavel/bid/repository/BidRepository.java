package com.gavel.bid.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.bid.model.Bid;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Repository
public class BidRepository {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public BidRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void create(Bid bid) {
        String data = serialize(bid);
        double score = (double) bid.getTimestamp().toEpochMilli();

        redisTemplate.opsForValue().set(bidDetailKey(bid.getBidId()), data);
        redisTemplate.opsForZSet().add(auctionBidsKey(bid.getAuctionId()), bid.getBidId(), score);
        redisTemplate.opsForZSet().add(userBidsKey(bid.getUserId()), bid.getBidId(), score);
    }

    public List<Bid> getByAuction(String auctionId) {
        return getBidsByKey(auctionBidsKey(auctionId));
    }

    public List<Bid> getByUser(String userId) {
        return getBidsByKey(userBidsKey(userId));
    }

    public void markWon(String auctionId, String winnerId) {
        Set<String> ids = redisTemplate.opsForZSet().range(auctionBidsKey(auctionId), 0, -1);
        if (ids == null) return;

        for (String id : ids) {
            String raw = redisTemplate.opsForValue().get(bidDetailKey(id));
            if (raw == null) continue;

            Bid bid = deserialize(raw);
            if (bid == null) continue;

            if (winnerId.equals(bid.getUserId()) && "ACCEPTED".equals(bid.getStatus())) {
                bid.setStatus("WON");
                redisTemplate.opsForValue().set(bidDetailKey(id), serialize(bid));
                return;
            }
        }
    }

    public void markUserPreviousBids(String auctionId, String userId, String excludeBidId) {
        Set<String> ids = redisTemplate.opsForZSet().range(auctionBidsKey(auctionId), 0, -1);
        if (ids == null) return;

        for (String id : ids) {
            if (id.equals(excludeBidId)) continue;

            String raw = redisTemplate.opsForValue().get(bidDetailKey(id));
            if (raw == null) continue;

            Bid bid = deserialize(raw);
            if (bid == null) continue;

            if (userId.equals(bid.getUserId()) && "ACCEPTED".equals(bid.getStatus())) {
                bid.setStatus("OUTBID");
                bid.setTimestamp(Instant.now());
                redisTemplate.opsForValue().set(bidDetailKey(id), serialize(bid));
            }
        }
    }

    public void markOutbid(String auctionId, String excludeBidId) {
        Set<String> ids = redisTemplate.opsForZSet().range(auctionBidsKey(auctionId), 0, -1);
        if (ids == null) return;

        for (String id : ids) {
            if (id.equals(excludeBidId)) continue;

            String raw = redisTemplate.opsForValue().get(bidDetailKey(id));
            if (raw == null) continue;

            Bid bid = deserialize(raw);
            if (bid == null) continue;

            if ("ACCEPTED".equals(bid.getStatus())) {
                bid.setStatus("OUTBID");
                bid.setTimestamp(Instant.now());
                redisTemplate.opsForValue().set(bidDetailKey(id), serialize(bid));
            }
        }
    }

    private List<Bid> getBidsByKey(String key) {
        Set<String> ids = redisTemplate.opsForZSet().reverseRange(key, 0, -1);
        if (ids == null || ids.isEmpty()) return Collections.emptyList();

        List<String> keys = ids.stream().map(this::bidDetailKey).toList();
        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        if (values == null) return Collections.emptyList();

        List<Bid> bids = new ArrayList<>();
        for (String val : values) {
            if (val == null) continue;
            Bid bid = deserialize(val);
            if (bid != null) bids.add(bid);
        }
        return bids;
    }

    private String serialize(Bid bid) {
        try {
            return objectMapper.writeValueAsString(bid);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize bid", e);
        }
    }

    private Bid deserialize(String json) {
        try {
            return objectMapper.readValue(json, Bid.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String auctionBidsKey(String auctionId) { return "bids:auction:" + auctionId; }
    private String userBidsKey(String userId) { return "bids:user:" + userId; }
    private String bidDetailKey(String bidId) { return "bid:" + bidId; }
}
