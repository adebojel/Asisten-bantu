package com.asisten.bantu;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ActionSafetyPolicyTest {
    @Test public void ordinaryNavigationDoesNotRequireConfirmation() {
        assertFalse(ActionSafetyPolicy.requiresConfirmation("Buka pengaturan"));
        assertFalse(ActionSafetyPolicy.requiresConfirmation("Kembali"));
    }

    @Test public void destructiveAndExternalActionsRequireConfirmation() {
        assertTrue(ActionSafetyPolicy.requiresConfirmation("Kirim pesan"));
        assertTrue(ActionSafetyPolicy.requiresConfirmation("Hapus akun"));
        assertTrue(ActionSafetyPolicy.requiresConfirmation("Allow access"));
    }

    @Test public void highImpactActionsRequireTwoConfirmations() {
        assertTrue(ActionSafetyPolicy.requiresDoubleConfirmation("Bayar sekarang"));
        assertTrue(ActionSafetyPolicy.requiresDoubleConfirmation("Transfer"));
        assertTrue(ActionSafetyPolicy.requiresDoubleConfirmation("Factory reset"));
        assertFalse(ActionSafetyPolicy.requiresDoubleConfirmation("Kirim pesan"));
    }
}
