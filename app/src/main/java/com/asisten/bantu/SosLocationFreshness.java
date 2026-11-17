package com.asisten.bantu;

/**
 * Conservative freshness policy for coordinates included in emergency SMS.
 * A cached fix is labelled current only when it is no more than two minutes old.
 */
final class SosLocationFreshness {
    static final long MAX_FRESH_AGE_MS = 2L * 60L * 1000L;

    private SosLocationFreshness() { }

    static boolean isFresh(long nowMs, long fixTimeMs) {
        return fixTimeMs > 0L
                && nowMs >= fixTimeMs
                && nowMs - fixTimeMs <= MAX_FRESH_AGE_MS;
    }

    /** Returns rounded-up age in minutes, or -1 when the timestamp is invalid/future-dated. */
    static long ageMinutes(long nowMs, long fixTimeMs) {
        if (fixTimeMs <= 0L || nowMs < fixTimeMs) return -1L;
        long ageMs = nowMs - fixTimeMs;
        return ageMs == 0L ? 0L : 1L + ((ageMs - 1L) / 60_000L);
    }
}
