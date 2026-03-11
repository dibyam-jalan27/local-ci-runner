package com.localci.model;

/**
 * Immutable result of executing a single pipeline step.
 *
 * This is a Java 17 {@code record} — the compiler auto-generates
 * the constructor, getters, equals, hashCode, and toString.
 *
 * @param name       human-readable step name
 * @param status     outcome (PASSED, FAILED, SKIPPED, TIMED_OUT)
 * @param durationMs wall-clock time the step took in milliseconds
 * @param exitCode   process exit code (-1 if never started or timed out)
 * @param attempts   number of attempts made (1 = no retries)
 */
public record StepResult(
        String name,
        StepStatus status,
        long durationMs,
        int exitCode,
        int attempts) {

    /**
     * Convenience factory for a skipped step (e.g. in dry-run mode).
     */
    public static StepResult skipped(String name) {
        return new StepResult(name, StepStatus.SKIPPED, 0, -1, 0);
    }
}
