package com.ripple.client.integration

class Waiter {
    private val lock = Object()
    private var failed: Boolean? = null
    operator fun invoke(timeout: Long = 0): Boolean {
        var result: Boolean? = null
        synchronized(lock) {
            lock.wait(timeout)
            result = failed
        }
        if (this.failed == null) {
            throw IllegalStateException()
        } else {
            return !result!!
        }
    }

    private fun done(failed: Boolean = false) {
        synchronized(lock) {
            if (this.failed != null) {
                throw IllegalStateException()
            }
            this.failed = failed
            lock.notify()
        }
    }

    fun ok() = done(false)
    fun fail() = done(true)
}