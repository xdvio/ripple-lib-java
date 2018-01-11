package com.ripple.config;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;

public class Config {
    private static boolean bouncyInitiated = false;
    private static final Object lock = new Object();
    static public void initBouncy() {
        synchronized (lock) {
            if (!bouncyInitiated) {
                // For android
                Security.removeProvider("BC");
                Security.addProvider(new BouncyCastleProvider());
                bouncyInitiated = true;
            }
        }
    }
}
