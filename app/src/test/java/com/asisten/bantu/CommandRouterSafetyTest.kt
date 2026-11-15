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
            "Logout", "Block", "Report", "Pesan"
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
