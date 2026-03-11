package com.localci.tui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages pipeline run history, persisted to {@code .ci-history.json}.
 */
public class RunHistory {

    private static final String HISTORY_FILE = ".ci-history.json";
    private static final int MAX_ENTRIES = 50;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final List<Entry> entries;
    private final String filePath;

    public RunHistory() {
        this(HISTORY_FILE);
    }

    public RunHistory(String filePath) {
        this.filePath = filePath;
        this.entries = load();
    }

    /**
     * Adds a new run entry.
     */
    public void addEntry(String pipelineName, String status, long durationMs, String yamlPath) {
        Entry entry = new Entry();
        entry.pipelineName = pipelineName;
        entry.status = status;
        entry.timestamp = LocalDateTime.now().format(DTF);
        entry.durationMs = durationMs;
        entry.yamlPath = yamlPath;

        entries.add(0, entry); // Most recent first

        if (entries.size() > MAX_ENTRIES) {
            entries.subList(MAX_ENTRIES, entries.size()).clear();
        }

        save();
    }

    /**
     * Returns the last N entries.
     */
    public List<Entry> getLastN(int n) {
        return entries.subList(0, Math.min(n, entries.size()));
    }

    /**
     * Returns the YAML path of the last run (for re-run).
     */
    public String getLastPipelinePath() {
        if (entries.isEmpty())
            return null;
        return entries.get(0).yamlPath;
    }

    /**
     * Returns all entries.
     */
    public List<Entry> getAll() {
        return entries;
    }

    // ── Persistence ─────────────────────────────────────

    private List<Entry> load() {
        File file = new File(filePath);
        if (!file.exists())
            return new ArrayList<>();

        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<List<Entry>>() {
            }.getType();
            List<Entry> loaded = GSON.fromJson(reader, type);
            return loaded != null ? new ArrayList<>(loaded) : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void save() {
        try (Writer writer = new FileWriter(filePath)) {
            GSON.toJson(entries, writer);
        } catch (IOException e) {
            // Silently fail — history is non-critical
        }
    }

    // ── Entry record ────────────────────────────────────

    public static class Entry {
        public String pipelineName;
        public String status;
        public String timestamp;
        public long durationMs;
        public String yamlPath;
    }
}
