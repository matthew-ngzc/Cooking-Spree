package com.game.cookingspree;

/** Tracks expired orders and reports the existing three-failure terminal rule. */
final class FailureCounter {
    static final int TERMINAL_FAILURES = 3;

    private int count;

    boolean recordExpiry() {
        count++;
        return isTerminal();
    }

    void setCount(int count) { this.count = count; }
    void reset() { count = 0; }
    int getCount() { return count; }
    boolean isTerminal() { return count >= TERMINAL_FAILURES; }
}
