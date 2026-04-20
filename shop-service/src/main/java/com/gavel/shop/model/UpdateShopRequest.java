package com.gavel.shop.model;

public record UpdateShopRequest(
        String name,
        String location,
        String logoUrl,
        Double lat,
        Double lng
) {}
