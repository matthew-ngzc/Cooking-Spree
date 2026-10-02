package com.game.cookingspree.util;

import java.util.function.IntConsumer;

public final class JoystickSelection {
    private JoystickSelection() {}

    /** Select the saved option before the caller installs its user-change listener. */
    public static void hydrate(float savedScale, float largeScale, int smallId, int largeId,
                               IntConsumer select, Runnable installListener) {
        select.accept(savedScale == largeScale ? largeId : smallId);
        installListener.run();
    }
}
