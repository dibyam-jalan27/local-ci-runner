package com.localci.model;

import java.util.List;

/**
 * Represents lifecycle hooks for the pipeline.
 *
 * YAML structure:
 * 
 * <pre>
 *   hooks:
 *     before_pipeline:
 *       - "echo 'Pipeline starting'"
 *       - "scripts/setup.sh"
 *     after_pipeline:
 *       - "scripts/cleanup.sh"
 *     before_step:
 *       - "echo 'Step starting'"
 *     after_step:
 *       - "echo 'Step done'"
 * </pre>
 *
 * Each hook entry can be:
 * - A shell command (executed directly)
 * - A path to a .sh script
 * - A path to a .jar plugin
 */
public class Hooks {

    private List<String> before_pipeline;
    private List<String> after_pipeline;
    private List<String> before_step;
    private List<String> after_step;

    public Hooks() {
    }

    // ── Getters ──────────────────────────────────────────

    public List<String> getBefore_pipeline() {
        return before_pipeline;
    }

    public List<String> getAfter_pipeline() {
        return after_pipeline;
    }

    public List<String> getBefore_step() {
        return before_step;
    }

    public List<String> getAfter_step() {
        return after_step;
    }

    // ── Setters ──────────────────────────────────────────

    public void setBefore_pipeline(List<String> before_pipeline) {
        this.before_pipeline = before_pipeline;
    }

    public void setAfter_pipeline(List<String> after_pipeline) {
        this.after_pipeline = after_pipeline;
    }

    public void setBefore_step(List<String> before_step) {
        this.before_step = before_step;
    }

    public void setAfter_step(List<String> after_step) {
        this.after_step = after_step;
    }
}
