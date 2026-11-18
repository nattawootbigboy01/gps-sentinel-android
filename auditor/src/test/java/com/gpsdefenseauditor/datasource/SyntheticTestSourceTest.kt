package com.gpsdefenseauditor.datasource

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class SyntheticTestSourceTest {

    private fun frames(count: Int) = (0 until count).map { index ->
        TelemetrySnapshot(
            timestampMs = 1_000L + index,
            latitude = 16.0 + index * 0.0001,
            longitude = 102.0,
            speedMps = 4f,
            accuracyMeters = 5f,
            altitudeMeters = 200.0,
            isMockFlagSet = false,
            satelliteCount = 8,
            accelMagnitude = 9.8f,
            isVpnActive = false,
            sourceMode = SourceMode.SYNTHETIC_DATASET
        )
    }

    @Test
    fun oldAsyncSessionCannotContinueAfterStopAndRestart() {
        val seen = mutableListOf<Pair<Long, Long>>()
        val completed = AtomicInteger()
        lateinit var source: SyntheticTestSource

        source = SyntheticTestSource(
            testFrames = frames(2),
            intervalMs = 10L,
            onCompleted = { completed.incrementAndGet() }
        )
        source.setListener { frame ->
            seen += frame.sessionId to frame.sequenceNumber
            if (frame.sessionId == 1L && frame.sequenceNumber == 1L) {
                source.stop()
                source.start()
            }
        }

        source.start()
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)

        assertEquals(
            listOf(1L to 1L, 2L to 1L, 2L to 2L),
            seen
        )
        assertEquals(1, completed.get())
        assertEquals(2L, source.getSessionCount())
    }

    @Test
    fun stopCancelsSynchronousBatchBeforeNextFrame() {
        val seen = mutableListOf<Long>()
        val completed = AtomicInteger()
        val source = SyntheticTestSource(
            testFrames = frames(5),
            onCompleted = { completed.incrementAndGet() }
        )
        source.setListener { frame ->
            seen += frame.sequenceNumber
            if (frame.sequenceNumber == 1L) source.stop()
        }

        val worker = Thread { source.runBatchSynchronous() }
        worker.start()
        worker.join(2_000L)

        assertTrue("Batch worker should finish after stop()", !worker.isAlive)
        assertEquals(listOf(1L), seen)
        assertEquals(0, completed.get())
    }

    @Test
    fun synchronousBatchEmitsEveryFrameAndCompletesOnce() {
        val seen = mutableListOf<Long>()
        val completed = AtomicInteger()
        val source = SyntheticTestSource(
            testFrames = frames(4),
            onCompleted = { completed.incrementAndGet() }
        )
        source.setListener { seen += it.sequenceNumber }

        val worker = Thread { source.runBatchSynchronous() }
        worker.start()
        worker.join(2_000L)

        assertTrue("Batch worker should finish", !worker.isAlive)
        assertEquals(listOf(1L, 2L, 3L, 4L), seen)
        assertEquals(1, completed.get())
    }
}
