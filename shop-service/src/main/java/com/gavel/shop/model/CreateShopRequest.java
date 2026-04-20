package com.gavel.shop.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateShopRequest(
        @NotBlank @Size(min = 2) String name,
        @NotBlank String location,
        String logoUrl,
        Double lat,
        Double lng
) {}
