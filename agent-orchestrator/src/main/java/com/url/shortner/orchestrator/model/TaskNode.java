package com.url.shortner.orchestrator.model;

import java.util.List;

/** Explicit dependency graph; approvals are gates owned by the orchestrator. */
public record TaskNode(String id, List<String> dependsOn, String exitGate) {}
