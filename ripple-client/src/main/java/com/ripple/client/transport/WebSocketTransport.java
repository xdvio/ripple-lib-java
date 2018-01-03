package com.ripple.client.transport;

import org.json.JSONObject;

import java.net.URI;

public interface WebSocketTransport {
    void setHandler(TransportEventHandler events);
    void sendMessage(JSONObject msg);
    void connect(URI url);
    void disconnect();
}
