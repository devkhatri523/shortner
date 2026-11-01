package com.url.shortner.orchestrator.controller;

import com.url.shortner.orchestrator.dto.DecisionRequest;
import com.url.shortner.orchestrator.dto.RequirementRequest;
import com.url.shortner.orchestrator.model.WorkflowRun;
import com.url.shortner.orchestrator.workflow.WorkflowEngine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {
    private final WorkflowEngine engine;

    public WorkflowController(WorkflowEngine engine) {
        this.engine = engine;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowRun create(@Valid @RequestBody RequirementRequest request) {
        return engine.plan(request.requirement());
    }

    @GetMapping("/{id}")
    public WorkflowRun get(@PathVariable String id) {
        return engine.get(id);
    }

    @PostMapping("/{id}/plan-decision")
    public WorkflowRun planDecision(@PathVariable String id, @RequestBody DecisionRequest request) {
        return engine.planDecision(id, request.approve());
    }

    @PostMapping("/{id}/execute")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public WorkflowRun execute(@PathVariable String id) {
        return engine.execute(id);
    }

    @PostMapping("/{id}/review-decision")
    public WorkflowRun reviewDecision(@PathVariable String id, @RequestBody DecisionRequest request) {
        return engine.reviewDecision(id, request.approve());
    }

    @ExceptionHandler(java.util.NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public java.util.Map<String, String> notFound(Exception ex) {
        return java.util.Map.of("error", ex.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public java.util.Map<String, String> badRequest(Exception ex) {
        return java.util.Map.of("error", ex.getMessage());
    }
}
