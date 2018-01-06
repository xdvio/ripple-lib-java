package com.ripple.client;

import com.ripple.client.enums.Command;
import com.ripple.client.enums.Message;
import com.ripple.client.enums.RPCErr;
import com.ripple.client.pubsub.Publisher;
import com.ripple.client.requests.Request;
import com.ripple.client.responses.Response;
import com.ripple.client.subscriptions.ServerInfo;
import com.ripple.client.subscriptions.SubscriptionManager;
import com.ripple.client.subscriptions.TrackedAccountRoot;
import com.ripple.client.subscriptions.TransactionSubscriptionManager;
import com.ripple.client.transactions.AccountTxPager;
import com.ripple.client.transactions.TransactionManager;
import com.ripple.client.transport.TransportEventHandler;
import com.ripple.client.transport.WebSocketTransport;
import com.ripple.client.types.AccountLine;
import com.ripple.core.coretypes.AccountID;
import com.ripple.core.coretypes.Issue;
import com.ripple.core.coretypes.STObject;
import com.ripple.core.coretypes.hash.Hash256;
import com.ripple.core.coretypes.uint.UInt32;
import com.ripple.core.types.known.sle.LedgerEntry;
import com.ripple.core.types.known.sle.entries.AccountRoot;
import com.ripple.core.types.known.sle.entries.Offer;
import com.ripple.core.types.known.tx.result.TransactionResult;
import com.ripple.crypto.keys.IKeyPair;
import com.ripple.crypto.Seed;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.ripple.client.requests.Request.Manager;
import static com.ripple.client.requests.Request.VALIDATED_LEDGER;

public class Client extends Publisher<Client.events> {
    private static AtomicInteger clients = new AtomicInteger();

    /**
     * Using an inner class so the methods aren't public.
     * This is the easiest refactoring from the prior case where
     * the Client implemented the TransportEventHandler directly
     */
    protected class InnerWebSocketHandler implements TransportEventHandler {
        /**
         * This is to ensure we run everything on {@link Client#clientThread}
         */
        @Override
        public void onMessage(final JSONObject msg) {
            resetReconnectStatus();
            run(() -> onMessageInClientThread(msg));
        }

        @Override
        public void onConnecting() {
            log(Level.FINE, "socket.onConnecting");
        }

        @Override
        public void onError(Exception error) {
            log(Level.WARNING, "error {0}", error);
            onException(error);
        }

        @Override
        public void onDisconnected() {
            run(Client.this::doOnDisconnected);
        }

        @Override
        public void onConnected() {
            run(Client.this::doOnConnected);
        }
    }

    public static final Logger logger = Logger.getLogger(Client.class.getName());

    private boolean logMessages = false;
    private long connectionCount = 0;

    // Events
    public interface events<T> extends Publisher.Callback<T> {}
    public interface OnLedgerClosed extends events<ServerInfo> {}
    public interface OnConnected extends events<Client> {}
    public interface OnStateChange extends events<Client> {}
    public interface OnPathFind extends events<JSONObject> {}
    public interface OnValidatedTransaction extends events<TransactionResult> {}
    public interface OnDisconnected extends events<Client> {}
    public interface OnSubscribed extends events<ServerInfo> {}
    public interface OnMessage extends events<JSONObject> {}
    public interface OnSendMessage extends events<JSONObject> {}
    public interface OnValidationReceived extends events<JSONObject> {}

    // Fluent binders
    public Client onValidatedTransaction(OnValidatedTransaction cb) {
        on(OnValidatedTransaction.class, cb);
        return this;
    }

    public Client onValidationReceived(OnValidationReceived onValidationReceived) {
        on(OnValidationReceived.class, onValidationReceived);
        return this;
    }

    public Client onceValidationReceived(OnValidationReceived onValidationReceived) {
        once(OnValidationReceived.class, onValidationReceived);
        return this;
    }

    public Client onLedgerClosed(OnLedgerClosed cb) {
        on(OnLedgerClosed.class, cb);
        return this;
    }

