package com.game.cookingspree.util;

/** Prevents a local cache hydration from being echoed as a user edit. */
public final class CloudSyncGate {
    private final ThreadLocal<Integer> suppressionDepth = ThreadLocal.withInitial(() -> 0);

    public boolean isSuppressed() { return suppressionDepth.get() > 0; }

    public void runIfAllowed(Runnable cloudWrite) {
        if (!isSuppressed()) cloudWrite.run();
    }

    public void withoutSync(Runnable action) {
        suppressionDepth.set(suppressionDepth.get() + 1);
        try {
            action.run();
        } finally {
            int depth = suppressionDepth.get() - 1;
            if (depth == 0) suppressionDepth.remove();
            else suppressionDepth.set(depth);
        }
    }
}
