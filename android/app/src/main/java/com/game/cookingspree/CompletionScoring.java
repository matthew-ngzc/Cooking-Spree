package com.game.cookingspree;

/** Applies the current streak bonus for successful order completions. */
final class CompletionScoring {
    private static final int BASE_POINTS = 100;
    private static final long STREAK_TIME_LIMIT_MS = 10_000L;

    private int streakCount;
    private long lastCompletionTime;

    int awardForCompletionAt(long completedAtMillis) {
        if (lastCompletionTime > 0
                && completedAtMillis - lastCompletionTime <= STREAK_TIME_LIMIT_MS) {
            streakCount++;
        } else {
            streakCount = 1;
        }
        lastCompletionTime = completedAtMillis;
        return BASE_POINTS * streakCount;
    }

    void reset() {
        streakCount = 0;
        lastCompletionTime = 0L;
    }
}
