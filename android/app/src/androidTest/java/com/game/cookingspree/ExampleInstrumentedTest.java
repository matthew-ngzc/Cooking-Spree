package com.game.cookingspree;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.Activity;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.view.View;
import android.widget.Button;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.game.cookingspree", appContext.getPackageName());
    }

    @Test
    public void tutorialEntryComposesOneSessionAndClosesOnRepeatedExit() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context appContext = instrumentation.getTargetContext();
        for (int attempt = 0; attempt < 2; attempt++) {
            Intent intent = new Intent(appContext, TutorialActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity launched = instrumentation.startActivitySync(intent);
            assertTrue(launched instanceof TutorialActivity);
            GameActivity tutorial = (GameActivity) launched;
            assertEquals(1, tutorial.getSessionCompositionCountForTest());
            assertNotNull(tutorial.game);
            assertNotNull(tutorial.gameManager);
            GameManager manager = tutorial.gameManager;

            instrumentation.runOnMainSync(tutorial::finish);
            instrumentation.waitForIdleSync();
            long deadline = SystemClock.elapsedRealtime() + 5_000;
            while (!manager.isClosed() && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(50);
            }
            assertTrue("manager should close after tutorial exit", manager.isClosed());
        }
    }

    @Test
    public void tutorialMovementStepAllowsMovementOnlyAndSkipStartsOneSession() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        TutorialActivity tutorial = (TutorialActivity) instrumentation.startActivitySync(
                new Intent(instrumentation.getTargetContext(), TutorialActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        assertFalse(tutorial.gameManager.isRunning());
        assertTrue(tutorial.gameManager.getActiveProcesses().isEmpty());
        assertFalse(tutorial.pauseState.isMovementAllowed());

        instrumentation.runOnMainSync(() -> ((Button) tutorial.findViewById(R.id.tutorialNextButton)).performClick());
        assertFalse(tutorial.gameManager.isRunning());
        assertTrue(tutorial.pauseState.isMovementAllowed());
        assertTrue(tutorial.gameManager.getActiveProcesses().isEmpty());

        instrumentation.runOnMainSync(() -> ((Button) tutorial.findViewById(R.id.tutorialSkipButton)).performClick());
        assertTrue(tutorial.gameManager.isRunning());
        assertTrue(tutorial.pauseState.isRunning());
        assertEquals(1, tutorial.getSessionCompositionCountForTest());
        finishAndAwaitClosed(instrumentation, tutorial);
    }

    @Test
    public void backgroundForegroundPreservesManualPauseAndResumesRunningGame() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        GameActivity activity = launchGame(instrumentation);
        instrumentation.runOnMainSync(() -> ((Button) activity.findViewById(R.id.togglePauseButton)).performClick());
        instrumentation.runOnMainSync(() -> instrumentation.callActivityOnPause(activity));
        instrumentation.runOnMainSync(() -> instrumentation.callActivityOnResume(activity));
        assertFalse(activity.gameManager.isRunning());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.pauseMenu).getVisibility());
        instrumentation.runOnMainSync(() -> ((Button) activity.findViewById(R.id.btnResume)).performClick());
        assertTrue(activity.gameManager.isRunning());

        instrumentation.runOnMainSync(() -> instrumentation.callActivityOnPause(activity));
        instrumentation.runOnMainSync(() -> instrumentation.callActivityOnResume(activity));
        assertTrue(activity.gameManager.isRunning());
        finishAndAwaitClosed(instrumentation, activity);
    }

    @Test
    public void activeCookAndFetchPreserveRemainingWorkAcrossTenSecondPause() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        GameActivity activity = launchGame(instrumentation);
        Pot pot = instrumentationCall(instrumentation, activity::startCookForTest);
        IngredientFetchWorker fetcher = activity.getIngredientFetcherForTest();
        java.util.List<Ingredient> availableBeforeFetch = fetcher.getAvailableList();
        java.util.List<Ingredient> usedBeforeFetch = fetcher.getUsedListForTest();
        instrumentation.runOnMainSync(activity::startFetchForTest);
        await(() -> fetcher.isFetchingForTest()
                        && pot.getPotFunctions().getCookProgress() > 0,
                3_000, "cook and ingredient fetch did not both start");

        instrumentation.runOnMainSync(activity.gameManager::pauseGame);
        int cookProgressAtPause = pot.getPotFunctions().getCookProgress();
        int ordersAtPause = activity.gameManager.getActiveProcesses().size();
        java.util.List<Integer> orderTimesAtPause = new java.util.ArrayList<>();
        for (Order order : activity.gameManager.getActiveProcesses()) orderTimesAtPause.add(order.getTimeRemaining());
        SystemClock.sleep(10_000);

        assertEquals(cookProgressAtPause, pot.getPotFunctions().getCookProgress());
        assertFalse(pot.getPotFunctions().gotFood());
        assertEquals(availableBeforeFetch, fetcher.getAvailableList());
        assertEquals(usedBeforeFetch, fetcher.getUsedListForTest());
        assertEquals(ordersAtPause, activity.gameManager.getActiveProcesses().size());
        java.util.List<Integer> orderTimesAfterPause = new java.util.ArrayList<>();
        for (Order order : activity.gameManager.getActiveProcesses()) orderTimesAfterPause.add(order.getTimeRemaining());
        assertEquals(orderTimesAtPause, orderTimesAfterPause);

        instrumentation.runOnMainSync(activity.gameManager::resumeGame);
        await(() -> pot.getPotFunctions().gotFood(), 8_000, "pot did not complete after resume");
        await(() -> !usedBeforeFetch.equals(fetcher.getUsedListForTest()), 5_000,
                "ingredient fetch did not finish after resume");
        finishAndAwaitClosed(instrumentation, activity);
    }

    @Test
    public void gameExitDuringActiveFetchClosesOwnersWithoutLateMutationOrCallback() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        GameActivity activity = launchGame(instrumentation);
        GameManager manager = activity.gameManager;
        IngredientFetchWorker fetcher = activity.getIngredientFetcherForTest();
        PotThreadPool potPool = activity.getPotThreadPoolForTest();
        java.util.List<Ingredient> availableBefore = fetcher.getAvailableList();
        java.util.List<Ingredient> usedBefore = fetcher.getUsedListForTest();
        java.util.List<String> processesBefore = processSnapshot(manager);

        instrumentation.runOnMainSync(activity::startFetchForTest);
        await(() -> fetcher.isFetchingForTest(), 2_000, "ingredient fetch did not start");
        instrumentation.runOnMainSync(activity::finish);
        await(() -> manager.isClosed() && fetcher.isClosed() && potPool.isClosed(),
                5_000, "activity owners did not close within the bound");
        int callbacksAtClose = activity.getDeliveredSessionCallbacksForTest();

        // Wait past both the fetch duration and the manager's maximum 13-second spawn delay.
        SystemClock.sleep(13_300);
        assertEquals(availableBefore, fetcher.getAvailableList());
        assertEquals(usedBefore, fetcher.getUsedListForTest());
        assertEquals(processesBefore, processSnapshot(manager));
        assertEquals(callbacksAtClose, activity.getDeliveredSessionCallbacksForTest());

        GameActivity reentered = launchGame(instrumentation);
        assertEquals(1, reentered.getSessionCompositionCountForTest());
        finishAndAwaitClosed(instrumentation, reentered);
    }

    @Test
    public void gameExitDuringActiveCookClosesOwnersWithoutLateFoodOrCallback() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        GameActivity activity = launchGame(instrumentation);
        GameManager manager = activity.gameManager;
        IngredientFetchWorker fetcher = activity.getIngredientFetcherForTest();
        PotThreadPool potPool = activity.getPotThreadPoolForTest();
        Pot pot = instrumentationCall(instrumentation, activity::startCookForTest);
        await(() -> potPool.activeTaskCountForTest() == 1
                        && pot.getPotFunctions().getRecipeCooking() != null,
                2_000, "pot cook did not become active");
        instrumentation.runOnMainSync(activity::finish);
        await(() -> manager.isClosed() && fetcher.isClosed() && potPool.isClosed(),
                5_000, "activity owners did not close within the bound");
        int callbacksAtClose = activity.getDeliveredSessionCallbacksForTest();

        // Pot cooking takes six seconds. Wait past completion to detect late food publication.
        SystemClock.sleep(6_300);
        assertFalse(pot.getPotFunctions().gotFood());
        assertEquals(3, pot.getPotFunctions().getIngredientsInside().size());
        assertEquals(Pot.State.COOKING.name(), pot.getState());
        assertEquals(callbacksAtClose, activity.getDeliveredSessionCallbacksForTest());

        GameActivity reentered = launchGame(instrumentation);
        assertEquals(1, reentered.getSessionCompositionCountForTest());
        finishAndAwaitClosed(instrumentation, reentered);
    }

    @Test
    public void validAndRejectedLegacyLoadsAreIsolatedBeforeWorkersResume() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context appContext = instrumentation.getTargetContext();
        SharedPreferences saves = appContext.getSharedPreferences("GameSave", Context.MODE_PRIVATE);
        saves.edit().clear().commit();

        GameActivity source = launchGame(instrumentation);
        GameSaveSnapshot captured = instrumentationCall(instrumentation, source::captureSnapshotForTest);
        GameSaveSnapshot valid = new GameSaveSnapshot(captured.playerX, captured.playerY, 321,
                captured.deadProcessCount, captured.heldItem, captured.tableItems,
                captured.orders, captured.pots);
        finishAndAwaitClosed(instrumentation, source);
        writeLegacyFixture(saves, valid.toLegacyValues());

        GameActivity loaded = launchLoadGame(instrumentation);
        assertTrue("valid candidate should load", loaded.loadSucceededForTest());
        assertTrue("restore must happen while LOAD blocks new worker mutations",
                loaded.restoreWasIsolatedForTest());
        assertEquals(321, loaded.gameManager.getScore());
        finishAndAwaitClosed(instrumentation, loaded);

        GameActivity invalidSource = launchGame(instrumentation);
        GameSaveSnapshot invalidCaptured = instrumentationCall(instrumentation,
                invalidSource::captureSnapshotForTest);
        java.util.Map<String, Object> invalidValues = new java.util.HashMap<>(invalidCaptured.toLegacyValues());
        invalidValues.put("score", -1);
        finishAndAwaitClosed(instrumentation, invalidSource);
        writeLegacyFixture(saves, invalidValues);
        java.util.Map<String, ?> persistedBeforeLoad = new java.util.HashMap<>(saves.getAll());

        GameActivity rejected = launchLoadGame(instrumentation);
        assertFalse("invalid candidate must be rejected", rejected.loadSucceededForTest());
        assertEquals("rejected load must leave the fresh session unchanged", 0,
                rejected.gameManager.getScore());
        assertEquals("rejected save data must remain available for recovery",
                persistedBeforeLoad, saves.getAll());
        finishAndAwaitClosed(instrumentation, rejected);
        saves.edit().clear().commit();
    }

    @Test
    public void richLegacyRunRoundTripsPlayerTableOrderAndPartialPots() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        SharedPreferences saves = instrumentation.getTargetContext()
                .getSharedPreferences("GameSave", Context.MODE_PRIVATE);
        saves.edit().clear().commit();
        GameActivity source = launchGame(instrumentation);
        GameSaveSnapshot empty = instrumentationCall(instrumentation, source::captureSnapshotForTest);

        java.util.List<GameSaveSnapshot.SavedItem> tables = new java.util.ArrayList<>(empty.tableItems);
        assertFalse("map fixture needs at least one table", tables.isEmpty());
        tables.set(0, new GameSaveSnapshot.SavedItem(PlayerInventory.COOKED, 5, "Tomato Soup",
                java.util.Arrays.asList(4, 0, 2)));
        java.util.List<GameSaveSnapshot.SavedPot> pots = java.util.Arrays.asList(
                new GameSaveSnapshot.SavedPot(Pot.State.EMPTY, java.util.Collections.singletonList(1),
                        null, 0, null, java.util.Collections.emptyList()),
                new GameSaveSnapshot.SavedPot(Pot.State.EMPTY, java.util.Arrays.asList(4, 4),
                        null, 0, null, java.util.Collections.emptyList()));
        java.util.List<GameSaveSnapshot.SavedOrder> orders = java.util.Collections.singletonList(
                new GameSaveSnapshot.SavedOrder("Tomato Soup", 47, 90));
        GameSaveSnapshot rich = new GameSaveSnapshot(empty.playerX, empty.playerY, 321, 2,
                new GameSaveSnapshot.SavedItem(PlayerInventory.INGREDIENT, 4, "tomato",
                        java.util.Collections.emptyList()), tables, orders, pots);
        finishAndAwaitClosed(instrumentation, source);
        writeLegacyFixture(saves, rich.toLegacyValues());

        GameActivity loaded = launchLoadGame(instrumentation);
        assertTrue(loaded.loadSucceededForTest());
        assertTrue(loaded.restoreWasIsolatedForTest());
        assertEquals(321, loaded.gameManager.getScore());
        assertEquals(2, loaded.gameManager.getDeadProcessCount());
        assertEquals("tomato", loaded.game.getPlayer().getInventory().getHeld().getName());
        assertEquals("Tomato Soup", loaded.game.getTables().get(0).getItemOnTable().getName());
        assertEquals(1, loaded.gameManager.getActiveProcesses().size());
        Order order = loaded.gameManager.getActiveProcesses().get(0);
        assertEquals("Tomato Soup", order.getRecipe().getName());
        assertEquals(90, order.getTimeLimit());
        assertTrue(order.getTimeRemaining() <= 47 && order.getTimeRemaining() >= 46);
        assertEquals(java.util.Arrays.asList(1), ingredientIds(loaded.game.getPots().get(0).getInPot()));
        assertEquals(java.util.Arrays.asList(4, 4), ingredientIds(loaded.game.getPots().get(1).getInPot()));
        finishAndAwaitClosed(instrumentation, loaded);
        saves.edit().clear().commit();
    }

    @Test
    public void nearCompleteCookingSurvivesLoadSaveReloadThenFinishesOnce() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        SharedPreferences saves = instrumentation.getTargetContext()
                .getSharedPreferences("GameSave", Context.MODE_PRIVATE);
        saves.edit().clear().commit();
        GameActivity source = launchGame(instrumentation);
        GameSaveSnapshot empty = instrumentationCall(instrumentation, source::captureSnapshotForTest);
        GameSaveSnapshot.SavedPot cooking = new GameSaveSnapshot.SavedPot(Pot.State.COOKING,
                java.util.Arrays.asList(4, 0, 2), null, 5, "Tomato Soup",
                java.util.Arrays.asList(4, 0, 2));
        GameSaveSnapshot fixture = new GameSaveSnapshot(empty.playerX, empty.playerY, 25, 1,
                empty.heldItem, empty.tableItems, empty.orders,
                java.util.Arrays.asList(cooking, empty.pots.get(1)));
        finishAndAwaitClosed(instrumentation, source);
        writeLegacyFixture(saves, fixture.toLegacyValues());

        GameActivity firstLoad = launchLoadGame(instrumentation, true);
        assertTrue(firstLoad.loadSucceededForTest());
        Pot firstPot = firstLoad.game.getPots().get(0);
        assertEquals(Pot.State.COOKING.name(), firstPot.getState());
        assertEquals("Tomato Soup", firstPot.getPotFunctions().getRecipeCooking().getName());
        assertEquals(5, firstPot.getPotFunctions().getCookProgress());
        assertTrue(firstLoad.saveGameStateForTest());
        assertEquals(5, saves.getInt("pot_0_cooking_progress", -1));
        assertEquals("Tomato Soup", saves.getString("pot_0_recipe_name", null));
        finishAndAwaitClosed(instrumentation, firstLoad);

        GameActivity secondLoad = launchLoadGame(instrumentation, true);
        assertTrue(secondLoad.loadSucceededForTest());
        Pot resumedPot = secondLoad.game.getPots().get(0);
        assertEquals(5, resumedPot.getPotFunctions().getCookProgress());
        assertEquals("Tomato Soup", resumedPot.getPotFunctions().getRecipeCooking().getName());
        instrumentation.runOnMainSync(secondLoad.gameManager::resumeGame);
        await(() -> Pot.State.DONE.name().equals(resumedPot.getState())
                        && resumedPot.getPotFunctions().gotFood(),
                3_000, "near-complete restored cooking did not finish after resume");
        CookedFood collected = resumedPot.getFood();
        assertEquals("Tomato Soup", collected.getName());
        assertNull("finished food should be collectible only once", resumedPot.getFood());
        finishAndAwaitClosed(instrumentation, secondLoad);
        saves.edit().clear().commit();
    }

    @Test
    public void failedSnapshotPreservesPriorSaveAndLoadedTerminalRunClearsIt() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        SharedPreferences saves = instrumentation.getTargetContext()
                .getSharedPreferences("GameSave", Context.MODE_PRIVATE);
        saves.edit().clear().commit();
        GameActivity activity = launchGame(instrumentation);
        GameSaveSnapshot valid = instrumentationCall(instrumentation, activity::captureSnapshotForTest);
        writeLegacyFixture(saves, valid.toLegacyValues());
        java.util.Map<String, ?> beforeFailedSave = new java.util.HashMap<>(saves.getAll());
        Pot inconsistent = activity.game.getPots().get(0);
        instrumentation.runOnMainSync(() -> {
            activity.gameManager.pauseGame();
            inconsistent.setState(Pot.State.DONE.name()); // No food: snapshot must reject this live state.
            assertFalse("inconsistent live capture should fail", activity.saveGameStateForTest());
        });
        assertEquals(beforeFailedSave, saves.getAll());
        finishAndAwaitClosed(instrumentation, activity);

        GameActivity loadSource = launchGame(instrumentation);
        GameSaveSnapshot candidate = instrumentationCall(instrumentation, loadSource::captureSnapshotForTest);
        finishAndAwaitClosed(instrumentation, loadSource);
        writeLegacyFixture(saves, candidate.toLegacyValues());
        GameActivity loaded = launchLoadGame(instrumentation);
        assertTrue(loaded.loadSucceededForTest());
        instrumentation.runOnMainSync(() -> loaded.gameManager.setDeadProcessCount(3));
        await(() -> saves.getAll().isEmpty(), 2_000, "loaded game over should clear GameSave");
        assertTrue("later load must have no candidate", saves.getAll().isEmpty());
        finishAndAwaitClosed(instrumentation, loaded);
    }

    @Test
    public void repeatedDonePotSnapshotsDoNotConsumeFood() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context appContext = instrumentation.getTargetContext();
        SharedPreferences saves = appContext.getSharedPreferences("GameSave", Context.MODE_PRIVATE);
        saves.edit().clear().commit();
        GameActivity activity = launchGame(instrumentation);
        Recipe recipe = Recipe.getDefaultRecipes().get(0);
        Pot pot = activity.game.getPots().get(0);
        instrumentation.runOnMainSync(() -> {
            activity.gameManager.pauseGame();
            pot.getPotFunctions().setCookedFood(new CookedFood(5, recipe.getName(), recipe.getIngredients()));
            pot.setState(Pot.State.DONE.name());
            assertTrue(activity.saveGameStateForTest());
            assertTrue(activity.saveGameStateForTest());
        });
        assertEquals(Pot.State.DONE.name(), pot.getState());
        assertNotNull("saved food remains collectible", pot.getFood());
        assertNull("food is collected only once", pot.getFood());
        finishAndAwaitClosed(instrumentation, activity);
        saves.edit().clear().commit();
    }

    private static GameActivity launchLoadGame(android.app.Instrumentation instrumentation) {
        return launchLoadGame(instrumentation, false);
    }

    private static GameActivity launchLoadGame(android.app.Instrumentation instrumentation,
                                                boolean pauseAfterLoadForTest) {
        Intent intent = new Intent(instrumentation.getTargetContext(), GameActivity.class)
                .putExtra("loadSavedGame", true)
                .putExtra("pauseAfterLoadForTest", pauseAfterLoadForTest)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity launched = instrumentation.startActivitySync(intent);
        assertTrue(launched instanceof GameActivity);
        return (GameActivity) launched;
    }

    private static void writeLegacyFixture(SharedPreferences preferences, java.util.Map<String, ?> values) {
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (java.util.Map.Entry<String, ?> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
            else if (value instanceof Float) editor.putFloat(entry.getKey(), (Float) value);
            else if (value instanceof String) editor.putString(entry.getKey(), (String) value);
            else throw new AssertionError("Unsupported fixture type " + value.getClass());
        }
        assertTrue("fixture write should complete", editor.commit());
    }

    private static java.util.List<Integer> ingredientIds(java.util.List<Ingredient> ingredients) {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        for (Ingredient ingredient : ingredients) ids.add(ingredient.getId());
        return ids;
    }

    @Test
    public void repeatedActualSurfaceDetachAndAttachKeepsOneRenderLoopAndBoundedExit() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        GameActivity activity = launchGame(instrumentation);
        GameManager manager = activity.gameManager;
        GameView gameView = activity.getGameViewForTest();
        ViewGroup parent = (ViewGroup) gameView.getParent();
        ViewGroup.LayoutParams layoutParams = gameView.getLayoutParams();
        await(() -> gameView.activeRenderLoopsForTest() == 1
                        && gameView.surfaceCreateCallbacksForTest() >= 1,
                3_000, "initial surface render loop did not start");

        for (int cycle = 0; cycle < 5; cycle++) {
            int destroyedBefore = gameView.surfaceDestroyCallbacksForTest();
            int startsBefore = gameView.renderLoopStartsForTest();
            long destroyStart = SystemClock.elapsedRealtime();
            instrumentation.runOnMainSync(() -> parent.removeView(gameView));
            await(() -> gameView.surfaceDestroyCallbacksForTest() > destroyedBefore
                            && gameView.activeRenderLoopsForTest() == 0,
                    2_000, "surface destruction did not stop its render loop");
            assertTrue("surface teardown exceeded the bounded allowance",
                    SystemClock.elapsedRealtime() - destroyStart < 2_000);

            int createsBefore = gameView.surfaceCreateCallbacksForTest();
            instrumentation.runOnMainSync(() -> parent.addView(gameView, layoutParams));
            await(() -> gameView.surfaceCreateCallbacksForTest() > createsBefore
                            && gameView.renderLoopStartsForTest() > startsBefore
                            && gameView.activeRenderLoopsForTest() == 1,
                    3_000, "recreated surface did not start exactly one render loop");
            assertEquals(1, gameView.maxConcurrentRenderLoopsForTest());
        }

        instrumentation.runOnMainSync(activity::finish);
        await(() -> manager.isClosed() && gameView.activeRenderLoopsForTest() == 0
                        && gameView.isPermanentlyClosedForTest(),
                5_000, "activity render teardown did not finish within the bound");
        assertEquals(1, gameView.maxConcurrentRenderLoopsForTest());
    }

    private static GameActivity launchGame(android.app.Instrumentation instrumentation) {
        Intent intent = new Intent(instrumentation.getTargetContext(), GameActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity launched = instrumentation.startActivitySync(intent);
        assertTrue(launched instanceof GameActivity);
        return (GameActivity) launched;
    }

    private static void finishAndAwaitClosed(android.app.Instrumentation instrumentation,
                                             GameActivity activity) {
        GameManager manager = activity.gameManager;
        IngredientFetchWorker fetcher = activity.getIngredientFetcherForTest();
        PotThreadPool potPool = activity.getPotThreadPoolForTest();
        instrumentation.runOnMainSync(activity::finish);
        await(() -> manager.isClosed() && fetcher.isClosed() && potPool.isClosed(),
                5_000, "re-entered activity owners did not close within the bound");
    }

    private static java.util.List<String> processSnapshot(GameManager manager) {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (Order order : manager.getActiveProcesses()) {
            result.add(order.getId() + ":" + order.getTimeRemaining());
        }
        return result;
    }

    private static <T> T instrumentationCall(android.app.Instrumentation instrumentation,
                                               java.util.concurrent.Callable<T> action) {
        java.util.concurrent.atomic.AtomicReference<T> result = new java.util.concurrent.atomic.AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try { result.set(action.call()); }
            catch (Exception e) { throw new AssertionError(e); }
        });
        return result.get();
    }

    private static void await(java.util.function.BooleanSupplier condition, long timeoutMs,
                              String failureMessage) {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(20);
        }
        assertTrue(failureMessage, condition.getAsBoolean());
    }
}
