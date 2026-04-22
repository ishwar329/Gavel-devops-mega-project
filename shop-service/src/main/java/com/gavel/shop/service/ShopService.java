package com.gavel.shop.service;

import com.gavel.shared.events.ItemCreatedEvent;
import com.gavel.shared.events.ReviewCreatedEvent;
import com.gavel.shared.kafka.KafkaPublisher;
import com.gavel.shared.kafka.Topics;
import com.gavel.shop.model.*;
import com.gavel.shop.repository.ShopRepository;
import com.gavel.shop.storage.S3Uploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.time.Instant;
import java.util.*;

@Service
public class ShopService {

    private static final Logger log = LoggerFactory.getLogger(ShopService.class);

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final Map<String, String> ALLOWED_MIME_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif"
    );

    private final ShopRepository repository;
    private final S3Uploader uploader;
    private final KafkaPublisher kafkaPublisher;
    private final String publicUrl;
    private final RestClient paymentClient;

    public ShopService(ShopRepository repository,
                       S3Uploader uploader,
                       KafkaPublisher kafkaPublisher,
                       @Value("${aws.s3.public-url:http://localhost:3000/uploads}") String publicUrl,
                       @Value("${payment.service.url:}") String paymentServiceUrl) {
        this.repository = repository;
        this.uploader = uploader;
        this.kafkaPublisher = kafkaPublisher;
        this.publicUrl = publicUrl;
        this.paymentClient = paymentServiceUrl != null && !paymentServiceUrl.isBlank()
                ? RestClient.builder().baseUrl(paymentServiceUrl).build()
                : null;
    }

    public Shop createShop(CreateShopRequest request, String ownerId) {
        if (request.lat() == null || request.lng() == null
                || request.lat() == 0.0 && request.lng() == 0.0) {
            throw new InvalidInputException(
                    "shop location coordinates are required — use the Pin my location button");
        }
        Shop shop = new Shop();
        shop.setShopId(UUID.randomUUID().toString());
        shop.setName(request.name());
        shop.setLocation(request.location());
        shop.setOwnerId(ownerId);
        shop.setLogoUrl(request.logoUrl());
        shop.setLat(request.lat());
        shop.setLng(request.lng());
        repository.saveShop(shop);
        return shop;
    }

    public Shop updateShop(String shopId, UpdateShopRequest request, String callerId) {
        Shop shop = repository.findShopById(shopId)
                .orElseThrow(NotFoundException::new);
        if (!shop.getOwnerId().equals(callerId)) {
            throw new ForbiddenException();
        }
        if (request.name() != null && !request.name().isBlank()) {
            shop.setName(request.name());
        }
        if (request.location() != null && !request.location().isBlank()) {
            shop.setLocation(request.location());
        }
        shop.setLogoUrl(request.logoUrl());
        if (request.lat() != null && request.lng() != null
                && !(request.lat() == 0.0 && request.lng() == 0.0)) {
            shop.setLat(request.lat());
            shop.setLng(request.lng());
        }
        repository.saveShop(shop);
        return shop;
    }

    public Shop getShop(String shopId) {
        return repository.findShopById(shopId)
                .orElseThrow(NotFoundException::new);
    }

    public Item getItem(String itemId) {
        return repository.findItemById(itemId)
                .orElseThrow(NotFoundException::new);
    }

    public Item createItem(String shopId, CreateItemRequest request, String callerId) {
        if (request.retailValue() == null || request.retailValue() <= 0) {
            throw new InvalidInputException("retail_value must be greater than 0");
        }
        Shop shop = repository.findShopById(shopId)
                .orElseThrow(NotFoundException::new);
        if (!shop.getOwnerId().equals(callerId)) {
            throw new ForbiddenException();
        }
        Item item = new Item();
        item.setItemId(UUID.randomUUID().toString());
        item.setShopId(shopId);
        item.setTitle(request.title());
        item.setDescription(request.description());
        item.setRetailValue(request.retailValue());
        item.setImageUrl(request.imageUrl());
        item.setCategory(request.category());
        repository.saveItem(item);
        publishItemCreated(item);
        return item;
    }

    private void publishItemCreated(Item item) {
        try {
            var event = new ItemCreatedEvent(
                    item.getItemId(), item.getShopId(), item.getTitle(),
                    item.getDescription(), item.getRetailValue() != null ? item.getRetailValue() : 0,
                    item.getCategory(), item.getImageUrl());
            kafkaPublisher.publish(Topics.ITEM_CREATED, item.getItemId(), event);
        } catch (Exception e) {
            log.warn("Failed to publish item.created event: {}", e.getMessage());
        }
    }

    public List<Shop> listSellerShops(String ownerId) {
        return repository.findShopsByOwnerId(ownerId);
    }

    public List<Item> listItems(String shopId) {
        repository.findShopById(shopId).orElseThrow(NotFoundException::new);
        return repository.findItemsByShopId(shopId);
    }

    public Review createReview(String shopId, CreateReviewRequest request,
                               String reviewerId, String reviewerUsername, String authToken) {
        repository.findShopById(shopId).orElseThrow(NotFoundException::new);

        if (paymentClient != null) {
            if (!hasCompletedPayment(request.auctionId(), reviewerId, authToken)) {
                throw new PaymentNotCompletedException();
            }
        }

        if (repository.findReviewByAuctionAndReviewer(request.auctionId(), reviewerId).isPresent()) {
            throw new AlreadyReviewedException();
        }

        String now = Instant.now().toString();
        Review review = new Review();
        review.setReviewId(UUID.randomUUID().toString());
        review.setShopId(shopId);
        review.setReviewerId(reviewerId);
        review.setReviewerUsername(reviewerUsername);
        review.setAuctionId(request.auctionId());
        review.setRating(request.rating());
        review.setComment(request.comment());
        review.setCreatedAt(now);
        review.setUpdatedAt(now);
        repository.saveReview(review);
        publishReviewCreated(review, shopId);
        return review;
    }

    private void publishReviewCreated(Review review, String shopId) {
        try {
            String shopName = repository.findShopById(shopId)
                    .map(Shop::getName).orElse("Unknown Shop");
            var event = new ReviewCreatedEvent(
                    review.getReviewId(), shopId, shopName,
                    review.getReviewerUsername(), review.getAuctionId(),
                    review.getRating(), review.getComment());
            kafkaPublisher.publish(Topics.REVIEW_CREATED, review.getReviewId(), event);
        } catch (Exception e) {
            log.warn("Failed to publish review.created event: {}", e.getMessage());
        }
    }

    public ReviewsResponse listReviews(String shopId) {
        repository.findShopById(shopId).orElseThrow(NotFoundException::new);
        List<Review> reviews = repository.findReviewsByShopId(shopId);
        if (reviews.isEmpty()) {
            return new ReviewsResponse(reviews, 0.0, 0);
        }
        double sum = reviews.stream().mapToInt(Review::getRating).sum();
        double avg = sum / reviews.size();
        avg = (double) ((int) (avg * 10 + 0.5)) / 10;
        return new ReviewsResponse(reviews, avg, reviews.size());
    }

    public Review replyToReview(String shopId, String reviewId, String reply, String callerId) {
        Shop shop = repository.findShopById(shopId)
                .orElseThrow(NotFoundException::new);
        if (!shop.getOwnerId().equals(callerId)) {
            throw new ForbiddenException();
        }
        Review review = repository.findReviewById(reviewId)
                .orElseThrow(NotFoundException::new);
        if (!review.getShopId().equals(shopId)) {
            throw new NotFoundException();
        }
        String now = Instant.now().toString();
        repository.updateReviewReply(reviewId, reply, now);
        review.setSellerReply(reply);
        review.setUpdatedAt(now);
        return review;
    }

    public String uploadImage(String contentType, InputStream body, long size) {
        if (size > MAX_FILE_SIZE) {
            throw new FileTooLargeException();
        }
        String ext = ALLOWED_MIME_TYPES.get(contentType);
        if (ext == null) {
            throw new InvalidFileTypeException();
        }
        String key = "images/" + UUID.randomUUID() + ext;
        uploader.upload(key, contentType, body, size);
        return publicUrl + "/" + key;
    }

    private boolean hasCompletedPayment(String auctionId, String userId, String authToken) {
        try {
            var requestSpec = paymentClient.get()
                    .uri("/auctions/{auctionId}/payment", auctionId);
            if (authToken != null && !authToken.isBlank()) {
                requestSpec = requestSpec.header("Authorization", "Bearer " + authToken);
            }
            var response = requestSpec.retrieve().toEntity(Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<?, ?> body = response.getBody();
                return userId.equals(body.get("user_id"))
                        && "completed".equals(body.get("status"));
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static class NotFoundException extends RuntimeException {}
    public static class ForbiddenException extends RuntimeException {}
    public static class InvalidInputException extends RuntimeException {
        public InvalidInputException(String message) { super(message); }
    }
    public static class PaymentNotCompletedException extends RuntimeException {}
    public static class AlreadyReviewedException extends RuntimeException {}
    public static class FileTooLargeException extends RuntimeException {}
    public static class InvalidFileTypeException extends RuntimeException {}
}
