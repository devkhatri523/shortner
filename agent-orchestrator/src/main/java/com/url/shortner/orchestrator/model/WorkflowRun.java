package com.url.shortner.orchestrator.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Mutable only through WorkflowEngine while holding this run's monitor. */
public class WorkflowRun {
    public String id;
    public String requirement;
    public String analysis = "";
    public String plan = "";
    public String review = "";
    public String lastTestResult = "";
    public String workspace = "";
    public WorkflowStatus status = WorkflowStatus.WAITING_PLAN_APPROVAL;
    public int fixAttempts;
    public List<TaskNode> tasks = new ArrayList<>();
    public List<String> changedFiles = new ArrayList<>();
    public List<AuditEvent> audit = new ArrayList<>();
    public String createdAt;
    public String updatedAt;

    public WorkflowRun() {} // Jackson persistence

    public WorkflowRun(String requirement) {
        id = UUID.randomUUID().toString();
        this.requirement = requirement;
        createdAt = Instant.now().toString();
        updatedAt = createdAt;
    }

    public void record(String event, String detail) {
        String time = Instant.now().toString();
        audit.add(new AuditEvent(time, event, truncate(detail, 2500)));
        updatedAt = time;
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "...[truncated]" : text;
    }
}
