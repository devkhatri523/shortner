package com.url.shortner.orchestrator.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class SurefireReportReaderTest {
    @TempDir Path workspace;

    @Test void extractsFailureDetailsFromRealJUnitXml() throws Exception {
        Path reports = workspace.resolve("url-shortener-service/target/surefire-reports");
        Files.createDirectories(reports);
        Files.writeString(reports.resolve("TEST-com.url.shortner.LinkTest.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite tests="3" failures="1" errors="0">
                    <testcase classname="LinkTest" name="redirectOk" />
                    <testcase classname="LinkTest" name="redirectFail">
                        <failure message="Expected 302 but got 404">failure stack</failure>
                    </testcase>
                    <testcase classname="LinkTest" name="cacheHit" />
                </testsuite>
                """);
        var summary = new SurefireReportReader().parse(workspace);
        assertEquals(3, summary.total());
        assertEquals(1, summary.failed());
        assertTrue(summary.failures().contains("redirectFail"));
    }
}
