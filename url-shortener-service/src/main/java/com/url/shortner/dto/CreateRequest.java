package com.url.shortner.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRequest(@NotBlank String url) {}