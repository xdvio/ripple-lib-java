package com.ripple.client;

import java.util.Iterator;
import java.util.PriorityQueue;

public class MockLoop implements IClientLoop {
    private PriorityQueue<Callback> queue;
    private int ms = 0;

    MockLoop() {
        queue = new PriorityQueue<>();
    }

    public class Callback implements Comparable<Callback> {
        long when;

        Runnable runnable;
        public Callback(Runnable runnable, long delay) {
            this.when = ms + delay;
            this.runnable = runnable;
        }

        @Override
        public int compareTo(Callback o) {
            return Long.compare(when, o.when);
        }
    }

    @Override
    public boolean runningOnClientThread() {
        // TODO:
        return true;
    }

    @Override
    public void run(Runnable runnable) {
        // run these instantly ...
        runnable.run();
    }

    public void schedule(long delay, Runnable runnable) {
        queue.add(new Callback(runnable, delay));
    }

    @Override
    public void start(String clientName) {
        //
    }

    @Override
    public void stop() {
        //
    }

    void tick(int pass) {
        ms += pass;
        Iterator<Callback> iterator = queue.iterator();
        while (iterator.hasNext()) {
            Callback next = iterator.next();
            if (next.when <= ms) {
                try {
                    next.runnable.run();
                } catch (Exception ignored) {
                    throw new RuntimeException(ignored);
                } finally {
                    iterator.remove();
                }
            } else {
                break;
            }
        }
    }

}