    public Client onceLedgerClosed(OnLedgerClosed cb) {
        once(OnLedgerClosed.class, cb);
        return this;
    }

    public Client onConnected(OnConnected onConnected) {
        this.on(OnConnected.class, onConnected);
        return this;
    }

    // Event handler sugar
    public Client onDisconnected(OnDisconnected cb) {
        on(OnDisconnected.class, cb);
        return this;
    }

    public Client onceConnected(OnConnected onConnected) {
        once(OnConnected.class, onConnected);
        return this;
    }
    public Client onPathFind(OnPathFind onPathFind) {
        on(OnPathFind.class, onPathFind);
        return this;
    }
    public Client oncePathFind(OnPathFind onPathFind) {
        once(OnPathFind.class, onPathFind);
        return this;
    }
    public Client onMessage(OnMessage onMessage) {
        on(OnMessage.class, onMessage);
        return this;
    }
    public Client onceMessage(OnMessage onMessage) {
        once(OnMessage.class, onMessage);
        return this;
    }
    public Client onSendMessage(OnSendMessage onSendMessage) {
        on(OnSendMessage.class, onSendMessage);
        return this;
    }
    public Client onceSendMessage(OnSendMessage onSendMessage) {
        once(OnSendMessage.class, onSendMessage);
        return this;
    }
    public Client onSubscribed(OnSubscribed onSubscribed) {
        on(OnSubscribed.class, onSubscribed);
        return this;
    }
    public Client onceSubscribed(OnSubscribed onSubscribed) {
        once(OnSubscribed.class, onSubscribed);
        return this;
    }
    public Client onceValidatedTransaction(OnValidatedTransaction onValidatedTransaction) {
        once(OnValidatedTransaction.class, onValidatedTransaction);
        return this;
    }
    public Client onStateChange(OnStateChange onStateChange) {
        on(OnStateChange.class, onStateChange);
        return this;
    }
    public Client onceStateChange(OnStateChange onStateChange) {
        once(OnStateChange.class, onStateChange);
        return this;
    }
    public Client onceDisconnected(OnDisconnected onDisconnected) {
        once(OnDisconnected.class, onDisconnected);
        return this;
    }

    // ### Members
    // The implementation of the WebSocket
    private WebSocketTransport ws;

    /**
     * When this is non 0, we randomly disconnect when trying to send messages
     * See {@link Client#sendMessage}
     */
    @SuppressWarnings("FieldCanBeLocal")
    private double randomBugsFrequency = 0.0;
    private Random randomBugs = new Random();
    // When this is set, all transactions will be routed first to this, which
    // will then notify the client
    private TransactionSubscriptionManager transactionSubscriptionManager;

    // This is in charge of executing code in the `clientThread`
    @SuppressWarnings("WeakerAccess")
    protected ScheduledExecutorService service;
    // All code that use the Client api, must be run on this thread
    /**
     See {@link Client#run}
     */
    @SuppressWarnings("WeakerAccess")
    protected Thread clientThread;

    protected TreeMap<Integer, Request> requests = new TreeMap<>();

    // Give the client a name for debugging purposes
    private String name = "client-" + clients.incrementAndGet();

    // Keeps track of the `id` doled out to Request objects
    private int cmdIDs;
    // The last uri we were connected to
    private String previousUri;

    // Every x ms, we clean up timed out requests
    static private final long maintenanceSchedule = 10000; //ms

    // Are we currently connected?
    public boolean connected = false;
    // Are we currently reconnecting?
    private boolean reconnecting = false;

    // If we haven't received any message from the server after x many
    // milliseconds, disconnect and reconnect again.
    private long reconnectDormantAfter = 20000; // ms
    // ms since unix time of the last indication of an alive connection
    private long lastConnection = -1; // -1 means null
    // Did we disconnect manually? If not, try and reconnect
    private boolean manuallyDisconnected = false;

