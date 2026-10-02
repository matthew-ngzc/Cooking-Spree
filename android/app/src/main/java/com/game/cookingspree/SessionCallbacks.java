package com.game.cookingspree;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Activity-session gate used both before posting and when a queued callback runs. */
final class SessionCallbacks {
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger delivered = new AtomicInteger();

    boolean isOpen() { return !closed.get(); }
    void close() { closed.set(true); }
    void runIfOpen(Runnable callback) {
        if (isOpen() && callback != null) {
            delivered.incrementAndGet();
            callback.run();
        }
    }
    int deliveredCount() { return delivered.get(); }
}
