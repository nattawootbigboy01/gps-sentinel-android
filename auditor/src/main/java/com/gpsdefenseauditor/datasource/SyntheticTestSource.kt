package com.gpsdefenseauditor.datasource

import android.os.Handler
import android.os.Looper

/**
 * Replays immutable test frames on a Handler or synchronously on a worker thread.
 *
 * A generation token is captured once per run. stop() invalidates it immediately;
 * a later start() receives a different token. Listener callbacks are always made
 * outside the state lock. Completion callbacks are checked against the captured
 * token and invoked while holding the re-entrant state monitor so a newer session
 * cannot overtake an older completion notification.
 */
class SyntheticTestSource(
    testFrames: List<TelemetrySnapshot>,
    private val intervalMs: Long = 500L,
    override val mode: SourceMode = SourceMode.SYNTHETIC_DATASET,
    looper: Looper = Looper.getMainLooper(),
    private val onCompleted: (() -> Unit)? = null,
    private val onError: ((Throwable) -> Unit)? = null
) : LocationDataSource {

    private enum class State { IDLE, ASYNC, BATCH }

    private val frames = testFrames.toList()
    private val lock = Any()
    private val handler = Handler(looper)

    @Volatile
    private var listener: TelemetryListener? = null

    private var state = State.IDLE
    private var currentIndex = 0
    private var sessionCounter = 0L
    private var generationToken = 0L
    private var pendingRunnable: Runnable? = null

    init {
        require(intervalMs > 0L) {
            "intervalMs must be greater than zero; use runBatchSynchronous() for immediate replay"
        }
        require(
            mode == SourceMode.SYNTHETIC_DATASET ||
                mode == SourceMode.ADVERSARIAL_GENERATOR
        ) {
            "SyntheticTestSource cannot use LIVE_HARDWARE mode"
        }
    }

    override fun setListener(listener: TelemetryListener?) {
        synchronized(lock) {
            this.listener = listener
        }
    }

    override fun start() {
        synchronized(lock) {
            if (state != State.IDLE || frames.isEmpty()) return

            sessionCounter++
            generationToken++
            val token = generationToken
            currentIndex = 0
            state = State.ASYNC

            val sessionId = sessionCounter
            val task = Runnable { emitNext(token, sessionId) }
            pendingRunnable = task
            handler.post(task)
        }
    }

    private fun emitNext(token: Long, sessionId: Long) {
        val frame: TelemetrySnapshot
        val activeListener: TelemetryListener

        synchronized(lock) {
            if (state != State.ASYNC || generationToken != token) return

            if (currentIndex >= frames.size) {
                state = State.IDLE
                pendingRunnable = null
                deliverCompletionLocked(token)
                return
            }

            frame = frames[currentIndex].copy(
                sourceMode = mode,
                sequenceNumber = currentIndex.toLong() + 1L,
                sessionId = sessionId
            )
            currentIndex++
            activeListener = listener ?: NO_OP_LISTENER
        }

        try {
            // The callback may call stop() and start() re-entrantly.
            synchronized(lock) {
                if (state != State.ASYNC || generationToken != token) return
            }
            activeListener.onTelemetryReceived(frame)
        } catch (error: Exception) {
            failSession(token, error)
            return
        }

        synchronized(lock) {
            // Never let a stopped or superseded run schedule another frame.
            if (state != State.ASYNC || generationToken != token) return

            if (currentIndex < frames.size) {
                val next = Runnable { emitNext(token, sessionId) }
                pendingRunnable = next
                handler.postDelayed(next, intervalMs)
            } else {
                state = State.IDLE
                pendingRunnable = null
                deliverCompletionLocked(token)
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            generationToken++
            state = State.IDLE
            pendingRunnable?.let(handler::removeCallbacks)
            pendingRunnable = null
        }
    }

    /**
     * Processes the same frames immediately on the calling thread.
     * Intended for WorkManager/background workers, never the Android main thread.
     */
    fun runBatchSynchronous() {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "runBatchSynchronous() must run on a background thread"
        }

        val token: Long
        val sessionId: Long
        synchronized(lock) {
            if (state != State.IDLE || frames.isEmpty()) return

            sessionCounter++
            generationToken++
            token = generationToken
            sessionId = sessionCounter
            currentIndex = 0
            state = State.BATCH
        }

        for (index in frames.indices) {
            val frame: TelemetrySnapshot
            val activeListener: TelemetryListener

            synchronized(lock) {
                if (state != State.BATCH || generationToken != token) return

                currentIndex = index + 1
                frame = frames[index].copy(
                    sourceMode = mode,
                    sequenceNumber = index.toLong() + 1L,
                    sessionId = sessionId
                )
                activeListener = listener ?: NO_OP_LISTENER
            }

            try {
                activeListener.onTelemetryReceived(frame)
            } catch (error: Exception) {
                failSession(token, error)
                return
            }

            // stop() or a replacement session invalidates the old batch here.
            synchronized(lock) {
                if (state != State.BATCH || generationToken != token) return
            }
        }

        synchronized(lock) {
            if (state == State.BATCH && generationToken == token) {
                state = State.IDLE
                deliverCompletionLocked(token)
            }
        }
    }

    private fun failSession(token: Long, error: Throwable) {
        synchronized(lock) {
            if (generationToken != token) return
            state = State.IDLE
            generationToken++
            pendingRunnable?.let(handler::removeCallbacks)
            pendingRunnable = null
        }
        runCatching { onError?.invoke(error) }
    }

    /**
     * Must be called with lock held. Re-checking the token suppresses a completion
     * that has been overtaken by stop() or a newer start().
     */
    private fun deliverCompletionLocked(token: Long) {
        if (generationToken == token && state == State.IDLE) {
            runCatching { onCompleted?.invoke() }
        }
    }

    fun getProgress(): Pair<Int, Int> = synchronized(lock) {
        currentIndex to frames.size
    }

    fun getSessionCount(): Long = synchronized(lock) {
        sessionCounter
    }

    private companion object {
        val NO_OP_LISTENER = TelemetryListener { }
    }
}
