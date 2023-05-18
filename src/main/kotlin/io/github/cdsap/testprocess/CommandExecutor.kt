package io.github.cdsap.testprocess

import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.streams.toList

class CommandExecutor {

    fun execute(command: String): Any {
        val process = process(command)
        val bufferedReader = bufferReader(process)
        val output = processInputFirstLine(bufferedReader)
        process.waitFor()
        if (process.exitValue() != 0) {
            // throw IllegalStateException("$command failed with ${process.exitValue()}")
        }
        return output
    }

    private fun process(command: String): Process {
        return ProcessBuilder().command("bash", "-c", command)
            .redirectErrorStream(true)
            .start()
    }

    private fun bufferReader(process: Process): BufferedReader {
        return BufferedReader(InputStreamReader(process.inputStream))
    }

    private fun processInputFirstLine(bufferedReader: BufferedReader): String {
        val value = bufferedReader.lines().toList().joinToString("\n")
        bufferedReader.close()
        return value
    }
}
