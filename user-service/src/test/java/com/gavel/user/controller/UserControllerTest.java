package com.gavel.user.controller;

import com.gavel.user.model.LoginRequest;
import com.gavel.user.model.RegisterRequest;
import com.gavel.user.model.UpdateProfileRequest;
import com.gavel.user.model.User;
import com.gavel.user.service.UserService;
import com.gavel.user.service.UserService.RegisterResult;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private Authentication auth;

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(userService, "http://localhost:8084");
    }

    @Nested
    class Register {

        @Test
        void success_returnsCreated() {
            RegisterRequest request = new RegisterRequest("user@test.com", "password123", "testUser", "buyer", false);
            when(userService.register(request)).thenReturn(new RegisterResult("u1", "buyer", null, null));

            ResponseEntity<?> response = controller.register(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("user_id", "u1");
            assertThat(body).containsEntry("role", "buyer");
        }

        @Test
        void usernameMismatch_returnsConflict() {
            RegisterRequest request = new RegisterRequest("user@test.com", "password123", "newName", "seller", false);
            when(userService.register(request)).thenReturn(new RegisterResult(null, null, RegisterResult.ErrorType.USERNAME_MISMATCH, "oldName"));

            ResponseEntity<?> response = controller.register(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("error", "username_mismatch");
            assertThat(body).containsEntry("existing_username", "oldName");
        }

        @Test
        void emailTaken_returnsConflict() {
            RegisterRequest request = new RegisterRequest("user@test.com", "password123", "testUser", "buyer", false);
            when(userService.register(request)).thenReturn(new RegisterResult(null, null, RegisterResult.ErrorType.EMAIL_TAKEN, null));

            ResponseEntity<?> response = controller.register(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("error", "email already registered");
        }

        @Test
        void alreadySeller_returnsConflict() {
            RegisterRequest request = new RegisterRequest("user@test.com", "password123", "testUser", "seller", false);
            when(userService.register(request)).thenReturn(new RegisterResult(null, null, RegisterResult.ErrorType.ALREADY_SELLER, null));

            ResponseEntity<?> response = controller.register(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("error", "account is already a seller");
        }

        @Test
        void invalidCredentials_returnsUnauthorized() {
            RegisterRequest request = new RegisterRequest("user@test.com", "wrongPw", "testUser", "seller", false);
            when(userService.register(request)).thenReturn(new RegisterResult(null, null, RegisterResult.ErrorType.INVALID_CREDENTIALS, null));

            ResponseEntity<?> response = controller.register(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("error", "incorrect password for existing account");
        }
    }

    @Nested
    class Login {

        @Test
        void success_returnsOkWithToken() {
            LoginRequest request = new LoginRequest("user@test.com", "password123");
            when(userService.login(request)).thenReturn("jwt-token-xyz");

            ResponseEntity<?> response = controller.login(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("token", "jwt-token-xyz");
        }

        @Test
        void failure_returnsUnauthorized() {
            LoginRequest request = new LoginRequest("user@test.com", "wrongPw");
            when(userService.login(request)).thenReturn(null);

            ResponseEntity<?> response = controller.login(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("error", "invalid email or password");
        }
    }

    @Nested
    class GetProfile {

        @Test
        void found_returnsOk() {
            User user = new User();
            user.setUserId("u1");
            user.setUsername("testUser");
            when(userService.getProfile("u1")).thenReturn(Optional.of(user));

            ResponseEntity<?> response = controller.getProfile("u1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isInstanceOf(User.class);
            assertThat(((User) response.getBody()).getUserId()).isEqualTo("u1");
        }

        @Test
        void notFound_returns404() {
            when(userService.getProfile("missing")).thenReturn(Optional.empty());

            ResponseEntity<?> response = controller.getProfile("missing");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("error", "user not found");
        }
    }

    @Nested
    class UpdateProfile {

        @Test
        void success_returnsOk() {
            when(auth.getPrincipal()).thenReturn("u1");
            UpdateProfileRequest request = new UpdateProfileRequest("newName", "http://avatar.png");
            when(userService.updateProfile("u1", request)).thenReturn(true);

            ResponseEntity<?> response = controller.updateProfile("u1", request, auth);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("ok", true);
        }

        @Test
        void wrongUser_returnsForbidden() {
            when(auth.getPrincipal()).thenReturn("other-user");
            UpdateProfileRequest request = new UpdateProfileRequest("newName", "http://avatar.png");

            ResponseEntity<?> response = controller.updateProfile("u1", request, auth);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("error", "cannot update another user's profile");
        }

        @Test
        void userNotFound_returns404() {
            when(auth.getPrincipal()).thenReturn("u1");
            UpdateProfileRequest request = new UpdateProfileRequest("newName", "http://avatar.png");
            when(userService.updateProfile("u1", request)).thenReturn(false);

            ResponseEntity<?> response = controller.updateProfile("u1", request, auth);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("error", "user not found");
        }
    }

    @Nested
    class GetWatchlist {

        @Test
        void success_returnsOkWithAuctionIds() {
            when(auth.getPrincipal()).thenReturn("u1");
            when(userService.getWatchlist("u1")).thenReturn(List.of("a1", "a2"));

            ResponseEntity<?> response = controller.getWatchlist("u1", auth);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("auction_ids", List.of("a1", "a2"));
        }

        @Test
        void wrongUser_returnsForbidden() {
            when(auth.getPrincipal()).thenReturn("other-user");

            ResponseEntity<?> response = controller.getWatchlist("u1", auth);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("error", "forbidden");
        }
    }

    @Nested
    class AddToWatchlist {

        @Test
        void success_returnsOk() {
            when(auth.getPrincipal()).thenReturn("u1");
            when(userService.addToWatchlist("u1", "auction-1")).thenReturn(true);

            ResponseEntity<?> response = controller.addToWatchlist("u1", "auction-1", auth);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("ok", true);
        }

        @Test
        void wrongUser_returnsForbidden() {
            when(auth.getPrincipal()).thenReturn("other-user");

            ResponseEntity<?> response = controller.addToWatchlist("u1", "auction-1", auth);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("error", "forbidden");
        }

        @Test
        void userNotFound_returns404() {
            when(auth.getPrincipal()).thenReturn("u1");
            when(userService.addToWatchlist("u1", "auction-1")).thenReturn(false);

            ResponseEntity<?> response = controller.addToWatchlist("u1", "auction-1", auth);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("error", "user not found");
        }
    }

    @Nested
    class RemoveFromWatchlist {

        @Test
        void success_returnsOk() {
            when(auth.getPrincipal()).thenReturn("u1");

            ResponseEntity<?> response = controller.removeFromWatchlist("u1", "auction-1", auth);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(userService).removeFromWatchlist("u1", "auction-1");
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("ok", true);
        }

        @Test
        void wrongUser_returnsForbidden() {
            when(auth.getPrincipal()).thenReturn("other-user");

            ResponseEntity<?> response = controller.removeFromWatchlist("u1", "auction-1", auth);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("error", "forbidden");
        }
    }
}
