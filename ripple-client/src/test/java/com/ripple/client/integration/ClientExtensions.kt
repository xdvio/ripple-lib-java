package com.ripple.client.integration

import com.ripple.client.Client
import com.ripple.client.enums.Command
import com.ripple.client.requests.Request
import com.ripple.client.responses.Response
import org.json.JSONObject

fun <T> makeManager(cb: (response: Response?, t: T?) -> Unit) = object: Request.Manager<T>() {
    override fun cb(response: Response?, t: T?) {
        cb(response, t)
    }
}

fun Client.requestLedgerAccept(cb: Request.Manager<JSONObject>) {
    makeManagedRequest<JSONObject>(Command.ledger_accept, cb, object : Request.Builder<JSONObject> {
        override fun buildTypedResponse(response: Response) = response.result
        override fun beforeRequest(request: Request) {}
    })
}

fun Client.requestLedgerAccept(cb: (Response?, JSONObject?) -> Unit) {
    requestLedgerAccept(makeManager<JSONObject>(cb))
}