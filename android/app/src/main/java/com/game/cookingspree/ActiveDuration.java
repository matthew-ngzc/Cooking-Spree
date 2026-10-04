package com.game.cookingspree;

/** Counts only elapsed time observed while a session is running. */
final class ActiveDuration {
    private long remainingNanos;

    ActiveDuration(long durationMillis) { remainingNanos = Math.max(0L, durationMillis) * 1_000_000L; }
    void consume(long elapsedNanos) { remainingNanos = Math.max(0L, remainingNanos - Math.max(0L, elapsedNanos)); }
    long remainingNanos() { return remainingNanos; }
    boolean isComplete() { return remainingNanos == 0L; }
}
