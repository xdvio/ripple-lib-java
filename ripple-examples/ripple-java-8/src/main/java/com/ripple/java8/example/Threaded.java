package com.ripple.java8.example;

import com.ripple.client.Client;
import com.ripple.client.transport.impl.JavaWebSocketTransportImpl;

import static com.ripple.java8.utils.Print.print;

public class Threaded {
    private static final Object lock = new Object();

    public static void main(String[] args) throws InterruptedException {
        Client client = new Client(new JavaWebSocketTransportImpl());
        client.connect("wss://s1.ripple.com", (c) -> {
            synchronized (lock) {
                lock.notify();
            }
        });
        print("connected={0}", client.connected);
        synchronized (lock) {
            lock.wait(5000);
        }
        print("connected={0}", client.connected);
    }
}
