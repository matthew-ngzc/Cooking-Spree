package com.game.cookingspree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Parses and validates the existing, unversioned GameSave key format without mutating a session. */
final class GameSaveParser {
    interface TileValidator { boolean isTraversable(int tileX, int tileY); }

    static final class InvalidSaveException extends Exception {
        InvalidSaveException(String message) { super(message); }
    }

    private final Map<String, ?> values;
    private final int tableCount;
    private final int potCount;
    private final int mapWidth;
    private final int mapHeight;
    private final int maxCookingTicks;
    private final TileValidator tileValidator;
    private final Map<String, List<Integer>> recipes = new HashMap<>();

    private GameSaveParser(Map<String, ?> values, int tableCount, int potCount,
                           int mapWidth, int mapHeight, int maxCookingTicks,
                           TileValidator tileValidator) {
        this.values = values;
        this.tableCount = tableCount;
        this.potCount = potCount;
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;
        this.maxCookingTicks = maxCookingTicks;
        this.tileValidator = tileValidator;
        for (Recipe recipe : Recipe.getDefaultRecipes()) {
            List<Integer> ids = new ArrayList<>();
            for (Ingredient ingredient : recipe.getIngredients()) ids.add(ingredient.getId());
            recipes.put(recipe.getName(), ids);
        }
    }

    static GameSaveSnapshot parse(Map<String, ?> values, int tableCount, int potCount,
                                  int mapWidth, int mapHeight, int maxCookingTicks,
                                  TileValidator tileValidator) throws InvalidSaveException {
        if (values == null || tileValidator == null) throw invalid("Save data is unavailable");
        return new GameSaveParser(values, tableCount, potCount, mapWidth, mapHeight,
                maxCookingTicks, tileValidator).parse();
    }

    private GameSaveSnapshot parse() throws InvalidSaveException {
        int[] position = normalizePosition(requiredFloat("playerX"), requiredFloat("playerY"));
        int score = requiredInt("score");
        int failures = requiredInt("deadProcessCount");
        if (score < 0) throw invalid("Score is negative");
        if (failures < 0 || failures >= 3) throw invalid("Failure count is terminal or invalid");

        GameSaveSnapshot.SavedItem held = readItem("heldItem", "heldItem", "heldItem", PlayerInventory.EMPTY);
        int savedTableCount = requiredInt("tableCount");
        if (savedTableCount != tableCount) throw invalid("Saved table count does not match this map");
        List<GameSaveSnapshot.SavedItem> tables = new ArrayList<>();
        for (int i = 0; i < savedTableCount; i++) {
            String prefix = "table_" + i + "_";
            int type = requiredInt(prefix + "itemType");
            if (type == -1) tables.add(null);
            else tables.add(readItem(prefix + "item", prefix + "item", prefix, Integer.MIN_VALUE));
        }

        int processCount = requiredInt("processCount");
        if (processCount < 0 || processCount > 5) throw invalid("Order count is out of range");
        List<GameSaveSnapshot.SavedOrder> orders = new ArrayList<>();
        for (int i = 0; i < processCount; i++) {
            String prefix = "process_" + i + "_";
            String recipe = requiredString(prefix + "recipe");
            if (!recipes.containsKey(recipe)) throw invalid("Unknown order recipe");
            int remaining = requiredInt(prefix + "remaining");
            int limit = requiredInt(prefix + "limit");
            if (limit < 60 || limit > 120 || remaining < 1 || remaining > limit) {
                throw invalid("Order time is out of range");
            }
            orders.add(new GameSaveSnapshot.SavedOrder(recipe, remaining, limit));
        }

        int savedPotCount = requiredInt("potCount");
        if (savedPotCount != potCount) throw invalid("Saved pot count does not match this map");
        List<GameSaveSnapshot.SavedPot> pots = new ArrayList<>();
        for (int i = 0; i < savedPotCount; i++) pots.add(readPot(i));
        return new GameSaveSnapshot(position[0] * Game.TILE_SIZE, position[1] * Game.TILE_SIZE,
                score, failures, held, tables, orders, pots);
    }

