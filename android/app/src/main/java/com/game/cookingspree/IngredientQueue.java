package com.game.cookingspree;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class IngredientQueue {
    //Queue replaces Ingredient Inventory, will fetch from Ingredient Fetch Worker to be consumed to fill the baskets at Basket Filler
    //Uses FIFO to maintain order between baskets and fetch worker used list
    private final Queue<Ingredient> queue = new LinkedList<>();
    private final int capacity;
    private final Lock queueLock = new ReentrantLock();
    private final Condition notEmpty = queueLock.newCondition();
    private final Condition notFull = queueLock.newCondition();
    private boolean closed;

    public IngredientQueue(int capacity) {
        this.capacity = capacity;
    }

    public void put(Ingredient ingredient) throws InterruptedException {
        //Standard producer put method
        queueLock.lock();
        try {
            if (closed) throw new InterruptedException("queue closed");
            while (queue.size() == capacity) {
                //Should never be full if full there is an error
                notFull.await();
                if (closed) throw new InterruptedException("queue closed");
            }

            queue.add(ingredient);
            notEmpty.signal();
        } finally {
            queueLock.unlock();
        }
    }

    public Ingredient take() throws InterruptedException {
        //Standard consumer take function
        queueLock.lock();
        try {
            while (queue.isEmpty()) {
                if (closed) throw new InterruptedException("queue closed");
                //Queue should be empty until fetch worker adds the first ingredient in
                notEmpty.await();
            }
            if (closed) throw new InterruptedException("queue closed");

            Ingredient ingredient = queue.poll();//Take from front for FIFO

            notFull.signal();
            return ingredient;
        } finally {
            queueLock.unlock();
        }
    }

    public void close() {
        queueLock.lock();
        try {
            if (closed) return;
            closed = true;
            queue.clear();
            notEmpty.signalAll();
            notFull.signalAll();
        } finally {
            queueLock.unlock();
        }
    }

    public int size() {
        queueLock.lock();
        try {
            return queue.size();
        } finally {
            queueLock.unlock();
        }
    }
}
