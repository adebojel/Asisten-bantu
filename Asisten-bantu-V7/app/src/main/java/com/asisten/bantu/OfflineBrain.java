package com.asisten.bantu;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Otak offline Kobra: kalkulator suara dan basis pengetahuan dasar (pertolongan pertama,
 * bantuan, hiburan). Java murni tanpa dependensi Android, jadi bisa diuji di komputer.
 * Ini BUKAN model bahasa besar: hanya mencocokkan frasa kunci dan menghitung ekspresi sederhana.
 */
public final class OfflineBrain {

    private static final class Entry {
        final List<String> keys;
        final String answer;
        final boolean shortOnly;
        Entry(List<String> keys, String answer, boolean shortOnly) {
            this.keys = keys;
            this.answer = answer;
            this.shortOnly = shortOnly;
        }
    }

    private static final int SHORT_MAX_WORDS = 4;
    private static final Map<String, Integer> UNITS = new HashMap<>();
    private static final Set<String> FILLERS = new HashSet<>(Arrays.asList(
            "berapa", "hitung", "hitungkan", "hasil", "hasilnya", "adalah", "sama", "dengan",
            "tolong", "coba", "ya", "itu", "nya", "kalkulator", "jadi", "apa"));

    static {
        String[] w = {"nol", "satu", "dua", "tiga", "empat", "lima", "enam", "tujuh",
                "delapan", "sembilan", "sepuluh", "sebelas"};
        for (int i = 0; i < w.length; i++) UNITS.put(w[i], i);
        UNITS.put("kosong", 0);
    }

    private final List<Entry> entries = new ArrayList<>();

    private OfflineBrain() {}

