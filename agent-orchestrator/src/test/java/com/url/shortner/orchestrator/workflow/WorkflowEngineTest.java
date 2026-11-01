package com.url.shortner.orchestrator.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.url.shortner.orchestrator.agent.*;
import com.url.shortner.orchestrator.config.AgentSettings;
import com.url.shortner.orchestrator.model.*;
import com.url.shortner.orchestrator.test.TestExecutor;
import com.url.shortner.orchestrator.workspace.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowEngineTest {
    @TempDir Path temp;
    ExecutorService executor;

    @AfterEach void shutdown() { if (executor != null) executor.shutdownNow(); }

    @Test void onFailureReturnsToCodingAgentThenRequestsHumanReview() throws Exception {
        var requirements = mock(RequirementAgent.class);
        var planner = mock(PlanningAgent.class);
        var coder = mock(CodingAgent.class);
        var reviewer = mock(ReviewAgent.class);
        var workspace = mock(WorkspaceManager.class);
        var files = mock(SourceFileTool.class);
        var tests = mock(TestExecutor.class);
        var props = new AgentSettings(temp, temp, 2, Duration.ofSeconds(60),
                "maven:3.9.9-eclipse-temurin-21", "1000:1000", temp);
        var store = new WorkflowStore(new ObjectMapper(), props);
        executor = Executors.newSingleThreadExecutor();
        var engine = new WorkflowEngine(requirements, planner, coder, reviewer, workspace,
                files, tests, store, props, executor);
        when(requirements.analyze(anyString())).thenReturn("Needs caching and tests");
        when(planner.plan(anyString(), anyString())).thenReturn("Add Redis caching");
        when(workspace.create(anyString())).thenReturn(temp.resolve("workspace"));
        when(files.sourceSnapshot(any(), anyInt())).thenReturn("existing Java code");
        var change = new CodeChange("fix", List.of(new CodeChange.SourceFile(
                SourceFileTool.MAIN_PREFIX + "Foo.java",
                "package com.url.shortner; public class Foo {}")));
        when(coder.propose(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(change);
        when(files.apply(any(), any(), anyBoolean())).thenReturn(List.of(SourceFileTool.MAIN_PREFIX + "Foo.java"));
        when(tests.run(any())).thenReturn(
                new TestReport(false, false, 3, 1, "One failing test"),
                new TestReport(true, false, 3, 0, "All passing"));
        when(reviewer.review(anyString(), anyString(), anyString())).thenReturn("Safe for human review");

        WorkflowRun plan = engine.plan("Add Redis caching");
        assertEquals(WorkflowStatus.WAITING_PLAN_APPROVAL, plan.status);
        assertThrows(IllegalStateException.class, () -> engine.execute(plan.id));
        engine.planDecision(plan.id, true);
        engine.execute(plan.id);
        awaitStatus(engine, plan.id, WorkflowStatus.WAITING_REVIEW);
        assertEquals(1, engine.get(plan.id).fixAttempts);
        verify(coder).propose(anyString(), anyString(), anyString(), eq(false));
        verify(coder).propose(anyString(), anyString(), anyString(), eq(true));
        verify(tests, times(2)).run(any());
        engine.reviewDecision(plan.id, true);
        assertEquals(WorkflowStatus.APPROVED, engine.get(plan.id).status);
    }

    @Test void retryLimitCausesSafeStop() throws Exception {
        var requirements = mock(RequirementAgent.class);
        var planner = mock(PlanningAgent.class);
        var coder = mock(CodingAgent.class);
        var reviewer = mock(ReviewAgent.class);
        var workspace = mock(WorkspaceManager.class);
        var files = mock(SourceFileTool.class);
        var tests = mock(TestExecutor.class);
        var props = new AgentSettings(temp, temp, 1, Duration.ofSeconds(60),
                "maven:3.9.9-eclipse-temurin-21", "1000:1000", temp);
        var store = new WorkflowStore(new ObjectMapper(), props);
        executor = Executors.newSingleThreadExecutor();
        var engine = new WorkflowEngine(requirements, planner, coder, reviewer, workspace,
                files, tests, store, props, executor);
        when(requirements.analyze(anyString())).thenReturn("analysis");
        when(planner.plan(anyString(), anyString())).thenReturn("plan");
        when(workspace.create(anyString())).thenReturn(temp.resolve("workspace"));
        when(files.sourceSnapshot(any(), anyInt())).thenReturn("code");
        when(coder.propose(anyString(), anyString(), anyString(), anyBoolean())).thenReturn(
                new CodeChange("fix", List.of(new CodeChange.SourceFile(
                        SourceFileTool.MAIN_PREFIX + "Foo.java", "package com.url.shortner; class Foo {}"))));
        when(files.apply(any(), any(), anyBoolean())).thenReturn(List.of("Foo.java"));
        when(tests.run(any())).thenReturn(new TestReport(false, false, 3, 3, "failed"));
        WorkflowRun run = engine.plan("Improve cache");
        engine.planDecision(run.id, true);
        engine.execute(run.id);
        awaitStatus(engine, run.id, WorkflowStatus.NEEDS_HUMAN);
        assertEquals(1, engine.get(run.id).fixAttempts);
        verify(tests, times(2)).run(any());
        assertThrows(IllegalStateException.class, () -> engine.reviewDecision(run.id, true));
    }

    private static void awaitStatus(WorkflowEngine engine, String id,
                                    WorkflowStatus expected) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            if (engine.get(id).status == expected) return;
            Thread.sleep(20);
        }
        fail("Workflow did not reach " + expected + ", got " + engine.get(id).status);
    }
}
