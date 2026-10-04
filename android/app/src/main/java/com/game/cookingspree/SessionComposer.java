package com.game.cookingspree;

/** Ensures an activity session's object graph is composed once, after its layout is selected. */
final class SessionComposer {
    private boolean composed;
    private int compositionCount;

    boolean composeOnce(Runnable composition) {
        if (composed) return false;
        composed = true;
        compositionCount++;
        composition.run();
        return true;
    }

    boolean isComposed() { return composed; }
    int getCompositionCount() { return compositionCount; }
}
