package com.asisten.bantu;

public final class BRouterVoiceInstruction {
    private BRouterVoiceInstruction() {}

    public static String toIndonesian(int commandIndex, int exitNumber) {
        switch (commandIndex) {
            case 1: return "lurus terus";
            case 2: return "belok kiri";
            case 3: return "belok tajam ke kiri";
            case 4: return "belok sedikit ke kiri";
            case 5: return "belok kanan";
            case 6: return "belok tajam ke kanan";
            case 7: return "belok sedikit ke kanan";
            case 8: return "tetap di sisi kiri";
            case 9: return "tetap di sisi kanan";
            case 10: return "putar balik ke kiri";
            case 11: return "putar balik ke kanan";
            case 12: return "keluar dari rute; periksa arah";
            case 13: return "keluar bundaran pada jalan ke " + Math.max(1, exitNumber);
            case 14: return "keluar bundaran pada jalan ke " + Math.max(1, Math.abs(exitNumber));
            case 15: return "putar balik";
            case 16: return "ambil cabang kiri";
            case 17: return "ikuti jalur kiri sampai akhir";
            case 18: return "ikuti jalur kanan sampai akhir";
            default: return null;
        }
    }
}