    // Tracks the serverInfo we are currently connected to
    public ServerInfo serverInfo = new ServerInfo();
    private HashMap<AccountID, Account> accounts = new HashMap<>();
    // Handles [un]subscription requests, also on reconnect
    public SubscriptionManager subscriptions = new SubscriptionManager();

    // Constructor
    public Client(WebSocketTransport transport) {
        ws = transport;
        ws.setHandler(new InnerWebSocketHandler());

        prepareExecutor();
        // requires executor, so call after prepareExecutor
        scheduleMaintenance();

        subscriptions.onSubscribed(subscription -> {
            // On connection we will subscribe
            if (!connected)
                return;
            // Change subscription upon changes!
            // There is no unsubscribe ...
            subscribe(subscription);
        });
    }

    // ### Getters
    private int reconnectDelay() {
        return 1000;
    }

    public boolean isManuallyDisconnected() {
        return manuallyDisconnected;
    }

    // ### Setters

    public void reconnectDormantAfter(long reconnectDormantAfter) {
        this.reconnectDormantAfter = reconnectDormantAfter;
    }

    public Client transactionSubscriptionManager(TransactionSubscriptionManager transactionSubscriptionManager) {
        this.transactionSubscriptionManager = transactionSubscriptionManager;
        return this;
    }

    // ### Helpers

    private static void log(Level level, String fmt, Object... args) {
        if (logger.isLoggable(level)) {
            logger.log(level, fmt, args);
        }
    }

    private static String prettyJSON(JSONObject object) {
        return object.toString(4);
    }

    /* --------------------------- CONNECT / RECONNECT -------------------------- */

    /**
     * After calling this method, all subsequent interaction with the api should
     * be called via posting Runnable() run blocks to the Executor.
     *
     * Essentially, all ripple-lib-java api interaction
     * should happen on the one thread.
     *
     * @see InnerWebSocketHandler#onMessage(org.json.JSONObject)
     */
    public Client connect(final String uri) {
        manuallyDisconnected = false;

        run(() -> doConnect(uri));
        return this;
    }

    public String name() {
        return name;
    }

    public Client name(String name) {
        this.name = name;
        return this;
    }


    private void doConnect(String uri) {
        if (connected) throw new IllegalStateException(
                "tried to connect when already connected");
        log(Level.INFO, "{0} Connecting to {1}", name(), uri);
        previousUri = uri;
        ws.connect(URI.create(uri));
    }

    @SuppressWarnings("WeakerAccess")
    public void disconnect() {
        if (!connected) {
            log(Level.WARNING,
                    "called disconnect when not connected");
        }
        manuallyDisconnected = true;
        // Emit events, maybe reschedule connects etc
        ws.disconnect();
        doOnDisconnected();
    }

    private void emitOnDisconnected() {
        // This ensures that the callback method onDisconnect is
        // called before a new connection is established this keeps
        // the symmetry of connect-> disconnect -> reconnect
        emit(OnDisconnected.class, this);
    }

    /**
     * This will detect stalled connections When connected we are subscribed to
     * a ledger, and ledgers should be at most 20 seconds apart.
     *
     * This also
     */
    private void scheduleMaintenance() {
        schedule(maintenanceSchedule, () -> {
            try {
                manageTimedOutRequests();
                int defaultValue = -1;

                if (!manuallyDisconnected) {
                    if (connected && lastConnection != defaultValue) {
                        long time = new Date().getTime();
                        long msSince = time - lastConnection;
                        if (msSince > reconnectDormantAfter) {
                            lastConnection = defaultValue;
                            if (!reconnecting) {
                                reconnect();
                            }
                        }
                    }
                }
                addRandomDisconnects();
            } finally {
                scheduleMaintenance();
            }
        });
    }

    @SuppressWarnings("WeakerAccess")
    public void reconnect() {
        if (reconnecting) {
            log(Level.WARNING, "already reconnecting!");
            return;
        }
        disconnect();
        connect(previousUri);
    }

