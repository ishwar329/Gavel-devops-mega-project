package com.gavel.shop.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateItemRequest(
        @NotBlank @Size(min = 1) String title,
        String description,
        Long retailValue,
        String imageUrl,
        String category
) {}
