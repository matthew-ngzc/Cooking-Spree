package com.game.cookingspree;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import android.os.SystemClock;

public class GameManager {
    private static final String TAG = "GameManager";
    private static final int TIMER_INTERVAL_MS = 16; // Update more frequently for smoother animation (~60 FPS)
    private static final int MAX_ACTIVE_PROCESSES = 5; // Maximum number of active processes

    // Timer utilities for smooth timing
    private final ElapsedTimer elapsedTimer = new ElapsedTimer();
    private final DeltaStepper timerStepper;

    // Simple mutex for thread synchronization
    private final Object mutex = new Object();

    // Process collections
    private final List<Order> activeOrders;
    private final List<Order> pendingRemovals;

    private final List<Recipe> availableRecipes;
    private int score;
    private final CompletionScoring completionScoring = new CompletionScoring();
    private final FailureCounter failureCounter = new FailureCounter();
    private boolean isGameOver;
    private final Random random;
    private final Handler mainHandler;
    private final Handler gameTickHandler;
    private Runnable processSpawnRunnable;
    private Runnable gameTickRunnable;
    private final Context context;
    private final GameListener gameListener;
    private boolean isPaused = false;
    private final PauseState pauseState;
    private long nextSpawnAt;
    private int remainingSpawnDelay;
    private boolean remainingSpawnPending;
    private volatile boolean closed;
    public interface GameListener {
        void onProcessAdded(Order order);
        void onProcessCompleted(Order order);
        void onProcessDied(Order order);
        void onScoreChanged(int newScore);
        void onGameOver(int finalScore);
        void onTimerTick(); // Notify UI of every timer tick
    }

    public GameManager(Context context, GameListener listener,List<Recipe> recipeList) {
        this(context, listener, recipeList, new PauseState());
    }

    public GameManager(Context context, GameListener listener,List<Recipe> recipeList, PauseState pauseState) {
        this.context = context;
        this.gameListener = listener;
        this.activeOrders = new ArrayList<>();
        this.pendingRemovals = new ArrayList<>();
        this.availableRecipes = recipeList;
        this.score = 0;
        this.failureCounter.reset();
        this.completionScoring.reset();
        this.isGameOver = false;
        this.random = new Random();
        this.pauseState = pauseState;

        this.timerStepper = new DeltaStepper(1000, this::tickUpdate);

        this.mainHandler = new Handler(Looper.getMainLooper());
        this.gameTickHandler = new Handler(Looper.getMainLooper());
    }

    private boolean tickUpdate(long deltaTime) {
        if (!closed && gameListener != null) {
            gameListener.onTimerTick();
        }
        return true;
    }

    public void startGame() {
        if (closed) return;
        Log.d(TAG, "Starting game");
        isGameOver = false;
        score = 0;
        failureCounter.reset();
        completionScoring.reset();

        // Clear any existing processes
        synchronized (mutex) {
            activeOrders.clear();
            pendingRemovals.clear();
        }

        isPaused = !pauseState.isRunning();
        if (!isPaused) {
            scheduleNextProcess();
            startGameTick();
        }
    }

    private void scheduleNextProcess() {
        int spawnDelay = remainingSpawnPending ? remainingSpawnDelay : 5000 + random.nextInt(8000);
        remainingSpawnDelay = 0;
        remainingSpawnPending = false;
        nextSpawnAt = SystemClock.elapsedRealtime() + spawnDelay;

        Log.d(TAG, "Scheduling next process in " + spawnDelay + "ms");

        processSpawnRunnable = () -> {
            if (!closed && !isGameOver && !isPaused && pauseState.isRunning()) {
                generateNewProcess();
                scheduleNextProcess();
            }
        };

        mainHandler.postDelayed(processSpawnRunnable, spawnDelay);
    }

    private void generateNewProcess() {
        Order newOrder = Order.generateRandomOrder(availableRecipes);

        synchronized (mutex) {
            // Check if we can add directly to active processes
            if (activeOrders.size() < MAX_ACTIVE_PROCESSES) {
                activeOrders.add(newOrder);
                Log.d(TAG, "New process added directly: " + newOrder.getName());

                if (!closed && gameListener != null) {
                    gameListener.onProcessAdded(newOrder);
                }
            }
        }
    }