    private void manageTimedOutRequests() {
        long now = System.currentTimeMillis();
        ArrayList<Request> timedOut = new ArrayList<>();

        for (Request request : requests.values()) {
            if (request.sendTime != 0) {
                long since = now - request.sendTime;
                if (since >= Request.TIME_OUT) {
                    timedOut.add(request);
                }
            }
        }
        for (Request request : timedOut) {
            request.emit(Request.OnTimeout.class, request.response);
            requests.remove(request.id);
        }
    }

    // ### Handler binders binder

    public void connect(final String s, final OnConnected onConnected) {
        run(() -> {
            connect(s);
            onceConnected(onConnected);
        });
    }

    public void disconnect(final OnDisconnected onDisconnected) {
        run(() -> {
            onceDisconnected(onDisconnected);
            disconnect();
        });
    }

    private void whenConnected(boolean nextTick, final OnConnected onConnected) {
        if (connected) {
            if (nextTick) {
                schedule(0, () -> {
                    onConnected.called(Client.this);
                });
            } else {
                // TODO: run ?
                // need an annotation ...
                onConnected.called(this);
            }
        }  else {
            once(OnConnected.class, onConnected);
        }
    }

    public void nowOrWhenConnected(OnConnected onConnected) {
        whenConnected(false, onConnected);
    }

    public void nextTickOrWhenConnected(OnConnected onConnected) {
        whenConnected(true, onConnected);
    }

    public void dispose() {
        int nListeners = clearAllListeners();
        List<Runnable> runnables = service.shutdownNow();
        log(Level.WARNING, "disposing {0} listeners and {1} queued tasks",
                nListeners, runnables.size());
        ws = null;
    }

    /* -------------------------------- EXECUTOR -------------------------------- */

    public void run(Runnable runnable) {
        // What if we are already in the client thread?? What happens then ?
        if (runningOnClientThread()) {
            errorHandling(runnable).run();
        } else {
            service.submit(errorHandling(runnable));
        }
    }

    public void schedule(long ms, Runnable runnable) {
        service.schedule(errorHandling(runnable), ms, TimeUnit.MILLISECONDS);
    }

    public boolean runningOnClientThread() {
        return clientThread != null && Thread.currentThread().getId() ==
                clientThread.getId();
    }

    protected void prepareExecutor() {
        service = new ScheduledThreadPoolExecutor(1, r -> {
            clientThread = new Thread(r);
            clientThread.setName(name + "-thread");
            return clientThread;
        });
    }

    public static abstract class ThrowingRunnable implements Runnable {
        public abstract void throwingRun() throws Exception;

