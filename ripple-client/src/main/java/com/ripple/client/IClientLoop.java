package com.ripple.client;

public interface IClientLoop {
    boolean runningOnClientThread();

    /**
     * If {@link IClientLoop#runningOnClientThread()} then run straight away
     * otherwise submit task to run ASAP.
     * @param runnable - the block of code to run
     */
    void run(Runnable runnable);
    default void runAndWait(Runnable runnable) {
        run(runnable);
    }

    void schedule(long ms, Runnable runnable);

    void start(String clientName);
    void stop();
}
