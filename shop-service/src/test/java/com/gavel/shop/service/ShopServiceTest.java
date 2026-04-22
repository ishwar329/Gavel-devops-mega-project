package com.gavel.shop.service;

import com.gavel.shared.kafka.KafkaPublisher;
import com.gavel.shop.model.*;
import com.gavel.shop.repository.ShopRepository;
import com.gavel.shop.storage.S3Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock
    private ShopRepository repository;

    @Mock
    private S3Uploader uploader;

    @Mock
    private KafkaPublisher kafkaPublisher;

    private ShopService service;

    private static final String OWNER_ID = "owner-123";
    private static final String OTHER_USER = "other-456";
    private static final String SHOP_ID = "shop-abc";

    @BeforeEach
    void setUp() {
        service = new ShopService(repository, uploader, kafkaPublisher,
                "http://localhost:3000/uploads", "");
    }

    private Shop buildShop() {
        Shop shop = new Shop();
        shop.setShopId(SHOP_ID);
        shop.setName("Test Shop");
        shop.setLocation("123 Main St");
        shop.setOwnerId(OWNER_ID);
        shop.setLogoUrl("http://logo.png");
        shop.setLat(40.7128);
        shop.setLng(-74.0060);
        return shop;
    }

    private Review buildReview(String shopId) {
        Review review = new Review();
        review.setReviewId("rev-1");
        review.setShopId(shopId);
        review.setReviewerId("reviewer-1");
        review.setReviewerUsername("reviewerUser");
        review.setAuctionId("auction-1");
        review.setRating(4);
        review.setComment("Great shop");
        review.setCreatedAt("2026-01-01T00:00:00Z");
        review.setUpdatedAt("2026-01-01T00:00:00Z");
        return review;
    }

    // ---- createShop ----

    @Nested
    class CreateShopTests {

        @Test
        void createShop_validRequest_savesAndReturns() {
            var request = new CreateShopRequest("My Shop", "Somewhere", "http://logo.png", 40.0, -74.0);

            Shop result = service.createShop(request, OWNER_ID);

            assertNotNull(result.getShopId());
            assertEquals("My Shop", result.getName());
            assertEquals("Somewhere", result.getLocation());
            assertEquals(OWNER_ID, result.getOwnerId());
            assertEquals("http://logo.png", result.getLogoUrl());
            assertEquals(40.0, result.getLat());
            assertEquals(-74.0, result.getLng());
            verify(repository).saveShop(any(Shop.class));
        }

        @Test
        void createShop_nullCoords_throwsInvalidInput() {
            var request = new CreateShopRequest("Shop", "Loc", null, null, null);

            assertThrows(ShopService.InvalidInputException.class,
                    () -> service.createShop(request, OWNER_ID));
            verify(repository, never()).saveShop(any());
        }

        @Test
        void createShop_zeroCoords_throwsInvalidInput() {
            var request = new CreateShopRequest("Shop", "Loc", null, 0.0, 0.0);

            assertThrows(ShopService.InvalidInputException.class,
                    () -> service.createShop(request, OWNER_ID));
            verify(repository, never()).saveShop(any());
        }

        @Test
        void createShop_latNullLngPresent_throwsInvalidInput() {
            var request = new CreateShopRequest("Shop", "Loc", null, null, -74.0);

            assertThrows(ShopService.InvalidInputException.class,
                    () -> service.createShop(request, OWNER_ID));
        }

        @Test
        void createShop_latPresentLngNull_throwsInvalidInput() {
            var request = new CreateShopRequest("Shop", "Loc", null, 40.0, null);

            assertThrows(ShopService.InvalidInputException.class,
                    () -> service.createShop(request, OWNER_ID));
        }

        @Test
        void createShop_latZeroLngNonZero_succeeds() {
            // lat==0 && lng!=0 -> the condition (lat==0 && lng==0) is false, so it passes
            var request = new CreateShopRequest("Shop", "Loc", null, 0.0, 10.0);

            Shop result = service.createShop(request, OWNER_ID);

            assertNotNull(result.getShopId());
            verify(repository).saveShop(any(Shop.class));
        }
    }

    // ---- updateShop ----

    @Nested
    class UpdateShopTests {

        @Test
        void updateShop_notFound_throwsNotFound() {
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.empty());

            assertThrows(ShopService.NotFoundException.class,
                    () -> service.updateShop(SHOP_ID, new UpdateShopRequest(null, null, null, null, null), OWNER_ID));
        }

        @Test
        void updateShop_notOwner_throwsForbidden() {
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.of(buildShop()));

            assertThrows(ShopService.ForbiddenException.class,
                    () -> service.updateShop(SHOP_ID, new UpdateShopRequest("New", null, null, null, null), OTHER_USER));
            verify(repository, never()).saveShop(any());
        }

        @Test
        void updateShop_partialUpdate_nameOnly() {
            Shop existing = buildShop();
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.of(existing));

            Shop result = service.updateShop(SHOP_ID,
                    new UpdateShopRequest("Updated Name", null, "new-logo.png", null, null), OWNER_ID);

            assertEquals("Updated Name", result.getName());
            assertEquals("123 Main St", result.getLocation()); // unchanged
            assertEquals("new-logo.png", result.getLogoUrl());
            verify(repository).saveShop(existing);
        }

        @Test
        void updateShop_partialUpdate_locationOnly() {
            Shop existing = buildShop();
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.of(existing));

            Shop result = service.updateShop(SHOP_ID,
                    new UpdateShopRequest(null, "New Location", null, null, null), OWNER_ID);

            assertEquals("Test Shop", result.getName()); // unchanged because name was null
            assertEquals("New Location", result.getLocation());
            verify(repository).saveShop(existing);
        }

        @Test
        void updateShop_blankNameAndLocation_notUpdated() {
            Shop existing = buildShop();
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.of(existing));

            Shop result = service.updateShop(SHOP_ID,
                    new UpdateShopRequest("  ", "  ", null, null, null), OWNER_ID);

            assertEquals("Test Shop", result.getName());
            assertEquals("123 Main St", result.getLocation());
            verify(repository).saveShop(existing);
        }

        @Test
        void updateShop_withValidCoords_updatesLatLng() {
            Shop existing = buildShop();
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.of(existing));

            Shop result = service.updateShop(SHOP_ID,
                    new UpdateShopRequest(null, null, null, 51.5, -0.12), OWNER_ID);

            assertEquals(51.5, result.getLat());
            assertEquals(-0.12, result.getLng());
            verify(repository).saveShop(existing);
        }

        @Test
        void updateShop_zeroCoordsSkipped() {
            Shop existing = buildShop();
            Double originalLat = existing.getLat();
            Double originalLng = existing.getLng();
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.of(existing));

            Shop result = service.updateShop(SHOP_ID,
                    new UpdateShopRequest(null, null, null, 0.0, 0.0), OWNER_ID);

            assertEquals(originalLat, result.getLat());
            assertEquals(originalLng, result.getLng());
            verify(repository).saveShop(existing);
        }

        @Test
        void updateShop_oneCoordNull_skipsUpdate() {
            Shop existing = buildShop();
            Double originalLat = existing.getLat();
            Double originalLng = existing.getLng();
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.of(existing));

            Shop result = service.updateShop(SHOP_ID,
                    new UpdateShopRequest(null, null, null, 51.5, null), OWNER_ID);

            assertEquals(originalLat, result.getLat());
            assertEquals(originalLng, result.getLng());
        }
    }

    // ---- getShop ----

    @Nested
    class GetShopTests {

        @Test
        void getShop_found_returnsShop() {
            Shop shop = buildShop();
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.of(shop));

            Shop result = service.getShop(SHOP_ID);

            assertEquals(shop, result);
        }

        @Test
        void getShop_notFound_throwsNotFound() {
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.empty());

            assertThrows(ShopService.NotFoundException.class,
                    () -> service.getShop(SHOP_ID));
        }
    }

    // ---- getItem ----

    @Nested
    class GetItemTests {

        @Test
        void getItem_found_returnsItem() {
            Item item = new Item();
            item.setItemId("item-1");
            when(repository.findItemById("item-1")).thenReturn(Optional.of(item));

            Item result = service.getItem("item-1");

            assertEquals(item, result);
        }

        @Test
        void getItem_notFound_throwsNotFound() {
            when(repository.findItemById("item-1")).thenReturn(Optional.empty());

            assertThrows(ShopService.NotFoundException.class,
                    () -> service.getItem("item-1"));
        }
    }

    // ---- createItem ----

    @Nested
    class CreateItemTests {

        @Test
        void createItem_nullRetailValue_throwsInvalidInput() {
            var request = new CreateItemRequest("Title", "Desc", null, "http://img.png", "food");

            assertThrows(ShopService.InvalidInputException.class,
                    () -> service.createItem(SHOP_ID, request, OWNER_ID));
        }

        @Test
        void createItem_zeroRetailValue_throwsInvalidInput() {
            var request = new CreateItemRequest("Title", "Desc", 0L, "http://img.png", "food");

            assertThrows(ShopService.InvalidInputException.class,
                    () -> service.createItem(SHOP_ID, request, OWNER_ID));
        }

        @Test
        void createItem_negativeRetailValue_throwsInvalidInput() {
            var request = new CreateItemRequest("Title", "Desc", -5L, "http://img.png", "food");

            assertThrows(ShopService.InvalidInputException.class,
                    () -> service.createItem(SHOP_ID, request, OWNER_ID));
        }

        @Test
        void createItem_shopNotFound_throwsNotFound() {
            var request = new CreateItemRequest("Title", "Desc", 1000L, "http://img.png", "food");
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.empty());

            assertThrows(ShopService.NotFoundException.class,
                    () -> service.createItem(SHOP_ID, request, OWNER_ID));
        }

        @Test
        void createItem_notOwner_throwsForbidden() {
            var request = new CreateItemRequest("Title", "Desc", 1000L, "http://img.png", "food");
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.of(buildShop()));

            assertThrows(ShopService.ForbiddenException.class,
                    () -> service.createItem(SHOP_ID, request, OTHER_USER));
            verify(repository, never()).saveItem(any());
        }

        @Test
        void createItem_success_savesAndReturns() {
            var request = new CreateItemRequest("Title", "Desc", 1000L, "http://img.png", "food");
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.of(buildShop()));

            Item result = service.createItem(SHOP_ID, request, OWNER_ID);

            assertNotNull(result.getItemId());
            assertEquals(SHOP_ID, result.getShopId());
            assertEquals("Title", result.getTitle());
            assertEquals("Desc", result.getDescription());
            assertEquals(1000L, result.getRetailValue());
            assertEquals("http://img.png", result.getImageUrl());
            assertEquals("food", result.getCategory());
            verify(repository).saveItem(any(Item.class));
        }
    }

    // ---- listSellerShops ----

    @Nested
    class ListSellerShopsTests {

        @Test
        void listSellerShops_delegatesToRepo() {
            List<Shop> expected = List.of(buildShop());
            when(repository.findShopsByOwnerId(OWNER_ID)).thenReturn(expected);

            List<Shop> result = service.listSellerShops(OWNER_ID);

            assertEquals(expected, result);
            verify(repository).findShopsByOwnerId(OWNER_ID);
        }
    }

    // ---- listItems ----

    @Nested
    class ListItemsTests {

        @Test
        void listItems_shopNotFound_throwsNotFound() {
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.empty());

            assertThrows(ShopService.NotFoundException.class,
                    () -> service.listItems(SHOP_ID));
        }

        @Test
        void listItems_success_returnsItems() {
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.of(buildShop()));
            Item item = new Item();
            item.setItemId("item-1");
            when(repository.findItemsByShopId(SHOP_ID)).thenReturn(List.of(item));

            List<Item> result = service.listItems(SHOP_ID);

            assertEquals(1, result.size());
            assertEquals("item-1", result.get(0).getItemId());
        }
    }

    // ---- createReview ----

    @Nested
    class CreateReviewTests {

        @Test
        void createReview_shopNotFound_throwsNotFound() {
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.empty());
            var request = new CreateReviewRequest("auction-1", 5, "Great");

            assertThrows(ShopService.NotFoundException.class,
                    () -> service.createReview(SHOP_ID, request, "reviewer-1", "user", null));
        }

        @Test
        void createReview_alreadyReviewed_throwsAlreadyReviewed() {
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.of(buildShop()));
            when(repository.findReviewByAuctionAndReviewer("auction-1", "reviewer-1"))
                    .thenReturn(Optional.of(buildReview(SHOP_ID)));
            var request = new CreateReviewRequest("auction-1", 5, "Great");

            assertThrows(ShopService.AlreadyReviewedException.class,
                    () -> service.createReview(SHOP_ID, request, "reviewer-1", "user", null));
            verify(repository, never()).saveReview(any());
        }

        @Test
        void createReview_success_savesAndReturns() {
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.of(buildShop()));
            when(repository.findReviewByAuctionAndReviewer("auction-1", "reviewer-1"))
                    .thenReturn(Optional.empty());
            var request = new CreateReviewRequest("auction-1", 4, "Nice");

            Review result = service.createReview(SHOP_ID, request, "reviewer-1", "reviewerUser", null);

            assertNotNull(result.getReviewId());
            assertEquals(SHOP_ID, result.getShopId());
            assertEquals("reviewer-1", result.getReviewerId());
            assertEquals("reviewerUser", result.getReviewerUsername());
            assertEquals("auction-1", result.getAuctionId());
            assertEquals(4, result.getRating());
            assertEquals("Nice", result.getComment());
            assertNotNull(result.getCreatedAt());
            assertNotNull(result.getUpdatedAt());
            verify(repository).saveReview(any(Review.class));
        }
    }

    // ---- listReviews ----

    @Nested
    class ListReviewsTests {

        @Test
        void listReviews_shopNotFound_throwsNotFound() {
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.empty());

            assertThrows(ShopService.NotFoundException.class,
                    () -> service.listReviews(SHOP_ID));
        }

        @Test
        void listReviews_emptyReviews_returnsZeroAvg() {
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.of(buildShop()));
            when(repository.findReviewsByShopId(SHOP_ID)).thenReturn(List.of());

            ReviewsResponse response = service.listReviews(SHOP_ID);

            assertEquals(0, response.totalReviews());
            assertEquals(0.0, response.averageRating());
            assertTrue(response.reviews().isEmpty());
        }

        @Test
        void listReviews_withReviews_calculatesAverage() {
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.of(buildShop()));
            Review r1 = buildReview(SHOP_ID);
            r1.setRating(4);
            Review r2 = buildReview(SHOP_ID);
            r2.setReviewId("rev-2");
            r2.setRating(5);
            when(repository.findReviewsByShopId(SHOP_ID)).thenReturn(List.of(r1, r2));

            ReviewsResponse response = service.listReviews(SHOP_ID);

            assertEquals(2, response.totalReviews());
            assertEquals(4.5, response.averageRating());
            assertEquals(2, response.reviews().size());
        }

        @Test
        void listReviews_averageRoundedToOneTenth() {
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.of(buildShop()));
            Review r1 = buildReview(SHOP_ID);
            r1.setRating(3);
            Review r2 = buildReview(SHOP_ID);
            r2.setReviewId("rev-2");
            r2.setRating(5);
            Review r3 = buildReview(SHOP_ID);
            r3.setReviewId("rev-3");
            r3.setRating(4);
            when(repository.findReviewsByShopId(SHOP_ID)).thenReturn(List.of(r1, r2, r3));

            ReviewsResponse response = service.listReviews(SHOP_ID);

            assertEquals(3, response.totalReviews());
            // (3+5+4)/3 = 4.0
            assertEquals(4.0, response.averageRating());
        }
    }

    // ---- replyToReview ----

    @Nested
    class ReplyToReviewTests {

        @Test
        void replyToReview_shopNotFound_throwsNotFound() {
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.empty());

            assertThrows(ShopService.NotFoundException.class,
                    () -> service.replyToReview(SHOP_ID, "rev-1", "Thanks!", OWNER_ID));
        }

        @Test
        void replyToReview_notOwner_throwsForbidden() {
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.of(buildShop()));

            assertThrows(ShopService.ForbiddenException.class,
                    () -> service.replyToReview(SHOP_ID, "rev-1", "Thanks!", OTHER_USER));
        }

        @Test
        void replyToReview_reviewNotFound_throwsNotFound() {
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.of(buildShop()));
            when(repository.findReviewById("rev-1")).thenReturn(Optional.empty());

            assertThrows(ShopService.NotFoundException.class,
                    () -> service.replyToReview(SHOP_ID, "rev-1", "Thanks!", OWNER_ID));
        }

        @Test
        void replyToReview_reviewWrongShop_throwsNotFound() {
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.of(buildShop()));
            Review review = buildReview("other-shop-id");
            when(repository.findReviewById("rev-1")).thenReturn(Optional.of(review));

            assertThrows(ShopService.NotFoundException.class,
                    () -> service.replyToReview(SHOP_ID, "rev-1", "Thanks!", OWNER_ID));
        }

        @Test
        void replyToReview_success() {
            when(repository.findShopById(SHOP_ID)).thenReturn(Optional.of(buildShop()));
            Review review = buildReview(SHOP_ID);
            when(repository.findReviewById("rev-1")).thenReturn(Optional.of(review));

            Review result = service.replyToReview(SHOP_ID, "rev-1", "Thanks!", OWNER_ID);

            assertEquals("Thanks!", result.getSellerReply());
            assertNotNull(result.getUpdatedAt());
            verify(repository).updateReviewReply(eq("rev-1"), eq("Thanks!"), anyString());
        }
    }

    // ---- uploadImage ----

    @Nested
    class UploadImageTests {

        @Test
        void uploadImage_tooLarge_throwsFileTooLarge() {
            long oversized = 5 * 1024 * 1024 + 1;
            InputStream body = new ByteArrayInputStream(new byte[0]);

            assertThrows(ShopService.FileTooLargeException.class,
                    () -> service.uploadImage("image/jpeg", body, oversized));
            verify(uploader, never()).upload(anyString(), anyString(), any(), anyLong());
        }

        @Test
        void uploadImage_invalidMime_throwsInvalidFileType() {
            InputStream body = new ByteArrayInputStream(new byte[0]);

            assertThrows(ShopService.InvalidFileTypeException.class,
                    () -> service.uploadImage("application/pdf", body, 1024));
            verify(uploader, never()).upload(anyString(), anyString(), any(), anyLong());
        }

        @Test
        void uploadImage_jpeg_success() {
            InputStream body = new ByteArrayInputStream(new byte[10]);

            String url = service.uploadImage("image/jpeg", body, 10);

            assertTrue(url.startsWith("http://localhost:3000/uploads/images/"));
            assertTrue(url.endsWith(".jpg"));
            verify(uploader).upload(anyString(), eq("image/jpeg"), eq(body), eq(10L));
        }

        @Test
        void uploadImage_png_success() {
            InputStream body = new ByteArrayInputStream(new byte[10]);

            String url = service.uploadImage("image/png", body, 10);

            assertTrue(url.endsWith(".png"));
            verify(uploader).upload(anyString(), eq("image/png"), eq(body), eq(10L));
        }

        @Test
        void uploadImage_webp_success() {
            InputStream body = new ByteArrayInputStream(new byte[10]);

            String url = service.uploadImage("image/webp", body, 10);

            assertTrue(url.endsWith(".webp"));
            verify(uploader).upload(anyString(), eq("image/webp"), eq(body), eq(10L));
        }

        @Test
        void uploadImage_gif_success() {
            InputStream body = new ByteArrayInputStream(new byte[10]);

            String url = service.uploadImage("image/gif", body, 10);

            assertTrue(url.endsWith(".gif"));
            verify(uploader).upload(anyString(), eq("image/gif"), eq(body), eq(10L));
        }

        @Test
        void uploadImage_exactlyMaxSize_succeeds() {
            long exactMax = 5 * 1024 * 1024;
            InputStream body = new ByteArrayInputStream(new byte[0]);

            String url = service.uploadImage("image/jpeg", body, exactMax);

            assertNotNull(url);
            verify(uploader).upload(anyString(), eq("image/jpeg"), eq(body), eq(exactMax));
        }
    }
}
