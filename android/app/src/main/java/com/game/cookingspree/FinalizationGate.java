package com.game.cookingspree;

import java.util.concurrent.atomic.AtomicBoolean;

/** Ensures terminal session side effects are accepted once. */
final class FinalizationGate {
    private final AtomicBoolean finalized = new AtomicBoolean();
    boolean runOnce(Runnable finalization) {
        if (!finalized.compareAndSet(false, true)) return false;
        finalization.run();
        return true;
    }
    boolean isFinalized() { return finalized.get(); }
}