        @Override
        public void run() {
            try {
                throwingRun();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private Runnable errorHandling(final Runnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (Exception e) {
                onException(e);
            }
        };
    }

    private void onException(Exception e) {
        e.printStackTrace(System.err);
        if (logger.isLoggable(Level.WARNING)) {
            log(Level.WARNING, "Exception {0}", e);
        }
    }

    private void resetReconnectStatus() {
        lastConnection = new Date().getTime();
    }

    private void updateServerInfo(JSONObject msg) {
        serverInfo.update(msg);
    }

    /* ----------------------- CLIENT THREAD EVENT HANDLER ---------------------- */

    void onMessageInClientThread(JSONObject msg) {
        Message type = Message.valueOf(msg.optString("type", null));

        try {
            emit(OnMessage.class, msg);
            if (logger.isLoggable(Level.FINER) && logMessages) {
                log(Level.FINER, "Receive `{0}`: {1}", type, prettyJSON(msg));
            } else {
                // Transactions are so common that we don't want to log them
                if (type != Message.transaction) {
                    log(Level.FINER,
                            "Receive `{0}`: {1}",
                            type,
                            slice(msg.toString(), 0, 1000));
                }
            }

            switch (type) {
                case serverStatus:
                    updateServerInfo(msg);
                    break;
                case ledgerClosed:
                    updateServerInfo(msg);
                    // TODO
                    emit(OnLedgerClosed.class, serverInfo);
                    break;
                case response:
                    onResponse(msg);
                    break;
                case transaction:
                    onTransaction(msg);
                    break;
                case path_find:
                    emit(OnPathFind.class, msg);
                    break;
                case validationReceived:
                    emit(OnValidationReceived.class, msg);
                    break;
                default:
                    unhandledMessage(msg);
                    break;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
            // This seems to be swallowed higher up, (at least by the
            // Java-WebSocket transport implementation)
            throw new RuntimeException(e);
        } finally {
            emit(OnStateChange.class, this);
        }
    }

    private String slice(String s, int i, int i1) {
        return s.substring(i, Math.min(i1, s.length()));
    }

    private void doOnDisconnected() {
        logger.entering(getClass().getName(), "doOnDisconnected");
        connected = false;
        emitOnDisconnected();
        maybeScheduleReconnect();

        logger.exiting(getClass().getName(), "doOnDisconnected");
    }

    private void maybeScheduleReconnect() {
        if (!manuallyDisconnected && !reconnecting) {
            reconnecting = true;
            schedule(reconnectDelay(), () -> {
                if (!manuallyDisconnected) {
                    connect(previousUri);
                }
                reconnecting = false;
            });
        } else {
            logger.fine("Currently disconnecting, so will not reconnect");
        }
    }

    private void doOnConnected() {
        resetReconnectStatus();
        connectionCount++;
        log(Level.INFO, "connection count {0}", connectionCount);

        logger.entering(getClass().getName(), "doOnConnected");
        connected = true;
        emit(OnConnected.class, this);

        subscribe(prepareSubscription());
        logger.exiting(getClass().getName(), "doOnConnected");
    }

    private void unhandledMessage(JSONObject msg) {
        log(Level.WARNING, "Unhandled message: " + msg);
    }

    private void onResponse(JSONObject msg) {
        Request request = requests.remove(msg.optInt("id", -1));

        if (request == null) {
            log(Level.WARNING, "Response without a request: {0}", msg);
            return;
        }
        request.handleResponse(msg);
    }

    private void onTransaction(JSONObject msg) {
        TransactionResult tr = new TransactionResult(msg, TransactionResult
                .Source
                .transaction_subscription_notification);
        if (tr.validated) {
            if (transactionSubscriptionManager != null) {
                // It's the subscription managers job to call
                // onTransactionResult
                transactionSubscriptionManager.notifyTransactionResult(tr);
            } else {
                onTransactionResult(tr);
            }
        }
    }

    public void onTransactionResult(TransactionResult tr) {
        if (logMessages) {
            log(Level.INFO, "Transaction {0} is validated", tr.hash);
        }
        Map<AccountID, AccountRoot> affected = tr.modifiedRoots();

        if (affected != null) {
            Hash256 transactionHash = tr.hash;
            UInt32 transactionLedgerIndex = tr.ledgerIndex;

            for (Map.Entry<AccountID, AccountRoot> entry : affected.entrySet()) {
                Account account = accounts.get(entry.getKey());
                if (account != null) {
                    STObject rootUpdates = entry.getValue();
                    account.getAccountRoot()
                            .updateFromTransaction(
                                    transactionHash, transactionLedgerIndex, rootUpdates);
                }
            }
        }

        Account initator = accounts.get(tr.initiatingAccount());
        if (initator != null) {
            log(Level.INFO, "Found initiator {0}, notifying transactionManager", initator);
            initator.transactionManager().notifyTransactionResult(tr);
        } else {
            if (logMessages) {
                log(Level.FINE, "Can't find initiating account!");
            }
        }
        emit(OnValidatedTransaction.class, tr);
    }

    private void sendMessage(JSONObject object) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Send: {0}", prettyJSON(object));
        }
        emit(OnSendMessage.class, object);
        ws.sendMessage(object);
        // addRandomDisconnects();
    }

