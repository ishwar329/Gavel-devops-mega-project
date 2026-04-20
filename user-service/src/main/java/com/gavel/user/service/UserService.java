package com.gavel.user.service;

import com.gavel.shared.security.JwtTokenProvider;
import com.gavel.user.model.*;
import com.gavel.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository repository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository,
                       JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    public RegisterResult register(RegisterRequest request) {
        String role = "seller".equals(request.role()) ? "seller" : "buyer";

        Optional<User> existing = repository.findByEmail(request.email());
        if (existing.isPresent()) {
            User user = existing.get();
            if ("seller".equals(role) && "buyer".equals(user.getRole())) {
                if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                    return RegisterResult.error(RegisterResult.ErrorType.INVALID_CREDENTIALS);
                }
                if (!request.username().equals(user.getUsername()) && !request.confirmUpgrade()) {
                    return RegisterResult.usernameMismatch(user.getUsername());
                }
                repository.updateRole(user.getUserId(), "seller");
                if (!request.username().equals(user.getUsername())) {
                    repository.updateProfile(user.getUserId(), request.username(), "");
                }
                return RegisterResult.success(user.getUserId(), "seller");
            }
            if ("seller".equals(role) && "seller".equals(user.getRole())) {
                return RegisterResult.error(RegisterResult.ErrorType.ALREADY_SELLER);
            }
            return RegisterResult.error(RegisterResult.ErrorType.EMAIL_TAKEN);
        }

        User user = new User();
        user.setUserId(UUID.randomUUID().toString());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setUsername(request.username());
        user.setRole(role);
        user.setCreatedAt(Instant.now().toString());

        repository.save(user);
        return RegisterResult.success(user.getUserId(), role);
    }

    public String login(LoginRequest request) {
        Optional<User> opt = repository.findByEmail(request.email());
        if (opt.isEmpty()) {
            return null;
        }
        User user = opt.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            return null;
        }
        return jwtTokenProvider.generateToken(user.getUserId(), user.getUsername(), user.getEmail(), user.getRole());
    }

    public Optional<User> getProfile(String userId) {
        return repository.findById(userId);
    }

    public boolean updateProfile(String userId, UpdateProfileRequest request) {
        if (repository.findById(userId).isEmpty()) {
            return false;
        }
        repository.updateProfile(userId, request.username(), request.avatarUrl());
        return true;
    }

    public boolean addToWatchlist(String userId, String auctionId) {
        if (repository.findById(userId).isEmpty()) {
            return false;
        }
        repository.addToWatchlist(userId, auctionId);
        return true;
    }

    public void removeFromWatchlist(String userId, String auctionId) {
        repository.removeFromWatchlist(userId, auctionId);
    }

    public List<String> getWatchlist(String userId) {
        return repository.getWatchlist(userId);
    }

    public record RegisterResult(String userId, String role, ErrorType error, String existingUsername) {
        public enum ErrorType { EMAIL_TAKEN, ALREADY_SELLER, INVALID_CREDENTIALS, USERNAME_MISMATCH }

        static RegisterResult success(String userId, String role) {
            return new RegisterResult(userId, role, null, null);
        }

        static RegisterResult error(ErrorType type) {
            return new RegisterResult(null, null, type, null);
        }

        static RegisterResult usernameMismatch(String existingUsername) {
            return new RegisterResult(null, null, ErrorType.USERNAME_MISMATCH, existingUsername);
        }

        public boolean isSuccess() {
            return error == null;
        }
    }
}
