package io.github.cdsap.testprocess.report

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Replaces a stable report path only after the complete payload was generated. */
internal object AtomicFileWriter {
    fun replace(target: File, content: String) {
        target.parentFile?.mkdirs()
        val temporary = Files.createTempFile(
            target.parentFile?.toPath(),
            ".${target.name}.",
            ".tmp"
        ).toFile()
        try {
            temporary.writeText(content)
            try {
                Files.move(temporary.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                // Some supported filesystems do not expose ATOMIC_MOVE. Keep the
                // same-directory temporary file and use the strongest available move.
                Files.move(temporary.toPath(), target.toPath(), REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }
}
