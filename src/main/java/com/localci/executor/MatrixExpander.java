package com.localci.executor;

import java.util.*;

/**
 * Expands a matrix configuration into all possible variable combinations
 * (cartesian product).
 *
 * Example input:
 * 
 * <pre>
 *   { "java_version": ["11", "17"], "os": ["ubuntu", "alpine"] }
 * </pre>
 *
 * Output: 4 combinations:
 * 
 * <pre>
 *   [
 *     { "java_version": "11", "os": "ubuntu" },
 *     { "java_version": "11", "os": "alpine" },
 *     { "java_version": "17", "os": "ubuntu" },
 *     { "java_version": "17", "os": "alpine" }
 *   ]
 * </pre>
 */
public class MatrixExpander {

    /**
     * Generates the cartesian product of all variable values.
     *
     * @param matrix map of variable name → list of values
     * @return list of all combinations, each as a Map
     */
    public static List<Map<String, String>> expand(Map<String, List<String>> matrix) {
        if (matrix == null || matrix.isEmpty()) {
            return List.of(Map.of());
        }

        List<String> keys = new ArrayList<>(matrix.keySet());
        List<List<String>> values = new ArrayList<>();
        for (String key : keys) {
            values.add(matrix.get(key));
        }

        List<Map<String, String>> combinations = new ArrayList<>();
        generateCombinations(keys, values, 0, new LinkedHashMap<>(), combinations);
        return combinations;
    }

    private static void generateCombinations(
            List<String> keys,
            List<List<String>> values,
            int depth,
            Map<String, String> current,
            List<Map<String, String>> result) {

        if (depth == keys.size()) {
            result.add(new LinkedHashMap<>(current));
            return;
        }

        String key = keys.get(depth);
        for (String value : values.get(depth)) {
            current.put(key, value);
            generateCombinations(keys, values, depth + 1, current, result);
            current.remove(key);
        }
    }

    /**
     * Formats a matrix combination into a display label.
     * E.g., { "java_version": "17", "os": "alpine" } → "[java_version=17,
     * os=alpine]"
     */
    public static String formatLabel(Map<String, String> combination) {
        if (combination == null || combination.isEmpty())
            return "";

        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (var entry : combination.entrySet()) {
            if (!first)
                sb.append(", ");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }
}
