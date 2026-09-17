package io.github.cdsap.testprocess.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class StatsTest {

    @Test
    fun incrementsTotalProcessesSafelyFromConcurrentCallbacks() {
        val stats = Stats()
        val workers = 16
        val incrementsPerWorker = 1_000
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val done = CountDownLatch(workers)
        val executor = Executors.newFixedThreadPool(workers)

        repeat(workers) {
            executor.submit {
                ready.countDown()
                start.await()
                repeat(incrementsPerWorker) { stats.incrementTotalProcesses() }
                done.countDown()
            }
        }

        ready.await()
        start.countDown()
        done.await()
        executor.shutdown()

        assertEquals(workers * incrementsPerWorker, stats.totalProcesses)
    }
}
