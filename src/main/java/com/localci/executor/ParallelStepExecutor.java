package com.localci.executor;

import com.localci.model.Step;
import com.localci.model.StepResult;
import com.localci.model.StepStatus;
import com.localci.tui.TerminalUI;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Executes a group of steps in parallel using a thread pool.
 *
 * If any step fails and {@code continueOnError} is not set on
 * the parent, the entire parallel group is marked as FAILED.
 */
public class ParallelStepExecutor {

    private final StepExecutor stepExecutor;
    private final TerminalUI ui;

    public ParallelStepExecutor(StepExecutor stepExecutor, TerminalUI ui) {
        this.stepExecutor = stepExecutor;
        this.ui = ui;
    }

    /**
     * Runs all sub-steps concurrently and returns a composite result.
     *
     * @param groupName       the name of the parallel group
     * @param subSteps        steps to run in parallel
     * @param continueOnError if true, don't fail the group on step failure
     * @return list of all sub-step results
     */
    public List<StepResult> executeParallel(String groupName, List<Step> subSteps,
            boolean continueOnError) {
        if (ui != null) {
            List<String> names = subSteps.stream().map(Step::getName).toList();
            ui.markParallelGroupRunning(groupName, names);
        }

        ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(subSteps.size(), Runtime.getRuntime().availableProcessors()));

        List<Future<StepResult>> futures = new ArrayList<>();
        for (Step step : subSteps) {
            futures.add(pool.submit(() -> stepExecutor.execute(step)));
        }

        List<StepResult> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                StepResult result = futures.get(i).get();
                results.add(result);

                if (ui != null) {
                    ui.updateParallelStep(groupName, i, result.status(),
                            result.durationMs(), result.attempts());
                }
            } catch (InterruptedException | ExecutionException e) {
                StepResult failResult = new StepResult(
                        subSteps.get(i).getName(), StepStatus.FAILED, 0, -1, 1);
                results.add(failResult);

                if (ui != null) {
                    ui.updateParallelStep(groupName, i, StepStatus.FAILED, 0, 1);
                }
            }
        }

        pool.shutdown();
        return results;
    }

    /**
     * Determines the composite status of a parallel group.
     */
    public static StepStatus compositeStatus(List<StepResult> results, boolean continueOnError) {
        boolean anyFailed = results.stream()
                .anyMatch(r -> r.status() == StepStatus.FAILED || r.status() == StepStatus.TIMED_OUT);

        if (anyFailed && !continueOnError) {
            return StepStatus.FAILED;
        }
        return StepStatus.PASSED;
    }
}
