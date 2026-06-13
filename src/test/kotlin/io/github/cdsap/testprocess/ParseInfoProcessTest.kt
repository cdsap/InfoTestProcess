package io.github.cdsap.testprocess

import org.junit.Test

class ParseInfoProcessTest {

    @Test
    fun emptyInfoReturnsNullTestProcess() {
        assert(ParseInfoProcess.get("") == null)
    }

    @Test
    fun missingHeapValueReturnsNullTestProcess() {
        val info = """
            user: Optional[inakivillar],
            cmd: /.asdf/installs/java/liberica-11.0.15.1+2/bin/java,
            args: [-Djava.awt.headless=true,
            -Dio.github.cdsap.testprocess.task=:feature:author:testProdReleaseUnitTest,
            -Dorg.gradle.native=false,
            -Dfile.encoding=UTF-8, -Duser.country=US, -Duser.language=en, -Duser.variant, -ea, worker.org.gradle.process.internal .worker.GradleWorkerMain, 'Gradle Test Executor 76'], startTime: Optional[2023-01-31T17:34:53.024Z]
        """.trimIndent()
        assert(ParseInfoProcess.get(info) == null)
    }

    @Test
    fun missingGradleTestExecutorReturnsNullTestProcess() {
        val info = """
            user: Optional[inakivillar],
            cmd: /.asdf/installs/java/liberica-11.0.15.1+2/bin/java,
            args: [-Djava.awt.headless=true,
            -Dio.github.cdsap.testprocess.task=:feature:author:testProdReleaseUnitTest,
            -Dorg.gradle.native=false,
            -Xmx512m -Dfile.encoding=UTF-8, -Duser.country=US, -Duser.language=en, -Duser.variant, -ea, worker.org.gradle.process.internal .worker.GradleWorkerMain], startTime: Optional[2023-01-31T17:34:53.024Z]
        """.trimIndent()
        assert(ParseInfoProcess.get(info) == null)
    }

    @Test
    fun missingTaskPropertyReturnsNullTestProcess() {
        val info = """
            user: Optional[inakivillar],
            cmd: /.asdf/installs/java/liberica-11.0.15.1+2/bin/java,
            args: [-Djava.awt.headless=true,
            -Dorg.gradle.native=false,
            -Xmx512m, -Dfile.encoding=UTF-8, -Duser.country=US, -Duser.language=en, -Duser.variant, -ea, worker.org.gradle.process.internal .worker.GradleWorkerMain, 'Gradle Test Executor 76'], startTime: Optional[2023-01-31T17:34:53.024Z]
        """.trimIndent()
        assert(ParseInfoProcess.get(info) == null)
    }

    @Test
    fun correctFormatParseTestProcess() {
        val info = """
            user: Optional[inakivillar],
            cmd: /.asdf/installs/java/liberica-11.0.15.1+2/bin/java,
            args: [-Djava.awt.headless=true,
            -Dio.github.cdsap.testprocess.task=:feature:author:testProdReleaseUnitTest,
            -Dorg.gradle.native=false,
            -Xmx512m, -Dfile.encoding=UTF-8, -Duser.country=US, -Duser.language=en, -Duser.variant, -ea, worker.org.gradle.process.internal .worker.GradleWorkerMain, 'Gradle Test Executor 76'], startTime: Optional[2023-01-31T17:34:53.024Z]
        """.trimIndent()
        val process = ParseInfoProcess.get(info)
        assert(process?.max == "512m")
        assert(process?.executor == "Gradle Test Executor 76")
        assert(process?.task == ":feature:author:testProdReleaseUnitTest")
    }

    @Test
    fun taskIdentityIsIndependentOfPathOverlap() {
        // Two subprojects with overlapping path prefixes — the old tmpdir-parsing approach
        // would conflate them. With the sentinel arg, identity is exact.
        val infoA = """
            args: [-Dio.github.cdsap.testprocess.task=:feature:author:test,
            -Dorg.gradle.internal.worker.tmpdir=/build/feature/author/build/tmp/test/work,
            -Xmx512m, 'Gradle Test Executor 1']
        """.trimIndent()
        val infoB = """
            args: [-Dio.github.cdsap.testprocess.task=:feature:authorDetail:test,
            -Dorg.gradle.internal.worker.tmpdir=/build/feature/authorDetail/build/tmp/test/work,
            -Xmx512m, 'Gradle Test Executor 2']
        """.trimIndent()
        assert(ParseInfoProcess.getTask(infoA) == ":feature:author:test")
        assert(ParseInfoProcess.getTask(infoB) == ":feature:authorDetail:test")
    }
}
