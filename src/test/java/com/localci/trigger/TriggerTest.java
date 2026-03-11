package com.localci.trigger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TriggerManager: cron parsing logic.
 */
class TriggerTest {

    @Test
    void parsesEveryNMinutes() {
        assertEquals(300, TriggerManager.parseCronToIntervalSeconds("*/5 * * * *"));
        assertEquals(600, TriggerManager.parseCronToIntervalSeconds("*/10 * * * *"));
        assertEquals(60, TriggerManager.parseCronToIntervalSeconds("*/1 * * * *"));
    }

    @Test
    void parsesEveryHour() {
        assertEquals(3600, TriggerManager.parseCronToIntervalSeconds("0 * * * *"));
    }

    @Test
    void parsesEveryDay() {
        assertEquals(86400, TriggerManager.parseCronToIntervalSeconds("0 0 * * *"));
    }

    @Test
    void defaultsToOneHourForComplexExpressions() {
        assertEquals(3600, TriggerManager.parseCronToIntervalSeconds("30 2 * * 1"));
    }

    @Test
    void handlesNullAndBlank() {
        assertEquals(3600, TriggerManager.parseCronToIntervalSeconds(null));
        assertEquals(3600, TriggerManager.parseCronToIntervalSeconds(""));
        assertEquals(3600, TriggerManager.parseCronToIntervalSeconds("   "));
    }

    @Test
    void handlesMalformedCron() {
        assertEquals(3600, TriggerManager.parseCronToIntervalSeconds("bad"));
        assertEquals(3600, TriggerManager.parseCronToIntervalSeconds("* *"));
    }
}
