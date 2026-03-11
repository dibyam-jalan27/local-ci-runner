package com.localci.parser;

import com.localci.exception.PipelineValidationException;
import com.localci.model.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PipelineMerger: merge logic for pipeline inheritance.
 */
class PipelineMergerTest {

    @Test
    void childOverridesScalars() {
        Pipeline base = new Pipeline();
        base.setPipeline("Base");
        base.setImage("ubuntu:20.04");

        Pipeline child = new Pipeline();
        child.setPipeline("Child");
        // child doesn't set image

        Pipeline merged = PipelineMerger.merge(base, child);
        assertEquals("Child", merged.getPipeline());
        assertEquals("ubuntu:20.04", merged.getImage()); // inherited
    }

    @Test
    void childOverridesImage() {
        Pipeline base = new Pipeline();
        base.setImage("ubuntu:20.04");

        Pipeline child = new Pipeline();
        child.setImage("alpine:3.19");

        Pipeline merged = PipelineMerger.merge(base, child);
        assertEquals("alpine:3.19", merged.getImage());
    }

    @Test
    void mergesEnvMaps() {
        Pipeline base = new Pipeline();
        base.setEnv(new HashMap<>(Map.of("A", "1", "B", "2")));

        Pipeline child = new Pipeline();
        child.setEnv(new HashMap<>(Map.of("B", "override", "C", "3")));

        Pipeline merged = PipelineMerger.merge(base, child);
        assertEquals("1", merged.getEnv().get("A")); // inherited
        assertEquals("override", merged.getEnv().get("B")); // overridden
        assertEquals("3", merged.getEnv().get("C")); // child-only
    }

    @Test
    void appendsStepLists() {
        Pipeline base = new Pipeline();
        Step baseStep = new Step();
        baseStep.setName("Base Step");
        baseStep.setRun("echo base");
        base.setSteps(List.of(baseStep));

        Pipeline child = new Pipeline();
        Step childStep = new Step();
        childStep.setName("Child Step");
        childStep.setRun("echo child");
        child.setSteps(List.of(childStep));

        Pipeline merged = PipelineMerger.merge(base, child);
        assertEquals(2, merged.getSteps().size());
        assertEquals("Base Step", merged.getSteps().get(0).getName());
        assertEquals("Child Step", merged.getSteps().get(1).getName());
    }

    @Test
    void childObjectOverridesBase() {
        Trigger baseTrigger = new Trigger();
        baseTrigger.setSchedule("0 * * * *");

        Pipeline base = new Pipeline();
        base.setTriggers(baseTrigger);

        Trigger childTrigger = new Trigger();
        childTrigger.setSchedule("*/5 * * * *");

        Pipeline child = new Pipeline();
        child.setTriggers(childTrigger);

        Pipeline merged = PipelineMerger.merge(base, child);
        assertEquals("*/5 * * * *", merged.getTriggers().getSchedule());
    }

    @Test
    void inheritsObjectsWhenChildDoesntDefine() {
        Trigger baseTrigger = new Trigger();
        baseTrigger.setSchedule("0 * * * *");

        Pipeline base = new Pipeline();
        base.setTriggers(baseTrigger);

        Pipeline child = new Pipeline();

        Pipeline merged = PipelineMerger.merge(base, child);
        assertNotNull(merged.getTriggers());
        assertEquals("0 * * * *", merged.getTriggers().getSchedule());
    }

    @Test
    void circularDetection() {
        Set<String> visited = new HashSet<>();
        String file = new java.io.File("test.yml").getAbsolutePath();

        // First visit should work
        assertDoesNotThrow(() -> PipelineMerger.checkCircular(file, visited));

        // Second visit to same file should throw
        assertThrows(PipelineValidationException.class,
                () -> PipelineMerger.checkCircular(file, visited));
    }

    @Test
    void nullListsMergeGracefully() {
        Pipeline base = new Pipeline();
        Pipeline child = new Pipeline();

        Pipeline merged = PipelineMerger.merge(base, child);
        assertNull(merged.getSteps());
        assertNull(merged.getStages());
        assertNull(merged.getArtifacts());
    }

    @Test
    void extendsIsStrippedFromMergedResult() {
        Pipeline base = new Pipeline();
        base.setPipeline("Base");

        Pipeline child = new Pipeline();
        child.setExtends("base.yml");

        Pipeline merged = PipelineMerger.merge(base, child);
        assertNull(merged.getExtends()); // Should not propagate
    }
}
