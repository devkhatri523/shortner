package com.url.shortner.orchestrator.test;

import com.url.shortner.orchestrator.model.TestReport;
import java.nio.file.Path;

public interface TestExecutor {
    TestReport run(Path workspace);
}
