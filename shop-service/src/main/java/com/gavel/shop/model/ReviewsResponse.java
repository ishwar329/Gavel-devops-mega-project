package com.gavel.shop.model;

import java.util.List;

public record ReviewsResponse(
        List<Review> reviews,
        double averageRating,
        int totalReviews
) {}
