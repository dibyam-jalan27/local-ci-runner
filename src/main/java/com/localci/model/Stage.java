package com.localci.model;

import java.util.List;

/**
 * Represents a pipeline stage that groups steps together.
 *
 * YAML structure:
 * 
 * <pre>
 *   stages:
 *     - name: "build"
 *       steps:
 *         - name: "compile"
 *           run: "mvn compile"
 *     - name: "test"
 *       steps:
 *         - name: "unit-tests"
 *           run: "mvn test"
 * </pre>
 *
 * Stages run sequentially; steps within a stage can run in parallel.
 */
public class Stage {

    private String name;
    private List<Step> steps;

    public Stage() {
    }

    // ── Getters ──────────────────────────────────────────

    public String getName() {
        return name;
    }

    public List<Step> getSteps() {
        return steps;
    }

    // ── Setters ──────────────────────────────────────────

    public void setName(String name) {
        this.name = name;
    }

    public void setSteps(List<Step> steps) {
        this.steps = steps;
    }
}
