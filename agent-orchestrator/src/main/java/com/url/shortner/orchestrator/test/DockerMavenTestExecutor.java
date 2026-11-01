package com.url.shortner.orchestrator.test;

import com.url.shortner.orchestrator.config.AgentSettings;
import com.url.shortner.orchestrator.model.TestReport;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Untrusted agent-generated code is built inside an offline, unprivileged Docker sandbox. */
@Component
public class DockerMavenTestExecutor implements TestExecutor {
    private final AgentSettings settings;
    private final SurefireReportReader reader;

    public DockerMavenTestExecutor(AgentSettings settings, SurefireReportReader reader) {
        this.settings = settings;
        this.reader = reader;
    }

    @Override
    public TestReport run(Path workspace) {
        Path cache = settings.mavenCache().toAbsolutePath().normalize();
        if (!Files.isDirectory(cache)) {
            return new TestReport(false, true, 0, 0,
                    "Maven dependency cache missing: " + cache +
                    ". Run the documented dependency prewarm step.");
        }
        String containerName = "shortner-agent-test-" + java.util.UUID.randomUUID();
        List<String> args = new ArrayList<>(List.of(
                "docker", "run", "--rm", "--name", containerName,
                "--network", "none",
                "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
                "--pids-limit", "256", "--memory", "1024m", "--cpus", "1",
                "--user", settings.dockerUser(),
                "-v", workspace.toAbsolutePath().normalize() + ":/workspace:rw",
                "-v", cache + ":/maven-cache:ro",
                "-w", "/workspace",
                "-e", "MAVEN_OPTS=-Dmaven.repo.local=/maven-cache",
                settings.dockerImage(),
                "mvn", "-o", "-B", "-ntp", "-pl", "url-shortener-service", "test"));
        try {
            Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
            StringBuilder output = new StringBuilder();
            Thread readerThread = Thread.ofVirtual().start(() -> {
                try (var input = new BufferedReader(new InputStreamReader(
                        process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = input.readLine()) != null) {
                        output.append(line).append('\n');
                        if (output.length() > 60000) output.delete(0, output.length() - 32000);
                    }
                } catch (IOException ignored) { }
            });
            boolean finished = process.waitFor(settings.testTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                // Do not rely on killing only the Docker CLI; stop the container too.
                Process cleanup = new ProcessBuilder("docker", "rm", "-f", containerName)
                        .redirectErrorStream(true).start();
                cleanup.waitFor(10, TimeUnit.SECONDS);
                readerThread.join(1000);
                return new TestReport(false, true, 0, 0, "Maven test timeout; container stopped");
            }
            readerThread.join(1000);
            SurefireReportReader.Summary summary = reader.parse(workspace);
            boolean passing = process.exitValue() == 0 && summary.total() > 0 && summary.failed() == 0;
            // Compilation failures are CODE failures: return them to the coding agent.
            // Missing tools/dependencies/zero-test success are infrastructure failures.
            boolean compileFailure = output.toString().contains("COMPILATION ERROR") ||
                    output.toString().contains("Compilation failure") ||
                    output.toString().contains("cannot find symbol");
            boolean infra = summary.total() == 0 && !compileFailure ||
                    (process.exitValue() != 0 && summary.failed() == 0 && !compileFailure);
            String details = "exit=" + process.exitValue() + ", tests=" + summary.total() +
                    ", failed=" + summary.failed() + "\n" + summary.failures() +
                    "\nMaven output:\n" + output;
            return new TestReport(passing, infra, summary.total(), summary.failed(), details);
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return new TestReport(false, true, 0, 0,
                    "Cannot execute Docker test sandbox: " + e.getMessage());
        }
    }
}
