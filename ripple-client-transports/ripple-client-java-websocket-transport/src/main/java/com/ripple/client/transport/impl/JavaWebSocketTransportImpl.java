package com.ripple.client.transport.impl;

import com.ripple.client.transport.TransportEventHandler;
import com.ripple.client.transport.WebSocketTransport;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

class WS extends WebSocketClient {
    Logger logger = Logger.getLogger(WS.class.getName());

    TransportEventHandler h2;

    WS(URI serverURI) {
        super(serverURI, new Draft_6455());
    }

    public void muteEventHandler() {
        logger.log(Level.FINE,"muting handler!");
        h2 = null;
    }

    public void setEventHandler(TransportEventHandler eventHandler) {
        h2 = eventHandler;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        TransportEventHandler handler = getHandler();
        if (handler != null) {
            handler.onConnected();
        }
    }

    private TransportEventHandler getHandler() {
        return h2;
    }

    @Override
    public void onMessage(String message) {
        TransportEventHandler handler = getHandler();
        if (handler != null) {
            handler.onMessage(new JSONObject(message));
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.log(Level.FINE, "code = [" + code + "], reason = [" + reason + "], remote = [" + remote + "]" + getTime());
        TransportEventHandler handler = getHandler();
        if (handler != null) {
            handler.onDisconnected();
        } else {
            logger.log(Level.FINE,"handler is null!");
        }
    }

    private String getTime() {
        return " @  " + new Date();
    }

    @Override
    public void onError(Exception ex) {
        logger.log(Level.FINE, "ex = [" + ex + "]" + getTime());
        TransportEventHandler handler = getHandler();
        if (handler != null) {
            handler.onError(ex);
        }
    }
}

public class JavaWebSocketTransportImpl implements WebSocketTransport {
    private WeakReference<TransportEventHandler> handler;
    private WS client = null;

    @Override
    public void setHandler(TransportEventHandler events) {
        handler = new WeakReference<>(events);
        if (client != null) {
            client.setEventHandler(events);
        }
    }

    @Override
    public void sendMessage(JSONObject msg) {
        client.send(msg.toString());
    }

    @Override
    public void connect(URI uri) {
        TransportEventHandler curHandler = handler.get();
        if (curHandler == null) {
            throw new RuntimeException("must call setEventHandler() before connect(...)");
        }
        disconnect();
        client = new WS(uri);

        client.setEventHandler(curHandler);
        curHandler.onConnecting();
        client.connect();
    }

    @Override
    public void disconnect() {
        if (client != null) {
            client.muteEventHandler();
            client.close();
            client = null;
        }
    }
}
