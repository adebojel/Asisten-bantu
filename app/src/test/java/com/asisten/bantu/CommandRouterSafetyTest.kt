package com.asisten.bantu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRouterSafetyTest {

    @Test
    fun blocksCommunicationTransactionsAndDestructiveActions() {
        val riskyTargets = listOf(
            "Kirim", "Send", "Bayar", "Transfer", "Beli", "Purchase",
            "Panggil", "Call", "Hapus", "Delete", "Remove", "Install",
            "Allow", "Accept", "Confirm", "Setuju", "Bagikan", "Upload",
            "Logout", "Block", "Report", "Pesan", "Messages", "New message",
            "Reply", "Compose", "Chat", "Dial", "Record", "Capture", "Start", "Open message",
            "Create", "Add"
        )
        riskyTargets.forEach { target ->
            assertTrue("Harus ditolak: $target", CommandRouter.isRiskyAgentTarget(target))
        }
    }

    @Test
    fun blocksSystemSettingsAndSecurityChanges() {
        val riskyTargets = listOf(
            "Wi-Fi", "Bluetooth", "Mobile data", "Data seluler", "Lokasi",
            "Password", "Akun", "Privacy", "Accessibility", "Izin aplikasi",
            "Matikan", "Aktifkan", "Disable", "Toggle", "Factory reset",
            "Mode pesawat", "Simpan", "Apply", "OK", "Yes", "Continue"
        )
        riskyTargets.forEach { target ->
            assertTrue("Harus ditolak: $target", CommandRouter.isRiskyAgentTarget(target))
        }
    }

    @Test
    fun legacyMacrosRejectTypingCoordinatesAndUnsafeTargets() {
        assertFalse(CommandRouter.isSafeStoredMacroStep("type_text", "hello", -1, -1))
        assertFalse(CommandRouter.isSafeStoredMacroStep("click_xy", "", 120, 240))
        assertFalse(CommandRouter.isSafeStoredMacroStep("click_label", "Send", -1, -1))
        assertFalse(CommandRouter.isSafeStoredMacroStep("click_label", "OK", -1, -1))
        assertFalse(CommandRouter.isSafeStoredMacroStep("click_label", "Continue", -1, -1))
        assertFalse(CommandRouter.isSafeStoredMacroStep("click_label", "Inbox", 120, 240))
    }

    @Test
    fun legacyMacrosAllowOnlyNonCoordinateNavigation() {
        assertTrue(CommandRouter.isSafeStoredMacroStep("open_app", "Chrome", -1, -1))
        assertTrue(CommandRouter.isSafeStoredMacroStep("click_label", "Inbox", -1, -1))
        assertTrue(CommandRouter.isSafeStoredMacroStep("scroll_down", "", -1, -1))
        assertTrue(CommandRouter.isSafeStoredMacroStep("scroll_up", "", -1, -1))
        assertTrue(CommandRouter.isSafeStoredMacroStep("back", "", -1, -1))
        assertTrue(CommandRouter.isSafeStoredMacroStep("home", "", -1, -1))
    }

    @Test
    fun allowsOrdinaryNavigationLabels() {
        val safeTargets = listOf(
            "Settings", "Apps", "Home", "Back", "Menu",
            "Chrome", "History", "Recent apps", "Search"
        )
        safeTargets.forEach { target ->
            assertFalse("Tidak boleh diblokir: $target", CommandRouter.isRiskyAgentTarget(target))
        }
    }
}
