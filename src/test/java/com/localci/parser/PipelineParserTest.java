package com.localci.parser;

import com.localci.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PipelineParser: validates that all YAML fields
 * (existing and new) are correctly parsed.
 */
class PipelineParserTest {

    private final PipelineParser parser = new PipelineParser();

    @TempDir
    Path tempDir;

    private String writeTempYaml(String content) throws IOException {
        File file = tempDir.resolve("test-pipeline.yml").toFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        return file.getAbsolutePath();
    }

    @Test
    void parsesBasicPipeline() throws IOException {
        String yaml = """
                pipeline: "Test"
                steps:
                  - name: "Step 1"
                    run: "echo hello"
                """;
        Pipeline p = parser.parse(writeTempYaml(yaml));
        assertEquals("Test", p.getPipeline());
        assertNotNull(p.getSteps());
        assertEquals(1, p.getSteps().size());
        assertEquals("Step 1", p.getSteps().get(0).getName());
        assertEquals("echo hello", p.getSteps().get(0).getRun());
    }

    @Test
    void parsesDockerImage() throws IOException {
        String yaml = """
                pipeline: "Docker Test"
                image: "alpine:3.19"
                steps:
                  - name: "Step 1"
                    run: "echo hi"
                """;
        Pipeline p = parser.parse(writeTempYaml(yaml));
        assertEquals("alpine:3.19", p.getImage());
    }

    @Test
    void parsesEnvVars() throws IOException {
        String yaml = """
                pipeline: "Env Test"
                env:
                  FOO: "bar"
                  BAZ: "qux"
                steps:
                  - name: "Step 1"
                    run: "echo hello"
                """;
        Pipeline p = parser.parse(writeTempYaml(yaml));
        assertNotNull(p.getEnv());
        assertEquals("bar", p.getEnv().get("FOO"));
        assertEquals("qux", p.getEnv().get("BAZ"));
    }

    @Test
    void parsesTriggers() throws IOException {
        String yaml = """
                pipeline: "Trigger Test"
                triggers:
                  schedule: "*/5 * * * *"
                  watch:
                    - "src/"
                  manual: true
                steps:
                  - name: "Step 1"
                    run: "echo hello"
                """;
        Pipeline p = parser.parse(writeTempYaml(yaml));
        assertNotNull(p.getTriggers());
        assertEquals("*/5 * * * *", p.getTriggers().getSchedule());
        assertEquals(1, p.getTriggers().getWatch().size());
        assertTrue(p.getTriggers().isManual());
    }

    @Test
    void parsesParallelSteps() throws IOException {
        String yaml = """
                pipeline: "Parallel Test"
                steps:
                  - name: "Parallel Group"
                    parallel:
                      - name: "Sub 1"
                        run: "echo sub1"
                      - name: "Sub 2"
                        run: "echo sub2"
                """;
        Pipeline p = parser.parse(writeTempYaml(yaml));
        Step step = p.getSteps().get(0);
        assertNotNull(step.getParallel());
        assertEquals(2, step.getParallel().size());
        assertEquals("Sub 1", step.getParallel().get(0).getName());
    }

    @Test
    void parsesStages() throws IOException {
        String yaml = """
                pipeline: "Stages Test"
                stages:
                  - name: "build"
                    steps:
                      - name: "Compile"
                        run: "echo compile"
                  - name: "test"
                    steps:
                      - name: "Unit Test"
                        run: "echo test"
                """;
        Pipeline p = parser.parse(writeTempYaml(yaml));
        assertNotNull(p.getStages());
        assertEquals(2, p.getStages().size());
        assertEquals("build", p.getStages().get(0).getName());
        assertEquals("test", p.getStages().get(1).getName());
    }

    @Test
    void parsesHooks() throws IOException {
        String yaml = """
                pipeline: "Hooks Test"
                hooks:
                  before_pipeline:
                    - "echo before"
                  after_step:
                    - "echo after"
                steps:
                  - name: "Step 1"
                    run: "echo hello"
                """;
        Pipeline p = parser.parse(writeTempYaml(yaml));
        assertNotNull(p.getHooks());
        assertEquals(1, p.getHooks().getBefore_pipeline().size());
        assertEquals(1, p.getHooks().getAfter_step().size());
    }

