package com.ripple.client.transport;

import org.json.JSONObject;

import java.net.URI;

public interface WebSocketTransport {
    void setHandler(TransportEventHandler events);
    void sendMessage(JSONObject msg);
    void connect(URI url);
    /**
     * It's the responsibility of implementations to trigger
     * {@link com.ripple.client.transport.TransportEventHandler#onDisconnected}
     */
    void disconnect();
}
