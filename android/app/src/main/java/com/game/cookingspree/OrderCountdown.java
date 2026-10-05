package com.game.cookingspree;

/** Pure active-time countdown shared by orders and deterministic gameplay-rule tests. */
final class OrderCountdown {
    private int timeRemaining;
    private boolean complete;
    private boolean expired;
    private final DeltaStepper stepper = new DeltaStepper(1000, this::decrementOneSecond);

    OrderCountdown(int initialSeconds) {
        this.timeRemaining = Math.max(0, initialSeconds);
        this.expired = initialSeconds <= 0;
    }

    void advanceActiveMillis(long elapsedMillis) {
        if (!complete && !expired && elapsedMillis > 0) stepper.update(elapsedMillis);
    }

    void complete() {
        if (!expired) complete = true;
    }

    int getTimeRemaining() { return timeRemaining; }
    boolean isComplete() { return complete; }
    boolean isExpired() { return expired; }

    private boolean decrementOneSecond(long ignoredDelta) {
        if (!complete && !expired) {
            timeRemaining--;
            if (timeRemaining <= 0) {
                timeRemaining = 0;
                expired = true;
            }
        }
        return true;
    }
}
