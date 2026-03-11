package com.localci.tui;

import com.localci.model.StepStatus;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * A clean, scrolling CI output logger — inspired by GitHub Actions and Jenkins.
 *
 * Features (v4.0):
 * - Colored step headers and status badges
 * - Indented, timestamped log lines
 * - ASCII progress indicators
 * - Final summary table with durations
 * - Parallel step group display
 * - Stage headers and summaries
 * - Matrix combination labels
 * - Pipeline history view
 * - Keyboard shortcuts (h=history, r=rerun, q=quit)
 * - Live log streaming panel
 *
 * Thread-safety: all methods are synchronized.
 */
public class TerminalUI {

    // ── ANSI colors ──────────────────────────────────────
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String BLUE = "\u001B[34m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String WHITE = "\u001B[37m";
    private static final String BG_GREEN = "\u001B[42m\u001B[30m";
    private static final String BG_RED = "\u001B[41m\u001B[37m";
    private static final String BG_CYAN = "\u001B[46m\u001B[30m";
    private static final String BG_YELLOW = "\u001B[43m\u001B[30m";
    private static final String BG_MAGENTA = "\u001B[45m\u001B[37m";
    private static final String BG_BLUE = "\u001B[44m\u001B[37m";

    private static final String LINE = "─".repeat(60);

    private final PrintStream out = System.out;
    private final Scanner scanner = new Scanner(System.in);
    private final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ── State ────────────────────────────────────────────
    private final List<StepRecord> stepRecords = new ArrayList<>();
    private final List<String> stepNames = new ArrayList<>();
    private final List<String> activeSecrets = new ArrayList<>();
    private final List<String> logBuffer = new ArrayList<>();
    private int totalSteps = 0;
    private int currentStep = -1;

    private record StepRecord(String name, StepStatus status,
            long durationMs, int attempts) {
    }

    // ═════════════════════════════════════════════════════
    // WELCOME SCREEN
    // ═════════════════════════════════════════════════════

    public String showWelcome() {
        out.println();
        out.println(CYAN + "  ╔══════════════════════════════════════════════════════╗" + RESET);
        out.println(CYAN + "  ║" + BOLD + "          LOCAL CI RUNNER  v4.0                       " + RESET + CYAN + "║"
                + RESET);
        out.println(CYAN + "  ║" + DIM + "    Lightweight Docker Pipeline Executor              " + RESET + CYAN + "║"
                + RESET);
        out.println(CYAN + "  ║" + DIM + "    Commands: 'exit' | 'h' history | 'r' re-run       " + RESET + CYAN + "║"
                + RESET);
        out.println(CYAN + "  ╚══════════════════════════════════════════════════════╝" + RESET);
        out.println();
        out.print(BOLD + "  Pipeline file path: " + RESET);
        out.flush();

        return readLine();
    }

    public String showErrorAndRetry(String errorMsg) {
        out.println();
        out.println(RED + "  ✗ Error: " + errorMsg + RESET);
        out.println();
        out.print(BOLD + "  Pipeline file path: " + RESET);
        out.flush();

        return readLine();
    }

    // ═════════════════════════════════════════════════════
    // PIPELINE EXECUTION OUTPUT
    // ═════════════════════════════════════════════════════

    public synchronized void init(String pipelineName, List<String> stepNames) {
        this.stepNames.clear();
        this.stepNames.addAll(stepNames);
        totalSteps = stepNames.size();
        currentStep = -1;
        stepRecords.clear();
        logBuffer.clear();

        out.println();
        out.println(CYAN + "  " + LINE + RESET);
        out.println(BOLD + "  ▶ Pipeline: " + RESET + pipelineName);
        out.println(BOLD + "    Steps:    " + RESET + totalSteps);
        out.println(BOLD + "    Started:  " + RESET + LocalDateTime.now().format(TIME_FMT));
        out.println(CYAN + "  " + LINE + RESET);
        out.println();
    }

    public synchronized void setContainerInfo(String containerId, String image) {
        out.println(BLUE + "  ⚙ Docker" + RESET
                + DIM + "  container=" + RESET + containerId
                + DIM + "  image=" + RESET + image);
        out.println();
    }

    // ── Step Running / Results ───────────────────────────

    public synchronized void markStepRunning(int index) {
        currentStep = index;
        String stepName = (index >= 0 && index < stepNames.size())
                ? stepNames.get(index)
                : "Step " + (index + 1);
        out.println(BG_CYAN + " STEP " + (index + 1) + "/" + totalSteps + " " + RESET
                + BOLD + "  ▶ Running: " + stepName + RESET);
        out.println();
    }

    public synchronized void addSecret(String secretValue) {
        if (secretValue != null && !secretValue.isBlank() && !activeSecrets.contains(secretValue)) {
            activeSecrets.add(secretValue);
        }
    }

    public synchronized void appendLog(String line) {
        String maskedLine = line;
        for (String secret : activeSecrets) {
            maskedLine = maskedLine.replace(secret, "***");
        }

        String ts = DIM + TIME_FMT.format(LocalDateTime.now()) + RESET;
        String formattedLine = "    " + ts + "  " + maskedLine;
        out.println(formattedLine);
        logBuffer.add(maskedLine);
    }

    public synchronized void updateStep(int index, StepStatus status,
            long durationMs, int attempts) {
        String stepName = (index >= 0 && index < stepNames.size())
                ? stepNames.get(index)
                : "Step " + (index + 1);
        stepRecords.add(new StepRecord(stepName, status, durationMs, attempts));

        String badge;
        String msg;
        switch (status) {
            case PASSED -> {
                badge = BG_GREEN + " PASS " + RESET;
                msg = GREEN + "  completed in " + formatDuration(durationMs) + RESET;
            }
            case FAILED -> {
                badge = BG_RED + " FAIL " + RESET;
                msg = RED + "  failed after " + formatDuration(durationMs) + RESET;
            }
            case TIMED_OUT -> {
                badge = BG_YELLOW + " TIME " + RESET;
                msg = YELLOW + "  timed out after " + formatDuration(durationMs) + RESET;
            }
            default -> {
                badge = DIM + " SKIP " + RESET;
                msg = DIM + "  skipped" + RESET;
            }
        }

        String retryInfo = (attempts > 1) ? DIM + " (" + attempts + " attempts)" + RESET : "";
        out.println();
        out.println("  " + badge + msg + retryInfo);
        out.println();
    }

    // ═════════════════════════════════════════════════════
    // PARALLEL STEP DISPLAY (Feature 2)
    // ═════════════════════════════════════════════════════

    public synchronized void markParallelGroupRunning(String groupName, List<String> subStepNames) {
        out.println(BG_MAGENTA + " PARALLEL " + RESET
                + BOLD + "  ▶ Group: " + groupName + " (" + subStepNames.size() + " steps)" + RESET);
        for (int i = 0; i < subStepNames.size(); i++) {
            out.println(MAGENTA + "    ├── " + RESET + DIM + "[QUEUED] " + RESET + subStepNames.get(i));
        }
        out.println();
    }

    public synchronized void updateParallelStep(String groupName, int subIndex,
            StepStatus status, long durationMs, int attempts) {
        String badge = switch (status) {
            case PASSED -> GREEN + "[PASS]" + RESET;
            case FAILED -> RED + "[FAIL]" + RESET;
            case TIMED_OUT -> YELLOW + "[TIME]" + RESET;
            case SKIPPED -> DIM + "[SKIP]" + RESET;
        };
        out.println(MAGENTA + "    ├── " + RESET + badge + " Sub-step " + (subIndex + 1)
                + " — " + formatDuration(durationMs)
                + (attempts > 1 ? DIM + " (" + attempts + " attempts)" + RESET : ""));
    }

    /**
     * Shows a summary after a parallel group completes.
     */
    public synchronized void showParallelGroupSummary(String groupName, boolean passed,
            int passedCount, int total, long durationMs) {
        String badge = passed ? BG_GREEN + " PASS " + RESET : BG_RED + " FAIL " + RESET;
        out.println();
        out.println("  " + badge + MAGENTA + " Parallel '" + groupName + "': "
                + passedCount + "/" + total + " passed"
                + DIM + " (" + formatDuration(durationMs) + ")" + RESET);
        out.println();
    }

    // ═════════════════════════════════════════════════════
    // STAGE DISPLAY (Feature 3)
    // ═════════════════════════════════════════════════════

    public synchronized void showStageHeader(String stageName, int stageIndex, int totalStages) {
        out.println();
        out.println(BG_BLUE + " STAGE " + (stageIndex + 1) + "/" + totalStages + " " + RESET
                + BOLD + BLUE + "  ▶ " + stageName + RESET);
        out.println(BLUE + "  " + "─".repeat(50) + RESET);
        out.println();
    }

    public synchronized void showStageSummary(String stageName, boolean passed,
            int passedSteps, int totalSteps) {
        String badge = passed ? BG_GREEN + " PASS " + RESET : BG_RED + " FAIL " + RESET;
        out.println();
        out.println(BLUE + "  " + "─".repeat(50) + RESET);
        out.println("  " + badge + BLUE + " Stage '" + stageName + "': "
                + passedSteps + "/" + totalSteps + " steps passed" + RESET);
        out.println();
    }

    // ═════════════════════════════════════════════════════
    // MATRIX DISPLAY (Feature 6)
    // ═════════════════════════════════════════════════════

    public synchronized void showMatrixHeader(String label, int comboIndex, int totalCombos) {
        out.println();
        out.println(BG_YELLOW + " MATRIX " + (comboIndex + 1) + "/" + totalCombos + " " + RESET
                + BOLD + YELLOW + "  " + label + RESET);
        out.println(YELLOW + "  " + "─".repeat(50) + RESET);
        out.println();
    }

    public synchronized void showMatrixSummary(int passed, int total) {
        out.println();
        out.println(YELLOW + "  " + "─".repeat(50) + RESET);
        String badge = (passed == total) ? BG_GREEN + " PASS " + RESET : BG_RED + " FAIL " + RESET;
        out.println("  " + badge + YELLOW + " Matrix: " + passed + "/" + total + " combinations passed" + RESET);
        out.println();
    }

    // ═════════════════════════════════════════════════════
    // HISTORY VIEW (Feature 10)
    // ═════════════════════════════════════════════════════

    public void showHistory(List<RunHistory.Entry> entries) {
        out.println();
        out.println(CYAN + "  " + LINE + RESET);
        out.println(BOLD + "  📋 Pipeline History (last " + entries.size() + " runs)" + RESET);
        out.println();

        if (entries.isEmpty()) {
            out.println(DIM + "  No runs recorded yet." + RESET);
        } else {
            out.println(DIM + "  ┌─────┬──────────────────────────┬──────────┬──────────┬───────────────────┐" + RESET);
            out.println(
                    BOLD + "  │  #  │ Pipeline                 │ Status   │ Duration │ Timestamp         │" + RESET);
            out.println(DIM + "  ├─────┼──────────────────────────┼──────────┼──────────┼───────────────────┤" + RESET);

            for (int i = 0; i < entries.size(); i++) {
                RunHistory.Entry e = entries.get(i);
                String statusStr = "PASSED".equals(e.status)
                        ? GREEN + "PASS  " + RESET
                        : RED + "FAIL  " + RESET;
                out.printf("  │ %3d │ %-24s │ %s   │ %8s │ %-17s │%n",
                        i + 1,
                        truncate(e.pipelineName != null ? e.pipelineName : "(unknown)", 24),
                        statusStr,
                        formatDuration(e.durationMs),
                        e.timestamp != null ? truncate(e.timestamp, 17) : "");
            }

            out.println(DIM + "  └─────┴──────────────────────────┴──────────┴──────────┴───────────────────┘" + RESET);
        }

        out.println();
        out.println(CYAN + "  " + LINE + RESET);
        out.println();
    }

    // ═════════════════════════════════════════════════════
    // LIVE LOG PANEL (Feature 10)
    // ═════════════════════════════════════════════════════

    public synchronized void showLogPanel(String title, int maxLines) {
        out.println();
        out.println(DIM + "  ┌─ " + title + " " + "─".repeat(Math.max(0, 50 - title.length())) + "┐" + RESET);
        int start = Math.max(0, logBuffer.size() - maxLines);
        for (int i = start; i < logBuffer.size(); i++) {
            out.println(DIM + "  │ " + RESET + logBuffer.get(i));
        }
        out.println(DIM + "  └" + "─".repeat(55) + "┘" + RESET);
    }

    // ═════════════════════════════════════════════════════
    // COMPLETION SUMMARY
    // ═════════════════════════════════════════════════════

    public String showComplete(boolean success, int passed, int total) {
        out.println(CYAN + "  " + LINE + RESET);
        out.println();

        // Summary table
        out.println(BOLD + "  RESULTS" + RESET);
        out.println();
        out.println(DIM + "  ┌─────┬──────────────────────────┬──────────┬──────────┐" + RESET);
        out.println(BOLD + "  │  #  │ Step                     │ Status   │ Duration │" + RESET);
        out.println(DIM + "  ├─────┼──────────────────────────┼──────────┼──────────┤" + RESET);

        for (int i = 0; i < stepRecords.size(); i++) {
            StepRecord r = stepRecords.get(i);
            String statusStr = switch (r.status()) {
                case PASSED -> GREEN + "PASS  " + RESET;
                case FAILED -> RED + "FAIL  " + RESET;
                case TIMED_OUT -> YELLOW + "TIME  " + RESET;
                case SKIPPED -> DIM + "SKIP  " + RESET;
            };
            out.printf("  │ %3d │ %-24s │ %s   │ %8s │%n",
                    i + 1,
                    truncate(r.name(), 24),
                    statusStr,
                    formatDuration(r.durationMs()));
        }

        out.println(DIM + "  └─────┴──────────────────────────┴──────────┴──────────┘" + RESET);
        out.println();

        // Final status
        if (success) {
            out.println(BG_GREEN + " PIPELINE PASSED " + RESET
                    + GREEN + "  " + passed + "/" + total + " steps succeeded" + RESET);
        } else {
            out.println(BG_RED + " PIPELINE FAILED " + RESET
                    + RED + "  " + passed + "/" + total + " steps succeeded" + RESET);
        }

        out.println();
        out.println(CYAN + "  " + LINE + RESET);
        out.println();
        out.print(DIM + "  Press Enter to continue (r=re-run, h=history, q=quit)..." + RESET);
        out.flush();
        return readLine();
    }

    /**
     * Reads a line from stdin, returning "exit" on EOF.
     */
    private String readLine() {
        try {
            if (scanner.hasNextLine()) {
                return scanner.nextLine().trim();
            }
        } catch (Exception ignored) {
        }
        return "exit";
    }

    // ── Helpers ──────────────────────────────────────────

    private String formatDuration(long ms) {
        if (ms < 1000)
            return ms + "ms";
        if (ms < 60_000)
            return String.format("%.1fs", ms / 1000.0);
        return String.format("%dm %ds", ms / 60_000, (ms % 60_000) / 1000);
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 2) + "..";
    }
}
