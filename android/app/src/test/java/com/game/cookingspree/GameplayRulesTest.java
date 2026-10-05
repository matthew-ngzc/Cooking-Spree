package com.game.cookingspree;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class GameplayRulesTest {
    @Test
    public void mashedPotatoMatchesExactlyTwoPotatoesAndOneOnion() {
        Recipe mashedPotato = recipeNamed("Mashed Potato");

        assertTrue(mashedPotato.canCook(ingredients(1, 1, 2)));
        assertFalse(mashedPotato.canCook(ingredients(1, 0, 2)));
        assertFalse(mashedPotato.canCook(ingredients(1, 1, 2, 4)));
        assertFalse(mashedPotato.canCook(ingredients(1, 2)));
    }

    @Test
    public void activeOrderExpiresAtZeroAndDoesNotGoNegative() {
        OrderCountdown order = new OrderCountdown(1);

        order.advanceActiveMillis(999);
        assertEquals(1, order.getTimeRemaining());
        assertFalse(order.isExpired());

        order.advanceActiveMillis(2);
        assertEquals(0, order.getTimeRemaining());
        assertTrue(order.isExpired());
        order.advanceActiveMillis(2_000);
        assertEquals(0, order.getTimeRemaining());
    }

    @Test
    public void successfulOrderPointsIncreaseWithinTenSecondsThenReset() {
        CompletionScoring scoring = new CompletionScoring();

        assertEquals(100, scoring.awardForCompletionAt(1_000));
        assertEquals(200, scoring.awardForCompletionAt(11_000));
        assertEquals(100, scoring.awardForCompletionAt(21_001));

        scoring.reset();
        assertEquals(100, scoring.awardForCompletionAt(30_000));
    }

    @Test
    public void thirdExpiredOrderReachesTerminalThresholdAndFinalizesOnlyOnce() {
        FailureCounter failures = new FailureCounter();
        assertFalse(failures.recordExpiry());
        assertEquals(1, failures.getCount());
        assertFalse(failures.recordExpiry());
        assertEquals(2, failures.getCount());
        assertTrue(failures.recordExpiry());
        assertTrue(failures.isTerminal());
        assertEquals(3, failures.getCount());

        FinalizationGate gate = new FinalizationGate();
        int[] acceptedFinalizations = {0};
        for (int i = 0; i < 3; i++) {
            gate.runOnce(() -> acceptedFinalizations[0]++);
        }
        assertEquals(1, acceptedFinalizations[0]);
    }

    private static Recipe recipeNamed(String name) {
        for (Recipe recipe : Recipe.getDefaultRecipes()) {
            if (recipe.getName().equals(name)) return recipe;
        }
        throw new AssertionError("Missing default recipe: " + name);
    }

    private static List<Ingredient> ingredients(int... ids) {
        Ingredient[] values = new Ingredient[ids.length];
        for (int i = 0; i < ids.length; i++) values[i] = new Ingredient(ids[i]);
        return Arrays.asList(values);
    }
}
