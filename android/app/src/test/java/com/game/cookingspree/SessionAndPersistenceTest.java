package com.game.cookingspree;

import com.game.cookingspree.util.CloudSyncGate;
import com.game.cookingspree.util.JoystickSelection;
import com.game.cookingspree.util.LocalFirstWrite;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class SessionAndPersistenceTest {
    @Test
    public void independentPauseReasonsRemainUntilEachReasonIsCleared() {
        PauseState state = new PauseState();
        state.setPaused(PauseState.Reason.MANUAL_MENU, true);
        state.setPaused(PauseState.Reason.BACKGROUND, true);

        state.setPaused(PauseState.Reason.BACKGROUND, false);
        assertFalse(state.isRunning());
        state.setPaused(PauseState.Reason.MANUAL_MENU, false);
        assertTrue(state.isRunning());
    }

    @Test
    public void activeDurationRetainsOnlyTheUnelapsedRemainder() {
        ActiveDuration duration = new ActiveDuration(1200);
        duration.consume(400_000_000L);
        assertEquals(800_000_000L, duration.remainingNanos());
        duration.consume(300_000_000L);
        assertEquals(500_000_000L, duration.remainingNanos());
        duration.consume(900_000_000L);
        assertTrue(duration.isComplete());
    }

    @Test
    public void duplicateResumeDoesNotClearAnotherPauseReason() {
        PauseState state = new PauseState();
        state.setPaused(PauseState.Reason.MANUAL_MENU, true);
        state.setPaused(PauseState.Reason.BACKGROUND, true);
        state.setPaused(PauseState.Reason.MANUAL_MENU, false);
        state.setPaused(PauseState.Reason.MANUAL_MENU, false);
        assertFalse(state.isRunning());
        state.setPaused(PauseState.Reason.BACKGROUND, false);
        assertTrue(state.isRunning());
    }

    @Test
    public void tutorialFirstResumeOnlyRemovesBackgroundPause() {
        PauseState state = new PauseState();
        state.setPaused(PauseState.Reason.TUTORIAL, true);
        state.setPaused(PauseState.Reason.BACKGROUND, true);
        state.setPaused(PauseState.Reason.BACKGROUND, false);
        assertFalse(state.isRunning());
        state.setPaused(PauseState.Reason.TUTORIAL, false);
        assertTrue(state.isRunning());
    }

    @Test
    public void tutorialMovementAllowanceDoesNotRunGameplayAndBackgroundBlocksIt() {
        PauseState state = new PauseState();
        state.setPaused(PauseState.Reason.TUTORIAL, true);
        state.allowTutorialMovement(true);

        assertFalse(state.isRunning());
        assertTrue(state.isMovementAllowed());
        state.setPaused(PauseState.Reason.BACKGROUND, true);
        assertFalse(state.isMovementAllowed());
        state.setPaused(PauseState.Reason.BACKGROUND, false);
        assertTrue(state.isMovementAllowed());
        state.allowTutorialMovement(false);
        assertFalse(state.isMovementAllowed());
        assertFalse(state.isRunning());
    }

    @Test
    public void pauseTransitionWaitsForSerializedGameplayCallback() throws Exception {
        PauseState state = new PauseState();
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        CountDownLatch pauseAttempted = new CountDownLatch(1);
        CountDownLatch pauseCompleted = new CountDownLatch(1);
        AtomicBoolean callbackDelivered = new AtomicBoolean();
        Thread callbackThread = new Thread(() -> state.runIfRunning(() -> {
            callbackDelivered.set(true);
            callbackEntered.countDown();
            try { releaseCallback.await(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }));
        Thread pauseThread = new Thread(() -> {
            pauseAttempted.countDown();
            state.setPaused(PauseState.Reason.BACKGROUND, true);
            pauseCompleted.countDown();
        });

        callbackThread.start();
        assertTrue(callbackEntered.await(1, TimeUnit.SECONDS));
        pauseThread.start();
        assertTrue(pauseAttempted.await(1, TimeUnit.SECONDS));
        releaseCallback.countDown();
        assertTrue(pauseCompleted.await(1, TimeUnit.SECONDS));
        callbackThread.join(1000);
        pauseThread.join(1000);

        assertTrue(callbackDelivered.get());
        assertFalse(state.isRunning());
    }

    @Test
    public void repeatedTerminalCallbacksFinalizeStatsAndSaveOnce() {
        FinalizationGate gate = new FinalizationGate();
        AtomicInteger statWrites = new AtomicInteger();
        AtomicInteger saveClears = new AtomicInteger();
        AtomicInteger dialogs = new AtomicInteger();
        Runnable finalization = () -> {
            statWrites.incrementAndGet();
            saveClears.incrementAndGet();
            dialogs.incrementAndGet();
        };

        gate.runOnce(finalization);
        gate.runOnce(finalization);
        gate.runOnce(finalization);

        assertEquals(1, statWrites.get());
        assertEquals(1, saveClears.get());
        assertEquals(1, dialogs.get());
    }

    @Test
    public void sessionClosureIsIdempotentAndSuppressesAlreadyQueuedCallbacks() {
        SessionCallbacks callbacks = new SessionCallbacks();
        AtomicInteger calls = new AtomicInteger();
        Runnable alreadyPosted = () -> callbacks.runIfOpen(calls::incrementAndGet);

        callbacks.close();
        callbacks.close();
        alreadyPosted.run();

        assertEquals(0, calls.get());
        assertFalse(callbacks.isOpen());
    }

    @Test
    public void cookingInterruptedBeforeCompletionDoesNotCreateFoodOrNotify() throws Exception {
        PotFunctions pot = new PotFunctions(10_000);
        pot.addIngredient(new Ingredient(0));
        pot.addIngredient(new Ingredient(1));
        pot.addIngredient(new Ingredient(2));
        AtomicInteger notifications = new AtomicInteger();
        Thread cook = new Thread(() -> pot.cookIngredients(
                new Recipe("test", Recipe.getDefaultRecipes().get(0).getIngredients()),
                progress -> notifications.incrementAndGet()));

        cook.start();
        awaitThreadState(cook, Thread.State.TIMED_WAITING);
        cook.interrupt();
        cook.join(1000);

        assertFalse(cook.isAlive());
        assertFalse(pot.gotFood());
        assertEquals(3, pot.getIngredientsInside().size());
        assertEquals(0, notifications.get());
    }

    @Test
    public void restartedCookingInterruptedBeforeCompletionDoesNotCreateFoodOrNotify() throws Exception {
        PotFunctions pot = new PotFunctions(10_000);
        Recipe recipe = Recipe.getDefaultRecipes().get(0);
        pot.setCookProgress(0);
        AtomicInteger notifications = new AtomicInteger();
        Thread cook = new Thread(() -> pot.restartCooking(
                recipe, progress -> notifications.incrementAndGet()));

        cook.start();
        awaitThreadState(cook, Thread.State.TIMED_WAITING);
        cook.interrupt();
        cook.join(1000);

        assertFalse(cook.isAlive());
        assertFalse(pot.gotFood());
        assertEquals(0, pot.getCookProgress());
        assertEquals(0, notifications.get());
    }

    @Test
    public void interruptedQueueConsumerDoesNotContinueWithIngredient() throws Exception {
        IngredientQueue queue = new IngredientQueue(1);
        AtomicInteger consumed = new AtomicInteger();
        CountDownLatch stopped = new CountDownLatch(1);
        Thread consumer = new Thread(() -> {
            try {
                if (queue.take() != null) consumed.incrementAndGet();
            } catch (InterruptedException expected) {
                Thread.currentThread().interrupt();
            } finally {
                stopped.countDown();
            }
        });

        consumer.start();
        awaitThreadState(consumer, Thread.State.WAITING);
        consumer.interrupt();

        assertTrue(stopped.await(1, TimeUnit.SECONDS));
        assertEquals(0, consumed.get());
        queue.close();
    }

    @Test
    public void closingBlockedFillerWakesConsumerWithoutMutationOrCallback() throws Exception {
        IngredientQueue queue = new IngredientQueue(1);
        IngredientBasketFiller filler = new IngredientBasketFiller(queue, new BasketManager(1), 1);
        AtomicInteger callbacks = new AtomicInteger();
        filler.startFilling(ingredients -> callbacks.incrementAndGet());

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!filler.isWaitingForIngredientForTest() && System.nanoTime() < deadline) Thread.yield();
        assertTrue(filler.isWaitingForIngredientForTest());
        filler.close();
        filler.close();

        long closeDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (!filler.isTerminatedForTest() && System.nanoTime() < closeDeadline) Thread.yield();
        assertTrue(filler.isClosed());
        assertTrue(filler.isTerminatedForTest());
        assertEquals(0, callbacks.get());
        assertEquals(0, queue.size());
    }

    @Test
    public void potPoolCloseIsIdempotentAndInterruptsRunningWork() throws Exception {
        PotThreadPool pool = new PotThreadPool(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        pool.submit(() -> {
            started.countDown();
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException expected) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
        });

        assertTrue(started.await(1, TimeUnit.SECONDS));
        pool.close();
        pool.close();

        assertTrue(interrupted.await(1, TimeUnit.SECONDS));
        assertTrue(pool.isClosed());
    }

    @Test
    public void simultaneousDirectionReleaseKeepsRemainingDirectionHeld() {
        HeldDirections directions = new HeldDirections();
        directions.press(1);
        directions.press(2);

        assertTrue(directions.release(2));
        assertTrue(directions.isHeld(1));
        assertFalse(directions.isHeld(2));
        assertFalse(directions.release(1));
        assertTrue(directions.isEmpty());
    }

    @Test
    public void tutorialCompositionRunsOnlyOnceWhenBothInitializationHooksAreCalled() {
        SessionComposer composer = new SessionComposer();
        AtomicInteger componentGraphs = new AtomicInteger();

        assertTrue(composer.composeOnce(componentGraphs::incrementAndGet));
        assertFalse(composer.composeOnce(componentGraphs::incrementAndGet));

        assertEquals(1, componentGraphs.get());
        assertTrue(composer.isComposed());
    }

    private static void awaitThreadState(Thread thread, Thread.State expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (thread.getState() != expected && System.nanoTime() < deadline) Thread.yield();
        assertEquals(expected, thread.getState());
    }

    @Test
    public void nullUserSkipsCloudUpdateAndLooksUpUserOnce() {
        AtomicInteger lookups = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();

        boolean updated = LocalFirstWrite.ifPresent(() -> {
            lookups.incrementAndGet();
            return null;
        }, user -> writes.incrementAndGet());

        assertFalse(updated);
        assertEquals(1, lookups.get());
        assertEquals(0, writes.get());
    }

    @Test
    public void failedCloudSyncDoesNotUndoOrSkipLocalWrite() {
        AtomicInteger localValue = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        LocalFirstWrite.run(() -> localValue.set(7), () -> {
            throw new IllegalStateException("simulated offline failure");
        }, failure -> failures.incrementAndGet());

        assertEquals(7, localValue.get());
        assertEquals(1, failures.get());
    }

    @Test
    public void serverHydrationDoesNotTriggerCloudUploads() {
        CloudSyncGate gate = new CloudSyncGate();
        AtomicInteger uploads = new AtomicInteger();
        gate.withoutSync(() -> {
            gate.runIfAllowed(uploads::incrementAndGet);
            gate.withoutSync(() -> {
                gate.runIfAllowed(uploads::incrementAndGet);
            });
        });

        assertEquals(0, uploads.get());
        assertFalse(gate.isSuppressed());
    }

    @Test
    public void joystickHydrationSelectsSavedValueBeforeInstallingUserListener() {
        AtomicInteger selected = new AtomicInteger(-1);
        AtomicInteger cloudUploads = new AtomicInteger();
        AtomicBoolean listenerInstalled = new AtomicBoolean();

        JoystickSelection.hydrate(1.4f, 1.4f, 10, 20, id -> {
            selected.set(id);
            if (listenerInstalled.get()) cloudUploads.incrementAndGet();
        }, () -> listenerInstalled.set(true));

        assertEquals(20, selected.get());
        assertTrue(listenerInstalled.get());
        assertEquals(0, cloudUploads.get());
    }

    @Test
    public void gameOverWritesStatsLocallyAndClearsSavedRun() {
        FakeStats stats = new FakeStats();
        AtomicInteger saveClears = new AtomicInteger();

        GameOverPersistence.complete(80, stats, saveClears::incrementAndGet);

        assertEquals(80, stats.highScore);
        assertEquals(4, stats.gamesPlayed);
        assertEquals(35f, stats.averageScore, 0.001f);
        assertEquals(1, saveClears.get());
    }

    private static final class FakeStats implements GameOverPersistence.StatsStore {
        int highScore = 60;
        int gamesPlayed = 3;
        float averageScore = 20f;

        @Override public int getHighScore() { return highScore; }
        @Override public int getGamesPlayed() { return gamesPlayed; }
        @Override public float getAverageScore() { return averageScore; }
        @Override public void setHighScore(int score) { highScore = score; }
        @Override public void setAverageScore(float score) { averageScore = score; }
        @Override public void setGamesPlayed(int count) { gamesPlayed = count; }
    }
}