    private void addRandomDisconnects() {
        if (!connected) {
            return;
        }
        if (randomBugsFrequency != 0) {
            if (randomBugs.nextDouble() > (1D - randomBugsFrequency)) {
                disconnect();
                schedule(randomBugs.nextInt(2000),
                        () -> connect(previousUri));

                String msg = "I disconnected you, now I'm gonna throw, " +
                        "deal with it suckah! ;)";
                logger.warning(msg);
                throw new RuntimeException(msg);
            }

        }
    }

    /* -------------------------------- ACCOUNTS -------------------------------- */

    public Account accountFromSeed(String masterSeed) {
        IKeyPair kp = Seed.fromBase58(masterSeed).keyPair();
        return account(AccountID.fromKeyPair(kp), kp);
    }

    public Account account(final AccountID id, IKeyPair keyPair) {
        if (accounts.containsKey(id)) {
            return accounts.get(id);
        } else {
            TrackedAccountRoot accountRoot = accountRoot(id);
            Account account = new Account(
                    id,
                    keyPair,
                    accountRoot,
                    new TransactionManager(this, accountRoot, id, keyPair)
            );
            accounts.put(id, account);
            subscriptions.addAccount(id);

            return account;
        }
    }

    private TrackedAccountRoot accountRoot(AccountID id) {
        TrackedAccountRoot accountRoot = new TrackedAccountRoot();
        requestAccountRoot(id, accountRoot);
        return accountRoot;
    }

    private void requestAccountRoot(final AccountID id,
                                    final TrackedAccountRoot accountRoot) {

        makeManagedRequest(Command.ledger_entry, new Manager<JSONObject>() {
            @Override
            public boolean retryOnUnsuccessful(Response r) {
                return r == null || r.rpcerr == null || r.rpcerr != RPCErr.entryNotFound;
            }

            @Override
            public void cb(Response response, JSONObject jsonObject) throws JSONException {
                if (response.succeeded) {
                    accountRoot.setFromJSON(jsonObject);
                } else {
                    log(Level.INFO, "Unfunded account: {0}", response.message);
                    accountRoot.setUnfundedAccount(id);
                }
            }
        }, new Request.Builder<JSONObject>() {
            @Override
            public void beforeRequest(Request request) {
                request.json("account_root", id);
            }

            @Override
            public JSONObject buildTypedResponse(Response response) {
                return response.result.getJSONObject("node");
            }
        });
    }

    /* ------------------------------ SUBSCRIPTIONS ----------------------------- */

    private void subscribe(JSONObject subscription) {
        Request request = newRequest(Command.subscribe);
        request.connectionAffinity = connectionCount;

        request.json(subscription);
        request.onSuccess(response -> {
            // TODO ... make sure this isn't just an account subscription
            serverInfo.update(response.result);
            emit(OnSubscribed.class, serverInfo);
        });
        request.request();
    }

    private JSONObject prepareSubscription() {
        subscriptions.pauseEventEmissions();
        subscriptions.addStream(SubscriptionManager.Stream.ledger);
        subscriptions.addStream(SubscriptionManager.Stream.server);
        subscriptions.unpauseEventEmissions();
        return subscriptions.allSubscribed();
    }

    /* ------------------------------ REQUESTS ------------------------------ */

    public Request newRequest(Command cmd) {
        return new Request(cmd, cmdIDs++, this);
    }

    public void sendRequest(final Request request) {
        Logger reqLog = Request.logger;
        if (request.connectionAffinity != -1 &&
                request.connectionAffinity != connectionCount) {
            reqLog.log(Level.WARNING, "Discarding stale request");
            request.clearAllListeners();
            requests.remove(request.id);
            return;
        }

        try {
            requests.put(request.id, request);
            request.bumpSendTime();
            sendMessage(request.toJSON());
            // Better safe than sorry
        } catch (Exception e) {
            if (reqLog.isLoggable(Level.WARNING)) {
                reqLog.log(Level.WARNING, "Exception when trying to request: {0}", e);
            }
            nextTickOrWhenConnected(args -> sendRequest(request));
        }
    }

