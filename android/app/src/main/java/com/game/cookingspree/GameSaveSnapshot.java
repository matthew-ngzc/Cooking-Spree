package com.game.cookingspree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable in-memory representation written using the existing GameSave keys. */
final class GameSaveSnapshot {
    final int playerX;
    final int playerY;
    final int score;
    final int deadProcessCount;
    final SavedItem heldItem;
    final List<SavedItem> tableItems;
    final List<SavedOrder> orders;
    final List<SavedPot> pots;

    GameSaveSnapshot(int playerX, int playerY, int score, int deadProcessCount,
                     SavedItem heldItem, List<SavedItem> tableItems,
                     List<SavedOrder> orders, List<SavedPot> pots) {
        this.playerX = playerX;
        this.playerY = playerY;
        this.score = score;
        this.deadProcessCount = deadProcessCount;
        this.heldItem = heldItem;
        this.tableItems = immutableNullableCopy(tableItems);
        this.orders = Collections.unmodifiableList(new ArrayList<>(orders));
        this.pots = Collections.unmodifiableList(new ArrayList<>(pots));
    }

    static GameSaveSnapshot capture(Game game, GameManager manager, PlayerInventory inventory) {
        Player player = game.getPlayer();
        int playerX = player.getLogicalSaveX();
        int playerY = player.getLogicalSaveY();
        GameSaveSnapshot.SavedItem held = saveItem(inventory.getHeld(), inventory.checkHeldType());

        List<SavedItem> tables = new ArrayList<>();
        for (Table table : game.getTables()) tables.add(saveItem(table.getItemOnTable(), itemType(table.getItemOnTable())));

        List<SavedOrder> orders = new ArrayList<>();
        for (Order order : manager.getActiveProcesses()) {
            if (!order.isComplete() && !order.isDead()) {
                orders.add(new SavedOrder(order.getRecipe().getName(), order.getTimeRemaining(), order.getTimeLimit()));
            }
        }

        List<SavedPot> pots = new ArrayList<>();
        for (Pot pot : game.getPots()) pots.add(pot.snapshotForSave());
        return new GameSaveSnapshot(playerX, playerY, manager.getScore(), manager.getDeadProcessCount(),
                held, tables, orders, pots);
    }

    private static int itemType(FoodItem item) {
        if (item == null) return PlayerInventory.EMPTY;
        if (item instanceof Ingredient) return PlayerInventory.INGREDIENT;
        if (item instanceof CookedFood) return PlayerInventory.COOKED;
        return PlayerInventory.INVALID;
    }

    private static SavedItem saveItem(FoodItem item, int type) {
        if (item == null) return null;
        if (type == PlayerInventory.INVALID) throw new IllegalStateException("Unsupported saved item type");
        List<Integer> ingredients = new ArrayList<>();
        if (item instanceof CookedFood) {
            for (Ingredient ingredient : ((CookedFood) item).getMadeWith()) ingredients.add(ingredient.getId());
        }
        return new SavedItem(type, item.getId(), item.getName(), ingredients);
    }

    Map<String, Object> toLegacyValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("playerX", (float) playerX);
        values.put("playerY", (float) playerY);
        values.put("score", score);
        values.put("deadProcessCount", deadProcessCount);
        writeItem(values, "heldItem", "heldItem", "heldItem", heldItem, PlayerInventory.EMPTY);

        values.put("tableCount", tableItems.size());
        for (int i = 0; i < tableItems.size(); i++) {
            writeItem(values, "table_" + i + "_item", "table_" + i + "_item", "table_" + i + "_",
                    tableItems.get(i), -1);
        }

        values.put("processCount", orders.size());
        for (int i = 0; i < orders.size(); i++) {
            SavedOrder order = orders.get(i);
            values.put("process_" + i + "_recipe", order.recipeName);
            values.put("process_" + i + "_remaining", order.remainingSeconds);
            values.put("process_" + i + "_limit", order.limitSeconds);
        }

