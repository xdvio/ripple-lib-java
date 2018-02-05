package com.ripple.client;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ScheduledExecutorServiceLoop implements IClientLoop {
    ScheduledExecutorServiceLoop() {}
    private String clientName;
    private Thread clientThread;
    private ScheduledExecutorService service;

    public boolean runningOnClientThread() {
        return clientThread != null && Thread.currentThread().getId() ==
                clientThread.getId();
    }

    private void prepareExecutor() {
        service = new ScheduledThreadPoolExecutor(1, r -> {
            clientThread = new Thread(r);
            clientThread.setName(clientName + "-thread");
            return clientThread;
        });
    }

    @Override
    public void run(Runnable runnable) {
        if (service == null) {
            throw new IllegalStateException("Must start executor");
        }

        if (runningOnClientThread()) {
            runnable.run();
        } else {
            service.submit(runnable);
        }
    }

    @Override
    public void runAndWait(Runnable runnable) {
        if (runningOnClientThread()) {
            runnable.run();
        } else {
            try {
                service.submit(runnable).get();
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void schedule(long ms, Runnable runnable) {
        if (service == null) {
            throw new IllegalStateException("Must start executor");
        }
        service.schedule(runnable, ms, TimeUnit.MILLISECONDS);
    }

    @Override
    public void start(String clientName) {
        this.clientName = clientName;
        this.prepareExecutor();
    }

    @Override
    public void stop() {
        try {
            service.shutdownNow();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
