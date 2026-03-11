package com.localci.executor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MatrixExpander: cartesian product expansion.
 */
class MatrixExpanderTest {

    @Test
    void expandsSingleVariable() {
        Map<String, List<String>> matrix = Map.of(
                "version", List.of("11", "17", "21"));

        var combos = MatrixExpander.expand(matrix);

        assertEquals(3, combos.size());
        assertTrue(combos.stream().anyMatch(c -> "11".equals(c.get("version"))));
        assertTrue(combos.stream().anyMatch(c -> "17".equals(c.get("version"))));
        assertTrue(combos.stream().anyMatch(c -> "21".equals(c.get("version"))));
    }

    @Test
    void expandsTwoVariables() {
        Map<String, List<String>> matrix = new LinkedHashMap<>();
        matrix.put("version", List.of("11", "17"));
        matrix.put("os", List.of("ubuntu", "alpine"));

        var combos = MatrixExpander.expand(matrix);

        assertEquals(4, combos.size()); // 2 × 2

        // Check all combinations exist
        assertTrue(combos.stream().anyMatch(c -> "11".equals(c.get("version")) && "ubuntu".equals(c.get("os"))));
        assertTrue(combos.stream().anyMatch(c -> "11".equals(c.get("version")) && "alpine".equals(c.get("os"))));
        assertTrue(combos.stream().anyMatch(c -> "17".equals(c.get("version")) && "ubuntu".equals(c.get("os"))));
        assertTrue(combos.stream().anyMatch(c -> "17".equals(c.get("version")) && "alpine".equals(c.get("os"))));
    }

    @Test
    void expandsThreeVariables() {
        Map<String, List<String>> matrix = new LinkedHashMap<>();
        matrix.put("a", List.of("1", "2"));
        matrix.put("b", List.of("x", "y"));
        matrix.put("c", List.of("A"));

        var combos = MatrixExpander.expand(matrix);
        assertEquals(4, combos.size()); // 2 × 2 × 1
    }

    @Test
    void emptyMatrixReturnsSingleEmptyMap() {
        var combos = MatrixExpander.expand(Map.of());
        assertEquals(1, combos.size());
        assertTrue(combos.get(0).isEmpty());
    }

    @Test
    void nullMatrixReturnsSingleEmptyMap() {
        var combos = MatrixExpander.expand(null);
        assertEquals(1, combos.size());
    }

    @Test
    void formatLabelProducesReadableOutput() {
        Map<String, String> combo = new LinkedHashMap<>();
        combo.put("version", "17");
        combo.put("os", "alpine");

        String label = MatrixExpander.formatLabel(combo);
        assertEquals("[version=17, os=alpine]", label);
    }

    @Test
    void formatLabelEmptyMap() {
        assertEquals("", MatrixExpander.formatLabel(Map.of()));
    }
}
