package com.game.cookingspree;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/** @noinspection BusyWait*/
public class PotFunctions {
    //To handle the pot internal functions
    private final String TAG="PotFunctions";
    private final List<Ingredient> ingredientsInside;
    private CookedFood foodDone;
    private final int progressStep=1000;//in ms, how long each progress tick is
    private final int maxIngredients;
    private final int cookTime;
    private final Object foodDoneLock = new Object();// to sync available list
    private final Object ingredientLock=new Object();//ingredient lock
    private volatile Recipe recipeCooking;
    private volatile Integer cookProgress;
    private final PauseState pauseState;

    public interface PotListener{//To send to UI thread in GameActivity
        void potProgressUpdate(int progress);
    }

    public PotFunctions(int cookTime){
        this(cookTime, new PauseState());
    }

    public PotFunctions(int cookTime, PauseState pauseState){
        this.ingredientsInside=new ArrayList<>();
        this.maxIngredients=3;
        this.foodDone=null;
        this.cookTime=cookTime;
        this.pauseState=pauseState;
    }

    public boolean isReadyToCook() {
        //Ready to cook if ingredient list reaches the maxIngredients
        synchronized (ingredientLock) {
            return ingredientsInside.size() == maxIngredients;
        }
    }

    public List<Ingredient> getIngredientsInside(){
        //Return a copy of ingredients inside, shouldn't be manipulated outside of this class
        synchronized (ingredientLock) {
            return new ArrayList<>(ingredientsInside);
        }
    }

    public CookedFood getFood(){
        //Get finished food from pot and clear the food when passed
        synchronized (foodDoneLock) {
            if (this.foodDone != null) {
                CookedFood food = this.foodDone;
                this.foodDone = null;
                return food;
            }
        }
        return null;
    }

    public void addIngredient(Ingredient ingredient){
        synchronized (ingredientLock) {
            ingredientsInside.add(ingredient);
        }
    }

    public boolean gotFood(){
        //If there is finished food in the pot
        synchronized (foodDoneLock) {
            return foodDone != null;
        }
    }

    public void cookIngredients(Recipe recipe,PotListener listener){
        //Cooking ingredients function

        boolean canCook;
        synchronized (ingredientLock) {
            canCook=isReadyToCook();
        }

        if (!canCook) return;
        try {
            while (!pauseState.runIfRunning(() -> { recipeCooking=recipe; cookProgress=0; })) {
                if (!pauseState.awaitActiveDuration(0)) return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        cookUntilDone(recipe, listener);
    }

    public void restartCooking(Recipe recipe,PotListener listener){
        try {
            while (!pauseState.runIfRunning(() -> {
                recipeCooking=recipe;
                if (cookProgress == null) cookProgress=0;
            })) {
                if (!pauseState.awaitActiveDuration(0)) return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        cookUntilDone(recipe, listener);
    }

    private void cookUntilDone(Recipe recipe, PotListener listener) {
        try {
            while (cookProgress < cookTime / progressStep) {
                if (!pauseState.awaitActiveDuration(progressStep)) return;
                while (!pauseState.runIfRunning(() -> {
                    int progress = ++cookProgress;
                    if (listener != null) listener.potProgressUpdate(progress);
                })) {
                    if (!pauseState.awaitActiveDuration(0)) return;
                }
            }
            CookedFood newFood = new CookedFood(5, recipe.getName(), new ArrayList<>(recipe.getIngredients()));
            while (!pauseState.runIfRunning(() -> {
                synchronized (ingredientLock) { ingredientsInside.clear(); }
                synchronized (foodDoneLock) { foodDone = newFood; }
                recipeCooking = null;
                cookProgress = 0;
                if (listener != null) listener.potProgressUpdate(cookTime / progressStep + 1);
            })) {
                if (!pauseState.awaitActiveDuration(0)) return;
            }
            Log.d(TAG, "Cooking complete");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public Recipe getRecipeCooking(){
        return this.recipeCooking;
    }

    public int getCookProgress(){
        return this.cookProgress;
    }

    public void setCookedFood(CookedFood food) {
        //For loading
        synchronized (foodDoneLock) {
            this.foodDone = food;
        }
    }

    public void setCookProgress(int progress){
        this.cookProgress=progress;
    }
}
