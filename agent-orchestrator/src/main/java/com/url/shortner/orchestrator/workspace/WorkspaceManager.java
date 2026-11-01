package com.url.shortner.orchestrator.workspace;

import com.url.shortner.orchestrator.config.AgentSettings;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

/** Copies only approved source/pom files into a workflow-specific workspace. */
@Component
public class WorkspaceManager {
    private final AgentSettings settings;

    public WorkspaceManager(AgentSettings settings) { this.settings = settings; }

    public Path create(String workflowId) throws IOException {
        Path root = settings.sourceRoot().toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve("pom.xml")) ||
            !Files.isDirectory(root.resolve("url-shortener-service/src"))) {
            // Convenience: supports launching the application from the reactor root.
            Path parent = root.getParent();
            if (parent != null && Files.isRegularFile(parent.resolve("pom.xml")) &&
                    Files.isDirectory(parent.resolve("url-shortener-service/src"))) {
                root = parent;
            } else if (Files.isRegularFile(root.resolve("agent-orchestrator/../pom.xml")) &&
                    Files.isDirectory(root.resolve("url-shortener-service/src"))) {
                // Already validated by first condition in normal use.
            } else {
                throw new IllegalArgumentException("AGENT_SOURCE_ROOT must point at the Maven reactor root");
            }
        }
        Path workspaceRoot = settings.workspaceRoot().toAbsolutePath().normalize();
        Files.createDirectories(workspaceRoot);
        Path dst = workspaceRoot.resolve(workflowId).normalize();
        if (!dst.getParent().equals(workspaceRoot) || Files.exists(dst)) {
            throw new IOException("Invalid or existing workflow workspace");
        }
        Files.createDirectory(dst);
        try {
            copyFile(root.resolve("pom.xml"), dst.resolve("pom.xml"));
            copyFile(root.resolve("url-shortener-service/pom.xml"),
                    dst.resolve("url-shortener-service/pom.xml"));
            // Root POM declares both modules; copy orchestrator POM only, not credentials or code.
            copyFile(root.resolve("agent-orchestrator/pom.xml"),
                    dst.resolve("agent-orchestrator/pom.xml"));
            copyTree(root.resolve("url-shortener-service/src"),
                    dst.resolve("url-shortener-service/src"));
        } catch (IOException | RuntimeException e) {
            deleteTree(dst);
            throw e;
        }
        return dst;
    }

    private void copyFile(Path from, Path to) throws IOException {
        if (Files.isSymbolicLink(from) || !Files.isRegularFile(from)) {
            throw new IOException("Unexpected source file: " + from);
        }
        Files.createDirectories(to.getParent());
        Files.copy(from, to);
    }

    private void copyTree(Path from, Path to) throws IOException {
        // Source checkout is trusted input; never follow symbolic links.
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            int count = 0;
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                if (Files.isSymbolicLink(dir)) throw new IOException("Symlink directory: " + dir);
                Files.createDirectories(to.resolve(from.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (Files.isSymbolicLink(file) || !attrs.isRegularFile()) {
                    throw new IOException("Unexpected source type: " + file);
                }
                if (++count > 500 || attrs.size() > 2_000_000) {
                    throw new IOException("Source tree exceeds demo safety limits");
                }
                Files.copy(file, to.resolve(from.relativize(file)));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            for (Path path : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
