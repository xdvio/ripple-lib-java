package com.ripple.client.integration

import com.ripple.client.Client
import com.ripple.client.enums.Command
import com.ripple.client.requests.Request
import com.ripple.client.responses.Response
import com.ripple.client.transport.impl.JavaWebSocketTransportImpl
import com.ripple.core.coretypes.AccountID
import com.ripple.core.coretypes.Amount
import com.ripple.core.types.known.tx.result.TransactionResult
import com.ripple.core.types.known.tx.txns.Payment
import com.ripple.crypto.Seed
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentLinkedQueue

typealias LogWatcher = (line: String) -> Unit

class Rippled(private val path: String, private val wd: String) {
    private val watchers = ConcurrentLinkedQueue<LogWatcher>()
    private lateinit var process: Process
    private lateinit var watcherThread: Thread

    fun spawn() {
        val builder = ProcessBuilder()
        builder.command(path, "-a", "--fg")
        builder.directory(File(wd))
        // builder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
        builder.redirectErrorStream(true)
        process = builder.start()
        watcherThread = Thread({
            val buffered = BufferedInputStream(process.inputStream)
            val reader = InputStreamReader(buffered)
            while (true) {
                try {
                    reader.forEachLine { line ->
                        watchers.forEach({ it(line) })
                    }
                } catch (e: IOException) {
                    //
                }
            }
        })
        watcherThread.start()
    }

    fun kill() {
        process.destroyForcibly()
    }

    fun waitUntilDead() = process.waitFor()
    fun isAlive() = process.isAlive
    fun retCode() = process.exitValue()
    fun addWatcher(watcher: LogWatcher) {
        synchronized(watchers) {
            watchers.add(watcher)
        }
    }

    fun removeWatcher(watcher: LogWatcher) {
        synchronized(watchers) {
            watchers.remove(watcher)
        }
    }

    fun waiter(pattern: String, timeout: Long = 0): () -> Unit {
        val regex = Regex(pattern)
        val waiter = Waiter()
        val watcher: (String) -> Unit = { line ->
            if (regex.matches(line)) {
                waiter.ok()
            }
        }
        addWatcher(watcher)
        return {
            waiter(timeout)
            removeWatcher(watcher)
        }
    }
}

fun Client.requestLedgerAccept(cb: Request.Manager<JSONObject>) {
    makeManagedRequest<JSONObject>(Command.ledger_accept, cb, object : Request.Builder<JSONObject> {
        override fun beforeRequest(request: Request) {

        }
        override fun buildTypedResponse(response: Response): JSONObject {
            return response.result
        }
    })
}

fun Client.requestLedgerAccept(cb: (Response, JSONObject) -> Unit) {
    requestLedgerAccept(object : Request.Manager<JSONObject>() {
        override fun cb(response: Response, jsonObject: JSONObject) {
            cb(response, jsonObject)
        }
    })
}

abstract class IntegrationTestBase {
    abstract val rippledPath: String
    abstract val rippledWorkingDirectory: String

    protected lateinit var client: Client
    @Suppress("MemberVisibilityCanBePrivate")
    protected lateinit var rippled: Rippled

    @Before
    fun beforeEach() {
        rippled = Rippled(rippledPath, rippledWorkingDirectory)

        val waitWs = rippled.waiter(".*Opened 'port_ws.*")
        rippled.spawn()
        waitWs()

        client = Client(JavaWebSocketTransportImpl())
        val connected = Object()
        client.connect("ws://localhost:6006", {
            synchronized(connected) {
                connected.notify()
            }
        })
        synchronized(connected) {
            connected.wait()
        }
    }

    @After
    fun afterEach() {
        rippled.kill()
        client.disconnect()
        client.dispose()
    }

    protected fun closeLedger(client: Client, every: Long = 1000, onResult: (json: JSONObject) -> Boolean = {false}) {
        client.run({
            client.requestLedgerAccept { _, result ->
                if (onResult(result)) {
                    client.schedule(every, {
                        closeLedger(client, every, onResult)
                    })
                }
            }
        })
    }

    fun <T> clientThreaded(block: () -> T): T {
        val waiter = Waiter()
        var result: T? = null
        client.run({
            result = block()
            waiter.ok()
        })
        waiter()
        return result!!
    }
}

class Waiter {
    private val lock = Object()
    private var failed = false
    operator fun invoke (timeout: Long = 0): Boolean {
        var result: Boolean? = null
        synchronized(lock) {
            lock.wait(timeout)
            result = failed
        }
        return result!!
    }

    private fun done (failed: Boolean = false) {
        synchronized(lock) {
            this.failed = failed
            lock.notify()
        }
    }

    fun ok() = done(false)
    fun fail() = done(true)
}

@Ignore
class CloseLedgerTest : IntegrationTestBase() {
    override val rippledPath = "/Users/ndudfield/ripple/rippled/build/rippled"
    override val rippledWorkingDirectory = "/Users/ndudfield/ripple/rippled/"

    private val masterSeed = Seed.fromPassPhrase("masterpassphrase")
    private val rootKeyPair = masterSeed.keyPair()
    private val bobSeed = Seed.fromPassPhrase("bob")
    private val bobPair = bobSeed.keyPair()
    private val bobId = AccountID(bobPair.id())

    @Test
    fun rootPaysBob() {
        var waiter = Waiter()

        val managed = clientThreaded {
            val root = client.account(AccountID(rootKeyPair.id()), rootKeyPair)
            val txManager = root.transactionManager()

            val payment = Payment()
            payment.amount(Amount(1000))
            payment.destination(bobId)

            val managed = txManager.manage(payment)

            managed.onSubmitSuccess {
                closeLedger(client)
            }

            managed.onSubmitFailure {
                waiter.fail()
            }

            txManager.queue(managed)

            managed.onError {
                waiter.fail()
            }

            managed.onValidated {
                waiter.ok()
            }
            return@clientThreaded managed
        }

        assertFalse(waiter())
        waiter = Waiter()

        clientThreaded {
            val hash = managed.result.hash
            client.requestTransaction(hash, object: Request.Manager<TransactionResult>() {
                override fun cb(response: Response?, txr: TransactionResult?) {
                    if (txr is TransactionResult) {
                        println(txr.toJSON().toString(2))
                        waiter.ok()
                    } else {
                        waiter.fail()
                    }
                }
            })
        }
        assertFalse(waiter())
    }
}