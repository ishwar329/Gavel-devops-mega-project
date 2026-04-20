package com.gavel.user.service;

import com.gavel.shared.security.JwtTokenProvider;
import com.gavel.user.model.LoginRequest;
import com.gavel.user.model.RegisterRequest;
import com.gavel.user.model.UpdateProfileRequest;
import com.gavel.user.model.User;
import com.gavel.user.repository.UserRepository;
import com.gavel.user.service.UserService.RegisterResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository repo;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(repo, jwtTokenProvider, passwordEncoder);
    }

    private User existingUser(String userId, String email, String username, String role, String passwordHash) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(email);
        user.setUsername(username);
        user.setRole(role);
        user.setPasswordHash(passwordHash);
        user.setCreatedAt("2026-01-01T00:00:00Z");
        return user;
    }

    @Nested
    class Register {

        @Test
        void newBuyerRegistration_savesUserAndReturnsSuccess() {
            when(repo.findByEmail("buyer@test.com")).thenReturn(Optional.empty());
            when(passwordEncoder.encode("password123")).thenReturn("hashed");

            RegisterRequest request = new RegisterRequest("buyer@test.com", "password123", "buyerUser", "buyer", false);
            RegisterResult result = userService.register(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.role()).isEqualTo("buyer");
            assertThat(result.userId()).isNotNull();
            assertThat(result.error()).isNull();

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(repo).save(captor.capture());
            User saved = captor.getValue();
            assertThat(saved.getEmail()).isEqualTo("buyer@test.com");
            assertThat(saved.getUsername()).isEqualTo("buyerUser");
            assertThat(saved.getRole()).isEqualTo("buyer");
            assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        }

        @Test
        void newSellerRegistration_savesUserWithSellerRole() {
            when(repo.findByEmail("seller@test.com")).thenReturn(Optional.empty());
            when(passwordEncoder.encode("password123")).thenReturn("hashed");

            RegisterRequest request = new RegisterRequest("seller@test.com", "password123", "sellerUser", "seller", false);
            RegisterResult result = userService.register(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.role()).isEqualTo("seller");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(repo).save(captor.capture());
            assertThat(captor.getValue().getRole()).isEqualTo("seller");
        }

        @Test
        void nullRole_defaultsToBuyer() {
            when(repo.findByEmail("user@test.com")).thenReturn(Optional.empty());
            when(passwordEncoder.encode("password123")).thenReturn("hashed");

            RegisterRequest request = new RegisterRequest("user@test.com", "password123", "someUser", null, false);
            RegisterResult result = userService.register(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.role()).isEqualTo("buyer");
        }

        @Test
        void emailAlreadyTaken_existingBuyerRegisteringAsBuyer_returnsEmailTaken() {
            User existing = existingUser("u1", "taken@test.com", "existUser", "buyer", "hash");
            when(repo.findByEmail("taken@test.com")).thenReturn(Optional.of(existing));

            RegisterRequest request = new RegisterRequest("taken@test.com", "password123", "newUser", "buyer", false);
            RegisterResult result = userService.register(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.error()).isEqualTo(RegisterResult.ErrorType.EMAIL_TAKEN);
            verify(repo, never()).save(any());
        }

        @Test
        void buyerToSellerUpgrade_passwordMatchesAndUsernameMatches_success() {
            User existing = existingUser("u1", "upgrade@test.com", "sameUser", "buyer", "hashedPw");
            when(repo.findByEmail("upgrade@test.com")).thenReturn(Optional.of(existing));
            when(passwordEncoder.matches("password123", "hashedPw")).thenReturn(true);

            RegisterRequest request = new RegisterRequest("upgrade@test.com", "password123", "sameUser", "seller", false);
            RegisterResult result = userService.register(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.userId()).isEqualTo("u1");
            assertThat(result.role()).isEqualTo("seller");
            verify(repo).updateRole("u1", "seller");
            verify(repo, never()).updateProfile(anyString(), anyString(), anyString());
            verify(repo, never()).save(any());
        }

        @Test
        void buyerToSellerUpgrade_passwordWrong_returnsInvalidCredentials() {
            User existing = existingUser("u1", "upgrade@test.com", "existUser", "buyer", "hashedPw");
            when(repo.findByEmail("upgrade@test.com")).thenReturn(Optional.of(existing));
            when(passwordEncoder.matches("wrongPw", "hashedPw")).thenReturn(false);

            RegisterRequest request = new RegisterRequest("upgrade@test.com", "wrongPw", "existUser", "seller", false);
            RegisterResult result = userService.register(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.error()).isEqualTo(RegisterResult.ErrorType.INVALID_CREDENTIALS);
            verify(repo, never()).updateRole(anyString(), anyString());
        }

        @Test
        void buyerToSellerUpgrade_usernameMismatchWithoutConfirm_returnsUsernameMismatch() {
            User existing = existingUser("u1", "upgrade@test.com", "oldName", "buyer", "hashedPw");
            when(repo.findByEmail("upgrade@test.com")).thenReturn(Optional.of(existing));
            when(passwordEncoder.matches("password123", "hashedPw")).thenReturn(true);

            RegisterRequest request = new RegisterRequest("upgrade@test.com", "password123", "newName", "seller", false);
            RegisterResult result = userService.register(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.error()).isEqualTo(RegisterResult.ErrorType.USERNAME_MISMATCH);
            assertThat(result.existingUsername()).isEqualTo("oldName");
            verify(repo, never()).updateRole(anyString(), anyString());
        }

        @Test
        void buyerToSellerUpgrade_usernameMismatchWithConfirm_successAndUpdatesProfile() {
            User existing = existingUser("u1", "upgrade@test.com", "oldName", "buyer", "hashedPw");
            when(repo.findByEmail("upgrade@test.com")).thenReturn(Optional.of(existing));
            when(passwordEncoder.matches("password123", "hashedPw")).thenReturn(true);

            RegisterRequest request = new RegisterRequest("upgrade@test.com", "password123", "newName", "seller", true);
            RegisterResult result = userService.register(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.userId()).isEqualTo("u1");
            assertThat(result.role()).isEqualTo("seller");
            verify(repo).updateRole("u1", "seller");
            verify(repo).updateProfile("u1", "newName", "");
        }

        @Test
        void alreadySeller_returnsAlreadySeller() {
            User existing = existingUser("u1", "seller@test.com", "sellerUser", "seller", "hashedPw");
            when(repo.findByEmail("seller@test.com")).thenReturn(Optional.of(existing));

            RegisterRequest request = new RegisterRequest("seller@test.com", "password123", "sellerUser", "seller", false);
            RegisterResult result = userService.register(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.error()).isEqualTo(RegisterResult.ErrorType.ALREADY_SELLER);
        }
    }

    @Nested
    class Login {

        @Test
        void emailNotFound_returnsNull() {
            when(repo.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

            LoginRequest request = new LoginRequest("unknown@test.com", "password123");
            String token = userService.login(request);

            assertThat(token).isNull();
            verify(jwtTokenProvider, never()).generateToken(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void passwordWrong_returnsNull() {
            User user = existingUser("u1", "user@test.com", "testUser", "buyer", "hashedPw");
            when(repo.findByEmail("user@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongPw", "hashedPw")).thenReturn(false);

            LoginRequest request = new LoginRequest("user@test.com", "wrongPw");
            String token = userService.login(request);

            assertThat(token).isNull();
            verify(jwtTokenProvider, never()).generateToken(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void success_returnsToken() {
            User user = existingUser("u1", "user@test.com", "testUser", "buyer", "hashedPw");
            when(repo.findByEmail("user@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("password123", "hashedPw")).thenReturn(true);
            when(jwtTokenProvider.generateToken("u1", "testUser", "user@test.com", "buyer"))
                    .thenReturn("jwt-token-123");

            LoginRequest request = new LoginRequest("user@test.com", "password123");
            String token = userService.login(request);

            assertThat(token).isEqualTo("jwt-token-123");
            verify(jwtTokenProvider).generateToken("u1", "testUser", "user@test.com", "buyer");
        }
    }

    @Nested
    class GetProfile {

        @Test
        void delegatesToRepository() {
            User user = existingUser("u1", "user@test.com", "testUser", "buyer", "hash");
            when(repo.findById("u1")).thenReturn(Optional.of(user));

            Optional<User> result = userService.getProfile("u1");

            assertThat(result).isPresent();
            assertThat(result.get().getUserId()).isEqualTo("u1");
            verify(repo).findById("u1");
        }

        @Test
        void userNotFound_returnsEmpty() {
            when(repo.findById("missing")).thenReturn(Optional.empty());

            Optional<User> result = userService.getProfile("missing");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class UpdateProfile {

        @Test
        void userNotFound_returnsFalse() {
            when(repo.findById("missing")).thenReturn(Optional.empty());

            UpdateProfileRequest request = new UpdateProfileRequest("newName", "http://avatar.png");
            boolean result = userService.updateProfile("missing", request);

            assertThat(result).isFalse();
            verify(repo, never()).updateProfile(anyString(), anyString(), anyString());
        }

        @Test
        void userFound_updatesAndReturnsTrue() {
            User user = existingUser("u1", "user@test.com", "oldName", "buyer", "hash");
            when(repo.findById("u1")).thenReturn(Optional.of(user));

            UpdateProfileRequest request = new UpdateProfileRequest("newName", "http://avatar.png");
            boolean result = userService.updateProfile("u1", request);

            assertThat(result).isTrue();
            verify(repo).updateProfile("u1", "newName", "http://avatar.png");
        }
    }

    @Nested
    class AddToWatchlist {

        @Test
        void userNotFound_returnsFalse() {
            when(repo.findById("missing")).thenReturn(Optional.empty());

            boolean result = userService.addToWatchlist("missing", "auction-1");

            assertThat(result).isFalse();
            verify(repo, never()).addToWatchlist(anyString(), anyString());
        }

        @Test
        void userFound_addsAndReturnsTrue() {
            User user = existingUser("u1", "user@test.com", "testUser", "buyer", "hash");
            when(repo.findById("u1")).thenReturn(Optional.of(user));

            boolean result = userService.addToWatchlist("u1", "auction-1");

            assertThat(result).isTrue();
            verify(repo).addToWatchlist("u1", "auction-1");
        }
    }

    @Nested
    class RemoveFromWatchlist {

        @Test
        void delegatesToRepository() {
            userService.removeFromWatchlist("u1", "auction-1");

            verify(repo).removeFromWatchlist("u1", "auction-1");
        }
    }

    @Nested
    class GetWatchlist {

        @Test
        void delegatesToRepository() {
            when(repo.getWatchlist("u1")).thenReturn(List.of("a1", "a2"));

            List<String> result = userService.getWatchlist("u1");

            assertThat(result).containsExactly("a1", "a2");
            verify(repo).getWatchlist("u1");
        }

        @Test
        void emptyWatchlist_returnsEmptyList() {
            when(repo.getWatchlist("u1")).thenReturn(List.of());

            List<String> result = userService.getWatchlist("u1");

            assertThat(result).isEmpty();
        }
    }
}
