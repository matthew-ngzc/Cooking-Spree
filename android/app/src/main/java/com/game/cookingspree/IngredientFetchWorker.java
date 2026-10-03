package com.game.cookingspree;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class IngredientFetchWorker {
    // to simulate a worker maintaining the ingredient storage
    private static final String TAG = "Ingredient Fetcher";
    private final IngredientBasketFiller basketFiller;
    private final ExecutorService executor= Executors.newSingleThreadExecutor();
    private final int totalIngredients =5;
    private List<Ingredient> availableList=new ArrayList<>(totalIngredients);
    private final List<Ingredient> usedList=new ArrayList<>();
    private final Random random;
    private final IngredientQueue queue;
    private final Object availableLock = new Object(); // to sync available list and usedList
    private final int maxCap;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile boolean fetching;
    private final PauseState pauseState;


    public interface ingredientFetchListener{
        void fetchIngredientProgressUpdate(int progress);
    }

    public IngredientFetchWorker(Integer maxCap,BasketManager basketManager){
        this(maxCap, basketManager, new PauseState());
    }

    public IngredientFetchWorker(Integer maxCap,BasketManager basketManager, PauseState pauseState){
        // fill the available list
        Log.d(TAG, "Initializing ingredient list");
        generateIngredientList();
        this.maxCap=maxCap;
        this.pauseState=pauseState;
        this.queue=new IngredientQueue(maxCap);
        this.random=new Random();
        this.basketFiller=new IngredientBasketFiller(queue,basketManager,maxCap,pauseState);
    }

    private void generateIngredientList(){
        //generate ingredient list
        List<Ingredient> ingredientList=new ArrayList<>();
        for (int i = 0; i< totalIngredients; i++){
            Ingredient ingredient=new Ingredient(i);
            ingredientList.add(ingredient);
        }
        synchronized (availableLock) {
            this.availableList=ingredientList;
        }
    }

    public List<Ingredient> getAvailableList(){
        synchronized (availableLock) {
            return new ArrayList<>(availableList); // Return a copy to prevent external modification
        }
    }

    public void exchangeIngredient(Ingredient returnIngredient,Ingredient getIngredient,ingredientFetchListener listener){
        if (closed.get()) return;
        //Swaps returnIngredient into availableList and getIngredient into usedList
        try { executor.submit(()->{
            fetching = true;
            try{
                fetchIngredient(returnIngredient,getIngredient,listener);
            }catch(InterruptedException e){
                Thread.currentThread().interrupt();
            }catch(Exception e){
                Log.e(TAG,"Error at fetch Ingredient thread "+e.getLocalizedMessage());
            } finally {
                fetching = false;
            }
        });
        } catch (RejectedExecutionException ignored) { }

    }

    public void updateBaskets(ingredientFetchListener listener){
        if (closed.get()) return;
        //Updates baskets using Producer-Consumer pattern with used list
        try { executor.submit(()->{
            try{
                //Start consumer
                basketFiller.startFilling((IngredientBasketFiller.BasketFillListener) listener);
                //Start producer
                updatingBaskets();
            }catch(InterruptedException e){
                Thread.currentThread().interrupt();
            }catch(Exception e){
                Log.e(TAG,"Error at fetch Ingredient thread "+e.getLocalizedMessage());
            }
        });
        } catch (RejectedExecutionException ignored) { }

    }

    private void updatingBaskets() throws InterruptedException {
        synchronized (availableLock){
            if (closed.get() || Thread.currentThread().isInterrupted()) return;
            //Used list should always be size of maxCap
            if (usedList.size()!=maxCap){
                Log.e(TAG,"Used list is not correct size");
                return;
            }
            //Put all ingredients into the queue, will be taken from queue by basket filler
            for (Ingredient ingredient:usedList){
                if (closed.get() || Thread.currentThread().isInterrupted()) return;
                if (!pauseState.awaitActiveDuration(0)) return;
                queue.put(ingredient);
            }
        }
    }

    public List<Ingredient> generateIngredientsRandom(ingredientFetchListener listener){
        if (closed.get()) return new ArrayList<>();
        //Generates initial ingredient set up to maxCap
        synchronized (availableLock) {
            for (int i = 0; i < maxCap; i++) {
                if (availableList.isEmpty()) {
                    break;
                }

                int randomIndex = random.nextInt(availableList.size());

                // remove ingredient to prevent duplicates
                usedList.add(availableList.remove(randomIndex));
            }
        }
        updateBaskets(listener);
        return usedList;
    }

    private void fetchIngredient(Ingredient returnIngredient,Ingredient ingredient, ingredientFetchListener listener) throws InterruptedException {
        //swaps the ingredient using a timer
        Log.d(TAG,"Starting fetch");
        //In ms, currently 3 seconds
        int fetchTime = 3000;
        for (int i = 1; i<= fetchTime /1000; i++){
                    if (closed.get() || !pauseState.awaitActiveDuration(1000)) return;
                    if (!deliverProgress(listener, i)) return;
        }
        if (closed.get()) return;
        Ingredient result = null;
        // get input ingredient from the available list
        final Ingredient[] swapped = new Ingredient[1];
        while (swapped[0] == null && !closed.get()) {
            if (!pauseState.runIfRunning(() -> {
                synchronized (availableLock) {
                    if (closed.get()) return;
                    if (usedList.contains(returnIngredient) && availableList.contains(ingredient)) {
                        int swapItemIndex=availableList.indexOf(ingredient);
                        availableList.set(swapItemIndex,returnIngredient);
                        int returnItemIndex=usedList.indexOf(returnIngredient);
                        usedList.set(returnItemIndex,ingredient);
                        swapped[0]=ingredient;
                    }
                }
            })) {
                if (!pauseState.awaitActiveDuration(0)) return;
            } else break;
        }
        result = swapped[0];
        if (result != null) {
            Log.d(TAG,"Swapped "+returnIngredient.getName()+" for "+ingredient.getName());
        }else{
            Log.d(TAG,"Failed to swap  "+returnIngredient.getName());
        }

        if (closed.get() || !deliverProgress(listener, fetchTime /1000+1)) return;
        Log.d(TAG,"Starting update Baskets");
        updateBaskets(listener);
    }

    private boolean deliverProgress(ingredientFetchListener listener, int progress) throws InterruptedException {
        if (listener == null) return !closed.get();
        while (!closed.get()) {
            if (pauseState.runIfRunning(() -> listener.fetchIngredientProgressUpdate(progress))) return true;
            if (!pauseState.awaitActiveDuration(0)) return false;
        }
        return false;
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        queue.close();
        synchronized (availableLock) { }
        executor.shutdownNow();
        basketFiller.close();
    }

    boolean isClosed() { return closed.get(); }
    boolean isFetchingForTest() { return fetching; }
    List<Ingredient> getUsedListForTest() {
        synchronized (availableLock) { return new ArrayList<>(usedList); }
    }
}
