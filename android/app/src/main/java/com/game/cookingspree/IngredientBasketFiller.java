package com.game.cookingspree;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class IngredientBasketFiller {
    //Fills baskets off IngredientFetchWorker's input into Ingredient Queue
    //To maintain index order with used List in fetch worker must maintain FIFO principles
    private static final String TAG = "Basket Filler";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final IngredientQueue queue;
    private final BasketManager basketManager;
    private final int fillSize;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object mutationLock = new Object();
    private volatile boolean waitingForIngredient;

    public interface BasketFillListener{
        void finishedBasketFilling(List<Ingredient> fillOrder);
    }

    public IngredientBasketFiller(IngredientQueue queue, BasketManager basketManager,Integer fillSize) {
        this.queue = queue;
        this.basketManager = basketManager;
        this.fillSize=fillSize;
    }

    public void startFilling(BasketFillListener listener) {
        if (closed.get()) return;
        try {
            executor.submit(() -> {
                List<Ingredient> fillOrder=new ArrayList<>();

                for (int i=fillSize-1;i> -1;i--){
                        // Take ingredient from queue (blocks if no ingredient inside)
                         Ingredient ingredient=null;
                         try {
                             waitingForIngredient = true;
                             ingredient = queue.take();
                         } catch (InterruptedException e) {
                             Thread.currentThread().interrupt();
                             return;
                         } finally {
                             waitingForIngredient = false;
                         }

                         if (closed.get() || Thread.currentThread().isInterrupted()) return;
                         if (ingredient!=null){
                             synchronized (mutationLock) {
                                 if (closed.get() || Thread.currentThread().isInterrupted()) return;
                                 basketManager.updateBasketContents(i, ingredient);
                             }

                             Log.d(TAG, "filled basket with: " + ingredient.getName() + " at basket " + i);
                             fillOrder.add(ingredient);
                         }else{
                             Log.e(TAG,"Failed to add an ingredient at basket"+i);
                         }
                }
                if (!closed.get() && !Thread.currentThread().isInterrupted() && listener != null) {
                    listener.finishedBasketFilling(fillOrder);
                }
            });
        } catch (RejectedExecutionException ignored) {
            // Closure won the race with submission.
        }
    }

    public void close() {
        synchronized (mutationLock) {
            if (!closed.compareAndSet(false, true)) return;
        }
        queue.close();
        executor.shutdownNow();
    }

    boolean isWaitingForIngredientForTest() { return waitingForIngredient; }
    boolean isClosed() { return closed.get(); }
    boolean isTerminatedForTest() { return executor.isTerminated(); }
}
