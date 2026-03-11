package com.localci.validator;

import com.localci.exception.PipelineValidationException;
import com.localci.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates a {@link Pipeline} before execution.
 *
 * Collects ALL validation errors rather than failing on the first one,
 * so the user can fix everything in a single pass.
 */
public class PipelineValidator {

    /**
     * Validates the pipeline structure and throws if any issues are found.
     *
     * @param pipeline the parsed pipeline to validate
     * @throws PipelineValidationException with a summary of all errors
     */
    public void validate(Pipeline pipeline) throws PipelineValidationException {
        List<String> errors = new ArrayList<>();

        // ── Pipeline-level: must have steps OR stages ────
        boolean hasSteps = pipeline.getSteps() != null && !pipeline.getSteps().isEmpty();
        boolean hasStages = pipeline.getStages() != null && !pipeline.getStages().isEmpty();

        if (!hasSteps && !hasStages) {
            throw new PipelineValidationException(
                    "Pipeline must define either 'steps' or 'stages'.");
        }

        if (hasSteps && hasStages) {
            errors.add("Pipeline cannot define both top-level 'steps' and 'stages'. Use one or the other.");
        }

        // ── Validate top-level steps ─────────────────────
        if (hasSteps) {
            validateStepList(pipeline.getSteps(), "steps", errors);
        }

        // ── Validate stages ──────────────────────────────
        if (hasStages) {
            List<Stage> stages = pipeline.getStages();
            for (int i = 0; i < stages.size(); i++) {
                Stage stage = stages.get(i);
                String prefix = "Stage " + (i + 1);

                if (stage.getName() == null || stage.getName().isBlank()) {
                    errors.add(prefix + ": 'name' is required.");
                }

                if (stage.getSteps() == null || stage.getSteps().isEmpty()) {
                    errors.add(prefix + " (" + stage.getName() + "): must contain at least one step.");
                } else {
                    validateStepList(stage.getSteps(),
                            "stages[" + (stage.getName() != null ? stage.getName() : i) + "]", errors);
                }
            }
        }

        // ── Validate triggers ────────────────────────────
        if (pipeline.getTriggers() != null) {
            Trigger t = pipeline.getTriggers();
            if (t.getSchedule() != null && !t.getSchedule().isBlank()) {
                String[] parts = t.getSchedule().trim().split("\\s+");
                if (parts.length < 5) {
                    errors.add("Trigger schedule: cron expression must have 5 fields (got " + parts.length + ").");
                }
            }
        }

        // ── Validate hooks ──────────────────────────────
        // Hooks are flexible (any string), no strict validation beyond presence

        // ── Validate matrix ─────────────────────────────
        if (pipeline.getMatrix() != null) {
            for (var entry : pipeline.getMatrix().entrySet()) {
                if (entry.getValue() == null || entry.getValue().isEmpty()) {
                    errors.add("Matrix variable '" + entry.getKey() + "' must have at least one value.");
                }
            }
        }

        // ── Validate notify ─────────────────────────────
        if (pipeline.getNotify() != null) {
            NotifyConfig n = pipeline.getNotify();
            if (n.getOn() != null) {
                for (String cond : n.getOn()) {
                    if (!"failure".equalsIgnoreCase(cond)
                            && !"success".equalsIgnoreCase(cond)
                            && !"always".equalsIgnoreCase(cond)) {
                        errors.add("Notify 'on' condition must be 'failure', 'success', or 'always' (got: '" + cond
                                + "').");
                    }
                }
            }

            if (n.getSlack() != null
                    && (n.getSlack().getWebhookUrl() == null || n.getSlack().getWebhookUrl().isBlank())) {
                errors.add("Notify slack: 'webhookUrl' is required.");
            }

            if (n.getEmail() != null) {
                if (n.getEmail().getSmtpHost() == null || n.getEmail().getSmtpHost().isBlank()) {
                    errors.add("Notify email: 'smtpHost' is required.");
                }
                if (n.getEmail().getRecipient() == null || n.getEmail().getRecipient().isBlank()) {
                    errors.add("Notify email: 'recipient' is required.");
                }
            }

            if (n.getWebhook() != null && (n.getWebhook().getUrl() == null || n.getWebhook().getUrl().isBlank())) {
                errors.add("Notify webhook: 'url' is required.");
            }
        }

        // ── Report all errors at once ────────────────────
        if (!errors.isEmpty()) {
            String message = "Pipeline validation failed:\n  - "
                    + String.join("\n  - ", errors);
            throw new PipelineValidationException(message);
        }
    }

    /**
     * Validates a list of steps (used for both top-level steps and stage steps).
     */
    private void validateStepList(List<Step> steps, String context, List<String> errors) {
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            String prefix = context + " → Step " + (i + 1);

            if (step.getName() == null || step.getName().isBlank()) {
                errors.add(prefix + ": 'name' is required.");
            }

            boolean hasRun = step.getRun() != null && !step.getRun().isBlank();
            boolean hasParallel = step.getParallel() != null && !step.getParallel().isEmpty();

            if (!hasRun && !hasParallel) {
                errors.add(prefix + " (" + step.getName() + "): must have 'run' or 'parallel'.");
            }

            if (hasRun && hasParallel) {
                errors.add(prefix + " (" + step.getName() + "): cannot have both 'run' and 'parallel'.");
            }

            if (step.getTimeout() < 0) {
                errors.add(prefix + " (" + step.getName() + "): 'timeout' must be >= 0.");
            }

            if (step.getRetries() < 0) {
                errors.add(prefix + " (" + step.getName() + "): 'retries' must be >= 0.");
            }

            // Validate parallel sub-steps recursively
            if (hasParallel) {
                validateStepList(step.getParallel(), prefix + " → parallel", errors);
            }

            // Validate step-level matrix
            if (step.getMatrix() != null) {
                for (var entry : step.getMatrix().entrySet()) {
                    if (entry.getValue() == null || entry.getValue().isEmpty()) {
                        errors.add(prefix + " (" + step.getName()
                                + "): matrix variable '" + entry.getKey() + "' must have at least one value.");
                    }
                }
            }
        }
    }
}
