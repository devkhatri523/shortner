package com.url.shortner.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;

public record RequirementRequest(@NotBlank String requirement) {}