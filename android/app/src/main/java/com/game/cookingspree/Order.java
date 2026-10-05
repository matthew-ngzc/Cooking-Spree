package com.game.cookingspree;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.UUID;

public class Order {
    private static final String TAG = "Order";

    private final String id;
    private final String name;
    private final int timeLimit; // in seconds
    private final OrderCountdown countdown;
    private final Recipe recipe;
    private final Object mutex = new Object();

    private final ElapsedTimer elapsedTimer = new ElapsedTimer();

    private static final String[] CUSTOMER_NAMES = {
            "Customer A", "Customer B", "Customer C"
    };

    public Order(Recipe recipe, int timeLimit) {
        this(recipe, timeLimit, timeLimit);
    }

    private Order(Recipe recipe, int timeLimit, int timeRemaining) {
        this.id = UUID.randomUUID().toString();
        this.name = CUSTOMER_NAMES[(int)(Math.random() * CUSTOMER_NAMES.length)];
        this.recipe = recipe;
        this.timeLimit = timeLimit;
        this.countdown = new OrderCountdown(timeRemaining);

        Log.d(TAG, "New order created: " + name + ", Recipe: " + recipe.getName() + ", Time: " + timeLimit + "s");
    }

    public static Order generateRandomOrder(List<Recipe> availableRecipes) {
        // Random time between 60-120 seconds
        int randomTime = 60 + (int)(Math.random() * 61);
        Recipe randomRecipe = availableRecipes.get((int)(Math.random() * availableRecipes.size()));
        return new Order(randomRecipe, randomTime);
    }
    public static Order generateRandomOrder(Recipe recipe, int timeLimit, int timeRemaining) {
        return new Order(recipe, timeLimit, timeRemaining);
    }

    // This will be called from GameManager
    public void updateTime() {
        // Only update if the timer is not paused
        if (!elapsedTimer.isPaused()) {
            long delta = elapsedTimer.progress();
            if (delta > 0) {
                synchronized (mutex) { countdown.advanceActiveMillis(delta); }
            }
        }
    }
    
    // Add methods to pause and resume the order timer
    public void pauseTimer() {
        elapsedTimer.pause();
    }
    
    public void resumeTimer() {
        elapsedTimer.resume();
    }

    public void completeOrder() {
        synchronized (mutex) {
            if (!countdown.isComplete() && !countdown.isExpired()) {
                countdown.complete();
                Log.d(TAG, "Order completed: " + name);
            }
        }
    }

    // Getters with thread-safe access
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getTimeLimit() {
        return timeLimit;
    }

    public int getTimeRemaining() {
        synchronized (mutex) {
            return countdown.getTimeRemaining();
        }
    }

    public boolean isComplete() {
        synchronized (mutex) {
            return countdown.isComplete();
        }
    }

    public boolean isDead() {
        synchronized (mutex) {
            return countdown.isExpired();
        }
    }

    public Recipe getRecipe() {
        return recipe;
    }

    // For debugging
    @NonNull
    @Override
    public String toString() {
        synchronized (mutex) {
            return "Order{" + "name='" + name + '\'' + ", recipe=" + recipe.getName() + ", timeRemaining=" + countdown.getTimeRemaining() + ", isComplete=" + countdown.isComplete() + ", isDead=" + countdown.isExpired() + '}';
        }
    }
}
