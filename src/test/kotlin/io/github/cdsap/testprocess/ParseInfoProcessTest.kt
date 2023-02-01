package io.github.cdsap.testprocess

import org.junit.Assert.*
import org.junit.Test

class ParseInfoProcessTest {

    @Test
    fun emptyInfoReturnsNullTestProcess() {
        val parseInfoProcess = ParseInfoProcess("")
        assert(parseInfoProcess.get("") == null)
    }

    @Test
    fun missingHeapValueReturnsNullTestProcess() {
        val parseInfoProcess = ParseInfoProcess("")
        val info = """
            user: Optional[inakivillar],
            cmd: /.asdf/installs/java/liberica-11.0.15.1+2/bin/java,
            args: [-Djava.awt.headless=true,
            -Dorg.gradle.internal.worker.tmpdir=/nowinandroid/feature/author/build/tmp/testProdReleaseUnitTest/work,
            -Dorg.gradle.native=false, -javaagent:build/tmp/expandedArchives/org.jacoco.agent-0.8.7.jar_3a83c50b4a016f281c4e9f3500d16b55/jacocoagent.jar=destfile=build/jacoco/testProdReleaseUnitTest.exec,
            append=true,excludes=jdk.internal .*,inclnolocationclasses=true,dumponexit=true,
            output=file,jmx=false, @ /.gradle/.tmp/gradle-worker-classpath909835543043326786txt,
            -Dfile.encoding=UTF-8, -Duser.country=US, -Duser.language=en, -Duser.variant, -ea, worker.org.gradle.process.internal .worker.GradleWorkerMain, 'Gradle Test Executor 76'], startTime: Optional[2023-01-31T17:34:53.024Z]
        """.trimIndent()
        assert(parseInfoProcess.get(info) == null)
    }

    @Test
    fun missingGradleTestExecutorReturnsNullTestProcess() {
        val parseInfoProcess = ParseInfoProcess("")
        val info = """
            user: Optional[inakivillar],
            cmd: /.asdf/installs/java/liberica-11.0.15.1+2/bin/java,
            args: [-Djava.awt.headless=true,
            -Dorg.gradle.internal.worker.tmpdir=/nowinandroid/feature/author/build/tmp/testProdReleaseUnitTest/work,
            -Dorg.gradle.native=false, -javaagent:build/tmp/expandedArchives/org.jacoco.agent-0.8.7.jar_3a83c50b4a016f281c4e9f3500d16b55/jacocoagent.jar=destfile=build/jacoco/testProdReleaseUnitTest.exec,
            append=true,excludes=jdk.internal .*,inclnolocationclasses=true,dumponexit=true,
            output=file,jmx=false, @ /.gradle/.tmp/gradle-worker-classpath909835543043326786txt,
            -Xmx512m -Dfile.encoding=UTF-8, -Duser.country=US, -Duser.language=en, -Duser.variant, -ea, worker.org.gradle.process.internal .worker.GradleWorkerMain], startTime: Optional[2023-01-31T17:34:53.024Z]
        """.trimIndent()
        assert(parseInfoProcess.get(info) == null)
    }

    @Test
    fun missingTmpDirReturnsNullTestProcess() {
        val parseInfoProcess = ParseInfoProcess("")
        val info = """
            user: Optional[inakivillar],
            cmd: /.asdf/installs/java/liberica-11.0.15.1+2/bin/java,
            args: [-Djava.awt.headless=true,
            -Dorg.gradle.native=false, -javaagent:build/tmp/expandedArchives/org.jacoco.agent-0.8.7.jar_3a83c50b4a016f281c4e9f3500d16b55/jacocoagent.jar=destfile=build/jacoco/testProdReleaseUnitTest.exec,
            append=true,excludes=jdk.internal .*,inclnolocationclasses=true,dumponexit=true,
            output=file,jmx=false, @ /.gradle/.tmp/gradle-worker-classpath909835543043326786txt,
            -Xmx512m -Dfile.encoding=UTF-8, -Duser.country=US, -Duser.language=en, -Duser.variant, -ea, worker.org.gradle.process.internal .worker.GradleWorkerMain], startTime: Optional[2023-01-31T17:34:53.024Z]
        """.trimIndent()
        assert(parseInfoProcess.get(info) == null)
    }

    @Test
    fun correctFormatParseTestProcess(){
        val parseInfoProcess = ParseInfoProcess("PATH")
        val info = """
            user: Optional[inakivillar],
            cmd: /.asdf/installs/java/liberica-11.0.15.1+2/bin/java,
            args: [-Djava.awt.headless=true,
            -Dorg.gradle.internal.worker.tmpdir=PATH/feature/author/build/tmp/testProdReleaseUnitTest/work,
            -Dorg.gradle.native=false, -javaagent:build/tmp/expandedArchives/org.jacoco.agent-0.8.7.jar_3a83c50b4a016f281c4e9f3500d16b55/jacocoagent.jar=destfile=build/jacoco/testProdReleaseUnitTest.exec,
            append=true,excludes=jdk.internal .*,inclnolocationclasses=true,dumponexit=true,
            output=file,jmx=false, @ /.gradle/.tmp/gradle-worker-classpath909835543043326786txt,
            -Xmx512m, -Dfile.encoding=UTF-8, -Duser.country=US, -Duser.language=en, -Duser.variant, -ea, worker.org.gradle.process.internal .worker.GradleWorkerMain, 'Gradle Test Executor 76'], startTime: Optional[2023-01-31T17:34:53.024Z]
        """.trimIndent()
        val process = parseInfoProcess.get(info)
        assert(process?.max == "512m")
        assert(process?.executor == "Gradle Test Executor 76")
        assert(process?.task == ":feature:author:testProdReleaseUnitTest")
    }

//    val info = """
//            user: Optional[inakivillar],
//            cmd: /.asdf/installs/java/liberica-11.0.15.1+2/bin/java,
//            args: [-Djava.awt.headless=true,
//            -Dorg.gradle.internal .worker.tmpdir=/nowinandroid/feature/author/build/tmp/testProdReleaseUnitTest/work,
//            -Dorg.gradle.native=false, -javaagent:build/tmp/expandedArchives/org.jacoco.agent-0.8.7.jar_3a83c50b4a016f281c4e9f3500d16b55/jacocoagent.jar=destfile=build/jacoco/testProdReleaseUnitTest.exec,
//            append=true,excludes=jdk.internal .*,inclnolocationclasses=true,dumponexit=true,
//            output=file,jmx=false, @ /.gradle/.tmp/gradle-worker-classpath909835543043326786txt,
//            -Xmx512m, -Dfile.encoding=UTF-8, -Duser.country=US, -Duser.language=en, -Duser.variant, -ea, worker.org.gradle.process.internal .worker.GradleWorkerMain, 'Gradle Test Executor 76'], startTime: Optional[2023-01-31T17:34:53.024Z]
//        """.trimIndent()

}
