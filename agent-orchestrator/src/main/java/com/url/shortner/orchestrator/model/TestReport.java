package com.url.shortner.orchestrator.model;

public record TestReport(boolean passed, boolean infrastructureError,
                         int totalTests, int failedTests, String details) {}
