package com.url.shortner.orchestrator.workflow;

import com.url.shortner.orchestrator.agent.*;
import com.url.shortner.orchestrator.config.AgentSettings;
import com.url.shortner.orchestrator.model.*;
import com.url.shortner.orchestrator.test.TestExecutor;
import com.url.shortner.orchestrator.workspace.SourceFileTool;
import com.url.shortner.orchestrator.workspace.WorkspaceManager;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;

@Service
public class WorkflowEngine {
    private final RequirementAgent requirementAgent;
    private final PlanningAgent planningAgent;
    private final CodingAgent codingAgent;
    private final ReviewAgent reviewAgent;
    private final WorkspaceManager workspaces;
    private final SourceFileTool fileTool;
    private final TestExecutor tests;
    private final WorkflowStore store;
    private final AgentSettings settings;
    private final ExecutorService executor;

    public WorkflowEngine(RequirementAgent requirementAgent, PlanningAgent planningAgent,
                          CodingAgent codingAgent, ReviewAgent reviewAgent,
                          WorkspaceManager workspaces, SourceFileTool fileTool,
                          TestExecutor tests, WorkflowStore store, AgentSettings settings,
                          ExecutorService executor) {
        this.requirementAgent = requirementAgent;
        this.planningAgent = planningAgent;
        this.codingAgent = codingAgent;
        this.reviewAgent = reviewAgent;
        this.workspaces = workspaces;
        this.fileTool = fileTool;
        this.tests = tests;
        this.store = store;
        this.settings = settings;
        this.executor = executor;
    }

    /** This is a REAL LLM call; the deterministic DAG provides governance. */
    public WorkflowRun plan(String requirement) {
        if (requirement == null || requirement.isBlank() || requirement.length() > 3000) {
            throw new IllegalArgumentException("Requirement is required (max 3000 chars)");
        }
        WorkflowRun run = new WorkflowRun(requirement);
        // Explicit dependencies: tests and documentation can be independent,
        // but review waits for evidence and human approval remains mandatory.
        run.tasks = List.of(
                new TaskNode("requirements", List.of(), "analysis-recorded"),
                new TaskNode("plan", List.of("requirements"), "human-plan-approval"),
                new TaskNode("implementation", List.of("plan"), "validated-source-writes"),
                new TaskNode("tests", List.of("implementation"), "surefire-tests-pass"),
                new TaskNode("review", List.of("tests"), "human-review-approval")
        );
        run.record("SUBMITTED", "Requirement submitted");
        store.save(run);
        try {
            run.analysis = requirementAgent.analyze(requirement);
            run.record("REQUIREMENTS_ANALYZED", run.analysis);
            store.save(run);
            run.plan = planningAgent.plan(requirement, run.analysis);
            run.record("PLAN_CREATED", run.plan);
            run.status = WorkflowStatus.WAITING_PLAN_APPROVAL;
            store.save(run);
        } catch (Exception ex) {
            run.status = WorkflowStatus.NEEDS_HUMAN;
            run.record("LLM_ERROR", ex.getClass().getSimpleName() + ": " + ex.getMessage());
            store.save(run);
        }
        return run;
    }

    public WorkflowRun get(String id) { return store.get(id); }

    public WorkflowRun planDecision(String id, boolean approved) {
        WorkflowRun run = store.get(id);
        synchronized (run) {
            if (run.status != WorkflowStatus.WAITING_PLAN_APPROVAL) {
                throw new IllegalStateException("Workflow is not awaiting plan approval");
            }
            run.status = approved ? WorkflowStatus.READY : WorkflowStatus.REJECTED;
            run.record("HUMAN_PLAN_DECISION", approved ? "Approved" : "Rejected");
            store.save(run);
            return run;
        }
    }

