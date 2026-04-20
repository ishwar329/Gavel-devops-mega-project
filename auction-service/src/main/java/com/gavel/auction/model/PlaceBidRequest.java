package com.gavel.auction.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlaceBidRequest(
        @JsonProperty("amount") long amount
) {}
