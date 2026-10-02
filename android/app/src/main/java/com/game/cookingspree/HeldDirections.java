package com.game.cookingspree;

import java.util.HashSet;
import java.util.Set;

/** Tracks independently held movement controls so one release cannot orphan another. */
final class HeldDirections {
    private final Set<Integer> held = new HashSet<>();
    void press(int direction) { held.add(direction); }
    boolean release(int direction) { held.remove(direction); return !held.isEmpty(); }
    void clear() { held.clear(); }
    boolean isHeld(int direction) { return held.contains(direction); }
    boolean isEmpty() { return held.isEmpty(); }
}
