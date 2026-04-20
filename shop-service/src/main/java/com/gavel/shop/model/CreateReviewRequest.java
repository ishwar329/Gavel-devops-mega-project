package com.gavel.shop.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateReviewRequest(
        @NotBlank String auctionId,
        @NotNull @Min(1) @Max(5) Integer rating,
        String comment
) {}
