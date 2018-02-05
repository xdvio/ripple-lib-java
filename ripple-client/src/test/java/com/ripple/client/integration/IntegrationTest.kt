package com.ripple.client.integration

import com.ripple.core.coretypes.Amount
import com.ripple.core.types.known.tx.result.TransactionResult
import com.ripple.core.types.known.tx.txns.Payment
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test

@Ignore
class IntegrationTest : IntegrationTestBase() {
    override val rippledPath = "/Users/ndudfield/ripple/rippled/build/rippled"
    override val rippledWorkingDirectory = "/Users/ndudfield/ripple/rippled/"

    lateinit var root: TestAccount
    lateinit var bob: TestAccount

    @Test
    fun rootPaysBob() {
        val submitTx = Waiter()

        val managed = clientThreaded {
            val root = client.account(root.id, root.keyPair)
            val txManager = root.transactionManager()

            val payment = Payment()
            payment.amount(Amount(1000))
            payment.destination(bob.id)

            val managed = txManager.manage(payment)

            managed.onSubmitSuccess {
                closeLedger(client)
            }

            managed.onSubmitFailure {
                submitTx.fail()
            }

            txManager.queue(managed)

            managed.onError {
                submitTx.fail()
            }

            managed.onValidated {
                submitTx.ok()
            }
            return@clientThreaded managed
        }

        Assert.assertTrue(submitTx())
        val getTx = Waiter()

        clientThreaded {
            val hash = managed.result.hash
            client.requestTransaction(hash, makeManager({
                _, txr ->
                    if (txr is TransactionResult) {
                        getTx.ok()
                    } else {
                        getTx.fail()
                    }
            }))
        }
        Assert.assertTrue(getTx())
    }

}