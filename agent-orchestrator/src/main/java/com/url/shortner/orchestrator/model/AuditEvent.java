package com.url.shortner.orchestrator.model;

public record AuditEvent(String timestamp, String event, String detail) {}
