package com.localci;

import com.localci.docker.DockerManager;
import com.localci.exception.PipelineValidationException;
import com.localci.executor.*;
import com.localci.logger.PipelineLogger;
import com.localci.model.*;
import com.localci.notification.NotificationManager;
import com.localci.parser.PipelineParser;
import com.localci.report.ReportGenerator;
import com.localci.trigger.TriggerManager;
import com.localci.tui.RunHistory;
import com.localci.tui.TerminalUI;
import com.localci.validator.PipelineValidator;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Entry point for the Local CI Runner v4.0.
 * <p>
 * Features:
 * 1. Pipeline Triggers & Scheduling
 * 2. Parallel Step Execution
 * 3. Pipeline Stages / Grouping
 * 4. Artifact Versioning & Reports
 * 5. Plugin / Hook System
 * 6. Matrix Builds
 * 7. Remote Pipeline Fetching
 * 8. Notification Integrations
 * 9. Pipeline Inheritance / Templates
 * 10. TUI Enhancements (history, re-run, keyboard shortcuts)
 */
public class LocalCIRunner {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static boolean reportHtml = false;
    private static boolean triggerFlag = false;

    public static void main(String[] args) {

        // ── Parse CLI flags ─────────────────────────────
        String pipelineArg = null;
        boolean dryRun = false;

        for (String arg : args) {
            switch (arg) {
                case "--dry-run" -> dryRun = true;
                case "--report-html" -> reportHtml = true;
                case "--trigger" -> triggerFlag = true;
                default -> {
                    if (pipelineArg == null)
                        pipelineArg = arg;
                }
            }
        }

        // Scripted dry-run mode (no loop, no TUI)
        if (dryRun && pipelineArg != null) {
            runDryMode(pipelineArg);
            return;
        }

        // ── Interactive loop ─────────────────────────────
        TerminalUI ui = new TerminalUI();
        RunHistory history = new RunHistory();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.print("\033[?25h"); // show cursor
            System.out.println("\n  Goodbye!");
        }));

        // If pipeline argument provided on CLI, run it directly
        if (pipelineArg != null) {
            Pipeline pipeline = loadPipeline(pipelineArg, ui);
            handlePipelineExecution(ui, pipeline, pipelineArg, history);
            return;
        }

        while (true) {
            String filePath = ui.showWelcome();

            if ("exit".equalsIgnoreCase(filePath) || "quit".equalsIgnoreCase(filePath)
                    || "q".equalsIgnoreCase(filePath)) {
                System.out.println("\n  Goodbye!");
                break;
            }

            // History view
            if ("h".equalsIgnoreCase(filePath)) {
                ui.showHistory(history.getLastN(15));
                continue;
            }

            // Re-run last pipeline
            if ("r".equalsIgnoreCase(filePath)) {
                String lastPath = history.getLastPipelinePath();
                if (lastPath == null) {
                    filePath = ui.showErrorAndRetry("No previous pipeline to re-run.");
                    if ("exit".equalsIgnoreCase(filePath))
                        break;
                    continue;
                }
                filePath = lastPath;
                ui.appendLog("Re-running: " + filePath);
            }

            if (filePath.isBlank())
                continue;

            Pipeline pipeline = loadPipeline(filePath, ui);
            if (pipeline == null)
                break; // user typed quit during load

            // Handle triggers
            if (pipeline.getTriggers() != null) {
                handleTriggers(ui, pipeline, filePath, history);
                continue;
            }

            String postAction = handlePipelineExecution(ui, pipeline, filePath, history);
            if (isQuit(postAction))
                break;
            if ("r".equalsIgnoreCase(postAction)) {
                // Re-run the same pipeline
                pipeline = loadPipeline(filePath, ui);
                if (pipeline == null)
                    break;
                handlePipelineExecution(ui, pipeline, filePath, history);
            }
        }
    }

    // ══════════════════════════════════════════════════════
    // PIPELINE EXECUTION (orchestrates ALL features)
    // ══════════════════════════════════════════════════════

    private static String handlePipelineExecution(TerminalUI ui, Pipeline pipeline,
            String yamlPath, RunHistory history) {
        // Matrix builds: fan out
        if (pipeline.getMatrix() != null && !pipeline.getMatrix().isEmpty()) {
            runMatrixPipeline(ui, pipeline, yamlPath, history);
            return "";
        } else {
            return runSinglePipeline(ui, pipeline, yamlPath, history);
        }
    }

    // ── Matrix fan-out ──────────────────────────────────

    private static void runMatrixPipeline(TerminalUI ui, Pipeline pipeline,
            String yamlPath, RunHistory history) {
        List<Map<String, String>> combos = MatrixExpander.expand(pipeline.getMatrix());
        int totalCombos = combos.size();
        int passedCombos = 0;

        ui.appendLog("Matrix build: " + totalCombos + " combinations");

        for (int c = 0; c < totalCombos; c++) {
            Map<String, String> combo = combos.get(c);
            String label = MatrixExpander.formatLabel(combo);
            ui.showMatrixHeader(label, c, totalCombos);

            // Merge matrix vars into pipeline env
            Pipeline matrixPipeline = clonePipelineWithEnv(pipeline, combo);
            String postAction = runSinglePipeline(ui, matrixPipeline, yamlPath, null);
            if (isQuit(postAction))
                break;
            if (!"FAILED".equals(postAction) && !"r".equalsIgnoreCase(postAction))
                passedCombos++;
        }

        ui.showMatrixSummary(passedCombos, totalCombos);

        // Record history
        if (history != null) {
            history.addEntry(pipeline.getPipeline(),
                    passedCombos == totalCombos ? "PASSED" : "FAILED", 0, yamlPath);
        }
    }

    // ── Single pipeline run ─────────────────────────────

    private static String runSinglePipeline(TerminalUI ui, Pipeline pipeline,
            String yamlPath, RunHistory history) {
        String startTime = LocalDateTime.now().format(DTF);
        long pipelineStartNanos = System.nanoTime();

        // Resolve steps: either from stages or top-level steps
        List<Stage> stages = null;
        List<Step> allSteps;

        if (pipeline.getStages() != null && !pipeline.getStages().isEmpty()) {
            stages = pipeline.getStages();
            allSteps = stages.stream()
                    .flatMap(s -> s.getSteps().stream())
                    .toList();
        } else {
            allSteps = pipeline.getSteps();
        }

        boolean useDocker = pipeline.getImage() != null && !pipeline.getImage().isBlank();
        String workspacePath = resolveWorkspace(yamlPath);

        // Initialize TUI
        List<String> stepNames = allSteps.stream().map(Step::getName).toList();
        ui.init(pipeline.getPipeline(), stepNames);

        // Process secrets
        processSecrets(pipeline, ui);

        // Hook executor
        HookExecutor hookExec = new HookExecutor(ui,
                pipeline.getEnv() != null ? pipeline.getEnv() : Map.of());

        DockerManager dockerManager = null;
        String networkId = null;
        List<String> serviceContainerIds = new ArrayList<>();
        String containerId = null;
        List<StepResult> results = new ArrayList<>();
        boolean pipelineFailed = false;

        try {
            // Docker setup
            if (useDocker) {
                dockerManager = new DockerManager(ui);
                if (pipeline.getServices() != null && !pipeline.getServices().isEmpty()) {
                    networkId = dockerManager.createNetwork();
                    for (Service svc : pipeline.getServices()) {
                        String svcId = dockerManager.startService(svc, networkId);
                        serviceContainerIds.add(svcId);
                    }
                }
                dockerManager.pullImage(pipeline.getImage());
                containerId = dockerManager.createContainer(
                        pipeline.getImage(), pipeline.getEnv(), workspacePath,
                        networkId, pipeline.getCache());
                ui.setContainerInfo(containerId, pipeline.getImage());
            } else {
                ui.setContainerInfo("local", "none (local mode)");
            }

            // ── before_pipeline hook ────────────────────
            if (pipeline.getHooks() != null) {
                hookExec.executeHooks(pipeline.getHooks().getBefore_pipeline(), "before_pipeline");
            }

            StepExecutor executor = new StepExecutor(
                    pipeline.getEnv(), dockerManager, containerId, ui);
            ParallelStepExecutor parallelExec = new ParallelStepExecutor(executor, ui);

            // Execute either by stages or flat steps
            if (stages != null) {
                pipelineFailed = executeStages(ui, stages, executor, parallelExec,
                        hookExec, pipeline.getHooks(), results);
            } else {
                pipelineFailed = executeSteps(ui, allSteps, executor, parallelExec,
                        hookExec, pipeline.getHooks(), results, 0);
            }

            // ── after_pipeline hook ─────────────────────
            if (pipeline.getHooks() != null) {
                hookExec.executeHooks(pipeline.getHooks().getAfter_pipeline(), "after_pipeline");
            }

        } catch (Exception e) {
            pipelineFailed = true;
            ui.appendLog("[ERROR] " + e.getMessage());
        } finally {
            // Extract artifacts
            if (!pipelineFailed && useDocker && containerId != null && dockerManager != null) {
                if (pipeline.getArtifacts() != null && !pipeline.getArtifacts().isEmpty()) {
                    String artifactsDir = new java.io.File(workspacePath, "artifacts").getAbsolutePath();
                    dockerManager.copyArtifacts(containerId, pipeline.getArtifacts(), artifactsDir);
                }
            }
            // Cleanup
            if (dockerManager != null) {
                if (containerId != null)
                    dockerManager.cleanup(containerId);
                for (String svcId : serviceContainerIds)
                    dockerManager.cleanup(svcId);
                if (networkId != null) {
                    try {
                        ui.appendLog("Removing network " + networkId + " ...");
                        new ProcessBuilder("docker", "network", "rm", networkId).start().waitFor();
                    } catch (Exception e) {
                        ui.appendLog("[WARN] Failed to remove network " + networkId);
                    }
                }
            }
        }

        long totalDurationMs = (System.nanoTime() - pipelineStartNanos) / 1_000_000;
        String endTime = LocalDateTime.now().format(DTF);

        // Count passed
        int passed = (int) results.stream()
                .filter(r -> r.status() == StepStatus.PASSED || r.status() == StepStatus.SKIPPED)
                .count();

        // ── Generate reports (Feature 4) ────────────────
        String artifactsDir = new java.io.File(resolveWorkspace(yamlPath), "artifacts").getAbsolutePath();
        try {
            RunReport report = buildReport(pipeline, results, startTime, endTime,
                    totalDurationMs, pipelineFailed);
            String jsonPath = ReportGenerator.generateJson(report, artifactsDir);
            ui.appendLog("Report saved: " + jsonPath);

            if (reportHtml) {
                String htmlPath = ReportGenerator.generateHtml(report, artifactsDir);
                ui.appendLog("HTML report saved: " + htmlPath);
            }

            // ── Notifications (Feature 8) ───────────────
            if (pipeline.getNotify() != null) {
                new NotificationManager(ui).notify(pipeline.getNotify(), report, !pipelineFailed);
            }
        } catch (Exception e) {
            ui.appendLog("[WARN] Report generation failed: " + e.getMessage());
        }

        // ── Record history (Feature 10) ─────────────────
        if (history != null) {
            history.addEntry(pipeline.getPipeline(),
                    pipelineFailed ? "FAILED" : "PASSED", totalDurationMs, yamlPath);
        }

        // Summary — returns user's post-run input (q/r/enter)
        String postAction = ui.showComplete(!pipelineFailed, passed, allSteps.size());

        return postAction;
    }

    // ══════════════════════════════════════════════════════
    // STAGE EXECUTION (Feature 3)
    // ══════════════════════════════════════════════════════

    private static boolean executeStages(TerminalUI ui, List<Stage> stages,
            StepExecutor executor,
            ParallelStepExecutor parallelExec,
            HookExecutor hookExec, Hooks hooks,
            List<StepResult> allResults) {
        boolean pipelineFailed = false;
        int globalStepIndex = 0;

        for (int s = 0; s < stages.size(); s++) {
            Stage stage = stages.get(s);
            ui.showStageHeader(stage.getName(), s, stages.size());

            List<StepResult> stageResults = new ArrayList<>();
            boolean stageFailed = executeSteps(ui, stage.getSteps(), executor, parallelExec,
                    hookExec, hooks, stageResults, globalStepIndex);

            allResults.addAll(stageResults);
            globalStepIndex += stage.getSteps().size();

            int stagePassed = (int) stageResults.stream()
                    .filter(r -> r.status() == StepStatus.PASSED || r.status() == StepStatus.SKIPPED)
                    .count();
            ui.showStageSummary(stage.getName(), !stageFailed,
                    stagePassed, stage.getSteps().size());

            if (stageFailed) {
                pipelineFailed = true;
                break;
            }
        }

        return pipelineFailed;
    }

    // ══════════════════════════════════════════════════════
    // STEP EXECUTION (with parallel & matrix support)
    // ══════════════════════════════════════════════════════

    private static boolean executeSteps(TerminalUI ui, List<Step> steps,
            StepExecutor executor,
            ParallelStepExecutor parallelExec,
            HookExecutor hookExec, Hooks hooks,
            List<StepResult> results,
            int stepIndexOffset) {
        boolean failed = false;

        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            int globalIndex = stepIndexOffset + i;

            // before_step hook
            if (hooks != null) {
                hookExec.executeHooks(hooks.getBefore_step(), "before_step");
            }

            // ── Step-level matrix (Feature 6) ───────────
            if (step.getMatrix() != null && !step.getMatrix().isEmpty()) {
                List<Map<String, String>> combos = MatrixExpander.expand(step.getMatrix());
                ui.appendLog("Step matrix: " + combos.size() + " combinations for '" + step.getName() + "'");

                boolean matrixFailed = false;
                int matrixPassed = 0;

                for (int c = 0; c < combos.size(); c++) {
                    Map<String, String> combo = combos.get(c);
                    String label = step.getName() + " " + MatrixExpander.formatLabel(combo);
                    ui.showMatrixHeader(label, c, combos.size());

                    // Create a step clone with matrix env merged
                    Step matrixStep = cloneStepWithEnv(step, combo);
                    matrixStep.setMatrix(null); // prevent infinite recursion

                    ui.markStepRunning(globalIndex);
                    StepResult result = executor.execute(matrixStep);
                    results.add(result);
                    ui.updateStep(globalIndex, result.status(), result.durationMs(), result.attempts());

                    if (result.status() == StepStatus.PASSED || result.status() == StepStatus.SKIPPED) {
                        matrixPassed++;
                    } else {
                        matrixFailed = true;
                    }
                }

                ui.showMatrixSummary(matrixPassed, combos.size());

                if (matrixFailed && !step.isContinueOnError()) {
                    failed = true;
                    break;
                }

                // ── Parallel execution (Feature 2) ──────────
            } else if (step.getParallel() != null && !step.getParallel().isEmpty()) {
                long parallelStart = System.nanoTime();
                List<StepResult> parallelResults = parallelExec.executeParallel(
                        step.getName(), step.getParallel(), step.isContinueOnError());

                long parallelDuration = (System.nanoTime() - parallelStart) / 1_000_000;
                results.addAll(parallelResults);

                StepStatus groupStatus = ParallelStepExecutor.compositeStatus(
                        parallelResults, step.isContinueOnError());
                int parallelPassed = (int) parallelResults.stream()
                        .filter(r -> r.status() == StepStatus.PASSED)
                        .count();

                ui.showParallelGroupSummary(step.getName(), groupStatus == StepStatus.PASSED,
                        parallelPassed, parallelResults.size(), parallelDuration);

                if (groupStatus == StepStatus.FAILED) {
                    failed = true;
                    break;
                }

                // ── Normal step ─────────────────────────────
            } else {
                ui.markStepRunning(globalIndex);
                StepResult result = executor.execute(step);
                results.add(result);
                ui.updateStep(globalIndex, result.status(), result.durationMs(), result.attempts());

                if (result.status() != StepStatus.PASSED && result.status() != StepStatus.SKIPPED) {
                    if (step.isContinueOnError()) {
                        ui.appendLog("[WARN] '" + step.getName() + "' failed — continue-on-error is set.");
                    } else {
                        failed = true;
                        break;
                    }
                }
            }

            // after_step hook
            if (hooks != null) {
                hookExec.executeHooks(hooks.getAfter_step(), "after_step");
            }
        }

        return failed;
    }

    // ══════════════════════════════════════════════════════
    // TRIGGERS (Feature 1)
    // ══════════════════════════════════════════════════════

    private static void handleTriggers(TerminalUI ui, Pipeline pipeline,
            String yamlPath, RunHistory history) {
        Trigger trigger = pipeline.getTriggers();
        TriggerManager mgr = new TriggerManager(ui);

        Runnable pipelineRun = () -> {
            try {
                Pipeline freshPipeline = new PipelineParser().parse(yamlPath);
                new PipelineValidator().validate(freshPipeline);
                runSinglePipeline(ui, freshPipeline, yamlPath, history);
            } catch (Exception e) {
                ui.appendLog("[ERROR] Triggered pipeline failed: " + e.getMessage());
            }
        };

        if (triggerFlag && trigger.isManual()) {
            mgr.manualTrigger(pipelineRun);
            return;
        }

        if (trigger.isManual() && !triggerFlag) {
            ui.appendLog("Pipeline has manual trigger. Use --trigger flag to execute.");
            return;
        }

        mgr.start(trigger, pipelineRun);

        // If schedule or watch, block until user interrupts
        if (trigger.getSchedule() != null || (trigger.getWatch() != null && !trigger.getWatch().isEmpty())) {
            ui.appendLog("Trigger active. Press Ctrl+C to stop...");
            try {
                Thread.currentThread().join(); // Block forever
            } catch (InterruptedException e) {
                mgr.shutdown();
            }
        }
    }

    // ══════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════

    private static Pipeline loadPipeline(String filePath, TerminalUI ui) {
        PipelineParser parser = new PipelineParser();
        PipelineValidator validator = new PipelineValidator();

        while (true) {
            if (isQuit(filePath))
                return null;
            try {
                Pipeline pipeline = parser.parse(filePath);
                validator.validate(pipeline);
                return pipeline;
            } catch (PipelineValidationException e) {
                filePath = ui.showErrorAndRetry(e.getMessage());
            } catch (Exception e) {
                filePath = ui.showErrorAndRetry("Cannot read file: " + e.getMessage());
            }
        }
    }

    private static boolean isQuit(String input) {
        return input != null && ("exit".equalsIgnoreCase(input)
                || "quit".equalsIgnoreCase(input) || "q".equalsIgnoreCase(input));
    }

    private static String resolveWorkspace(String filePath) {
        return System.getProperty("user.dir");
    }

    private static void processSecrets(Pipeline pipeline, TerminalUI ui) {
        if (pipeline.getSecrets() != null) {
            for (String secretName : pipeline.getSecrets()) {
                String secretValue = System.getenv(secretName);
                if (secretValue != null && !secretValue.isBlank()) {
                    ui.addSecret(secretValue);
                    if (pipeline.getEnv() == null) {
                        pipeline.setEnv(new HashMap<>());
                    }
                    pipeline.getEnv().put(secretName, secretValue);
                } else {
                    ui.appendLog("[WARN] Secret '" + secretName + "' not found in host environment.");
                }
            }
        }
    }

    private static RunReport buildReport(Pipeline pipeline, List<StepResult> results,
            String startTime, String endTime,
            long totalDurationMs, boolean failed) {
        RunReport report = new RunReport();
        report.setPipelineName(pipeline.getPipeline() != null ? pipeline.getPipeline() : "(unnamed)");
        report.setStartTime(startTime);
        report.setEndTime(endTime);
        report.setTotalDurationMs(totalDurationMs);
        report.setStatus(failed ? "FAILED" : "PASSED");

        List<RunReport.StepEntry> entries = new ArrayList<>();
        for (StepResult r : results) {
            entries.add(new RunReport.StepEntry(
                    r.name(), r.status().name(), r.durationMs(),
                    r.attempts(), r.exitCode()));
        }
        report.setSteps(entries);

        return report;
    }

    private static Pipeline clonePipelineWithEnv(Pipeline original, Map<String, String> extraEnv) {
        Pipeline clone = new Pipeline();
        clone.setPipeline(original.getPipeline());
        clone.setImage(original.getImage());
        clone.setSteps(original.getSteps());
        clone.setStages(original.getStages());
        clone.setArtifacts(original.getArtifacts());
        clone.setSecrets(original.getSecrets());
        clone.setCache(original.getCache());
        clone.setServices(original.getServices());
        clone.setHooks(original.getHooks());
        clone.setNotify(original.getNotify());

        Map<String, String> mergedEnv = new HashMap<>();
        if (original.getEnv() != null)
            mergedEnv.putAll(original.getEnv());
        mergedEnv.putAll(extraEnv);
        clone.setEnv(mergedEnv);

        return clone;
    }

    private static Step cloneStepWithEnv(Step original, Map<String, String> extraEnv) {
        Step clone = new Step();
        clone.setName(original.getName());
        clone.setRun(original.getRun());
        clone.setTimeout(original.getTimeout());
        clone.setRetries(original.getRetries());
        clone.setContinueOnError(original.isContinueOnError());
        clone.setWorkingDir(original.getWorkingDir());
        clone.setIf(original.getIf());
        clone.setParallel(original.getParallel());

        Map<String, String> mergedEnv = new HashMap<>();
        if (original.getEnv() != null)
            mergedEnv.putAll(original.getEnv());
        mergedEnv.putAll(extraEnv);
        clone.setEnv(mergedEnv);

        return clone;
    }

    // ══════════════════════════════════════════════════════
    // DRY-RUN MODE
    // ══════════════════════════════════════════════════════

    private static void runDryMode(String filePath) {
        Pipeline pipeline;
        try {
            pipeline = new PipelineParser().parse(filePath);
            new PipelineValidator().validate(pipeline);
        } catch (Exception e) {
            PipelineLogger.error(e.getMessage());
            System.exit(1);
            return;
        }

        boolean useDocker = pipeline.getImage() != null && !pipeline.getImage().isBlank();

        PipelineLogger.info("Local CI Runner v4.0 — DRY RUN");
        PipelineLogger.info("Pipeline: "
                + (pipeline.getPipeline() != null ? pipeline.getPipeline() : "(unnamed)"));

        if (useDocker) {
            PipelineLogger.dryRun("Docker image: " + pipeline.getImage());
            PipelineLogger.dryRun("Workspace mount: CWD → /workspace");
        } else {
            PipelineLogger.dryRun("Mode: local execution");
        }

        // Show triggers
        if (pipeline.getTriggers() != null) {
            Trigger t = pipeline.getTriggers();
            if (t.getSchedule() != null)
                PipelineLogger.dryRun("Trigger schedule: " + t.getSchedule());
            if (t.getWatch() != null)
                PipelineLogger.dryRun("Trigger watch: " + t.getWatch());
            if (t.isManual())
                PipelineLogger.dryRun("Trigger: manual (--trigger)");
        }

        // Show matrix
        if (pipeline.getMatrix() != null) {
            List<Map<String, String>> combos = MatrixExpander.expand(pipeline.getMatrix());
            PipelineLogger.dryRun("Matrix: " + combos.size() + " combinations");
            for (Map<String, String> combo : combos) {
                PipelineLogger.dryRun("  " + MatrixExpander.formatLabel(combo));
            }
        }

        // Show hooks
        if (pipeline.getHooks() != null) {
            Hooks h = pipeline.getHooks();
            if (h.getBefore_pipeline() != null)
                PipelineLogger.dryRun("Hooks before_pipeline: " + h.getBefore_pipeline());
            if (h.getAfter_pipeline() != null)
                PipelineLogger.dryRun("Hooks after_pipeline: " + h.getAfter_pipeline());
            if (h.getBefore_step() != null)
                PipelineLogger.dryRun("Hooks before_step: " + h.getBefore_step());
            if (h.getAfter_step() != null)
                PipelineLogger.dryRun("Hooks after_step: " + h.getAfter_step());
        }

        // Show extends
        if (pipeline.getExtends() != null) {
            PipelineLogger.dryRun("Extends: " + pipeline.getExtends());
        }

        // Show notify
        if (pipeline.getNotify() != null) {
            NotifyConfig n = pipeline.getNotify();
            PipelineLogger.dryRun("Notifications: on=" + n.getOn());
            if (n.getSlack() != null)
                PipelineLogger.dryRun("  Slack: " + n.getSlack().getWebhookUrl());
            if (n.getEmail() != null)
                PipelineLogger.dryRun("  Email: " + n.getEmail().getRecipient());
            if (n.getWebhook() != null)
                PipelineLogger.dryRun("  Webhook: " + n.getWebhook().getUrl());
        }

        // Show stages or steps
        if (pipeline.getStages() != null) {
            PipelineLogger.info(pipeline.getStages().size() + " stage(s):");
            for (Stage stage : pipeline.getStages()) {
                PipelineLogger.info("  Stage: " + stage.getName() + " (" + stage.getSteps().size() + " steps)");
                printStepsDry(stage.getSteps());
            }
        } else if (pipeline.getSteps() != null) {
            PipelineLogger.info(pipeline.getSteps().size() + " step(s):");
            System.out.println();
            printStepsDry(pipeline.getSteps());
        }
    }

    private static void printStepsDry(List<Step> steps) {
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            PipelineLogger.stepHeader(i + 1, s.getName());

            if (s.getParallel() != null) {
                PipelineLogger.dryRun("Parallel group (" + s.getParallel().size() + " sub-steps):");
                for (Step sub : s.getParallel()) {
                    PipelineLogger.dryRun("  ├── " + sub.getName() + ": " + sub.getRun());
                }
            } else {
                PipelineLogger.dryRun("Command:  " + s.getRun());
            }

            if (s.getTimeout() > 0)
                PipelineLogger.dryRun("Timeout:  " + s.getTimeout() + "s");
            if (s.getRetries() > 0)
                PipelineLogger.dryRun("Retries:  " + s.getRetries());
            if (s.isContinueOnError())
                PipelineLogger.dryRun("Continue on error: yes");
            if (s.getEnv() != null && !s.getEnv().isEmpty())
                PipelineLogger.dryRun("Env vars: " + s.getEnv());
            if (s.getMatrix() != null) {
                List<Map<String, String>> combos = MatrixExpander.expand(s.getMatrix());
                PipelineLogger.dryRun("Step matrix: " + combos.size() + " combinations");
            }
        }
    }
}
