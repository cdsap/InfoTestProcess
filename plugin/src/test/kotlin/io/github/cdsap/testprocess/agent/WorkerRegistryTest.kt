package io.github.cdsap.testprocess.agent

import io.github.cdsap.testprocess.model.WorkerRuntimeStats
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WorkerRegistryTest {
    @Rule
    @JvmField
    val tmp = TemporaryFolder()

    @Test
    fun readsEntriesFromAgentOutput() {
        val dir = tmp.newFolder("workers")
        File(dir, "12345.json").writeText(
            """{"pid":12345,"task":":app:test","executor":"Gradle Test Executor 1","maxHeapBytes":536870912,"startMs":1700000000000,"args":["-Xmx512m","-Dio.github.cdsap.testprocess.task=:app:test"]}"""
        )
        File(dir, "67890.json").writeText(
            """{"pid":67890,"task":":lib:test","executor":"Gradle Test Executor 2","maxHeapBytes":1073741824,"startMs":1700000000001,"args":["-Xmx1g"]}"""
        )
        val entries = WorkerRegistry.read(dir).sortedBy { it.pid }
        assert(entries.size == 2)
        assert(entries[0].pid == 12345L)
        assert(entries[0].task == ":app:test")
        assert(entries[0].executor == "Gradle Test Executor 1")
        assert(entries[0].maxHeapBytes == 536870912L)
        assert(entries[0].startMs == 1700000000000L)
        assert(entries[0].args == listOf("-Xmx512m", "-Dio.github.cdsap.testprocess.task=:app:test"))
        assert(entries[1].pid == 67890L)
        assert(entries[1].task == ":lib:test")
        assert(entries[1].executor == "Gradle Test Executor 2")
        assert(entries[1].maxHeapBytes == 1073741824L)
        assert(entries[1].startMs == 1700000000001L)
        assert(entries[1].args == listOf("-Xmx1g"))
    }

    @Test
    fun ignoresMalformedFiles() {
        val dir = tmp.newFolder("workers")
        File(dir, "bad.json").writeText("{not json")
        File(dir, "ok.json").writeText(
            """{"pid":42,"task":":x:test","executor":"Gradle Test Executor 9","maxHeapBytes":268435456,"startMs":1,"args":[]}"""
        )
        val entries = WorkerRegistry.read(dir)
        assert(entries.size == 1)
        assert(entries.single().pid == 42L)
    }

    @Test
    fun readsMissingExecutorAsEmptyString() {
        val dir = tmp.newFolder("workers")
        File(dir, "7.json").writeText(
            """{"pid":7,"task":":a:test","maxHeapBytes":268435456,"startMs":1,"args":[]}"""
        )
        val entries = WorkerRegistry.read(dir)
        assert(entries.single().pid == 7L)
        assert(entries.single().executor == "")
        assert(entries.single().maxHeapBytes == 268435456L)
    }

    @Test
    fun emptyDirReturnsEmptyList() {
        val dir = tmp.newFolder("workers")
        assert(WorkerRegistry.read(dir).isEmpty())
    }

    @Test
    fun missingDirReturnsEmptyList() {
        assert(WorkerRegistry.read(File(tmp.root, "nope")).isEmpty())
    }

    @Test
    fun statsReaderIgnoresIdentityFiles() {
        val dir = tmp.newFolder("workers")
        File(dir, "1.json").writeText(
            """{"pid":1,"task":":a:test","executor":"E","maxHeapBytes":268435456,"startMs":1,"args":[]}"""
        )
        File(dir, "1.stats.json").writeText(
            """{"pid":1,"uptimeMs":12000,"cpuTimeMs":3400,"usedHeapBytes":104857600,"peakHeapBytes":209715200,"peakMetaspaceBytes":52428800,"maxHeapBytes":268435456,"gcCollections":4,"gcTimeMs":120,"gcType":"G1","jitTimeMs":850,"classesLoaded":4823,"peakThreads":18}"""
        )
        assert(WorkerRegistry.read(dir).single().pid == 1L)
        val s = WorkerRegistry.readStats(dir).single()
        assert(s.pid == 1L)
        assert(s.gcType == "G1")
        assert(s.cpuTimeMs == 3400L)
        assert(s.peakHeapBytes == 209715200L)
        assert(s.jitTimeMs == 850L)
        assert(s.classesLoaded == 4823L)
        assert(s.peakThreads == 18)
    }

    @Test
    fun statsReaderTolerantOfMissingFields() {
        val dir = tmp.newFolder("workers")
        // Older payload that doesn't include the new fields.
        File(dir, "2.stats.json").writeText(
            """{"pid":2,"uptimeMs":5000,"usedHeapBytes":1024,"maxHeapBytes":2048,"gcCollections":1,"gcTimeMs":10,"gcType":"PARALLEL"}"""
        )
        val s = WorkerRegistry.readStats(dir).single()
        assert(WorkerRuntimeStats::class.java.`package`.name == "io.github.cdsap.testprocess.model")
        assert(s.cpuTimeMs == -1L)
        assert(s.jitTimeMs == -1L)
        assert(s.classesLoaded == -1L)
        assert(s.peakThreads == -1)
        assert(s.uptimeMs == 5000L)
        assert(s.usedHeapBytes == 1024L)
        assert(s.peakHeapBytes == 0L)
        assert(s.peakMetaspaceBytes == 0L)
        assert(s.gcType == "PARALLEL")
    }

    @Test
    fun statsReaderIgnoresMalformedStatsFiles() {
        val dir = tmp.newFolder("workers")
        File(dir, "bad.stats.json").writeText("{not json")
        File(dir, "3.stats.json").writeText(
            """{"pid":3,"uptimeMs":1000,"cpuTimeMs":100,"usedHeapBytes":1,"peakHeapBytes":2,"peakMetaspaceBytes":3,"maxHeapBytes":4,"gcCollections":0,"gcTimeMs":0,"gcType":"G1","jitTimeMs":1,"classesLoaded":2,"peakThreads":3}"""
        )
        val stats = WorkerRegistry.readStats(dir)
        assert(stats.size == 1)
        assert(stats.single().pid == 3L)
    }
}
