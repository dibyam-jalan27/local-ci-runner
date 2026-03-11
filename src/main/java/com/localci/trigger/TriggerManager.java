package com.localci.trigger;

import com.localci.model.Trigger;
import com.localci.tui.TerminalUI;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * Manages pipeline triggers: cron scheduling, file watching, and manual gating.
 *
 * <ul>
 * <li><b>schedule</b> — parses a simplified cron expression and re-runs at
 * intervals</li>
 * <li><b>watch</b> — monitors file-system paths via {@link WatchService}</li>
 * <li><b>manual</b> — waits for explicit {@code --trigger} CLI flag</li>
 * </ul>
 */
public class TriggerManager {

    private final TerminalUI ui;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running = true;

    public TriggerManager(TerminalUI ui) {
        this.ui = ui;
    }

    /**
     * Starts the appropriate trigger mode and runs the pipeline callback upon
     * trigger.
     *
     * @param trigger  the trigger configuration
     * @param pipeline the runnable that executes the pipeline
     */
    public void start(Trigger trigger, Runnable pipeline) {
        if (trigger.getSchedule() != null && !trigger.getSchedule().isBlank()) {
            startCronScheduler(trigger.getSchedule(), pipeline);
        }

        if (trigger.getWatch() != null && !trigger.getWatch().isEmpty()) {
            startFileWatcher(trigger.getWatch(), pipeline);
        }

        if (!trigger.isManual()) {
            // If no manual gate and no schedule/watch, just run immediately
            if (trigger.getSchedule() == null && trigger.getWatch() == null) {
                pipeline.run();
            }
        }
    }

    /**
     * Waits for a manual trigger event (called when --trigger flag is passed).
     */
    public void manualTrigger(Runnable pipeline) {
        log("Manual trigger activated — executing pipeline...");
        pipeline.run();
    }

    /**
     * Stops all trigger monitoring.
     */
    public void shutdown() {
        running = false;
        scheduler.shutdownNow();
    }

    // ── Cron Scheduling ─────────────────────────────────

    private void startCronScheduler(String cronExpr, Runnable pipeline) {
        long intervalSeconds = parseCronToIntervalSeconds(cronExpr);
        log("Cron schedule active: '" + cronExpr + "' → every " + intervalSeconds + "s");

        scheduler.scheduleAtFixedRate(() -> {
            if (running) {
                log("Cron trigger fired — executing pipeline...");
                try {
                    pipeline.run();
                } catch (Exception e) {
                    log("[ERROR] Pipeline execution failed: " + e.getMessage());
                }
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * Simplified cron parser that extracts the interval.
     * Supports common patterns:
     * <ul>
     * <li>{@code * / N * * * *} — every N minutes</li>
     * <li>{@code 0 * * * *} — every hour</li>
     * <li>{@code 0 0 * * *} — every day</li>
     * </ul>
     * Falls back to 3600s (1 hour) for complex expressions.
     */
    static long parseCronToIntervalSeconds(String cron) {
        if (cron == null || cron.isBlank())
            return 3600;

        String[] parts = cron.trim().split("\\s+");
        if (parts.length < 5)
            return 3600;

        String minuteField = parts[0];
        String hourField = parts[1];

        // */N in minute field → every N minutes
        if (minuteField.startsWith("*/")) {
            try {
                int n = Integer.parseInt(minuteField.substring(2));
                return n * 60L;
            } catch (NumberFormatException e) {
                return 3600;
            }
        }

        // "0" in minute, "*" in hour → every hour
        if ("0".equals(minuteField) && "*".equals(hourField)) {
            return 3600;
        }

        // "0" in minute, "0" in hour → every day
        if ("0".equals(minuteField) && "0".equals(hourField)) {
            return 86400;
        }

        // Default: every hour
        return 3600;
    }

    // ── File Watching ───────────────────────────────────

    private void startFileWatcher(List<String> paths, Runnable pipeline) {
        Thread watchThread = new Thread(() -> {
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                for (String pathStr : paths) {
                    Path path = Paths.get(pathStr);
                    Path dir = path.toFile().isDirectory() ? path : path.getParent();
                    if (dir == null)
                        dir = Paths.get(".");

                    if (dir.toFile().exists()) {
                        dir.register(watchService,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_MODIFY,
                                StandardWatchEventKinds.ENTRY_DELETE);
                        log("Watching: " + dir.toAbsolutePath());
                    } else {
                        log("[WARN] Watch path does not exist: " + pathStr);
                    }
                }

                while (running) {
                    WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                    if (key != null) {
                        boolean changed = false;
                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();
                            if (kind != StandardWatchEventKinds.OVERFLOW) {
                                Path changedPath = (Path) event.context();
                                log("File change detected: " + changedPath + " (" + kind.name() + ")");
                                changed = true;
                            }
                        }
                        key.reset();

                        if (changed) {
                            log("Watch trigger fired — executing pipeline...");
                            try {
                                pipeline.run();
                            } catch (Exception e) {
                                log("[ERROR] Pipeline execution failed: " + e.getMessage());
                            }

                            // Debounce: wait 2 seconds before watching again
                            Thread.sleep(2000);
                        }
                    }
                }
            } catch (IOException | InterruptedException e) {
                if (running) {
                    log("[ERROR] File watcher failed: " + e.getMessage());
                }
            }
        }, "file-watcher");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    // ── Logging ─────────────────────────────────────────

    private void log(String message) {
        if (ui != null) {
            ui.appendLog(message);
        } else {
            System.out.println("[TRIGGER] " + message);
        }
    }
}