    // ### Managed Requests API
    // Can't return the Request object, as a new ones are created on retries
    public <T> void makeManagedRequest(final Command cmd,
                                       final Manager<T> manager,
                                       final Request.Builder<T> builder) {

        // TODO: replace with something sensible; Kotlin ;) ??
        final boolean[] finalized = new boolean[]{false};

        final Request request = newRequest(cmd);
        @SuppressWarnings("CodeBlock2Expr")
        final OnDisconnected cb = __ -> {
            nowOrWhenConnected((___) -> {
                if (!finalized[0] && manager.retryOnUnsuccessful(null)) {
                    finalized[0] = true;
                    logRetry(request, "Client disconnected");
                    request.clearAllListeners();
                    queueRetry(50, cmd, manager, builder);
                }
            });
        };
        onceDisconnected(cb);
        request.onceResponse(response -> {
            finalized[0] = true;
            Client.this.removeListener(OnDisconnected.class, cb);

            if (response.succeeded) {
                // TODO: this could be done in another thread
                final T t = builder.buildTypedResponse(response);
                manager.cb(response, t);
            } else {
                if (manager.retryOnUnsuccessful(response)) {
                    queueRetry(50, cmd, manager, builder);
                } else {
                    manager.cb(response, null);
                }
            }
        }).onceTimeout(args -> {
            if (!finalized[0] && manager.retryOnUnsuccessful(null)) {
                finalized[0] = true;
                boolean cleared =
                        Client.this.removeListener(OnDisconnected.class, cb);
                logRetry(request, "Request timed out, cleared onDisconnected="
                                + cleared);
                request.clearAllListeners();
                queueRetry(50, cmd, manager, builder);
            }
        });
        builder.beforeRequest(request);
        manager.beforeRequest(request);
        request.request();
    }

    private <T> void queueRetry(int ms,
                                final Command cmd,
                                final Manager<T> manager,
                                final Request.Builder<T> builder) {
        schedule(ms, () -> makeManagedRequest(cmd, manager, builder));
    }

    private void logRetry(Request request, String reason) {
        if (logger.isLoggable(Level.WARNING)) {
            log(Level.WARNING, previousUri + ": " + reason + ", muting listeners " +
                    "for `" + request.json() + "` and trying again");
        }
    }

    // ### Managed Requests

    public AccountTxPager accountTxPager(AccountID accountID) {
        return new AccountTxPager(this, accountID, null);
    }

    public void requestLedgerEntry(final Hash256 index, final Number ledger_index, final Manager<LedgerEntry> cb) {
        makeManagedRequest(Command.ledger_entry, cb, new Request.Builder<LedgerEntry>() {
            @Override
            public void beforeRequest(Request request) {
                if (ledger_index != null) {
                    request.json("ledger_index", ledgerIndex(ledger_index));
                }
                request.json("index", index.toJSON());
            }
            @Override
            public LedgerEntry buildTypedResponse(Response response) {
                String node_binary = response.result.optString("node_binary");
                STObject node = STObject.translate.fromHex(node_binary);
                node.put(Hash256.index, index);
                return (LedgerEntry) node;
            }
        });
    }

    private Object ledgerIndex(Number ledger_index) {
        long l = ledger_index.longValue();
        if (l == VALIDATED_LEDGER) {
            return "validated";
        }
        return l;
    }

    public void requestAccountInfo(final AccountID addy, final Manager<AccountRoot> manager) {
        makeManagedRequest(Command.account_info, manager, new Request.Builder<AccountRoot>() {
            @Override
            public void beforeRequest(Request request) {
                request.json("account", addy);
            }

            @Override
            public AccountRoot buildTypedResponse(Response response) {
                JSONObject root = response.result.optJSONObject("account_data");
                return (AccountRoot) STObject.fromJSONObject(root);
            }
        });
    }

