package com.ripple.client.requests;

import com.ripple.client.Client;
import com.ripple.client.enums.Command;
import com.ripple.client.pubsub.Publisher;
import com.ripple.client.responses.Response;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.logging.Logger;

// We can just shift to using delegation
public class Request extends Publisher<Request.events> {
    // com.ripple.client.requests.Request // ??
    public static final Logger logger = Logger.getLogger(Request.class.getName());
    public static final long TIME_OUT = 60000;
    static public final int VALIDATED_LEDGER = -3;
    static public final int CLOSED_LEDGER = -2;
    static public final int OPEN_LEDGER = -1;

    public void json(JSONObject jsonObject) {
        Iterator keys = jsonObject.keys();
        while (keys.hasNext()) {
            String next = (String) keys.next();
            json(next, jsonObject.opt(next));
        }
    }

    public static interface Builder<T> {
        void beforeRequest(Request request);
        T buildTypedResponse(Response response);
    }

    // Base events class and aliases
    public interface events<T>  extends Publisher.Callback<T> {}
    public interface OnSuccess  extends events<Response> {}
    public interface OnError    extends events<Response> {}
    public interface OnResponse extends events<Response> {}
    public interface OnTimeout   extends events<Response> {}

    public Request onResponse(OnResponse onResponse) {
        on(OnResponse.class, onResponse);
        return this;
    }
    public Request onceResponse(OnResponse onResponse) {
        once(OnResponse.class, onResponse);
        return this;
    }
    public Request onSuccess(OnSuccess onSuccess) {
        on(OnSuccess.class, onSuccess);
        return this;
    }
    public Request onceSuccess(OnSuccess onSuccess) {
        once(OnSuccess.class, onSuccess);
        return this;
    }
    public Request onTimeout(OnTimeout onTimeout) {
        on(OnTimeout.class, onTimeout);
        return this;
    }
    public Request onceTimeout(OnTimeout onTimeout) {
        once(OnTimeout.class, onTimeout);
        return this;
    }
    public Request onError(OnError onError) {
        on(OnError.class, onError);
        return this;
    }
    public Request onceError(OnError onError) {
        once(OnError.class, onError);
        return this;
    }

    public static abstract class Manager<T> {
        abstract public void cb(Response response, T t) throws JSONException;

        public boolean retryOnUnsuccessful(Response r) {
            return false;
        }

        public void beforeRequest(Request r) {}
    }

    Client client;
    public Command           cmd;
    public Response     response;
    private JSONObject      json;
    public int                id;
    public long         sendTime;

    /**
     * Set this to the client.connectionCount so that the request will only
     * be sent if connected, rather than retried on connection drops, exceptions
     * etc.
     */
    public long connectionAffinity = -1;

    public Request(Command command, int assignedId, Client client) {
        this.client = client;
        cmd         = command;
        id          = assignedId;
        json        = new JSONObject();

        json("command", cmd.toString());
        json("id",      assignedId);
    }

    public JSONObject json() {
        return json;
    }

    public void json(String key, Object value) {
        json.put(key, value);
    }

    public void request() {
        client.nowOrWhenConnected(client_ -> client.sendRequest(Request.this));
    }

    public  void bumpSendTime() {
        sendTime = System.currentTimeMillis();
    }

    public JSONObject toJSON() {
        return json();
    }

    public JSONObject jsonRepr() {
        JSONObject repr = new JSONObject();
        if (response != null) {
            repr.put("response", response.message);
        }
        // Copy this
        repr.put("request", new JSONObject(json.toString()));
        return repr;
    }

    public void handleResponse(JSONObject msg) {
        response = new Response(this, msg);

        if (response.succeeded) {
            emit(OnSuccess.class, response);
        } else {
            emit(OnError.class, response);
        }

        emit(OnResponse.class, response);
    }

}
