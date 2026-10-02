package com.game.cookingspree;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicInteger;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private static final long STOP_WAIT_MS = 300;
    private final Object threadLock = new Object();
    private volatile Thread gameThread;
    private volatile boolean isRunning;
    private volatile Game game;
    private volatile boolean surfaceAvailable;
    private volatile boolean permanentlyClosed;
    private final AtomicInteger activeLoops = new AtomicInteger();
    private final AtomicInteger maxConcurrentLoops = new AtomicInteger();
    private final AtomicInteger renderLoopStarts = new AtomicInteger();
    private final AtomicInteger surfaceCreateCallbacks = new AtomicInteger();
    private final AtomicInteger surfaceDestroyCallbacks = new AtomicInteger();

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        getHolder().addCallback(this);
    }

    public void init(Game game) {
        this.game = game;
        if (game != null && !permanentlyClosed && getHolder().getSurface().isValid()) {
            surfaceAvailable = true;
            startRenderThread();
        }
    }

    @Override public void surfaceCreated(@NonNull SurfaceHolder holder) {
        surfaceCreateCallbacks.incrementAndGet();
        if (permanentlyClosed) return;
        surfaceAvailable = true;
        startRenderThread();
    }

    private void startRenderThread() {
        synchronized (threadLock) {
            if (game == null || permanentlyClosed || !surfaceAvailable
                    || (gameThread != null && gameThread.isAlive())) return;
            isRunning = true;
            Thread thread = new Thread(this, "CookingSpree-Render");
            gameThread = thread;
            thread.start();
        }
    }

    @Override public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        surfaceDestroyCallbacks.incrementAndGet();
        surfaceAvailable = false;
        stopRendering();
    }

    public void shutdown() {
        permanentlyClosed = true;
        surfaceAvailable = false;
        stopRendering();
    }

    public void stopRendering() {
        Thread thread;
        synchronized (threadLock) {
            isRunning = false;
            thread = gameThread;
            if (thread != null) thread.interrupt();
        }
        if (thread == null || thread == Thread.currentThread()) return;
        try {
            thread.join(STOP_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (threadLock) {
            if (!thread.isAlive() && gameThread == thread) gameThread = null;
        }
    }

    @Override public void run() {
        Thread thisThread = Thread.currentThread();
        int concurrent = activeLoops.incrementAndGet();
        maxConcurrentLoops.accumulateAndGet(concurrent, Math::max);
        renderLoopStarts.incrementAndGet();
        try {
            while (isRunning && !thisThread.isInterrupted()) {
                Game current = game;
                if (current == null) break;
                current.update();
                current.draw();
                try {
                    Thread.sleep(current.getSleepTime());
                } catch (InterruptedException e) {
                    thisThread.interrupt();
                    break;
                }
            }
        } catch (RuntimeException e) {
            Log.e("GameView", "Render loop stopped after an update or draw failure", e);
        } finally {
            activeLoops.decrementAndGet();
            boolean restart;
            synchronized (threadLock) {
                isRunning = false;
                if (gameThread == thisThread) gameThread = null;
                restart = surfaceAvailable && !permanentlyClosed && game != null;
            }
            if (restart) startRenderThread();
        }
    }

    int activeRenderLoopsForTest() { return activeLoops.get(); }
    int maxConcurrentRenderLoopsForTest() { return maxConcurrentLoops.get(); }
    int renderLoopStartsForTest() { return renderLoopStarts.get(); }
    int surfaceCreateCallbacksForTest() { return surfaceCreateCallbacks.get(); }
    int surfaceDestroyCallbacksForTest() { return surfaceDestroyCallbacks.get(); }
    boolean isPermanentlyClosedForTest() { return permanentlyClosed; }

    public void useCanvas(Game.CanvasCallback callback) {
        Canvas canvas = null;
        try {
            canvas = getHolder().lockCanvas();
            if (canvas != null && callback != null) callback.draw(canvas);
        } catch (RuntimeException e) {
            Log.w("GameView", "Canvas draw skipped", e);
        } finally {
            if (canvas != null) {
                try { getHolder().unlockCanvasAndPost(canvas); }
                catch (RuntimeException e) { Log.w("GameView", "Canvas post skipped", e); }
            }
        }
    }

    @Override public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) { }
}
