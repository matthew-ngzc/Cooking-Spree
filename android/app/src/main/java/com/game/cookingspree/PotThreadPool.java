package com.game.cookingspree;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PotThreadPool {
    //Pool for pot threads, should be equal to number of pots
    final ExecutorService pool;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger activeTasks = new AtomicInteger();
    public PotThreadPool(int threads) {
        pool = Executors.newFixedThreadPool(threads);
    }
    public void submit(final Runnable task) {
        if (closed.get()) return;
        try {
            pool.submit(() -> {
                if (closed.get() || Thread.currentThread().isInterrupted()) return;
                activeTasks.incrementAndGet();
                try {
                    if (!closed.get() && !Thread.currentThread().isInterrupted()) task.run();
                } finally {
                    activeTasks.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException ignored) { }
    }

    public void close() {
        if (closed.compareAndSet(false, true)) pool.shutdownNow();
    }

    public boolean isClosed() {
        return closed.get();
    }

    int activeTaskCountForTest() { return activeTasks.get(); }
}
