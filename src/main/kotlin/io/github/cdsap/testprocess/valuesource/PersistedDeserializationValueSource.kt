package io.github.cdsap.testprocess

import io.github.cdsap.testprocess.model.PersistedState
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.nio.charset.Charset

abstract class PersistedDeserializationValueSource :
    ValueSource<PersistedState, PersistedDeserializationValueSource.Params> {
    interface Params : ValueSourceParameters {
        @get:InputFile
        @get:PathSensitive(PathSensitivity.RELATIVE)
        val file: RegularFileProperty
    }

    override fun obtain(): PersistedState? {
        val json = Json { prettyPrint = false; ignoreUnknownKeys = true }
        val statsFile = parameters.file.asFile.get()
        val content = statsFile
            .takeIf { it.exists() }
            ?.readText(Charset.forName("UTF-8"))
        if (statsFile.exists()) {
            statsFile.delete()
        }
        return if (content == null) {
            null
        } else {
            try {
                json.decodeFromString(content)
            } catch (e: SerializationException) {
                when (e) {
                    is SerializationException, is IllegalStateException -> null
                    else -> throw e
                }
            }
        }
    }
}
