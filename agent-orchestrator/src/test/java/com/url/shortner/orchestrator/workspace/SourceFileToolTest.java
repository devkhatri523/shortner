package com.url.shortner.orchestrator.workspace;

import com.url.shortner.orchestrator.model.CodeChange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SourceFileToolTest {
    @TempDir Path workspace;
    private final SourceFileTool tool = new SourceFileTool();

    @Test void writesOnlyAllowedJavaFiles() throws Exception {
        String path = SourceFileTool.MAIN_PREFIX + "AddedFeature.java";
        var proposal = new CodeChange("add feature", List.of(new CodeChange.SourceFile(
                path, "package com.url.shortner; public class AddedFeature {}")));
        assertEquals(List.of(path), tool.apply(workspace, proposal, false));
        assertTrue(Files.readString(workspace.resolve(path)).contains("AddedFeature"));
    }

    @Test void refusesPathTraversalAndOriginalRepoEdits() {
        var proposal = new CodeChange("escape", List.of(new CodeChange.SourceFile(
                SourceFileTool.MAIN_PREFIX + "../../../../secrets.java",
                "package com.url.shortner; public class Secrets {}")));
        assertThrows(SecurityException.class, () -> tool.apply(workspace, proposal, false));
    }

    @Test void refusesOverwritingExistingTests() throws Exception {
        String path = SourceFileTool.TEST_PREFIX + "ExistingTest.java";
        Files.createDirectories(workspace.resolve(path).getParent());
        Files.writeString(workspace.resolve(path), "original test");
        var proposal = new CodeChange("bad test", List.of(new CodeChange.SourceFile(
                path, "package com.url.shortner; public class ExistingTest {}")));
        assertThrows(SecurityException.class, () -> tool.apply(workspace, proposal, false));
        assertEquals("original test", Files.readString(workspace.resolve(path)));
    }

    @Test void refusesChangingTestsDuringRepair() {
        String path = SourceFileTool.TEST_PREFIX + "NewTest.java";
        var proposal = new CodeChange("repair", List.of(new CodeChange.SourceFile(
                path, "package com.url.shortner; public class NewTest {}")));
        assertThrows(SecurityException.class, () -> tool.apply(workspace, proposal, true));
    }
}
