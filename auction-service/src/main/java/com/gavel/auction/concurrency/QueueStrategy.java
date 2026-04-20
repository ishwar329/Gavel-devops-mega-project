package com.gavel.auction.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

@Component
public class QueueStrategy implements ConcurrencyStrategy {

    private static final Logger log = LoggerFactory.getLogger(QueueStrategy.class);
    private static final int QUEUE_CAPACITY = 1000;

    private final ConcurrentHashMap<String, BlockingQueue<BidTask>> queues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Future<?>> processors = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final StringRedisTemplate redisTemplate;

    public QueueStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public BidPlacement tryPlaceBid(String auctionId, long amount, String bidderId) {
        BlockingQueue<BidTask> queue = queues.computeIfAbsent(auctionId, k -> {
            LinkedBlockingQueue<BidTask> q = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
            processors.computeIfAbsent(auctionId, id -> executor.submit(() -> processQueue(id, q)));
            return q;
        });

        CompletableFuture<BidPlacement> future = new CompletableFuture<>();
        BidTask task = new BidTask(auctionId, amount, bidderId, future);

        if (!queue.offer(task)) {
            throw new RuntimeException("Bid queue full for auction " + auctionId);
        }

        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof BidException be) throw be;
            throw new RuntimeException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted waiting for bid result", e);
        } catch (TimeoutException e) {
            throw new RuntimeException("Bid processing timed out for auction " + auctionId, e);
        }
    }

    public void stop(String auctionId) {
        queues.remove(auctionId);
        Future<?> proc = processors.remove(auctionId);
        if (proc != null) {
            proc.cancel(true);
        }
    }

    private void processQueue(String auctionId, BlockingQueue<BidTask> queue) {
        String hashKey = "auction:" + auctionId;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                BidTask task = queue.poll(2, TimeUnit.SECONDS);
                if (task == null) continue;

                try {
                    BidPlacement result = executeBid(hashKey, task);
                    task.future().complete(result);
                } catch (Exception e) {
                    task.future().completeExceptionally(e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private BidPlacement executeBid(String hashKey, BidTask task) {
        String status = (String) redisTemplate.opsForHash().get(hashKey, "status");
        if (status == null) {
            throw new BidException(BidException.NOT_FOUND, "auction not found", 0);
        }
        if (!"OPEN".equals(status)) {
            throw new BidException(BidException.NOT_OPEN, "auction not open", 0);
        }

        String maxPriceStr = (String) redisTemplate.opsForHash().get(hashKey, "max_price");
        long maxPrice = maxPriceStr != null ? Long.parseLong(maxPriceStr) : 0;
        if (maxPrice > 0 && task.amount() > maxPrice) {
            throw new BidException(BidException.EXCEEDS_MAX, "bid exceeds max price", maxPrice);
        }

        String currentStr = (String) redisTemplate.opsForHash().get(hashKey, "current_highest");
        long current = currentStr != null ? Long.parseLong(currentStr) : 0;
        if (task.amount() <= current) {
            throw new BidException(BidException.BID_TOO_LOW, "bid must be higher", current);
        }

        String minIncrStr = (String) redisTemplate.opsForHash().get(hashKey, "min_increment");
        long minIncrement = minIncrStr != null ? Long.parseLong(minIncrStr) : 0;
        if (minIncrement > 0 && current > 0 && task.amount() < current + minIncrement) {
            throw new BidException(BidException.INCREMENT_TOO_SMALL,
                    "bid increment too small", current + minIncrement);
        }

        String versionStr = (String) redisTemplate.opsForHash().get(hashKey, "version");
        long version = versionStr != null ? Long.parseLong(versionStr) : 0;
        long newVersion = version + 1;

        redisTemplate.opsForHash().put(hashKey, "current_highest", String.valueOf(task.amount()));
        redisTemplate.opsForHash().put(hashKey, "highest_bidder", task.bidderId());
        redisTemplate.opsForHash().put(hashKey, "version", String.valueOf(newVersion));
        redisTemplate.opsForHash().increment(hashKey, "bid_count", 1);

        return new BidPlacement(newVersion, "", task.amount());
    }

    @Override
    public String name() {
        return "queue";
    }

    private record BidTask(String auctionId, long amount, String bidderId, CompletableFuture<BidPlacement> future) {}
}
