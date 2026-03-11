package com.localci.model;

/**
 * Possible outcomes of a pipeline step execution.
 *
 * Used by {@link StepResult} to report what happened
 * in a type-safe, switch-friendly way (Java 17 pattern matching).
 */
public enum StepStatus {

    /** The step's command exited with code 0. */
    PASSED,

    /** The step's command exited with a non-zero code after all retry attempts. */
    FAILED,

    /** The step was not executed (e.g. dry-run mode). */
    SKIPPED,

    /** The step exceeded its configured timeout and was forcefully killed. */
    TIMED_OUT
}