        values.put("potCount", pots.size());
        for (int i = 0; i < pots.size(); i++) {
            SavedPot pot = pots.get(i);
            String prefix = "pot_" + i + "_";
            values.put(prefix + "state", pot.state.name());
            values.put(prefix + "ingredientCount", pot.ingredients.size());
            for (int j = 0; j < pot.ingredients.size(); j++) {
                values.put(prefix + "ingredient_" + j + "_id", pot.ingredients.get(j));
            }
            if (pot.state == Pot.State.DONE) {
                writeFood(values, prefix + "food_", pot.food);
            } else if (pot.state == Pot.State.COOKING) {
                values.put(prefix + "cooking_progress", pot.cookingProgress);
                values.put(prefix + "recipe_name", pot.recipeName);
                values.put(prefix + "recipe_ingredientCount", pot.recipeIngredients.size());
                for (int j = 0; j < pot.recipeIngredients.size(); j++) {
                    values.put(prefix + "recipe_ingredient_" + j + "_id", pot.recipeIngredients.get(j));
                }
            }
        }
        return values;
    }

    private static void writeItem(Map<String, Object> values, String metadataPrefix,
                                  String typePrefix, String ingredientsPrefix,
                                  SavedItem item, int emptyType) {
        if (item == null) {
            values.put(typePrefix + "Type", emptyType);
            return;
        }
        values.put(typePrefix + "Type", item.type);
        values.put(metadataPrefix + "Id", item.id);
        values.put(metadataPrefix + "Name", item.name);
        if (item.type == PlayerInventory.COOKED) {
            String countKey = ingredientsPrefix.equals("heldItem")
                    ? ingredientsPrefix + "IngredientsCount" : ingredientsPrefix + "ingredientsCount";
            values.put(countKey, item.ingredients.size());
            for (int i = 0; i < item.ingredients.size(); i++) {
                String ingredientPrefix = ingredientsPrefix.equals("heldItem")
                        ? ingredientsPrefix + "Ingredient_" + i + "_"
                        : ingredientsPrefix + "ingredient_" + i + "_";
                values.put(ingredientPrefix + "id", item.ingredients.get(i));
                values.put(ingredientPrefix + "name", ingredientName(item.ingredients.get(i)));
            }
        }
    }

    private static void writeFood(Map<String, Object> values, String prefix, SavedItem food) {
        values.put(prefix + "id", food.id);
        values.put(prefix + "name", food.name);
        values.put(prefix + "ingredientCount", food.ingredients.size());
        for (int i = 0; i < food.ingredients.size(); i++) {
            values.put(prefix + "ingredient_" + i + "_id", food.ingredients.get(i));
        }
    }

    static String ingredientName(int id) {
        switch (id) {
            case 0: return "carrot";
            case 1: return "potato";
            case 2: return "onion";
            case 3: return "cabbage";
            case 4: return "tomato";
            default: throw new IllegalArgumentException("Unknown ingredient id: " + id);
        }
    }

    private static <T> List<T> immutableNullableCopy(List<T> items) {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    static final class SavedItem {
        final int type;
        final int id;
        final String name;
        final List<Integer> ingredients;

        SavedItem(int type, int id, String name, List<Integer> ingredients) {
            this.type = type;
            this.id = id;
            this.name = name;
            this.ingredients = Collections.unmodifiableList(new ArrayList<>(ingredients));
        }
    }

    static final class SavedOrder {
        final String recipeName;
        final int remainingSeconds;
        final int limitSeconds;
        SavedOrder(String recipeName, int remainingSeconds, int limitSeconds) {
            this.recipeName = recipeName;
            this.remainingSeconds = remainingSeconds;
            this.limitSeconds = limitSeconds;
        }
    }

    static final class SavedPot {
        final Pot.State state;
        final List<Integer> ingredients;
        final SavedItem food;
        final int cookingProgress;
        final String recipeName;
        final List<Integer> recipeIngredients;

        SavedPot(Pot.State state, List<Integer> ingredients, SavedItem food,
                 int cookingProgress, String recipeName, List<Integer> recipeIngredients) {
            this.state = state;
            this.ingredients = Collections.unmodifiableList(new ArrayList<>(ingredients));
            this.food = food;
            this.cookingProgress = cookingProgress;
            this.recipeName = recipeName;
            this.recipeIngredients = Collections.unmodifiableList(new ArrayList<>(recipeIngredients));
        }
    }
}
