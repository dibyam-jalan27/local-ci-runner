package com.localci.model;

import java.util.List;
import java.util.Map;

/**
 * Represents a single step in a CI pipeline.
 *
 * Each step has:
 * - name: human-readable label (required)
 * - run: shell command to execute (required, unless parallel is set)
 * - timeout: max seconds before the step is killed (0 = no limit)
 * - retries: how many times to retry on failure (0 = no retries)
 * - continueOnError: if true, pipeline won't stop when this step fails
 * - workingDir: working directory for the command (null = inherit)
 * - env: step-level environment variables (merged with global)
 * - if: conditional expression — step is skipped if it evaluates to false
 * - parallel: list of sub-steps to run concurrently (Feature 2)
 * - matrix: variable combinations to fan out this step (Feature 6)
 *
 * SnakeYAML requires a no-arg constructor and public setters.
 */
public class Step {

    private String name;
    private String run;
    private int timeout; // seconds, 0 = unlimited
    private int retries; // 0 = no retries
    private boolean continueOnError;
    private String workingDir;
    private Map<String, String> env;
    private String ifCondition;

    // ── Feature 2: Parallel Steps ───────────────────────
    private List<Step> parallel;

    // ── Feature 6: Matrix Builds ────────────────────────
    private Map<String, List<String>> matrix;

    /** No-arg constructor required by SnakeYAML. */
    public Step() {
    }

    // ── Getters ──────────────────────────────────────────

    public String getName() {
        return name;
    }

    public String getRun() {
        return run;
    }

    public int getTimeout() {
        return timeout;
    }

    public int getRetries() {
        return retries;
    }

    public boolean isContinueOnError() {
        return continueOnError;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public String getIf() {
        return ifCondition;
    }

    public List<Step> getParallel() {
        return parallel;
    }

    public Map<String, List<String>> getMatrix() {
        return matrix;
    }

    // ── Setters ──────────────────────────────────────────

    public void setName(String name) {
        this.name = name;
    }

    public void setRun(String run) {
        this.run = run;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public void setContinueOnError(boolean val) {
        this.continueOnError = val;
    }

    public void setWorkingDir(String workingDir) {
        this.workingDir = workingDir;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env;
    }

    public void setIf(String ifCondition) {
        this.ifCondition = ifCondition;
    }

    public void setParallel(List<Step> parallel) {
        this.parallel = parallel;
    }

    public void setMatrix(Map<String, List<String>> matrix) {
        this.matrix = matrix;
    }

    // ── Debug helper ─────────────────────────────────────

    @Override
    public String toString() {
        return "Step{name='" + name + "', run='" + run
                + "', timeout=" + timeout
                + ", retries=" + retries
                + ", continueOnError=" + continueOnError
                + ", parallel=" + (parallel != null ? parallel.size() + " sub-steps" : "null")
                + ", matrix=" + (matrix != null ? matrix.keySet() : "null")
                + '}';
    }
}
