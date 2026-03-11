package com.localci.validator;

import com.localci.exception.PipelineValidationException;
import com.localci.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PipelineValidator: validates all new validation rules.
 */
class PipelineValidatorTest {

    private final PipelineValidator validator = new PipelineValidator();

    private Pipeline pipelineWithSteps(Step... steps) {
        Pipeline p = new Pipeline();
        p.setPipeline("Test");
        p.setSteps(List.of(steps));
        return p;
    }

    private Step simpleStep(String name, String run) {
        Step s = new Step();
        s.setName(name);
        s.setRun(run);
        return s;
    }

    @Test
    void validBasicPipeline() {
        assertDoesNotThrow(() -> validator.validate(pipelineWithSteps(simpleStep("s1", "echo hi"))));
    }

    @Test
    void rejectsEmptyPipeline() {
        Pipeline p = new Pipeline();
        p.setPipeline("Empty");
        assertThrows(PipelineValidationException.class, () -> validator.validate(p));
    }

    @Test
    void rejectsStepsAndStagesTogether() {
        Pipeline p = new Pipeline();
        p.setPipeline("Both");
        p.setSteps(List.of(simpleStep("s1", "echo hi")));
        Stage stage = new Stage();
        stage.setName("build");
        stage.setSteps(List.of(simpleStep("s2", "echo build")));
        p.setStages(List.of(stage));

        PipelineValidationException ex = assertThrows(
                PipelineValidationException.class, () -> validator.validate(p));
        assertTrue(ex.getMessage().contains("cannot define both"));
    }

    @Test
    void rejectsStepWithBothRunAndParallel() {
        Step s = simpleStep("bad", "echo hi");
        s.setParallel(List.of(simpleStep("sub", "echo sub")));

        PipelineValidationException ex = assertThrows(
                PipelineValidationException.class,
                () -> validator.validate(pipelineWithSteps(s)));
        assertTrue(ex.getMessage().contains("cannot have both"));
    }

    @Test
    void rejectsStepWithNeitherRunNorParallel() {
        Step s = new Step();
        s.setName("empty");

        PipelineValidationException ex = assertThrows(
                PipelineValidationException.class,
                () -> validator.validate(pipelineWithSteps(s)));
        assertTrue(ex.getMessage().contains("must have 'run' or 'parallel'"));
    }

    @Test
    void validatesParallelSubSteps() {
        Step sub = new Step();
        sub.setName(""); // empty name → error

        Step group = new Step();
        group.setName("group");
        group.setParallel(List.of(sub));

        PipelineValidationException ex = assertThrows(
                PipelineValidationException.class,
                () -> validator.validate(pipelineWithSteps(group)));
        assertTrue(ex.getMessage().contains("'name' is required"));
    }

    @Test
    void validatesStages() {
        Stage stage = new Stage();
        stage.setName("");
        stage.setSteps(List.of(simpleStep("s", "echo")));

        Pipeline p = new Pipeline();
        p.setPipeline("Test");
        p.setStages(List.of(stage));

        PipelineValidationException ex = assertThrows(
                PipelineValidationException.class, () -> validator.validate(p));
        assertTrue(ex.getMessage().contains("'name' is required"));
    }

    @Test
    void validatesStageWithNoSteps() {
        Stage stage = new Stage();
        stage.setName("empty-stage");

        Pipeline p = new Pipeline();
        p.setPipeline("Test");
        p.setStages(List.of(stage));

        PipelineValidationException ex = assertThrows(
                PipelineValidationException.class, () -> validator.validate(p));
        assertTrue(ex.getMessage().contains("at least one step"));
    }

    @Test
    void validatesTriggerCron() {
        Pipeline p = pipelineWithSteps(simpleStep("s", "echo"));
        Trigger t = new Trigger();
        t.setSchedule("bad");
        p.setTriggers(t);

        PipelineValidationException ex = assertThrows(
                PipelineValidationException.class, () -> validator.validate(p));
        assertTrue(ex.getMessage().contains("cron expression must have 5 fields"));
    }

    @Test
    void validatesMatrixEmptyValues() {
        Pipeline p = pipelineWithSteps(simpleStep("s", "echo"));
        p.setMatrix(Map.of("ver", List.of()));

        PipelineValidationException ex = assertThrows(
                PipelineValidationException.class, () -> validator.validate(p));
        assertTrue(ex.getMessage().contains("at least one value"));
    }

    @Test
    void validatesNotifyConditions() {
        Pipeline p = pipelineWithSteps(simpleStep("s", "echo"));
        NotifyConfig n = new NotifyConfig();
        n.setOn(List.of("invalid"));
        p.setNotify(n);

        PipelineValidationException ex = assertThrows(
                PipelineValidationException.class, () -> validator.validate(p));
        assertTrue(ex.getMessage().contains("must be 'failure', 'success', or 'always'"));
    }

    @Test
    void validatesSlackWebhookRequired() {
        Pipeline p = pipelineWithSteps(simpleStep("s", "echo"));
        NotifyConfig n = new NotifyConfig();
        NotifyConfig.SlackConfig slack = new NotifyConfig.SlackConfig();
        n.setSlack(slack);
        p.setNotify(n);

        PipelineValidationException ex = assertThrows(
                PipelineValidationException.class, () -> validator.validate(p));
        assertTrue(ex.getMessage().contains("webhookUrl"));
    }

    @Test
    void validatesStepLevelMatrix() {
        Step s = simpleStep("s", "echo");
        s.setMatrix(Map.of("ver", List.of()));

        PipelineValidationException ex = assertThrows(
                PipelineValidationException.class,
                () -> validator.validate(pipelineWithSteps(s)));
        assertTrue(ex.getMessage().contains("at least one value"));
    }

    @Test
    void rejectsNegativeTimeout() {
        Step s = simpleStep("s", "echo");
        s.setTimeout(-1);

        PipelineValidationException ex = assertThrows(
                PipelineValidationException.class,
                () -> validator.validate(pipelineWithSteps(s)));
        assertTrue(ex.getMessage().contains("timeout"));
    }

    @Test
    void rejectsNegativeRetries() {
        Step s = simpleStep("s", "echo");
        s.setRetries(-1);

        PipelineValidationException ex = assertThrows(
                PipelineValidationException.class,
                () -> validator.validate(pipelineWithSteps(s)));
        assertTrue(ex.getMessage().contains("retries"));
    }
}