    @Test
    void parsesMatrix() throws IOException {
        String yaml = """
                pipeline: "Matrix Test"
                matrix:
                  version:
                    - "11"
                    - "17"
                  os:
                    - "ubuntu"
                steps:
                  - name: "Build"
                    run: "echo build"
                """;
        Pipeline p = parser.parse(writeTempYaml(yaml));
        assertNotNull(p.getMatrix());
        assertEquals(2, p.getMatrix().get("version").size());
        assertEquals(1, p.getMatrix().get("os").size());
    }

    @Test
    void parsesNotify() throws IOException {
        String yaml = """
                pipeline: "Notify Test"
                notify:
                  on:
                    - "failure"
                  webhook:
                    url: "https://example.com/hook"
                steps:
                  - name: "Step 1"
                    run: "echo hello"
                """;
        Pipeline p = parser.parse(writeTempYaml(yaml));
        assertNotNull(p.getNotify());
        assertEquals(1, p.getNotify().getOn().size());
        assertEquals("failure", p.getNotify().getOn().get(0));
        assertNotNull(p.getNotify().getWebhook());
        assertEquals("https://example.com/hook", p.getNotify().getWebhook().getUrl());
    }

    @Test
    void parsesStepLevelMatrix() throws IOException {
        String yaml = """
                pipeline: "Step Matrix"
                steps:
                  - name: "Multi-build"
                    run: "echo v=$version"
                    matrix:
                      version:
                        - "1"
                        - "2"
                """;
        Pipeline p = parser.parse(writeTempYaml(yaml));
        Step step = p.getSteps().get(0);
        assertNotNull(step.getMatrix());
        assertEquals(2, step.getMatrix().get("version").size());
    }

    @Test
    void parsesExtendsInheritance() throws IOException {
        // Create base file
        File baseFile = tempDir.resolve("base.yml").toFile();
        try (FileWriter w = new FileWriter(baseFile)) {
            w.write("""
                    pipeline: "Base"
                    image: "alpine:3.19"
                    env:
                      BASE_VAR: "hello"
                    steps:
                      - name: "Base Step"
                        run: "echo base"
                    """);
        }

        // Create child file
        File childFile = tempDir.resolve("child.yml").toFile();
        try (FileWriter w = new FileWriter(childFile)) {
            w.write("""
                    pipeline: "Child"
                    extends: "base.yml"
                    env:
                      CHILD_VAR: "world"
                    steps:
                      - name: "Child Step"
                        run: "echo child"
                    """);
        }

        Pipeline p = parser.parse(childFile.getAbsolutePath());
        assertEquals("Child", p.getPipeline());
        assertEquals("alpine:3.19", p.getImage()); // inherited
        assertNotNull(p.getEnv());
        assertEquals("hello", p.getEnv().get("BASE_VAR")); // inherited
        assertEquals("world", p.getEnv().get("CHILD_VAR")); // child
        assertEquals(2, p.getSteps().size()); // base + child steps
    }

    @Test
    void backwardCompatibleWithExistingPipelines() throws IOException {
        String yaml = """
                pipeline: "Old Style"
                image: "ubuntu:latest"
                env:
                  APP_ENV: "ci"
                secrets:
                  - "API_KEY"
                cache:
                  - "npm-cache:/root/.npm"
                steps:
                  - name: "Build"
                    run: "echo build"
                    timeout: 60
                    retries: 2
                    continueOnError: true
                  - name: "Test"
                    run: "echo test"
                    if: "true"
                """;
        Pipeline p = parser.parse(writeTempYaml(yaml));
        assertEquals("Old Style", p.getPipeline());
        assertEquals("ubuntu:latest", p.getImage());
        assertEquals(2, p.getSteps().size());
        assertEquals(60, p.getSteps().get(0).getTimeout());
        assertEquals(2, p.getSteps().get(0).getRetries());
        assertTrue(p.getSteps().get(0).isContinueOnError());
        assertEquals("true", p.getSteps().get(1).getIf());

        // New features should be null (backward compat)
        assertNull(p.getTriggers());
        assertNull(p.getStages());
        assertNull(p.getHooks());
        assertNull(p.getMatrix());
        assertNull(p.getNotify());
    }
}
