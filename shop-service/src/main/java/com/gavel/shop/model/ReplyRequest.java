package com.gavel.shop.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReplyRequest(
        @NotBlank @Size(min = 1) String reply
) {}