    private void startGameTick() {
        // Reset the elapsed timer
        elapsedTimer.progress();

        // Use a higher frequency timer for smoother updates
        gameTickRunnable = new Runnable() {
            @Override
            public void run() {
                if (!closed && !isGameOver && !isPaused && pauseState.isRunning()) {
                    updateProcesses();

                    // Schedule the next update
                    gameTickHandler.postDelayed(this, TIMER_INTERVAL_MS);
                }
            }
        };

        // Start immediately
        gameTickHandler.post(gameTickRunnable);
    }

    private void updateProcesses() {
        if (!pauseState.isRunning() || isGameOver || closed) return;
        // Get elapsed time since last update
        long delta = elapsedTimer.progress();

        // Update the timer stepper for UI updates
        timerStepper.update(delta);

        // Create a local list of processes to handle in this update
        List<Order> processesToUpdate;

        // First, get a snapshot of current processes
        synchronized (mutex) {
            processesToUpdate = new ArrayList<>(activeOrders);
        }

        // Process the snapshot without holding the lock
        for (Order order : processesToUpdate) {
            if (!order.isComplete() && !order.isDead()) {
                // Update each process's timer
                order.updateTime();

                // Check if process died during this update
                if (order.isDead()) {
                    handleDeadProcess(order);
                }
            }
        }

        // Now handle any pending removals
        handlePendingRemovals();
    }

    private void handleDeadProcess(Order order) {
        Log.d(TAG, "Process died: " + order.getName());

        boolean terminalFailureReached = failureCounter.recordExpiry();

        // Add to pending removals
        synchronized (mutex) {
            pendingRemovals.add(order);
        }

        if (!closed && gameListener != null) {
            gameListener.onProcessDied(order);
            gameListener.onScoreChanged(score);
        }

        // Check game over condition
        if (terminalFailureReached) {
            endGame();
        }
    }

    public void setDeadProcessCount(int count) {
        if (closed) return;
        this.failureCounter.setCount(count);

        if (failureCounter.isTerminal() && !isGameOver) {
            endGame();
        }
    }

    void restoreTerminalResult(int finalScore, int failures) {
        if (closed || isGameOver || failures != MAX_DEAD_PROCESSES) return;
        score = finalScore;
        deadProcessCount = failures;
        endGame();
    }
    private void handlePendingRemovals() {
        synchronized (mutex) {
            if (!pendingRemovals.isEmpty()) {
                // Remove all pending processes
                activeOrders.removeAll(pendingRemovals);
                pendingRemovals.clear();
            }
        }
    }

    public void completeProcess(String processId) {
        if (closed || isGameOver || !pauseState.isRunning()) return;
        Order orderToComplete = null;

        synchronized (mutex) {
            for (Order order : activeOrders) {
                if (order.getId().equals(processId) && !order.isComplete() && !order.isDead()) {
                    orderToComplete = order;
                    break;
                }
            }
        }

        // Complete the process if found
        if (orderToComplete != null) {
            orderToComplete.completeOrder();
            // Check if the process was successfully completed
            if (!orderToComplete.isDead()) {
                score += completionScoring.awardForCompletionAt(System.currentTimeMillis());
            } else {
                completionScoring.reset();
            }

            // Add to pending removals to be cleared next tick
            synchronized (mutex) {
                pendingRemovals.add(orderToComplete);
            }

            Log.d(TAG, "Process completed: " + orderToComplete.getName() + ", New score: " + score);

            if (!closed && gameListener != null) {
                gameListener.onProcessCompleted(orderToComplete);
                gameListener.onScoreChanged(score);
            }
        } else{
            completionScoring.reset();
        }
    }

