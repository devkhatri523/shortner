package com.url.shortner.orchestrator.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.url.shortner.orchestrator.config.AgentSettings;
import com.url.shortner.orchestrator.model.WorkflowRun;
import com.url.shortner.orchestrator.model.WorkflowStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Atomic JSON checkpoints for demonstration; replace with PostgreSQL in production. */
@Component
public class WorkflowStore {
    private final ObjectMapper mapper;
    private final Path location;
    private final Map<String, WorkflowRun> runs = new ConcurrentHashMap<>();

    public WorkflowStore(ObjectMapper mapper, AgentSettings settings) throws IOException {
        this.mapper = mapper;
        this.location = settings.workspaceRoot().toAbsolutePath().normalize().resolve("runs");
        Files.createDirectories(location);
        try (var paths = Files.list(location)) {
            for (Path file : paths.filter(f -> f.getFileName().toString().endsWith(".json")).toList()) {
                WorkflowRun run = mapper.readValue(file.toFile(), WorkflowRun.class);
                if (run.status == WorkflowStatus.RUNNING) {
                    run.status = WorkflowStatus.NEEDS_HUMAN;
                    run.record("INTERRUPTED", "Process stopped; manually inspect workspace before resuming");
                    save(run);
                }
                runs.put(run.id, run);
            }
        }
    }

    public WorkflowRun get(String id) {
        WorkflowRun run = runs.get(id);
        if (run == null) throw new NoSuchElementException("Unknown workflow: " + id);
        return run;
    }

    public void save(WorkflowRun run) {
        synchronized (run) {
            try {
                Path file = location.resolve(run.id + ".json");
                Path temp = Files.createTempFile(location, "checkpoint-", ".tmp");
                try {
                    mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), run);
                    try {
                        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException ex) {
                        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(temp);
                }
                runs.put(run.id, run);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot persist workflow checkpoint", e);
            }
        }
    }
}
