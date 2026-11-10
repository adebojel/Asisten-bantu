package com.asisten.bantu;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/** Pengujian logika murni OfflineBrain. Jalankan: lihat README bagian "Menguji logika offline". */
public class OfflineBrainCheck {
    static int fail = 0, pass = 0;

    static void eq(String name, Object actual, Object expected) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (ok) pass++; else { fail++; System.out.println("GAGAL " + name + ": dapat <" + actual + ">, harapan <" + expected + ">"); }
    }

    static void has(String name, String actual, String part) {
        boolean ok = actual != null && actual.contains(part);
        if (ok) pass++; else { fail++; System.out.println("GAGAL " + name + ": dapat <" + actual + ">, harus memuat <" + part + ">"); }
    }

    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "app/src/main/assets/kobra_kb.txt";
        OfflineBrain b = OfflineBrain.fromText(new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8));

        // kalkulator
        eq("tambah", b.answer("berapa 5 tambah 3"), "Hasilnya 8.");
        eq("kali kata", b.answer("dua puluh lima kali empat"), "Hasilnya 100.");
        eq("bagi", b.answer("100 dibagi 4"), "Hasilnya 25.");
        eq("bagi nol", b.answer("10 dibagi 0"), "Tidak bisa dibagi dengan nol.");
        eq("ratus", b.answer("seratus dua puluh tambah tiga ratus"), "Hasilnya 420.");
        eq("kurang negatif", b.answer("dua ribu lima ratus kurang satu juta"), "Hasilnya -997500.");
        eq("kurung", b.answer("(2 + 3) x 4"), "Hasilnya 20.");
        eq("prioritas", b.answer("2 + 3 x 4"), "Hasilnya 14.");
        eq("desimal", b.answer("7 / 2"), "Hasilnya 3,5.");
        eq("belas", b.answer("dua belas tambah tiga"), "Hasilnya 15.");
        eq("hitung kata awal", b.answer("tolong hitung sebelas kali sebelas"), "Hasilnya 121.");
        eq("bukan hitungan 1", OfflineBrain.calculate("dua kali sehari"), null);
        eq("bukan hitungan 2", OfflineBrain.calculate("uang saya kurang"), null);
        eq("bukan hitungan 3", OfflineBrain.calculate("tiga kali"), null);
        eq("bukan hitungan 4", OfflineBrain.calculate("jam tujuh"), null);

        // bilangan
        eq("angka jam", OfflineBrain.numbers("alarm jam 6 30"), Arrays.asList(6, 30));
        eq("kata jam", OfflineBrain.numbers("jam tujuh dua puluh"), Arrays.asList(7, 20));
        eq("timer kata", OfflineBrain.firstNumber("timer lima menit"), 5);
        eq("jam titik dua", OfflineBrain.numbers("bangunkan jam 6:45"), Arrays.asList(6, 45));
        eq("nomor tanpa angka", OfflineBrain.firstNumber("tekan nomor"), null);
        eq("duabelas", OfflineBrain.firstNumber("jam dua belas malam"), 12);

        // pengetahuan
        has("tersedak", b.answer("tolong saya tersedak"), "lima tepukan");
        has("luka", b.answer("ada pendarahan hebat"), "Tekan luka");
        has("bakar", b.answer("tangan saya terbakar"), "dua puluh menit");
        has("darurat", b.answer("nomor darurat berapa"), "112");
        has("sedih", b.answer("aku lagi sedih banget"), "mendengarkan");
        has("sapa pendek", b.answer("halo"), "Ada yang bisa");
        eq("sapa panjang tidak cocok", b.answer("halo saya mau tanya tentang cuaca di jakarta besok"), null);
        eq("tak dikenal", b.answer("siapa presiden amerika"), null);
        eq("kosong", b.answer("   "), null);
        has("p3k", b.answer("panduan p3k"), "tersedak");

        System.out.println("Lulus: " + pass + ", gagal: " + fail);
        if (fail > 0) System.exit(1);
    }
}
