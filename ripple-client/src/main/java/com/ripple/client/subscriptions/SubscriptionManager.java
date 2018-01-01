package com.ripple.client.subscriptions;

import com.ripple.client.pubsub.Publisher;
import com.ripple.core.coretypes.AccountID;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

public class SubscriptionManager extends Publisher<SubscriptionManager.events> {
    public void pauseEventEmissions() {
        paused = true;
    }

    public void unpauseEventEmissions() {
        paused = false;
    }

    public interface events<T>      extends Publisher.Callback<T> {}

    public interface OnSubscribed extends events<JSONObject> {}
    public interface OnUnSubscribed extends events<JSONObject> {}

    public SubscriptionManager onSubscribed(OnSubscribed onSubscribed) {
        on(OnSubscribed.class, onSubscribed);
        return this;
    }
    public SubscriptionManager onceSubscribed(OnSubscribed onSubscribed) {
        once(OnSubscribed.class, onSubscribed);
        return this;
    }
    public SubscriptionManager onUnSubscribed(OnUnSubscribed onUnSubscribed) {
        on(OnUnSubscribed.class, onUnSubscribed);
        return this;
    }
    public SubscriptionManager onceUnSubscribed(OnUnSubscribed onUnSubscribed) {
        once(OnUnSubscribed.class, onUnSubscribed);
        return this;
    }


    public boolean paused = false;

    public enum Stream {
        server,
        ledger,
        transactions,
        validations,
        transactions_propose
    }

    Set<Stream>                  streams = new TreeSet<Stream>();
    Set<AccountID>              accounts = new TreeSet<AccountID>();

    <T> Set<T> single(T element) {
        Set<T> set = new TreeSet<T>();
        set.add(element);
        return set;
    }

    public void addStream(Stream s) {
        streams.add(s);
        subscribeStream(s);
    }

    public void removeStream(Stream s) {
        streams.remove(s);
        unsubscribeStream(s);
    }

    private void subscribeStream(Stream s) {
       emit(OnSubscribed.class, basicSubscriptionObject(single(s), null));
    }

    @Override
    public <A, T extends Callback<A>> int emit(Class<T> key, A args) {
        if (paused) {
            return 0;
        }
        return super.emit(key, args);
    }

    private void unsubscribeStream(Stream s) {
        emit(OnUnSubscribed.class, basicSubscriptionObject(single(s), null));
    }

    public void addAccount(AccountID a) {
        accounts.add(a);
        emit(OnSubscribed.class, basicSubscriptionObject(null, single(a)));
    }
    public void removeAccount(AccountID a) {
        accounts.remove(a);
        emit(OnUnSubscribed.class, basicSubscriptionObject(null, single(a)));
    }

    private JSONObject basicSubscriptionObject(Set<Stream> streams, Set<AccountID> accounts) {
        JSONObject subs = new JSONObject();
        if (streams != null && streams.size() > 0) subs.put("streams", getJsonArray(streams));
        if (accounts != null && accounts.size() > 0) subs.put("accounts", getJsonArray(accounts));
        return subs;
    }

    private JSONArray getJsonArray(Collection<?> streams) {
        // Yes, JSONArray has a Collection constructor, but it doesn't play
        // so nicely on android.
        JSONArray jsonArray = new JSONArray();
        for (Object obj : streams) {
            jsonArray.put(obj);
        }

        return jsonArray;
    }

    public JSONObject allSubscribed() {
        return basicSubscriptionObject(streams, accounts);
    }
}