    public void requestLedgerData(final long ledger_index, final Manager<ArrayList<LedgerEntry>> manager) {
        makeManagedRequest(Command.ledger_data, manager, new Request.Builder<ArrayList<LedgerEntry>>() {
            @Override
            public void beforeRequest(Request request) {
                request.json("ledger_index", ledger_index);
                request.json("binary", true);
            }

            @Override
            public ArrayList<LedgerEntry> buildTypedResponse(Response response) {
                JSONArray state = response.result.getJSONArray("state");
                ArrayList<LedgerEntry> result = new ArrayList<LedgerEntry>();
                for (int i = 0; i < state.length(); i++) {
                    JSONObject stateObject = state.getJSONObject(i);
                    LedgerEntry le = (LedgerEntry) STObject.fromHex(stateObject.getString("data"));
                    le.index(Hash256.fromHex(stateObject.getString("index")));
                    result.add(le);
                }
                return result;
            }
        });
    }

    public void requestAccountLines(final AccountID addy, final Manager<ArrayList<AccountLine>> manager) {
        makeManagedRequest(Command.account_lines, manager, new Request.Builder<ArrayList<AccountLine>>() {
            @Override
            public void beforeRequest(Request request) {
                request.json("account", addy);
            }

            @Override
            public ArrayList<AccountLine> buildTypedResponse(Response response) {
                ArrayList<AccountLine> lines = new ArrayList<AccountLine>();
                JSONArray array = response.result.optJSONArray("lines");
                for (int i = 0; i < array.length(); i++) {
                    JSONObject line = array.optJSONObject(i);
                    lines.add(AccountLine.fromJSON(addy, line));
                }
                return lines;
            }
        });
    }

    public void requestHostID(final Callback<String> callback) {
        makeManagedRequest(Command.server_info, new Manager<String>() {
            @Override
            public void cb(Response response, String hostid) throws JSONException {
                callback.called(hostid);
            }

            @Override
            public boolean retryOnUnsuccessful(Response r) {
                return true;
            }
        }, new Request.Builder<String>() {
            @Override
            public void beforeRequest(Request request) {

            }

            @Override
            public String buildTypedResponse(Response response) {
                JSONObject info = response.result.getJSONObject("info");
                return info.getString("hostid");
            }
        });
    }

    public void requestBookOffers(final Number ledger_index,
                                  final Issue get,
                                  final Issue pay,
                                  final Manager<ArrayList<Offer>> cb) {
        makeManagedRequest(Command.book_offers, cb, new Request.Builder<ArrayList<Offer>>() {
            @Override
            public void beforeRequest(Request request) {
                request.json("taker_gets", get.toJSON());
                request.json("taker_pays", pay.toJSON());

                if (ledger_index != null) {
                    request.json("ledger_index", ledger_index);
                }
            }
            @Override
            public ArrayList<Offer> buildTypedResponse(Response response) {
                ArrayList<Offer> offers = new ArrayList<Offer>();
                JSONArray offersJson = response.result.getJSONArray("offers");
                for (int i = 0; i < offersJson.length(); i++) {
                    JSONObject jsonObject = offersJson.getJSONObject(i);
                    STObject object = STObject.fromJSONObject(jsonObject);
                    offers.add((Offer) object);
                }
                return offers;
            }
        });
    }

    public void requestLedger(final Number ledger_index, final Manager<JSONObject> cb) {
        makeManagedRequest(Command.ledger, cb, new Request.Builder<JSONObject>() {
            @Override
            public void beforeRequest(Request request) {
                request.json("ledger_index", ledgerIndex(ledger_index));
            }

            @Override
            public JSONObject buildTypedResponse(Response response) {
                return response.result.optJSONObject("ledger");
            }
        });
    }

    public void requestTransaction(final Hash256 hash, final Manager<TransactionResult> cb) {
        makeManagedRequest(Command.tx, cb, new Request.Builder<TransactionResult>() {
            @Override
            public void beforeRequest(Request request) {
                request.json("binary", true);
                request.json("transaction", hash);
            }

            @Override
            public TransactionResult buildTypedResponse(Response response) {
                return new TransactionResult(response.result, TransactionResult.Source.request_tx_binary);
            }
        });
    }
}