    private GameSaveSnapshot.SavedPot readPot(int index) throws InvalidSaveException {
        String prefix = "pot_" + index + "_";
        String stateValue = requiredString(prefix + "state");
        final Pot.State state;
        try { state = Pot.State.valueOf(stateValue); }
        catch (IllegalArgumentException e) { throw invalid("Unknown pot state"); }
        List<Integer> ingredients = readIds(prefix + "ingredientCount", prefix + "ingredient_", 3);
        switch (state) {
            case EMPTY:
                if (ingredients.size() > 2) throw invalid("An empty pot has too many ingredients");
                return new GameSaveSnapshot.SavedPot(state, ingredients, null, 0, null, Collections.emptyList());
            case COOKING: {
                if (ingredients.size() != 3) throw invalid("A cooking pot must contain three ingredients");
                int progress = requiredInt(prefix + "cooking_progress");
                if (progress < 0 || progress >= maxCookingTicks) throw invalid("Pot progress is out of range");
                String recipeName = requiredString(prefix + "recipe_name");
                List<Integer> recipeIngredients = readIds(prefix + "recipe_ingredientCount", prefix + "recipe_ingredient_", 3);
                validateRecipeIngredients(recipeName, recipeIngredients, true);
                if ("Waste".equals(recipeName) && !recipeIngredients.isEmpty()) {
                    throw invalid("Waste cooking state has unexpected recipe ingredients");
                }
                if (!"Waste".equals(recipeName) && !sameIngredients(ingredients, recipeIngredients)) {
                    throw invalid("Pot contents do not match the cooking recipe");
                }
                return new GameSaveSnapshot.SavedPot(state, ingredients, null, progress,
                        recipeName, recipeIngredients);
            }
            case DONE: {
                if (!ingredients.isEmpty()) throw invalid("A finished pot still has uncooked ingredients");
                int id = requiredInt(prefix + "food_id");
                String name = requiredString(prefix + "food_name");
                List<Integer> madeWith = readIds(prefix + "food_ingredientCount", prefix + "food_ingredient_", 3);
                GameSaveSnapshot.SavedItem food = new GameSaveSnapshot.SavedItem(
                        PlayerInventory.COOKED, id, name, madeWith);
                validateItem(food);
                return new GameSaveSnapshot.SavedPot(state, ingredients, food, 0, null, Collections.emptyList());
            }
            default: throw invalid("Unknown pot state");
        }
    }

    private GameSaveSnapshot.SavedItem readItem(String typePrefix, String metadataPrefix,
                                                String ingredientsPrefix, int emptyType)
            throws InvalidSaveException {
        int type = requiredInt(typePrefix + "Type");
        if (type == emptyType) return null;
        if (type != PlayerInventory.INGREDIENT && type != PlayerInventory.COOKED) {
            throw invalid("Unknown item type");
        }
        int id = requiredInt(metadataPrefix + "Id");
        String name = requiredString(metadataPrefix + "Name");
        List<Integer> ingredients = Collections.emptyList();
        if (type == PlayerInventory.COOKED) {
            String countKey = ingredientsPrefix.equals("heldItem")
                    ? ingredientsPrefix + "IngredientsCount" : ingredientsPrefix + "ingredientsCount";
            if (values.containsKey(countKey)) {
                ingredients = readIds(countKey, ingredientsPrefix.equals("heldItem")
                        ? ingredientsPrefix + "Ingredient_" : ingredientsPrefix + "ingredient_", 3);
                String ingredientPrefix = ingredientsPrefix.equals("heldItem")
                        ? ingredientsPrefix + "Ingredient_" : ingredientsPrefix + "ingredient_";
                for (int i = 0; i < ingredients.size(); i++) {
                    String savedName = requiredString(ingredientPrefix + i + "_name");
                    if (!GameSaveSnapshot.ingredientName(ingredients.get(i)).equals(savedName)) {
                        throw invalid("Item ingredient name does not match its ID");
                    }
                }
            }
        }
        GameSaveSnapshot.SavedItem item = new GameSaveSnapshot.SavedItem(type, id, name, ingredients);
        validateItem(item);
        return item;
    }

