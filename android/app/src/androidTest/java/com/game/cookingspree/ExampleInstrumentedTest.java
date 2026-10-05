package com.game.cookingspree;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.Activity;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.View;
import android.widget.Button;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.game.cookingspree.util.PrefsHelper;

import org.junit.Before;
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
    @Before
    public void resetSavedGameStorage() {
        PrefsHelper.init(InstrumentationRegistry.getInstrumentation().getTargetContext(), null);
        PrefsHelper.clearSaveState();
    }

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
    public void validLoadsAreIsolatedAndCorruptLoadsWaitForAcknowledgmentThenClose() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context appContext = instrumentation.getTargetContext();
        SharedPreferences saves = appContext.getSharedPreferences("GameSave", Context.MODE_PRIVATE);
        PrefsHelper.clearSaveState();

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
        assertTrue("rejection must be visible and acknowledged", rejected.isRecoveryDialogShowingForTest());
        assertTrue("LOAD must remain held while the rejection is visible",
                rejected.pauseState.isPaused(PauseState.Reason.LOAD));
        assertEquals("partial or fresh gameplay must stay hidden", View.INVISIBLE,
                rejected.findViewById(android.R.id.content).getVisibility());
        assertTrue("corruption copy must identify corruption",
                rejected.recoveryMessageForTest().toLowerCase().contains("corrupt"));
        assertEquals("rejected load must leave the fresh session unchanged", 0,
                rejected.gameManager.getScore());
        assertEquals("save remains until the player acknowledges the error",
                persistedBeforeLoad, saves.getAll());
        instrumentation.runOnMainSync(rejected::acknowledgeRecoveryForTest);
        finishAndAwaitClosed(instrumentation, rejected);
        assertTrue("acknowledgment clears the rejected save", saves.getAll().isEmpty());
        PrefsHelper.clearSaveState();
    }

    @Test
    public void richLegacyRunRoundTripsPlayerTableOrderAndPartialPots() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        SharedPreferences saves = instrumentation.getTargetContext()
                .getSharedPreferences("GameSave", Context.MODE_PRIVATE);
        PrefsHelper.clearSaveState();
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

        GameActivity loaded = launchLoadGame(instrumentation, true);
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
        assertEquals(47, order.getTimeRemaining());
        assertEquals(java.util.Arrays.asList(1), ingredientIds(loaded.game.getPots().get(0).getInPot()));
        assertEquals(java.util.Arrays.asList(4, 4), ingredientIds(loaded.game.getPots().get(1).getInPot()));
        finishAndAwaitClosed(instrumentation, loaded);
        PrefsHelper.clearSaveState();
    }

    @Test
    public void nearCompleteCookingSurvivesLoadSaveReloadThenFinishesOnce() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        SharedPreferences saves = instrumentation.getTargetContext()
                .getSharedPreferences("GameSave", Context.MODE_PRIVATE);
        PrefsHelper.clearSaveState();
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
        assertEquals("tomato_soup", saves.getString("pot_0_recipe_id", null));
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
        PrefsHelper.clearSaveState();
    }

    @Test
    public void twoSlotFailuresNeverReplacePriorSaveAndLoadedTerminalRunClearsIt() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context appContext = instrumentation.getTargetContext();
        SharedPreferences saves = instrumentation.getTargetContext()
                .getSharedPreferences("GameSave", Context.MODE_PRIVATE);
        PrefsHelper.clearSaveState();
        GameActivity activity = launchGame(instrumentation);
        GameSaveSnapshot valid = instrumentationCall(instrumentation, activity::captureSnapshotForTest);
        finishAndAwaitClosed(instrumentation, activity);

        assertFalse(PrefsHelper.hasGameSave());
        assertTrue("the first verified save promotes into slot A",
                PrefsHelper.writeGameSaveValues(valid.toLegacyValues()));
        assertEquals("A", PrefsHelper.activeGameSaveSlotForTest());
        assertEquals(valid.toLegacyValues(), PrefsHelper.getGameSaveValues());

        writeLegacyFixture(saves, valid.toLegacyValues());
        java.util.Map<String, ?> prior = new java.util.HashMap<>(valid.toLegacyValues());

        java.util.Map<String, Object> unverifiable = new java.util.HashMap<>(valid.toLegacyValues());
        unverifiable.put("score", 999);
        assertFalse("failed readback validation must fail the save",
                PrefsHelper.writeGameSaveValues(unverifiable, stored -> false));
        assertEquals("verification failure keeps legacy save active", "legacy",
                PrefsHelper.activeGameSaveSlotForTest());
        assertEquals(prior, PrefsHelper.getGameSaveValues());
        assertEquals("the prior payload itself is untouched", prior, saves.getAll());

        PrefsHelper.failNextGameSaveSlotWriteForTest();
        assertFalse("candidate slot write failure must fail the save",
                PrefsHelper.writeGameSaveValues(unverifiable));
        assertEquals("legacy", PrefsHelper.activeGameSaveSlotForTest());
        assertEquals(prior, PrefsHelper.getGameSaveValues());

        PrefsHelper.failNextGameSavePromotionForTest();
        assertFalse("selector promotion failure must fail the save",
                PrefsHelper.writeGameSaveValues(unverifiable));
        assertEquals("legacy", PrefsHelper.activeGameSaveSlotForTest());
        assertEquals(prior, PrefsHelper.getGameSaveValues());
        assertEquals(prior, saves.getAll());

        GameActivity priorLoad = launchLoadGame(instrumentation);
        assertTrue("the prior save remains loadable after every failed attempt",
                priorLoad.loadSucceededForTest());
        assertEquals(valid.score, priorLoad.gameManager.getScore());
        finishAndAwaitClosed(instrumentation, priorLoad);

        GameActivity inconsistentSource = launchGame(instrumentation);
        Pot inconsistent = inconsistentSource.game.getPots().get(0);
        instrumentation.runOnMainSync(() -> {
            inconsistentSource.gameManager.pauseGame();
            inconsistent.setState(Pot.State.DONE.name()); // No food: snapshot capture must reject this state.
            assertFalse("failed live capture must fail the save", inconsistentSource.saveGameStateForTest());
        });
        assertEquals("legacy", PrefsHelper.activeGameSaveSlotForTest());
        assertEquals(prior, PrefsHelper.getGameSaveValues());
        assertEquals(prior, saves.getAll());
        finishAndAwaitClosed(instrumentation, inconsistentSource);

        java.util.Map<String, Object> firstSuccess = new java.util.HashMap<>(valid.toLegacyValues());
        firstSuccess.put("score", 111);
        assertTrue(PrefsHelper.writeGameSaveValues(firstSuccess));
        assertEquals("A", PrefsHelper.activeGameSaveSlotForTest());
        assertEquals(firstSuccess, PrefsHelper.getGameSaveValues());
        assertEquals("legacy remains the fallback through the first promotion", prior, saves.getAll());

        SharedPreferences slotA = appContext.getSharedPreferences("GameSaveSlotA", Context.MODE_PRIVATE);
        java.util.Map<String, ?> firstSlotBeforeFailure = new java.util.HashMap<>(slotA.getAll());
        java.util.Map<String, Object> secondSuccess = new java.util.HashMap<>(valid.toLegacyValues());
        secondSuccess.put("score", 222);
        PrefsHelper.failNextGameSavePromotionForTest();
        assertFalse(PrefsHelper.writeGameSaveValues(secondSuccess));
        assertEquals("A", PrefsHelper.activeGameSaveSlotForTest());
        assertEquals(firstSuccess, PrefsHelper.getGameSaveValues());
        assertEquals("active slot is never rewritten by a failed attempt",
                firstSlotBeforeFailure, slotA.getAll());

        assertTrue(PrefsHelper.writeGameSaveValues(secondSuccess));
        assertEquals("B", PrefsHelper.activeGameSaveSlotForTest());
        assertEquals(secondSuccess, PrefsHelper.getGameSaveValues());
        assertEquals("the previous slot remains intact after promotion",
                firstSlotBeforeFailure, slotA.getAll());
        assertTrue("legacy compatibility storage is retired after both slots participate",
                saves.getAll().isEmpty());

        GameActivity loadSource = launchGame(instrumentation);
        GameSaveSnapshot candidate = instrumentationCall(instrumentation, loadSource::captureSnapshotForTest);
        finishAndAwaitClosed(instrumentation, loadSource);
        writeLegacyFixture(saves, candidate.toLegacyValues());
        GameActivity loaded = launchLoadGame(instrumentation);
        assertTrue(loaded.loadSucceededForTest());
        instrumentation.runOnMainSync(() -> loaded.gameManager.setDeadProcessCount(3));
        await(() -> PrefsHelper.getGameSaveValues().isEmpty(), 2_000,
                "loaded game over should clear every save slot");
        assertFalse("later load must have no candidate", PrefsHelper.hasGameSave());
        finishAndAwaitClosed(instrumentation, loaded);
    }

    @Test
    public void failedSaveButtonKeepsAndReloadsPreviousVerifiedSlot() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        GameActivity activity = launchGame(instrumentation);
        instrumentation.runOnMainSync(() -> {
            activity.gameManager.pauseGame();
            activity.findViewById(R.id.pauseMenu).setVisibility(View.VISIBLE);
            activity.gameManager.setScore(111);
            assertTrue(activity.saveGameStateForTest());
            activity.gameManager.setScore(222);
            PrefsHelper.failNextGameSavePromotionForTest();
        });

        tapView(instrumentation, activity.findViewById(R.id.btnSave));
        assertEquals("A", PrefsHelper.activeGameSaveSlotForTest());
        assertEquals(111, ((Integer) PrefsHelper.getGameSaveValues().get("score")).intValue());
        SystemClock.sleep(1_800); // Keep the failure explanation legible in review evidence.

        android.app.Instrumentation.ActivityMonitor menuMonitor = instrumentation.addMonitor(
                MainActivity.class.getName(), null, false);
        tapView(instrumentation, activity.findViewById(R.id.btnMainMenu));
        Activity menu = menuMonitor.waitForActivityWithTimeout(3_000);
        instrumentation.removeMonitor(menuMonitor);
        assertTrue(menu instanceof MainActivity);

        android.app.Instrumentation.ActivityMonitor loadMonitor = instrumentation.addMonitor(
                GameActivity.class.getName(), null, false);
        tapView(instrumentation, menu.findViewById(R.id.LoadGame));
        Activity reloaded = loadMonitor.waitForActivityWithTimeout(3_000);
        instrumentation.removeMonitor(loadMonitor);
        assertTrue(reloaded instanceof GameActivity);
        GameActivity loaded = (GameActivity) reloaded;
        assertTrue(loaded.loadSucceededForTest());
        assertEquals("failed save must reload the previous score", 111, loaded.gameManager.getScore());
        SystemClock.sleep(1_200); // Leave the restored result visible before teardown.
        finishAndAwaitClosed(instrumentation, loaded);
        instrumentation.runOnMainSync(menu::finish);
    }

    @Test
    public void incompatibleSaveShowsFullVersionsAndClearsOnlyAfterAcknowledgment() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        SharedPreferences saves = instrumentation.getTargetContext()
                .getSharedPreferences("GameSave", Context.MODE_PRIVATE);
        PrefsHelper.clearSaveState();
        saves.edit().putString("gameVersion", "2.4.1").commit();

        GameActivity rejected = launchLoadGame(instrumentation);
        assertFalse(rejected.loadSucceededForTest());
        assertTrue(rejected.isRecoveryDialogShowingForTest());
        String copy = rejected.recoveryMessageForTest();
        assertTrue(copy.contains("2.4.1"));
        assertTrue(copy.contains("1.0.0"));
        assertTrue(rejected.pauseState.isPaused(PauseState.Reason.LOAD));
        assertEquals(View.INVISIBLE, rejected.findViewById(android.R.id.content).getVisibility());
        assertEquals("2.4.1", saves.getString("gameVersion", null));
        instrumentation.runOnMainSync(rejected::acknowledgeRecoveryForTest);
        finishAndAwaitClosed(instrumentation, rejected);
        assertTrue(saves.getAll().isEmpty());
    }

    @Test
    public void applicationFailureAfterPartialMutationStaysBlockedUntilAcknowledged() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        SharedPreferences saves = instrumentation.getTargetContext()
                .getSharedPreferences("GameSave", Context.MODE_PRIVATE);
        PrefsHelper.clearSaveState();
        GameActivity source = launchGame(instrumentation);
        GameSaveSnapshot save = instrumentationCall(instrumentation, source::captureSnapshotForTest);
        finishAndAwaitClosed(instrumentation, source);
        writeLegacyFixture(saves, save.toLegacyValues());

        Intent intent = new Intent(instrumentation.getTargetContext(), GameActivity.class)
                .putExtra("loadSavedGame", true)
                .putExtra("failRestoreAfterPositionForTest", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity launched = instrumentation.startActivitySync(intent);
        assertTrue(launched instanceof GameActivity);
        GameActivity rejected = (GameActivity) launched;
        assertFalse(rejected.loadSucceededForTest());
        assertTrue(rejected.isRecoveryDialogShowingForTest());
        assertTrue(rejected.pauseState.isPaused(PauseState.Reason.LOAD));
        assertFalse(rejected.gameManager.isRunning());
        assertEquals(View.INVISIBLE, rejected.findViewById(android.R.id.content).getVisibility());
        assertTrue(rejected.recoveryMessageForTest().toLowerCase().contains("corrupt"));
        instrumentation.runOnMainSync(rejected::acknowledgeRecoveryForTest);
        finishAndAwaitClosed(instrumentation, rejected);
        assertTrue(saves.getAll().isEmpty());
    }

    @Test
    public void compatibleTerminalSnapshotFinalizesAndClearsExactlyOnce() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        SharedPreferences saves = instrumentation.getTargetContext()
                .getSharedPreferences("GameSave", Context.MODE_PRIVATE);
        PrefsHelper.clearSaveState();
        GameActivity source = launchGame(instrumentation);
        GameSaveSnapshot captured = instrumentationCall(instrumentation, source::captureSnapshotForTest);
        GameSaveSnapshot terminal = new GameSaveSnapshot(captured.gameVersion, captured.playerX, captured.playerY,
                432, 3, captured.heldItem, captured.tableItems, captured.orders, captured.pots);
        finishAndAwaitClosed(instrumentation, source);
        writeLegacyFixture(saves, terminal.toLegacyValues());

        GameActivity loaded = launchLoadGame(instrumentation);
        await(() -> saves.getAll().isEmpty(), 2_000, "terminal restore did not finalize and clear the save");
        assertTrue(loaded.gameManager.isGameOver());
        assertEquals(432, loaded.gameManager.getScore());
        assertFalse(loaded.gameManager.isRunning());
        finishAndAwaitClosed(instrumentation, loaded);
    }

    @Test
    public void repeatedDonePotSnapshotsDoNotConsumeFood() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context appContext = instrumentation.getTargetContext();
        SharedPreferences saves = appContext.getSharedPreferences("GameSave", Context.MODE_PRIVATE);
        PrefsHelper.clearSaveState();
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
        PrefsHelper.clearSaveState();
    }

    @Test
    public void saveDuringInterpolationCapturesLastReachedTile() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        GameActivity activity = launchGame(instrumentation);
        Player player = activity.game.getPlayer();
        float committedX = Math.round(player.getX() / Game.TILE_SIZE) * Game.TILE_SIZE;
        float committedY = Math.round(player.getY() / Game.TILE_SIZE) * Game.TILE_SIZE;
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int[] selected = null;
        for (int[] direction : directions) {
            if (activity.game.canMoveTo(committedX + direction[0] * Game.TILE_SIZE,
                    committedY + direction[1] * Game.TILE_SIZE)) {
                selected = direction;
                break;
            }
        }
        assertNotNull("spawn should have a traversable neighbor", selected);
        int[] direction = selected;
        instrumentation.runOnMainSync(() -> player.move(direction[0], direction[1]));
        await(() -> Math.abs(player.getX() - committedX) + Math.abs(player.getY() - committedY) >= 12f
                        && Math.abs(player.getX() - committedX) + Math.abs(player.getY() - committedY) < Game.TILE_SIZE,
                2_000, "player did not enter an interpolated tile move");
        instrumentation.runOnMainSync(() -> activity.gameManager.pauseGame());
        GameSaveSnapshot snapshot = instrumentationCall(instrumentation, activity::captureSnapshotForTest);
        assertEquals(committedX, snapshot.playerX, 0.001f);
        assertEquals(committedY, snapshot.playerY, 0.001f);
        finishAndAwaitClosed(instrumentation, activity);
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

    private static void tapView(android.app.Instrumentation instrumentation, View view) {
        instrumentation.waitForIdleSync();
        int[] location = new int[2];
        instrumentation.runOnMainSync(() -> view.getLocationOnScreen(location));
        float x = location[0] + view.getWidth() / 2f;
        float y = location[1] + view.getHeight() / 2f;
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(downTime, downTime + 80, MotionEvent.ACTION_UP, x, y, 0);
        instrumentation.sendPointerSync(down);
        instrumentation.sendPointerSync(up);
        down.recycle();
        up.recycle();
        instrumentation.waitForIdleSync();
    }

    private static void writeLegacyFixture(SharedPreferences preferences, java.util.Map<String, ?> values) {
        PrefsHelper.clearSaveState();
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
        instrumentation.waitForIdleSync();
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
