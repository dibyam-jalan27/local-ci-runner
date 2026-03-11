package com.localci.model;

import java.util.List;

/**
 * Represents those results of a complete pipeline run for report generation.
 *
 * Used by {@link com.localci.report.ReportGenerator} to emit
 * JSON and HTML reports after a pipeline execution.
 */
public class RunReport {

    private String pipelineName;
    private String startTime;
    private String endTime;
    private long totalDurationMs;
    private String status; // "PASSED" or "FAILED"
    private List<StepEntry> steps;

    public RunReport() {
    }

    // ── Getters / Setters ───────────────────────────────

    public String getPipelineName() {
        return pipelineName;
    }

    public void setPipelineName(String pipelineName) {
        this.pipelineName = pipelineName;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public void setTotalDurationMs(long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<StepEntry> getSteps() {
        return steps;
    }

    public void setSteps(List<StepEntry> steps) {
        this.steps = steps;
    }

    // ══════════════════════════════════════════════════════
    // Per-step entry for the report
    // ══════════════════════════════════════════════════════

    public static class StepEntry {
        private String name;
        private String status;
        private long durationMs;
        private int retries;
        private int exitCode;

        public StepEntry() {
        }

        public StepEntry(String name, String status, long durationMs, int retries, int exitCode) {
            this.name = name;
            this.status = status;
            this.durationMs = durationMs;
            this.retries = retries;
            this.exitCode = exitCode;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public void setDurationMs(long durationMs) {
            this.durationMs = durationMs;
        }

        public int getRetries() {
            return retries;
        }

        public void setRetries(int retries) {
            this.retries = retries;
        }

        public int getExitCode() {
            return exitCode;
        }

        public void setExitCode(int exitCode) {
            this.exitCode = exitCode;
        }
    }
}