    private void validateItem(GameSaveSnapshot.SavedItem item) throws InvalidSaveException {
        if (item.type == PlayerInventory.INGREDIENT) {
            if (item.id < 0 || item.id > 4 || !GameSaveSnapshot.ingredientName(item.id).equals(item.name)) {
                throw invalid("Invalid ingredient item");
            }
            if (!item.ingredients.isEmpty()) throw invalid("An ingredient cannot contain a recipe list");
            return;
        }
        if (item.id != 5) throw invalid("Invalid cooked food id");
        validateRecipeIngredients(item.name, item.ingredients, true);
    }

    private void validateRecipeIngredients(String name, List<Integer> ingredients, boolean allowWaste)
            throws InvalidSaveException {
        List<Integer> expected = recipes.get(name);
        if (expected == null) {
            if (allowWaste && "Waste".equals(name) && ingredients.isEmpty()) return;
            throw invalid("Unknown recipe name");
        }
        if (!sameIngredients(expected, ingredients)) throw invalid("Cooked food ingredients do not match its recipe");
    }

    private List<Integer> readIds(String countKey, String itemPrefix, int maximum)
            throws InvalidSaveException {
        int count = requiredInt(countKey);
        if (count < 0 || count > maximum) throw invalid("Ingredient count is out of range");
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int id = requiredInt(itemPrefix + i + "_id");
            if (id < 0 || id > 4) throw invalid("Unknown ingredient id");
            ids.add(id);
        }
        return ids;
    }

    private int[] normalizePosition(float x, float y) throws InvalidSaveException {
        if (!Float.isFinite(x) || !Float.isFinite(y)) throw invalid("Player position is not finite");
        int nearestX = Math.round(x / Game.TILE_SIZE);
        int nearestY = Math.round(y / Game.TILE_SIZE);
        int bestX = -1, bestY = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int ty = nearestY - 1; ty <= nearestY + 1; ty++) {
            for (int tx = nearestX - 1; tx <= nearestX + 1; tx++) {
                if (tx < 0 || tx >= mapWidth || ty < 0 || ty >= mapHeight) continue;
                float candidateX = tx * Game.TILE_SIZE;
                float candidateY = ty * Game.TILE_SIZE;
                float dx = Math.abs(x - candidateX), dy = Math.abs(y - candidateY);
                if (dx > Game.TILE_SIZE / 2f || dy > Game.TILE_SIZE / 2f || !tileValidator.isTraversable(tx, ty)) continue;
                float distance = dx * dx + dy * dy;
                if (distance < bestDistance) { bestDistance = distance; bestX = tx; bestY = ty; }
            }
        }
        if (bestX < 0) throw invalid("Player position is not on a nearby traversable tile");
        return new int[]{bestX, bestY};
    }

    private float requiredFloat(String key) throws InvalidSaveException {
        Object value = required(key);
        if (!(value instanceof Float)) throw invalid("Invalid type for " + key);
        return (Float) value;
    }

    private int requiredInt(String key) throws InvalidSaveException {
        Object value = required(key);
        if (!(value instanceof Integer)) throw invalid("Invalid type for " + key);
        return (Integer) value;
    }

    private String requiredString(String key) throws InvalidSaveException {
        Object value = required(key);
        if (!(value instanceof String) || ((String) value).isEmpty()) throw invalid("Invalid string for " + key);
        return (String) value;
    }

    private Object required(String key) throws InvalidSaveException {
        if (!values.containsKey(key)) throw invalid("Missing field " + key);
        return values.get(key);
    }

    private static boolean sameIngredients(List<Integer> left, List<Integer> right) {
        if (left.size() != right.size()) return false;
        Map<Integer, Integer> counts = new HashMap<>();
        for (int id : left) counts.put(id, counts.getOrDefault(id, 0) + 1);
        for (int id : right) counts.put(id, counts.getOrDefault(id, 0) - 1);
        for (int count : counts.values()) if (count != 0) return false;
        return true;
    }

    private static InvalidSaveException invalid(String message) { return new InvalidSaveException(message); }
}
