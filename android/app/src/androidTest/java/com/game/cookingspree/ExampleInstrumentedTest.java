package com.game.cookingspree;

import android.content.Context;
import android.content.Intent;
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

    private static Pot instrumentationCall(android.app.Instrumentation instrumentation,
                                            java.util.concurrent.Callable<Pot> action) {
        java.util.concurrent.atomic.AtomicReference<Pot> result = new java.util.concurrent.atomic.AtomicReference<>();
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
