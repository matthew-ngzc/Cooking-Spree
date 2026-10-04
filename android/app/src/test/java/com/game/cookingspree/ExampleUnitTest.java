package com.game.cookingspree;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;

/**
 * Small recipe-ordering regression; the broader gameplay rule cases live in GameplayRulesTest.
 */
public class ExampleUnitTest {
    @Test
    public void tomatoSoupAcceptsItsExactIngredientsInAnyOrder() {
        Recipe tomatoSoup = Recipe.getDefaultRecipes().get(0);

        assertTrue(tomatoSoup.canCook(Arrays.asList(
                new Ingredient(Recipe.ONION),
                new Ingredient(Recipe.CARROT),
                new Ingredient(Recipe.TOMATO))));
    }
}