    /** Format baris: kunci1|kunci2 => jawaban. Awalan ~ berarti hanya cocok untuk ucapan pendek. */
    public static OfflineBrain fromText(String text) {
        OfflineBrain b = new OfflineBrain();
        if (text == null) return b;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int sep = line.indexOf("=>");
            if (sep < 0) continue;
            boolean shortOnly = line.startsWith("~");
            String keyPart = line.substring(shortOnly ? 1 : 0, sep);
            String answer = line.substring(sep + 2).trim();
            List<String> keys = new ArrayList<>();
            for (String k : keyPart.split("\\|")) {
                String n = normalize(k);
                if (!n.isEmpty()) keys.add(n);
            }
            if (!keys.isEmpty() && !answer.isEmpty()) b.entries.add(new Entry(keys, answer, shortOnly));
        }
        return b;
    }

    /** Jawaban offline, atau null jika tidak ada yang cocok. */
    public String answer(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        Double calc = calculate(input);
        if (calc != null) {
            if (calc.isNaN()) return "Tidak bisa dibagi dengan nol.";
            return "Hasilnya " + formatNumber(calc) + ".";
        }
        String q = " " + normalize(input) + " ";
        int wordCount = q.trim().isEmpty() ? 0 : q.trim().split(" ").length;
        Entry best = null;
        int bestScore = 0;
        for (Entry e : entries) {
            if (e.shortOnly && wordCount > SHORT_MAX_WORDS) continue;
            int score = 0;
            for (String k : e.keys) {
                if (q.contains(" " + k + " ")) score = Math.max(score, k.split(" ").length);
            }
            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best == null ? null : best.answer;
    }

    // ------------------------------------------------------------------ teks dan bilangan

    static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Semua bilangan (angka atau kata) dalam ucapan, berurutan. Contoh: "jam tujuh dua puluh" menjadi 7 dan 20. */
    public static List<Integer> numbers(String text) {
        List<String> t = new ArrayList<>();
        String n = normalize(text == null ? "" : text);
        if (!n.isEmpty()) t.addAll(Arrays.asList(n.split(" ")));
        List<Integer> out = new ArrayList<>();
        int i = 0;
        while (i < t.size()) {
            String w = t.get(i);
            if (w.matches("\\d{1,9}")) {
                out.add(Integer.parseInt(w));
                i++;
                continue;
            }
            long[] r = parseWords(t, i);
            if (r != null && r[0] <= Integer.MAX_VALUE) {
                out.add((int) r[0]);
                i = (int) r[1];
            } else {
                i++;
            }
        }
        return out;
    }

    public static Integer firstNumber(String text) {
        List<Integer> n = numbers(text);
        return n.isEmpty() ? null : n.get(0);
    }

    /** Mengurai kata bilangan Indonesia mulai dari index. Mengembalikan {nilai, indexBerikutnya} atau null. */
    private static long[] parseWords(List<String> t, int start) {
        long total = 0, cur = 0, last = 0;
        boolean any = false, prevUnit = false;
        int j = start;
        while (j < t.size()) {
            String w = t.get(j);
            Integer u = UNITS.get(w);
            if (u != null) {
                if (prevUnit) break;
                cur += u;
                last = u;
                any = true;
                prevUnit = true;
                j++;
                continue;
            }
            prevUnit = false;
            if (w.equals("seratus")) { cur += 100; last = 0; any = true; }
            else if (w.equals("seribu")) { total += 1000; last = 0; any = true; }
            else if (w.equals("sejuta")) { total += 1000000; last = 0; any = true; }
            else if (any && w.equals("belas") && last >= 1 && last <= 9) { cur += 10; last = 0; }
            else if (any && w.equals("puluh") && last >= 1 && last <= 9) { cur += last * 9; last = 0; }
            else if (any && w.equals("ratus") && last >= 1 && last <= 9) { cur += last * 99; last = 0; }
            else if (any && w.equals("ribu")) { total += (cur == 0 ? 1 : cur) * 1000L; cur = 0; last = 0; }
            else if (any && w.equals("juta")) { total += (cur == 0 ? 1 : cur) * 1000000L; cur = 0; last = 0; }
            else break;
            j++;
        }
        if (!any) return null;
        return new long[]{total + cur, j};
    }

    // ------------------------------------------------------------------ kalkulator

    /**
     * Menghitung ekspresi dari ucapan, misalnya "berapa dua puluh lima kali empat".
     * Mengembalikan null jika ucapan bukan hitungan, NaN jika dibagi nol.
     */
    public static Double calculate(String input) {
        if (input == null) return null;
        String s = input.toLowerCase(Locale.ROOT);
        s = s.replaceAll("(?<=\\d),(?=\\d)", ".");
        s = s.replaceAll("[^a-z0-9.+*/\u00d7\u00f7()\\- ]", " ");
        s = s.replaceAll("([+*/\u00d7\u00f7()\\-])", " $1 ");
        List<String> t = new ArrayList<>();
        for (String x : s.trim().split("\\s+")) {
            String y = x.replaceAll("^\\.+|\\.+$", "");
            if (!y.isEmpty()) t.add(y);
        }
        List<Object> items = new ArrayList<>();
        boolean sawOp = false;
        int i = 0;
        while (i < t.size()) {
            String w = t.get(i);
            if (w.matches("\\d+(\\.\\d+)?")) {
                items.add(Double.parseDouble(w));
                i++;
                continue;
            }
            Character op = operatorOf(w);
            if (op != null) {
                items.add(op);
                if (op != '(' && op != ')') sawOp = true;
                i++;
                continue;
            }
            long[] r = parseWords(t, i);
            if (r != null) {
                items.add((double) r[0]);
                i = (int) r[1];
                continue;
            }
            if (FILLERS.contains(w)) { i++; continue; }
            return null;
        }
        if (!sawOp) return null;
        Parser p = new Parser(items);
        double v = p.expr();
        if (p.divZero) return Double.NaN;
        if (p.err || p.pos != items.size()) return null;
        return v;
    }

    private static Character operatorOf(String w) {
        switch (w) {
            case "+": case "tambah": case "plus": case "ditambah": return '+';
            case "-": case "kurang": case "dikurangi": case "dikurang": case "minus": return '-';
            case "*": case "x": case "kali": case "dikali": case "\u00d7": return '*';
            case "/": case "bagi": case "dibagi": case "\u00f7": return '/';
            case "(": return '(';
            case ")": return ')';
            default: return null;
        }
    }

    private static final class Parser {
        final List<Object> it;
        int pos = 0;
        boolean err = false, divZero = false;
        Parser(List<Object> it) { this.it = it; }

        private boolean isOp(char a, char b) {
            if (pos >= it.size()) return false;
            Object o = it.get(pos);
            return o instanceof Character && ((Character) o == a || (Character) o == b);
        }

        double expr() {
            double v = term();
            while (!err && !divZero && isOp('+', '-')) {
                char o = (Character) it.get(pos++);
                double r = term();
                v = o == '+' ? v + r : v - r;
            }
            return v;
        }

        double term() {
            double v = factor();
            while (!err && !divZero && isOp('*', '/')) {
                char o = (Character) it.get(pos++);
                double r = factor();
                if (o == '/') {
                    if (r == 0) { divZero = true; return 0; }
                    v /= r;
                } else {
                    v *= r;
                }
            }
            return v;
        }

        double factor() {
            if (pos >= it.size()) { err = true; return 0; }
            Object o = it.get(pos++);
            if (o instanceof Double) return (Double) o;
            if (o instanceof Character && (Character) o == '(') {
                double v = expr();
                if (pos < it.size() && it.get(pos) instanceof Character && (Character) it.get(pos) == ')') pos++;
                else err = true;
                return v;
            }
            err = true;
            return 0;
        }
    }

    /** Angka untuk dibacakan: bilangan bulat tanpa desimal, selain itu maksimal empat desimal dengan koma. */
    public static String formatNumber(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        String s = new BigDecimal(v).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        return s.replace('.', ',');
    }
}