    private void endGame() {
        if (closed || isGameOver) return;
        Log.d(TAG, "Game over! Final score: " + score);
        isGameOver = true;
        pauseState.setTerminal();

        // Remove callbacks to prevent further updates
        mainHandler.removeCallbacks(processSpawnRunnable);
        gameTickHandler.removeCallbacks(gameTickRunnable);

        // Save high score if applicable
        saveHighScore();

        if (!closed && gameListener != null) {
            gameListener.onGameOver(score);
        }

        Toast.makeText(context, "Game Over! Final Score: " + score, Toast.LENGTH_LONG).show();
    }

    private void saveHighScore() {
        // Get current high score
        int currentHighScore = context.getSharedPreferences("ProcessManagerPrefs", Context.MODE_PRIVATE)
                .getInt("highScore", 0);

        // Update if new score is higher
        if (score > currentHighScore) {
            context.getSharedPreferences("ProcessManagerPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("highScore", score)
                    .apply();

            Log.d(TAG, "New high score: " + score);
        }
    }

    public void pauseGame() {
        setPauseReason(PauseState.Reason.MANUAL_MENU, true);
    }

    public void pauseForBackground() { setPauseReason(PauseState.Reason.BACKGROUND, true); }
    public void resumeFromBackground() { setPauseReason(PauseState.Reason.BACKGROUND, false); }
    public void pauseForTutorial(boolean paused) { setPauseReason(PauseState.Reason.TUTORIAL, paused); }
    public void setLoading(boolean loading) { setPauseReason(PauseState.Reason.LOAD, loading); }

    private void setPauseReason(PauseState.Reason reason, boolean paused) {
        if (closed || isGameOver) return;
        boolean wasRunning = pauseState.isRunning();
        pauseState.setPaused(reason, paused);
        boolean running = pauseState.isRunning();
        if (wasRunning == running) return;
        if (!running) {
            isPaused = true;
            if (processSpawnRunnable != null) {
                remainingSpawnDelay = (int) Math.max(0L, nextSpawnAt - SystemClock.elapsedRealtime());
                remainingSpawnPending = true;
                mainHandler.removeCallbacks(processSpawnRunnable);
            }
            if (gameTickRunnable != null) gameTickHandler.removeCallbacks(gameTickRunnable);
            elapsedTimer.pause();
            synchronized (mutex) { for (Order order : activeOrders) order.pauseTimer(); }
        } else {
            isPaused = false;
            elapsedTimer.resume();
            synchronized (mutex) { for (Order order : activeOrders) order.resumeTimer(); }
            scheduleNextProcess();
            startGameTick();
        }
    }

    /* Compatibility entry point: explicit manual-menu resume only clears that reason. */
    public void resumeGame() {
        setPauseReason(PauseState.Reason.MANUAL_MENU, false);
    }

    public void stopGame() {
        if (closed) return;
        closed = true;
        pauseState.close();
        Log.d(TAG, "Game stopped");
        if (processSpawnRunnable != null) mainHandler.removeCallbacks(processSpawnRunnable);
        if (gameTickRunnable != null) gameTickHandler.removeCallbacks(gameTickRunnable);
    }

    public List<Order> getActiveProcesses() {
        List<Order> processesCopy;
        synchronized (mutex) {
            processesCopy = new ArrayList<>(activeOrders);
        }
        return processesCopy;
    }
    public int getScore() {
        return score;
    }

    public int getDeadProcessCount() {
        return failureCounter.getCount();
    }

    public boolean isGameOver() {
        return isGameOver;
    }

    public boolean isClosed() { return closed; }

    public boolean isRunning() {
        return !closed && !isPaused && !isGameOver && pauseState.isRunning();
    }

    PauseState getPauseStateForTest() { return pauseState; }

    public void addProcessDirectly(Order order) {
        if (closed) return;
        if (!pauseState.isRunning()) order.pauseTimer();
        synchronized (mutex) {
            activeOrders.add(order);
        }
    }

    public void setScore(int newScore) {
        if (closed) return;
        this.score = newScore;
        if (!closed && gameListener != null) {
            gameListener.onScoreChanged(newScore);
        }
    }
}
