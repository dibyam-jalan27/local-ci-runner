package com.localci.model;

import java.util.List;

/**
 * Represents the triggers configuration for a pipeline.
 *
 * YAML structure:
 * 
 * <pre>
 *   triggers:
 *     schedule: "0 * * * *"
 *     watch:
 *       - "src/"
 *       - "pom.xml"
 *     manual: true
 * </pre>
 */
public class Trigger {

    private String schedule; // cron expression
    private List<String> watch; // file-system paths to watch
    private boolean manual; // requires --trigger flag

    public Trigger() {
    }

    // ── Getters ──────────────────────────────────────────

    public String getSchedule() {
        return schedule;
    }

    public List<String> getWatch() {
        return watch;
    }

    public boolean isManual() {
        return manual;
    }

    // ── Setters ──────────────────────────────────────────

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public void setWatch(List<String> watch) {
        this.watch = watch;
    }

    public void setManual(boolean manual) {
        this.manual = manual;
    }
}
