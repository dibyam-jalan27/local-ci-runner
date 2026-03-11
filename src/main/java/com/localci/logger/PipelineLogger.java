package com.localci.logger;

import com.localci.model.StepResult;
import com.localci.model.StepStatus;

import java.util.List;

/**
 * Console logger with ANSI color codes for clear, visually distinct
 * pipeline output.
 *
 * All methods are static — no instance needed.
 */
public final class PipelineLogger {

    // ── ANSI color codes ─────────────────────────────────
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String YELLOW = "\u001B[33m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";

    private static final String SEPARATOR = "════════════════════════════════════════════════════════════";

    private PipelineLogger() {
    } // prevent instantiation

    // ── Standard log levels ──────────────────────────────

    /** Informational message (cyan). */
    public static void info(String message) {
        System.out.println(CYAN + "[INFO]  " + RESET + message);
    }

    /** Step passed (green). */
    public static void success(String message) {
        System.out.println(GREEN + "[PASS]  " + RESET + message);
    }

    /** Warning — retries, continue-on-error (yellow). */
    public static void warn(String message) {
        System.out.println(YELLOW + "[WARN]  " + RESET + message);
    }

    /** Step failed (red). */
    public static void error(String message) {
        System.out.println(RED + "[FAIL]  " + RESET + message);
    }

    /** Dry-run output (magenta). */
    public static void dryRun(String message) {
        System.out.println(MAGENTA + "[DRY]   " + RESET + message);
    }

    // ── Visual elements ──────────────────────────────────

    /** Visual header printed before each step runs. */
    public static void stepHeader(int index, String stepName) {
        System.out.println();
        System.out.println(YELLOW + SEPARATOR + RESET);
        System.out.println(BOLD + "  Step " + index + ": " + stepName + RESET);
        System.out.println(YELLOW + SEPARATOR + RESET);
    }

    /** Summary banner at the very end of the pipeline run. */
    public static void summary(int passed, int total, boolean allPassed) {
        System.out.println();
        System.out.println(YELLOW + SEPARATOR + RESET);
        if (allPassed) {
            System.out.println(GREEN + BOLD
                    + "  Pipeline finished: " + passed + "/" + total + " steps passed."
                    + RESET);
        } else {
            System.out.println(RED + BOLD
                    + "  Pipeline failed:   " + passed + "/" + total + " steps passed."
                    + RESET);
        }
        System.out.println(YELLOW + SEPARATOR + RESET);
    }

    // ── Results table ────────────────────────────────────

    /**
     * Prints a formatted ASCII table summarizing all step results.
     *
     * Example output:
     * 
     * <pre>
     *  ┌────┬──────────────────────┬───────────┬──────────┬──────────┐
     *  │ #  │ Step                 │ Status    │ Duration │ Attempts │
     *  ├────┼──────────────────────┼───────────┼──────────┼──────────┤
     *  │  1 │ Install deps         │  PASSED   │    1.2s  │      1   │
     *  │  2 │ Run tests            │  FAILED   │    3.4s  │      3   │
     *  └────┴──────────────────────┴───────────┴──────────┴──────────┘
     * </pre>
     */
    public static void printResultsTable(List<StepResult> results) {
        System.out.println();
        System.out.println(BOLD + "  Execution Summary" + RESET);
        System.out.println();

        // Column widths
        int nameWidth = results.stream()
                .mapToInt(r -> r.name().length())
                .max().orElse(10);
        nameWidth = Math.max(nameWidth, 4); // min 4 for header "Step"

        // Header
        String fmt = "  │ %-3s │ %-" + nameWidth + "s │ %-10s │ %8s │ %8s │%n";
        String divider = "  ├─" + "─".repeat(3) + "─┼─" + "─".repeat(nameWidth)
                + "─┼─" + "─".repeat(10) + "─┼─" + "─".repeat(8)
                + "─┼─" + "─".repeat(8) + "─┤";
        String top = "  ┌─" + "─".repeat(3) + "─┬─" + "─".repeat(nameWidth)
                + "─┬─" + "─".repeat(10) + "─┬─" + "─".repeat(8)
                + "─┬─" + "─".repeat(8) + "─┐";
        String bottom = "  └─" + "─".repeat(3) + "─┴─" + "─".repeat(nameWidth)
                + "─┴─" + "─".repeat(10) + "─┴─" + "─".repeat(8)
                + "─┴─" + "─".repeat(8) + "─┘";

        System.out.println(DIM + top + RESET);
        System.out.printf(BOLD + fmt + RESET, "#", "Step", "Status", "Duration", "Attempts");
        System.out.println(DIM + divider + RESET);

        for (int i = 0; i < results.size(); i++) {
            StepResult r = results.get(i);
            String statusColor = colorForStatus(r.status());
            String duration = formatDuration(r.durationMs());
            String attempts = String.valueOf(r.attempts());

            System.out.printf("  │ %3d │ %-" + nameWidth + "s │ %s%-10s%s │ %8s │ %8s │%n",
                    i + 1,
                    r.name(),
                    statusColor, r.status(), RESET,
                    duration,
                    attempts);
        }

        System.out.println(DIM + bottom + RESET);
    }

    // ── Private helpers ──────────────────────────────────

    private static String colorForStatus(StepStatus status) {
        return switch (status) {
            case PASSED -> GREEN;
            case FAILED -> RED;
            case SKIPPED -> DIM;
            case TIMED_OUT -> YELLOW;
        };
    }

    private static String formatDuration(long ms) {
        if (ms < 1000) {
            return ms + "ms";
        }
        return String.format("%.1fs", ms / 1000.0);
    }
}
