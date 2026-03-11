package com.localci.exception;

/**
 * Thrown when a parsed pipeline fails structural validation
 * before any steps are executed.
 *
 * Examples:
 * - A step has no name
 * - A step has no "run" command
 * - Timeout is negative
 */
public class PipelineValidationException extends Exception {

    public PipelineValidationException(String message) {
        super(message);
    }
}
