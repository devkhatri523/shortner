package com.url.shortner.orchestrator.workspace;

import com.url.shortner.orchestrator.model.CodeChange;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** Policy boundary between untrusted LLM text and a writable source tree. */
@Component
public class SourceFileTool {
    public static final String MAIN_PREFIX =
            "url-shortener-service/src/main/java/com/url/shortner/";
    public static final String TEST_PREFIX =
            "url-shortener-service/src/test/java/com/url/shortner/";

    public List<String> apply(Path workspace, CodeChange proposed, boolean repair)
            throws IOException {
        if (proposed == null || proposed.files() == null || proposed.files().isEmpty() ||
                proposed.files().size() > 12) {
            throw new SecurityException("Patch must contain 1..12 files");
        }
        Path root = workspace.toAbsolutePath().normalize();
        Map<Path, String> writes = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (CodeChange.SourceFile proposedFile : proposed.files()) {
            if (proposedFile == null || proposedFile.path() == null || proposedFile.content() == null) {
                throw new SecurityException("Invalid file proposal");
            }
            String path = proposedFile.path();
            boolean main = path.startsWith(MAIN_PREFIX);
            boolean test = path.startsWith(TEST_PREFIX);
            if ((!main && !test) || (repair && !main) ||
                    !path.endsWith(".java") || path.contains("\\") ||
                    path.contains("..") || path.contains("//") || path.length() > 250 ||
                    !seen.add(path)) {
                throw new SecurityException("Forbidden file path: " + path);
            }
            String code = proposedFile.content();
            if (code.length() > 80_000 || code.indexOf('\0') >= 0 ||
                    !(code.stripLeading().startsWith("package com.url.shortner;") ||
                      code.stripLeading().startsWith("package com.url.shortner."))) {
                throw new SecurityException("Invalid Java source content: " + path);
            }
            Path target = root.resolve(path).normalize();
            if (!target.startsWith(root) || !Files.isDirectory(root)) {
                throw new SecurityException("Path escapes isolated workspace");
            }
            // Never allow edits to existing test cases. Additional tests are allowed on first pass.
            if (test && (!path.endsWith("Test.java") || Files.exists(target))) {
                throw new SecurityException("Cannot overwrite existing tests: " + path);
            }
            for (Path parent = target; parent != null && parent.startsWith(root);
                 parent = parent.getParent()) {
                if (Files.isSymbolicLink(parent)) {
                    throw new SecurityException("Symlink prohibited: " + path);
                }
            }
            writes.put(target, code);
        }
        // Validate whole proposal before making any changes; always preserve original checkout.
        Map<Path, byte[]> originals = new LinkedHashMap<>();
        List<Path> written = new ArrayList<>();
        try {
            for (var entry : writes.entrySet()) {
                Path target = entry.getKey();
                originals.put(target, Files.exists(target) ? Files.readAllBytes(target) : null);
                Files.createDirectories(target.getParent());
                Files.writeString(target, entry.getValue(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                written.add(target);
            }
        } catch (IOException | RuntimeException ex) {
            for (Path target : written) {
                byte[] previous = originals.get(target);
                if (previous == null) Files.deleteIfExists(target);
                else Files.write(target, previous);
            }
            throw ex;
        }
        return writes.keySet().stream().map(root::relativize).map(Path::toString).toList();
    }

    public String sourceSnapshot(Path workspace, int limit) throws IOException {
        Path src = workspace.resolve("url-shortener-service/src");
        StringBuilder context = new StringBuilder();
        try (Stream<Path> paths = Files.walk(src)) {
            List<Path> files = paths.filter(p -> Files.isRegularFile(p) &&
                    p.toString().endsWith(".java") && !Files.isSymbolicLink(p))
                    .sorted().toList();
            for (Path file : files) {
                if (context.length() >= limit) break;
                String source = Files.readString(file);
                int left = limit - context.length();
                String header = "\nFILE " + workspace.relativize(file) + "\n";
                context.append(header);
                if (left < header.length() + 1) break;
                context.append(source, 0, Math.min(source.length(), left - header.length()));
                context.append("\n");
            }
        }
        return context.toString();
    }
}
