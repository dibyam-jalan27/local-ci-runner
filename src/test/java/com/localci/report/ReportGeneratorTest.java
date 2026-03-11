package com.localci.report;

import com.localci.model.RunReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReportGenerator: JSON and HTML report generation.
 */
class ReportGeneratorTest {

    @TempDir
    Path tempDir;

    private RunReport sampleReport() {
        RunReport report = new RunReport();
        report.setPipelineName("Test Pipeline");
        report.setStartTime("2026-03-04 12:00:00");
        report.setEndTime("2026-03-04 12:01:30");
        report.setTotalDurationMs(90000);
        report.setStatus("PASSED");
        report.setSteps(List.of(
                new RunReport.StepEntry("Build", "PASSED", 30000, 1, 0),
                new RunReport.StepEntry("Test", "PASSED", 45000, 2, 0),
                new RunReport.StepEntry("Deploy", "FAILED", 15000, 1, 1)));
        return report;
    }

    @Test
    void generatesJsonReport() throws IOException {
        String path = ReportGenerator.generateJson(sampleReport(), tempDir.toString());

        File file = new File(path);
        assertTrue(file.exists());
        assertEquals("run-report.json", file.getName());

        String content = Files.readString(file.toPath());
        assertTrue(content.contains("\"pipelineName\": \"Test Pipeline\""));
        assertTrue(content.contains("\"status\": \"PASSED\""));
        assertTrue(content.contains("\"Build\""));
        assertTrue(content.contains("\"Test\""));
        assertTrue(content.contains("\"Deploy\""));
    }

    @Test
    void generatesHtmlReport() throws IOException {
        String path = ReportGenerator.generateHtml(sampleReport(), tempDir.toString());

        File file = new File(path);
        assertTrue(file.exists());
        assertEquals("run-report.html", file.getName());

        String content = Files.readString(file.toPath());
        assertTrue(content.contains("<!DOCTYPE html>"));
        assertTrue(content.contains("Test Pipeline"));
        assertTrue(content.contains("PASSED"));
        assertTrue(content.contains("Build"));
        assertTrue(content.contains("Deploy"));
        assertTrue(content.contains("Local CI Runner"));
    }

    @Test
    void jsonContainsAllStepFields() throws IOException {
        String path = ReportGenerator.generateJson(sampleReport(), tempDir.toString());
        String content = Files.readString(Path.of(path));

        assertTrue(content.contains("\"durationMs\""));
        assertTrue(content.contains("\"retries\""));
        assertTrue(content.contains("\"exitCode\""));
        assertTrue(content.contains("90000"));
    }

    @Test
    void createsOutputDirectoryIfMissing() throws IOException {
        String subDir = tempDir.resolve("nonexistent/deep/dir").toString();
        String path = ReportGenerator.generateJson(sampleReport(), subDir);

        assertTrue(new File(path).exists());
    }
}