    public WorkflowRun execute(String id) {
        WorkflowRun run = store.get(id);
        synchronized (run) {
            if (run.status != WorkflowStatus.READY) {
                throw new IllegalStateException("Approval required or workflow already started");
            }
            run.status = WorkflowStatus.RUNNING;
            run.record("EXECUTION_STARTED", "Running within bounded workspace");
            store.save(run);
            try {
                executor.execute(() -> executeWorkflow(run));
            } catch (RuntimeException ex) {
                run.status = WorkflowStatus.NEEDS_HUMAN;
                run.record("EXECUTOR_ERROR", ex.getMessage());
                store.save(run);
            }
            return run;
        }
    }

    private void executeWorkflow(WorkflowRun run) {
        try {
            Path workspace = workspaces.create(run.id);
            synchronized (run) {
                run.workspace = workspace.toString();
                run.record("WORKSPACE_CREATED", "Isolated copy; original source is unchanged");
                store.save(run);
            }
            String context = fileTool.sourceSnapshot(workspace, 32000);
            CodeChange initial = codingAgent.propose(run.requirement, context,
                    "Generate initial implementation and optional NEW tests.", false);
            List<String> files = fileTool.apply(workspace, initial, false);
            update(run, "CODE_APPLIED", files, initial.explanation());

            while (true) {
                TestReport report = tests.run(workspace);
                synchronized (run) {
                    run.lastTestResult = report.details();
                    run.record("TEST_RESULT", "passed=" + report.passed() +
                            ", count=" + report.totalTests() + ", failed=" + report.failedTests() +
                            ", infrastructureError=" + report.infrastructureError());
                    store.save(run);
                }
                if (report.passed()) {
                    String review = reviewAgent.review(run.requirement,
                            String.join("\n", run.changedFiles) + "\n" +
                                    fileTool.sourceSnapshot(workspace, 26000),
                            report.details());
                    synchronized (run) {
                        run.review = review;
                        run.status = WorkflowStatus.WAITING_REVIEW;
                        run.record("HUMAN_REVIEW_REQUIRED", "All tests passed; human must inspect workspace and review");
                        store.save(run);
                    }
                    return;
                }
                synchronized (run) {
                    if (report.infrastructureError() ||
                            run.fixAttempts >= settings.maxFixAttempts()) {
                        run.status = WorkflowStatus.NEEDS_HUMAN;
                        run.record("SAFE_STOP", report.infrastructureError()
                                ? "Test infrastructure error: inspect Docker, Maven cache or compilation output"
                                : "Maximum configured fix attempts exhausted");
                        store.save(run);
                        return;
                    }
                    run.fixAttempts++;
                    run.record("REPAIR_ATTEMPT", "Fix attempt " + run.fixAttempts +
                            "/" + settings.maxFixAttempts());
                    store.save(run);
                }
                CodeChange fix = codingAgent.propose(run.requirement,
                        fileTool.sourceSnapshot(workspace, 32000),
                        report.details().substring(0, Math.min(12000, report.details().length())),
                        true);
                files = fileTool.apply(workspace, fix, true);
                update(run, "REPAIR_APPLIED", files, fix.explanation());
            }
        } catch (Exception ex) {
            synchronized (run) {
                run.status = WorkflowStatus.NEEDS_HUMAN;
                run.record("SAFE_STOP", ex.getClass().getSimpleName() + ": " + ex.getMessage());
                store.save(run);
            }
        }
    }

    private void update(WorkflowRun run, String event, List<String> files, String explanation) {
        synchronized (run) {
            for (String file : files) if (!run.changedFiles.contains(file)) run.changedFiles.add(file);
            run.record(event, "Files: " + files + " | " + explanation);
            store.save(run);
        }
    }

    public WorkflowRun reviewDecision(String id, boolean approved) {
        WorkflowRun run = store.get(id);
        synchronized (run) {
            if (run.status != WorkflowStatus.WAITING_REVIEW) {
                throw new IllegalStateException("Review decision allowed only after passing tests");
            }
            run.status = approved ? WorkflowStatus.APPROVED : WorkflowStatus.REJECTED;
            run.record("HUMAN_REVIEW_DECISION", approved
                    ? "Approved for manual merge; no automatic merge or deployment"
                    : "Rejected; source branch remains unchanged");
            store.save(run);
            return run;
        }
    }
}
