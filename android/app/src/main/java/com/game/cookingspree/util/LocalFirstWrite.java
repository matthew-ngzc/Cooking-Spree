package com.game.cookingspree.util;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Small, Android-free policies shared by local persistence and optional cloud sync. */
public final class LocalFirstWrite {
    private LocalFirstWrite() {}

    public static void run(Runnable localWrite, Runnable cloudWrite, Consumer<RuntimeException> onFailure) {
        localWrite.run();
        try {
            cloudWrite.run();
        } catch (RuntimeException failure) {
            if (onFailure != null) onFailure.accept(failure);
        }
    }

    public static <T> boolean ifPresent(Supplier<T> currentUser, Consumer<T> cloudWrite) {
        T user = currentUser.get();
        if (user == null) return false;
        cloudWrite.accept(user);
        return true;
    }
}
