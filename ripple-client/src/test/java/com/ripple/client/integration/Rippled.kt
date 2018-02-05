package com.ripple.client.integration

import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentLinkedQueue

typealias LogWatcher = (line: String) -> Unit

class Rippled(@Suppress("CanBeParameter") private val path: String,
              @Suppress("CanBeParameter") private val wd: String) {
    private val watchers = ConcurrentLinkedQueue<LogWatcher>()
    private val process: Process
    private val watcherThread: Thread

    init {
        val builder = ProcessBuilder()
        builder.command(path, "-a", "--fg")
        builder.directory(File(wd))
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