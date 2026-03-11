package com.localci.executor;

import com.localci.tui.TerminalUI;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

/**
 * Executes lifecycle hooks (before_pipeline, after_pipeline, etc.).
 * <p>
 * Each hook entry is dispatched based on its type:
 * <ul>
 * <li>Ends with {@code .sh} — executed as {@code sh <path>}</li>
 * <li>Ends with {@code .jar} — executed as {@code java -jar <path>}</li>
 * <li>Otherwise — executed as a raw shell command</li>
 * </ul>
 */
public class HookExecutor {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    private final TerminalUI ui;
    private final Map<String, String> env;

    public HookExecutor(TerminalUI ui, Map<String, String> env) {
        this.ui = ui;
        this.env = (env != null) ? env : Map.of();
    }

    /**
     * Executes a list of hook commands/scripts.
     *
     * @param hooks list of commands or script paths
     * @param phase descriptive label (e.g. "before_pipeline")
     */
    public void executeHooks(List<String> hooks, String phase) {
        if (hooks == null || hooks.isEmpty())
            return;

        log("── Hook: " + phase + " (" + hooks.size() + " entries) ──");

        for (String hook : hooks) {
            try {
                executeHook(hook.trim(), phase);
            } catch (Exception e) {
                log("[WARN] Hook failed (" + phase + "): " + e.getMessage());
            }
        }
    }

    private void executeHook(String hook, String phase) throws IOException, InterruptedException {
        ProcessBuilder builder;

        if (hook.endsWith(".sh")) {
            // Script file
            if (IS_WINDOWS) {
                builder = new ProcessBuilder("cmd", "/c", "sh " + hook);
            } else {
                builder = new ProcessBuilder("sh", hook);
            }
            log("  → Running script: " + hook);
        } else if (hook.endsWith(".jar")) {
            // JAR plugin
            builder = new ProcessBuilder("java", "-jar", hook);
            log("  → Running JAR plugin: " + hook);
        } else {
            // Raw shell command
            if (IS_WINDOWS) {
                builder = new ProcessBuilder("cmd", "/c", hook);
            } else {
                builder = new ProcessBuilder("sh", "-c", hook);
            }
            log("  → Running command: " + hook);
        }

        builder.environment().putAll(env);
        builder.redirectErrorStream(true);

        Process process = builder.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log("    " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log("  ✗ Hook exited with code " + exitCode + ": " + hook);
        } else {
            log("  ✓ Hook completed: " + hook);
        }
    }

    private void log(String message) {
        if (ui != null) {
            ui.appendLog(message);
        } else {
            System.out.println(message);
        }
    }
}
