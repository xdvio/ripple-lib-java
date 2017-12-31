package com.ripple.client.transport;

import org.json.JSONObject;

public interface TransportEventHandler {
    void onMessage(JSONObject msg);
    void onConnecting();
    void onDisconnected();
    void onError(Exception error);
    void onConnected();
}
