package com.localci.model;

import java.util.List;
import java.util.Map;

/**
 * Represents an entire CI pipeline.
 * <p>
 * YAML structure (v4.0 — extended):
 *
 * <pre>
 *   pipeline: "My Build"
 *   image: "ubuntu:latest"
 *   extends: "base-pipeline.yml"
 *   pipeline_ref: "<a href="https://example.com/pipeline.yml">...</a>"
 *   checksum: "sha256:abc..."
 *   env:
 *     APP_ENV: "ci"
 *   triggers:
 *     schedule: "0 * * * *"
 *     watch: ["src/"]
 *     manual: false
 *   matrix:
 *     java_version: ["11", "17", "21"]
 *   hooks:
 *     before_pipeline: ["echo starting"]
 *   notify:
 *     slack:
 *       webhookUrl: "https://..."
 *     on: ["failure"]
 *   stages:
 *     - name: "build"
 *       steps: [...]
 *   steps:
 *     - name: "Step 1"
 *       run: "echo hello"
 * </pre>
 *
 * If {@code image} is set, all steps run inside a Docker container.
 * If omitted, steps run on the local machine.
 */
public class Pipeline {

    // ── Core fields (existing) ──────────────────────────
    private String pipeline; // display name
    private String image; // Docker image
    private Map<String, String> env; // global env vars
    private List<String> artifacts;
    private List<String> secrets;
    private List<String> cache;
    private List<Service> services;
    private List<Step> steps;

    // ── Feature 1: Triggers ─────────────────────────────
    private Trigger triggers;

    // ── Feature 3: Stages ───────────────────────────────
    private List<Stage> stages;

    // ── Feature 5: Hooks ────────────────────────────────
    private Hooks hooks;

    // ── Feature 6: Matrix Builds ────────────────────────
    private Map<String, List<String>> matrix;

    // ── Feature 7: Remote Pipeline Fetching ─────────────
    private String pipeline_ref;
    private String checksum;

    // ── Feature 8: Notifications ────────────────────────
    private NotifyConfig notify;

    // ── Feature 9: Inheritance ──────────────────────────
    private String extendsRef; // YAML key: "extends"

    /** No-arg constructor required by SnakeYAML. */
    public Pipeline() {
    }

    // ══════════════════════════════════════════════════════
    // GETTERS
    // ══════════════════════════════════════════════════════

    public String getPipeline() {
        return pipeline;
    }

    public String getImage() {
        return image;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public List<Step> getSteps() {
        return steps;
    }

    public List<String> getArtifacts() {
        return artifacts;
    }

    public List<String> getSecrets() {
        return secrets;
    }

    public List<String> getCache() {
        return cache;
    }

    public List<Service> getServices() {
        return services;
    }

    public Trigger getTriggers() {
        return triggers;
    }

    public List<Stage> getStages() {
        return stages;
    }

    public Hooks getHooks() {
        return hooks;
    }

    public Map<String, List<String>> getMatrix() {
        return matrix;
    }

    public String getPipeline_ref() {
        return pipeline_ref;
    }

    public String getChecksum() {
        return checksum;
    }

    public NotifyConfig getNotify() {
        return notify;
    }

    /** SnakeYAML calls setExtends(), so the getter is getExtends(). */
    public String getExtends() {
        return extendsRef;
    }

    // ══════════════════════════════════════════════════════
    // SETTERS
    // ══════════════════════════════════════════════════════

    public void setPipeline(String pipeline) {
        this.pipeline = pipeline;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env;
    }

    public void setSteps(List<Step> steps) {
        this.steps = steps;
    }

    public void setArtifacts(List<String> artifacts) {
        this.artifacts = artifacts;
    }

    public void setSecrets(List<String> secrets) {
        this.secrets = secrets;
    }

    public void setCache(List<String> cache) {
        this.cache = cache;
    }

    public void setServices(List<Service> services) {
        this.services = services;
    }

    public void setTriggers(Trigger triggers) {
        this.triggers = triggers;
    }

    public void setStages(List<Stage> stages) {
        this.stages = stages;
    }

    public void setHooks(Hooks hooks) {
        this.hooks = hooks;
    }

    public void setMatrix(Map<String, List<String>> matrix) {
        this.matrix = matrix;
    }

    public void setPipeline_ref(String pipeline_ref) {
        this.pipeline_ref = pipeline_ref;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public void setNotify(NotifyConfig notify) {
        this.notify = notify;
    }

    public void setExtends(String extendsRef) {
        this.extendsRef = extendsRef;
    }
}
