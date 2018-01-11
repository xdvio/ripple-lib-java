package com.ripple.client;

public interface IClientLoop {
    boolean runningOnClientThread();
    void run(Runnable runnable);
    void schedule(long ms, Runnable runnable);

    void start(String clientName);
    void stop();
}
