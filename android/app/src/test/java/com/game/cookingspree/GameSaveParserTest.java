package com.game.cookingspree;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class GameSaveParserTest {
    @Test
    public void legacyValuesRoundTripWithoutChangingDoneFoodOrPartiallyFilledEmptyPot() throws Exception {
        Map<String, Object> legacy = validLegacySave();
        legacy.put("pot_0_state", Pot.State.DONE.name());
        legacy.put("pot_0_ingredientCount", 0);
        legacy.put("pot_0_food_id", 5);
        legacy.put("pot_0_food_name", "Waste");
        legacy.put("pot_0_food_recipe_id", "waste");
        legacy.put("pot_0_food_ingredientCount", 0);
        legacy.put("pot_1_ingredientCount", 2);
        legacy.put("pot_1_ingredient_0_id", 1);
        legacy.put("pot_1_ingredient_1_id", 1);

        GameSaveSnapshot first = parse(legacy);
        assertEquals(Pot.State.DONE, first.pots.get(0).state);
        assertEquals("Waste", first.pots.get(0).food.name);
        assertEquals(2, first.pots.get(1).ingredients.size());
        Map<String, Object> serialized = first.toLegacyValues();
        GameSaveSnapshot second = parse(serialized);

        assertEquals(serialized, second.toLegacyValues());
        assertEquals(BuildConfig.VERSION_NAME, second.gameVersion);
        assertEquals("Waste", second.pots.get(0).food.name);
        assertEquals(2, second.pots.get(1).ingredients.size());
    }

    @Test
    public void cookedDishWithoutMatchingActiveOrderRemainsValid() throws Exception {
        Map<String, Object> save = validLegacySave();
        save.put("heldItemType", PlayerInventory.COOKED);
        save.put("heldItemId", 5);
        save.put("heldItemName", "Tomato Soup");
        save.put("heldItemRecipeId", "tomato_soup");
        save.put("heldItemIngredientsCount", 3);
        save.put("heldItemIngredient_0_id", 4);
        save.put("heldItemIngredient_0_name", "old tomato label");
        save.put("heldItemIngredient_1_id", 0);
        save.put("heldItemIngredient_1_name", "carrot");
        save.put("heldItemIngredient_2_id", 2);
        save.put("heldItemIngredient_2_name", "onion");
        GameSaveSnapshot parsed = parse(save);
        assertEquals("Tomato Soup", parsed.heldItem.name);
        assertEquals(0, parsed.orders.size());
        assertEquals("Tomato Soup", parsed.toLegacyValues().get("heldItemName"));
        assertEquals("tomato", parsed.toLegacyValues().get("heldItemIngredient_0_name"));
    }

    @Test
    public void cookingRecipeAndProgressAreRestoredAsOneCandidate() throws Exception {
        Map<String, Object> legacy = validLegacySave();
        legacy.put("pot_0_state", Pot.State.COOKING.name());
        legacy.put("pot_0_ingredientCount", 3);
        legacy.put("pot_0_ingredient_0_id", 4);
        legacy.put("pot_0_ingredient_1_id", 0);
        legacy.put("pot_0_ingredient_2_id", 2);
        legacy.put("pot_0_cooking_progress", 2);
        legacy.put("pot_0_recipe_id", "tomato_soup");
        legacy.put("pot_0_recipe_ingredientCount", 3);
        legacy.put("pot_0_recipe_ingredient_0_id", 4);
        legacy.put("pot_0_recipe_ingredient_1_id", 0);
        legacy.put("pot_0_recipe_ingredient_2_id", 2);

        GameSaveSnapshot candidate = parse(legacy);
        assertEquals("Tomato Soup", candidate.pots.get(0).recipeName);
        assertEquals(2, candidate.pots.get(0).cookingProgress);
    }

    @Test
    public void potSnapshotDoesNotConsumeFoodAndCollectionStillConsumesItOnce() {
        PotFunctions functions = new PotFunctions(6_000);
        CookedFood waste = new CookedFood(5, "Waste", new ArrayList<>());
        functions.setCookedFood(waste);

        assertNotNull(functions.snapshotForSave().food);
        assertNotNull(functions.snapshotForSave().food);
        assertTrue(functions.gotFood());
        assertSame(waste, functions.getFood());
        assertNull(functions.getFood());
    }

    @Test
    public void malformedTypesCountsIdsStatesOrdersAndPositionsAreRejectedWithoutMutation() {
        assertRejected(save -> save.put("playerX", 120));
        assertRejected(save -> save.put("processCount", 6));
        assertRejected(save -> {
            save.put("heldItemType", PlayerInventory.INGREDIENT);
            save.put("heldItemId", 8);
            save.put("heldItemName", "invalid");
        });
        assertRejected(save -> save.put("pot_0_state", "BROKEN"));
        assertRejected(save -> {
            save.put("processCount", 1);
            save.put("process_0_recipe", "Mystery Soup");
            save.put("process_0_remaining", 10);
            save.put("process_0_limit", 60);
        });
        assertRejected(save -> {
            save.put("processCount", 1);
            save.put("process_0_recipe", "tomato_soup");
            save.put("process_0_remaining", 61);
            save.put("process_0_limit", 60);
        });
        assertRejected(save -> {
            save.put("processCount", 1);
            save.put("process_0_recipe", "tomato_soup");
            save.put("process_0_remaining", 30);
            save.put("process_0_limit", 0);
        });
        assertRejected(save -> {
            save.put("heldItemType", PlayerInventory.COOKED);
            save.put("heldItemId", 5);
            save.put("heldItemName", "Tomato Soup");
            save.put("heldItemRecipeId", "tomato_soup");
            save.put("heldItemIngredientsCount", 3);
            save.put("heldItemIngredient_0_id", 4);
            save.put("heldItemIngredient_0_name", "wrong");
            save.put("heldItemIngredient_1_id", 1);
            save.put("heldItemIngredient_1_name", "carrot");
            save.put("heldItemIngredient_2_id", 2);
            save.put("heldItemIngredient_2_name", "onion");
        });
        assertRejected(save -> save.put("playerX", 3000f));
        assertRejected(save -> save.put("score", -1));
        Map<String, Object> terminal = validLegacySave();
        terminal.put("deadProcessCount", 3);
        try { assertTrue(parse(terminal).terminal); }
        catch (GameSaveParser.InvalidSaveException e) { fail(e.getMessage()); }
        assertRejected(save -> {
            makeCooking(save, "Tomato Soup", 4, 0, 2);
            save.put("pot_0_cooking_progress", 6);
        });
        assertRejected(save -> {
            makeCooking(save, "Tomato Soup", 4, 0, 2);
            save.put("pot_0_cooking_progress", -1);
        });
    }

    @Test
    public void sameMajorSaveKeepsItsOriginalOrderTimingAcrossBalanceChanges() throws Exception {
        Map<String, Object> save = validLegacySave();
        save.put("gameVersion", "1.9.4");
        save.put("processCount", 1);
        save.put("process_0_recipe", "tomato_soup");
        save.put("process_0_remaining", 121);
        save.put("process_0_limit", 150);

        GameSaveSnapshot parsed = parse(save);
        assertEquals(121, parsed.orders.get(0).remainingSeconds);
        assertEquals(150, parsed.orders.get(0).limitSeconds);
    }

    @Test
    public void mapCountMismatchUnknownRecipeAndInvalidCookingContentsAreRejected() {
        Map<String, Object> mismatch = validLegacySave();
        mismatch.put("tableCount", 3);
        assertThrows(GameSaveParser.InvalidSaveException.class,
                () -> parse(mismatch));
        Map<String, Object> potMismatch = validLegacySave();
        potMismatch.put("potCount", 3);
        assertThrows(GameSaveParser.InvalidSaveException.class,
                () -> parse(potMismatch));

        Map<String, Object> unknownPotRecipe = validLegacySave();
        makeCooking(unknownPotRecipe, "Unknown Dish", 4, 0, 2);
        assertThrows(GameSaveParser.InvalidSaveException.class,
                () -> parse(unknownPotRecipe));

        Map<String, Object> mismatchedPotContents = validLegacySave();
        makeCooking(mismatchedPotContents, "Tomato Soup", 1, 1, 2);
        assertThrows(GameSaveParser.InvalidSaveException.class,
                () -> parse(mismatchedPotContents));
    }

    @Test
    public void fractionalLegacyPositionNormalizesOnlyToNearbyTraversableTile() throws Exception {
        Map<String, Object> fractional = validLegacySave();
        fractional.put("playerX", 60f);
        GameSaveSnapshot candidate = parse(fractional);
        assertEquals(0, candidate.playerX);
        assertEquals(120, candidate.playerY);

        Map<String, Object> blocked = validLegacySave();
        assertThrows(GameSaveParser.InvalidSaveException.class,
                () -> GameSaveParser.parse(blocked, 2, 2, 20, 9, 6, (x, y) -> false));
    }

    @Test
    public void invalidCandidateDoesNotMutateTheStoredLegacyMap() {
        Map<String, Object> stored = validLegacySave();
        stored.put("score", -5);
        Map<String, Object> before = new HashMap<>(stored);
        assertThrows(GameSaveParser.InvalidSaveException.class, () -> parse(stored));
        assertEquals(before, stored);
    }

    private static void assertRejected(java.util.function.Consumer<Map<String, Object>> mutation) {
        Map<String, Object> save = validLegacySave();
        mutation.accept(save);
        assertThrows(GameSaveParser.InvalidSaveException.class, () -> parse(save));
    }

    private static GameSaveSnapshot parse(Map<String, ?> values) throws GameSaveParser.InvalidSaveException {
        return GameSaveParser.parse(values, 2, 2, 20, 9, 6, (x, y) -> true);
    }

    private static Map<String, Object> validLegacySave() {
        Map<String, Object> save = new HashMap<>();
        save.put("gameVersion", "1.0.0");
        save.put("playerX", 120f);
        save.put("playerY", 120f);
        save.put("score", 10);
        save.put("deadProcessCount", 1);
        save.put("heldItemType", PlayerInventory.EMPTY);
        save.put("tableCount", 2);
        save.put("table_0_itemType", -1);
        save.put("table_1_itemType", -1);
        save.put("processCount", 0);
        save.put("potCount", 2);
        save.put("pot_0_state", Pot.State.EMPTY.name());
        save.put("pot_0_ingredientCount", 0);
        save.put("pot_1_state", Pot.State.EMPTY.name());
        save.put("pot_1_ingredientCount", 0);
        return save;
    }

    private static void makeCooking(Map<String, Object> save, String recipe,
                                    int recipeA, int recipeB, int recipeC) {
        save.put("pot_0_state", Pot.State.COOKING.name());
        save.put("pot_0_ingredientCount", 3);
        save.put("pot_0_ingredient_0_id", recipeA);
        save.put("pot_0_ingredient_1_id", recipeB);
        save.put("pot_0_ingredient_2_id", recipeC);
        save.put("pot_0_cooking_progress", 2);
        String stableId = "Waste".equals(recipe) ? "waste"
                : "Unknown Dish".equals(recipe) ? "unknown" : GameSaveSnapshot.recipeId(recipe);
        save.put("pot_0_recipe_id", stableId);
        List<Integer> recipeIds = recipe.equals("Tomato Soup")
                ? java.util.Arrays.asList(4, 0, 2) : new ArrayList<>();
        save.put("pot_0_recipe_ingredientCount", recipeIds.size());
        for (int i = 0; i < recipeIds.size(); i++) save.put("pot_0_recipe_ingredient_" + i + "_id", recipeIds.get(i));
    }
}
