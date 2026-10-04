package com.game.cookingspree;

import java.util.EnumSet;

/** Session pause reasons and an active-time wait used by gameplay workers. */
public final class PauseState {
    public enum Reason { MANUAL_MENU, BACKGROUND, TUTORIAL, LOAD }

    private final EnumSet<Reason> reasons = EnumSet.noneOf(Reason.class);
    private boolean terminal;
    private boolean closed;
    private boolean tutorialMovementAllowed;

    public synchronized void setPaused(Reason reason, boolean paused) {
        if (closed || terminal) return;
        if (paused) reasons.add(reason); else reasons.remove(reason);
        if (!paused && reason == Reason.TUTORIAL) tutorialMovementAllowed = false;
        notifyAll();
    }

    public synchronized void allowTutorialMovement(boolean allowed) {
        if (closed || terminal || !reasons.contains(Reason.TUTORIAL)) return;
        tutorialMovementAllowed = allowed;
    }

    public synchronized void setTerminal() {
        terminal = true;
        reasons.clear();
        tutorialMovementAllowed = false;
        notifyAll();
    }

    public synchronized void close() {
        closed = true;
        reasons.clear();
        tutorialMovementAllowed = false;
        notifyAll();
    }

    public synchronized boolean isRunning() {
        return !closed && !terminal && reasons.isEmpty();
    }

    public synchronized boolean isPaused(Reason reason) { return reasons.contains(reason); }
    public synchronized boolean isTerminal() { return terminal; }
    public synchronized boolean isMovementAllowed() {
        return isRunning() || (!closed && !terminal && tutorialMovementAllowed
                && reasons.size() == 1 && reasons.contains(Reason.TUTORIAL));
    }

    /** Wait for the requested amount of unpaused time, retaining the remainder over pauses. */
    public boolean awaitActiveDuration(long durationMillis) throws InterruptedException {
        ActiveDuration duration = new ActiveDuration(durationMillis);
        synchronized (this) {
            while (!isRunning() && !closed && !terminal) wait();
            if (closed || terminal || Thread.currentThread().isInterrupted()) return false;
            while (!duration.isComplete()) {
                while (!isRunning() && !closed && !terminal) wait();
                if (closed || terminal || Thread.currentThread().isInterrupted()) return false;
                long start = System.nanoTime();
                long millis = Math.max(1L, Math.min(50L, duration.remainingNanos() / 1_000_000L));
                wait(millis);
                if (isRunning()) duration.consume(System.nanoTime() - start);
            }
            return isRunning();
        }
    }

    /** Serialize a boundary mutation with pause transitions. */
    public synchronized boolean runIfRunning(Runnable mutation) {
        if (!isRunning()) return false;
        mutation.run();
        return true;
    }

    public synchronized boolean runIfMovementAllowed(Runnable mutation) {
        if (!isMovementAllowed()) return false;
        mutation.run();
        return true;
    }
}
