package com.asisten.bantu;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Penghitung jadwal sholat 5 waktu (Subuh, Dzuhur, Ashar, Maghrib, Isya) dan arah kiblat
 * secara 100% offline menggunakan rumus astronomi posisi matahari & trigonometri bola.
 */
public final class PrayerTimesHelper {

    public static final double KAABA_LAT = 21.422487;
    public static final double KAABA_LON = 39.826206;

    public static final class PrayerTimes {
        public final String subuh;
        public final String terbit;
        public final String dzuhur;
        public final String ashar;
        public final String maghrib;
        public final String isya;

        PrayerTimes(String subuh, String terbit, String dzuhur, String ashar, String maghrib, String isya) {
            this.subuh = subuh;
            this.terbit = terbit;
            this.dzuhur = dzuhur;
            this.ashar = ashar;
            this.maghrib = maghrib;
            this.isya = isya;
        }

        public String toSpeechString() {
            return "Jadwal sholat hari ini. Subuh pukul " + subuh
                    + ". Terbit pukul " + terbit
                    + ". Dzuhur pukul " + dzuhur
                    + ". Ashar pukul " + ashar
                    + ". Maghrib pukul " + maghrib
                    + ". Isya pukul " + isya + ".";
        }
    }

    private PrayerTimesHelper() {}

    /** Menghitung arah kiblat dalam derajat searah jarum jam dari Utara sejati (0..359). */
    public static int computeQiblaBearing(double lat, double lon) {
        double phiK = Math.toRadians(KAABA_LAT);
        double phi = Math.toRadians(lat);
        double dLambda = Math.toRadians(KAABA_LON - lon);
        double y = Math.sin(dLambda);
        double x = Math.cos(phi) * Math.tan(phiK) - Math.sin(phi) * Math.cos(dLambda);
        double deg = Math.toDegrees(Math.atan2(y, x));
        return ((int) Math.round(deg) % 360 + 360) % 360;
    }

    /** Menghitung jadwal sholat 5 waktu offline berdasarkan GPS & zona waktu HP (metode standar Kemenag: Subuh 20°, Isya 18°). */
    public static PrayerTimes calculateToday(double lat, double lon) {
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);
        double tzHours = TimeZone.getDefault().getOffset(cal.getTimeInMillis()) / 3600000.0;

        double jd = julianDate(year, month, day) - lon / (15.0 * 24.0);

        double[] sun = sunPosition(jd + 0.5);
        double decl = sun[0];
        double eqt = sun[1];

        double transit = 12.0 + tzHours - lon / 15.0 - eqt;
        double dzuhurHours = transit + 2.0 / 60.0; // +2 menit ihtiyat
        double subuhHours = transit - sunAngleTime(lat, decl, 20.0);
        double terbitHours = transit - sunAngleTime(lat, decl, 0.833);
        double asharHours = transit + asrTime(lat, decl, 1.0); // Mazhab Syafi'i (bayangan = 1x tinggi)
        double maghribHours = transit + sunAngleTime(lat, decl, 0.833) + 2.0 / 60.0;
        double isyaHours = transit + sunAngleTime(lat, decl, 18.0);

        return new PrayerTimes(
                formatHours(subuhHours),
                formatHours(terbitHours),
                formatHours(dzuhurHours),
                formatHours(asharHours),
                formatHours(maghribHours),
                formatHours(isyaHours));
    }

    private static double julianDate(int year, int month, int day) {
        if (month <= 2) {
            year -= 1;
            month += 12;
        }
        int a = year / 100;
        int b = 2 - a + (a / 4);
        return Math.floor(365.25 * (year + 4716))
                + Math.floor(30.6001 * (month + 1)) + day + b - 1524.5;
    }

    /** Mengembalikan {deklinasi matahari (radian), equation of time (jam)}. */
    private static double[] sunPosition(double jd) {
        double d = jd - 2451545.0;
        double g = Math.toRadians(fixAngle(357.529 + 0.98560028 * d));
        double q = fixAngle(280.459 + 0.98564736 * d);
        double l = Math.toRadians(fixAngle(q + 1.915 * Math.sin(g) + 0.020 * Math.sin(2.0 * g)));
        double e = Math.toRadians(23.439 - 0.00000036 * d);

        double ra = Math.toDegrees(Math.atan2(Math.cos(e) * Math.sin(l), Math.cos(l))) / 15.0;
        double eqt = q / 15.0 - fixHour(ra);
        double decl = Math.asin(Math.sin(e) * Math.sin(l));
        return new double[]{decl, eqt};
    }

    private static double sunAngleTime(double latDeg, double declRad, double angleDeg) {
        double latRad = Math.toRadians(latDeg);
        double num = -Math.sin(Math.toRadians(angleDeg)) - Math.sin(latRad) * Math.sin(declRad);
        double den = Math.cos(latRad) * Math.cos(declRad);
        double cosVal = Math.max(-1.0, Math.min(1.0, num / den));
        return Math.toDegrees(Math.acos(cosVal)) / 15.0;
    }

    private static double asrTime(double latDeg, double declRad, double shadowFactor) {
        double latRad = Math.toRadians(latDeg);
        double angle = -Math.atan(1.0 / (shadowFactor + Math.tan(Math.abs(latRad - declRad))));
        double num = Math.sin(angle) - Math.sin(latRad) * Math.sin(declRad);
        double den = Math.cos(latRad) * Math.cos(declRad);
        double cosVal = Math.max(-1.0, Math.min(1.0, num / den));
        return Math.toDegrees(Math.acos(cosVal)) / 15.0;
    }

    private static double fixAngle(double a) {
        a = a - 360.0 * Math.floor(a / 360.0);
        return a < 0 ? a + 360.0 : a;
    }

    private static double fixHour(double h) {
        h = h - 24.0 * Math.floor(h / 24.0);
        return h < 0 ? h + 24.0 : h;
    }

    private static String formatHours(double time) {
        if (Double.isNaN(time)) return "--:--";
        time = fixHour(time + 0.5 / 60.0);
        int hours = (int) Math.floor(time);
        int minutes = (int) Math.floor((time - hours) * 60.0);
        return String.format(Locale.US, "%02d:%02d", hours, minutes);
    }
}
