package com.asisten.bantu;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RoutineAutomationManagerTest {
    @Test
    public void acceptsOnlySupportedOfflineRoutineConditions() {
        assertTrue(RoutineAutomationManager.isValidCondition("always", -1));
        assertTrue(RoutineAutomationManager.isValidCondition("charging", -1));
        assertTrue(RoutineAutomationManager.isValidCondition("battery_below", 1));
        assertTrue(RoutineAutomationManager.isValidCondition("battery_below", 100));
        assertFalse(RoutineAutomationManager.isValidCondition("battery_below", 0));
        assertFalse(RoutineAutomationManager.isValidCondition("battery_below", 101));
        assertFalse(RoutineAutomationManager.isValidCondition("network_available", -1));
        assertFalse(RoutineAutomationManager.isValidCondition("open_app", -1));
    }
}
