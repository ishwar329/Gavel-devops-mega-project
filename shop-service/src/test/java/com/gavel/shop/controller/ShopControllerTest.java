package com.gavel.shop.controller;

import com.gavel.shop.model.*;
import com.gavel.shop.service.ShopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopControllerTest {

    @Mock
    private ShopService shopService;

    @Mock
    private Authentication auth;

    private ShopController controller;

    private static final String USER_ID = "user-123";
    private static final String SHOP_ID = "shop-abc";
    private static final String USERNAME = "testuser";

    @BeforeEach
    void setUp() {
        controller = new ShopController(shopService);
    }

    private void mockSellerAuth() {
        when(auth.getPrincipal()).thenReturn(USER_ID);
        when(auth.getDetails()).thenReturn(Map.of("role", "seller", "username", USERNAME));
    }

    private void mockBuyerAuth() {
        when(auth.getDetails()).thenReturn(Map.of("role", "buyer", "username", USERNAME));
    }

    private Shop buildShop() {
        Shop shop = new Shop();
        shop.setShopId(SHOP_ID);
        shop.setName("Test Shop");
        shop.setLocation("123 Main St");
        shop.setOwnerId(USER_ID);
        shop.setLat(40.7128);
        shop.setLng(-74.0060);
        return shop;
    }

    private Item buildItem() {
        Item item = new Item();
        item.setItemId("item-1");
        item.setShopId(SHOP_ID);
        item.setTitle("Test Item");
        item.setDescription("A test item");
        item.setRetailValue(1500L);
        item.setImageUrl("http://img.png");
        item.setCategory("food");
        return item;
    }

    private Review buildReview() {
        Review review = new Review();
        review.setReviewId("rev-1");
        review.setShopId(SHOP_ID);
        review.setReviewerId(USER_ID);
        review.setReviewerUsername(USERNAME);
        review.setAuctionId("auction-1");
        review.setRating(4);
        review.setComment("Great");
        review.setCreatedAt("2026-01-01T00:00:00Z");
        review.setUpdatedAt("2026-01-01T00:00:00Z");
        return review;
    }

    // ---- createShop ----

    @Nested
    class CreateShopTests {

        @Test
        void createShop_nonSeller_returnsForbidden() {
            mockBuyerAuth();
            var request = new CreateShopRequest("Shop", "Loc", null, 40.0, -74.0);

            ResponseEntity<?> response = controller.createShop(request, auth);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, String> body = (Map<String, String>) response.getBody();
            assertEquals("sellers only", body.get("error"));
            verify(shopService, never()).createShop(any(), any());
        }

        @Test
        void createShop_seller_returnsCreated() {
            mockSellerAuth();
            var request = new CreateShopRequest("Shop", "Loc", null, 40.0, -74.0);
            Shop shop = buildShop();
            when(shopService.createShop(request, USER_ID)).thenReturn(shop);

            ResponseEntity<?> response = controller.createShop(request, auth);

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertEquals(shop, response.getBody());
        }
    }

    // ---- updateShop ----

    @Nested
    class UpdateShopTests {

        @Test
        void updateShop_nonSeller_returnsForbidden() {
            mockBuyerAuth();
            var request = new UpdateShopRequest("New", null, null, null, null);

            ResponseEntity<?> response = controller.updateShop(SHOP_ID, request, auth);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            verify(shopService, never()).updateShop(any(), any(), any());
        }

        @Test
        void updateShop_seller_returnsOk() {
            mockSellerAuth();
            var request = new UpdateShopRequest("New", null, null, null, null);
            Shop shop = buildShop();
            shop.setName("New");
            when(shopService.updateShop(SHOP_ID, request, USER_ID)).thenReturn(shop);

            ResponseEntity<?> response = controller.updateShop(SHOP_ID, request, auth);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(shop, response.getBody());
        }
    }

    // ---- getShop ----

    @Nested
    class GetShopTests {

        @Test
        void getShop_returnsOk() {
            Shop shop = buildShop();
            when(shopService.getShop(SHOP_ID)).thenReturn(shop);

            ResponseEntity<?> response = controller.getShop(SHOP_ID);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(shop, response.getBody());
        }
    }

    // ---- getItem ----

    @Nested
    class GetItemTests {

        @Test
        void getItem_returnsOk() {
            Item item = buildItem();
            when(shopService.getItem("item-1")).thenReturn(item);

            ResponseEntity<?> response = controller.getItem("item-1");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(item, response.getBody());
        }
    }

    // ---- createItem ----

    @Nested
    class CreateItemTests {

        @Test
        void createItem_nonSeller_returnsForbidden() {
            mockBuyerAuth();
            var request = new CreateItemRequest("Item", "Desc", 1000L, "http://img.png", "food");

            ResponseEntity<?> response = controller.createItem(SHOP_ID, request, auth);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            verify(shopService, never()).createItem(any(), any(), any());
        }

        @Test
        void createItem_seller_returnsCreated() {
            mockSellerAuth();
            var request = new CreateItemRequest("Item", "Desc", 1000L, "http://img.png", "food");
            Item item = buildItem();
            when(shopService.createItem(SHOP_ID, request, USER_ID)).thenReturn(item);

            ResponseEntity<?> response = controller.createItem(SHOP_ID, request, auth);

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertEquals(item, response.getBody());
        }
    }

    // ---- listItems ----

    @Nested
    class ListItemsTests {

        @Test
        void listItems_returnsOk() {
            List<Item> items = List.of(buildItem());
            when(shopService.listItems(SHOP_ID)).thenReturn(items);

            ResponseEntity<?> response = controller.listItems(SHOP_ID);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertEquals(items, body.get("items"));
        }
    }

    // ---- listSellerShops ----

    @Nested
    class ListSellerShopsTests {

        @Test
        void listSellerShops_returnsOk() {
            List<Shop> shops = List.of(buildShop());
            when(shopService.listSellerShops(USER_ID)).thenReturn(shops);

            ResponseEntity<?> response = controller.listSellerShops(USER_ID);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertEquals(shops, body.get("shops"));
        }
    }

    // ---- createReview ----

    @Nested
    class CreateReviewTests {

        @Test
        void createReview_success_returnsCreated() {
            when(auth.getPrincipal()).thenReturn(USER_ID);
            when(auth.getDetails()).thenReturn(Map.of("role", "buyer", "username", USERNAME));
            var request = new CreateReviewRequest("auction-1", 5, "Excellent");
            Review review = buildReview();
            when(shopService.createReview(SHOP_ID, request, USER_ID, USERNAME, null))
                    .thenReturn(review);

            ResponseEntity<?> response = controller.createReview(SHOP_ID, request, null, auth);

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertEquals(review, response.getBody());
        }

        @Test
        void createReview_withBearerToken_extractsToken() {
            when(auth.getPrincipal()).thenReturn(USER_ID);
            when(auth.getDetails()).thenReturn(Map.of("role", "buyer", "username", USERNAME));
            var request = new CreateReviewRequest("auction-1", 5, "Excellent");
            Review review = buildReview();
            when(shopService.createReview(SHOP_ID, request, USER_ID, USERNAME, "mytoken123"))
                    .thenReturn(review);

            ResponseEntity<?> response = controller.createReview(SHOP_ID, request, "Bearer mytoken123", auth);

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            verify(shopService).createReview(SHOP_ID, request, USER_ID, USERNAME, "mytoken123");
        }

        @Test
        void createReview_nonBearerAuthHeader_tokenIsNull() {
            when(auth.getPrincipal()).thenReturn(USER_ID);
            when(auth.getDetails()).thenReturn(Map.of("role", "buyer", "username", USERNAME));
            var request = new CreateReviewRequest("auction-1", 5, "Excellent");
            Review review = buildReview();
            when(shopService.createReview(SHOP_ID, request, USER_ID, USERNAME, null))
                    .thenReturn(review);

            ResponseEntity<?> response = controller.createReview(SHOP_ID, request, "Basic abc123", auth);

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            verify(shopService).createReview(SHOP_ID, request, USER_ID, USERNAME, null);
        }
    }

    // ---- listReviews ----

    @Nested
    class ListReviewsTests {

        @Test
        void listReviews_returnsOk() {
            ReviewsResponse reviewsResponse = new ReviewsResponse(List.of(buildReview()), 4.0, 1);
            when(shopService.listReviews(SHOP_ID)).thenReturn(reviewsResponse);

            ResponseEntity<?> response = controller.listReviews(SHOP_ID);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(reviewsResponse, response.getBody());
        }
    }

    // ---- replyToReview ----

    @Nested
    class ReplyToReviewTests {

        @Test
        void replyToReview_nonSeller_returnsForbidden() {
            mockBuyerAuth();
            var request = new ReplyRequest("Thanks!");

            ResponseEntity<?> response = controller.replyToReview(SHOP_ID, "rev-1", request, auth);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            verify(shopService, never()).replyToReview(any(), any(), any(), any());
        }

        @Test
        void replyToReview_seller_returnsOk() {
            mockSellerAuth();
            var request = new ReplyRequest("Thanks!");
            Review review = buildReview();
            review.setSellerReply("Thanks!");
            when(shopService.replyToReview(SHOP_ID, "rev-1", "Thanks!", USER_ID))
                    .thenReturn(review);

            ResponseEntity<?> response = controller.replyToReview(SHOP_ID, "rev-1", request, auth);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(review, response.getBody());
        }
    }
}
