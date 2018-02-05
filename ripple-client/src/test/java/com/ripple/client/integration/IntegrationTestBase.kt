package com.ripple.client.integration

import com.ripple.client.Client
import com.ripple.client.transport.impl.JavaWebSocketTransportImpl
import org.json.JSONObject
import org.junit.After
import org.junit.Before

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
        val waitConnected = Waiter()
        client.connect("ws://localhost:6006", { waitConnected.ok() })
        waitConnected()
        setTestAccounts()
    }

    @After
    fun afterEach() {
        rippled.kill()
        client.disconnect()
        client.dispose()
    }

    protected fun closeLedger(client: Client, every: Long = 1000, onResult: (json: JSONObject?) -> Boolean = { false }) {
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

    private fun setTestAccounts() {
        javaClass.declaredFields.filter {
            it.type == TestAccount::class.java
        }.forEach({
                    it.set(this, TestAccount(it.name))
                })
    }
}