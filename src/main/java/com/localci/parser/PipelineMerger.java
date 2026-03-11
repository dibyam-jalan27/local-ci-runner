package com.localci.parser;

import com.localci.exception.PipelineValidationException;
import com.localci.model.*;

import java.util.*;

/**
 * Merges a child pipeline with a base (parent) pipeline for inheritance.
 *
 * Merge rules:
 * <ul>
 * <li>Scalar fields (pipeline, image): child overrides base</li>
 * <li>Map fields (env): child merges with base (child keys win)</li>
 * <li>List fields (steps, stages, artifacts, cache, secrets): child appends to
 * base</li>
 * <li>Object fields (triggers, hooks, notify): child overrides base
 * entirely</li>
 * </ul>
 */
public class PipelineMerger {

    /**
     * Merges a child pipeline onto a base pipeline.
     *
     * @param base  the parent pipeline (from the "extends" reference)
     * @param child the child pipeline (the one being parsed)
     * @return merged pipeline
     */
    public static Pipeline merge(Pipeline base, Pipeline child) {
        Pipeline merged = new Pipeline();

        // Scalars: child overrides base
        merged.setPipeline(choose(child.getPipeline(), base.getPipeline()));
        merged.setImage(choose(child.getImage(), base.getImage()));
        merged.setExtends(null); // Don't propagate extends

        // Maps: merge (child wins)
        merged.setEnv(mergeMaps(base.getEnv(), child.getEnv()));

        // Lists: child appends to base
        merged.setSteps(mergeLists(base.getSteps(), child.getSteps()));
        merged.setStages(mergeLists(base.getStages(), child.getStages()));
        merged.setArtifacts(mergeLists(base.getArtifacts(), child.getArtifacts()));
        merged.setCache(mergeLists(base.getCache(), child.getCache()));
        merged.setSecrets(mergeLists(base.getSecrets(), child.getSecrets()));
        merged.setServices(mergeLists(base.getServices(), child.getServices()));

        // Objects: child overrides base entirely
        merged.setTriggers(child.getTriggers() != null ? child.getTriggers() : base.getTriggers());
        merged.setHooks(child.getHooks() != null ? child.getHooks() : base.getHooks());
        merged.setNotify(child.getNotify() != null ? child.getNotify() : base.getNotify());
        merged.setMatrix(child.getMatrix() != null ? child.getMatrix() : base.getMatrix());
        merged.setPipeline_ref(child.getPipeline_ref() != null ? child.getPipeline_ref() : base.getPipeline_ref());
        merged.setChecksum(child.getChecksum() != null ? child.getChecksum() : base.getChecksum());

        return merged;
    }

    /**
     * Detects circular extends chains.
     *
     * @param filePath the file currently being parsed
     * @param visited  set of files already visited in this chain
     * @throws PipelineValidationException if a circular reference is detected
     */
    public static void checkCircular(String filePath, Set<String> visited)
            throws PipelineValidationException {
        String normalized = new java.io.File(filePath).getAbsolutePath();
        if (visited.contains(normalized)) {
            throw new PipelineValidationException(
                    "Circular pipeline inheritance detected: " + normalized
                            + "\n  Chain: " + String.join(" → ", visited) + " → " + normalized);
        }
        visited.add(normalized);
    }

    // ── Helpers ──────────────────────────────────────────

    private static String choose(String child, String base) {
        return (child != null && !child.isBlank()) ? child : base;
    }

    private static Map<String, String> mergeMaps(Map<String, String> base, Map<String, String> child) {
        if (base == null && child == null)
            return null;
        Map<String, String> result = new HashMap<>();
        if (base != null)
            result.putAll(base);
        if (child != null)
            result.putAll(child);
        return result;
    }

    private static <T> List<T> mergeLists(List<T> base, List<T> child) {
        if (base == null && child == null)
            return null;
        List<T> result = new ArrayList<>();
        if (base != null)
            result.addAll(base);
        if (child != null)
            result.addAll(child);
        return result;
    }
}
