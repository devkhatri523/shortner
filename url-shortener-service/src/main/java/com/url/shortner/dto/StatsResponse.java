package com.url.shortner.dto;

public record StatsResponse(String code, String originalUrl, long clicks) {}