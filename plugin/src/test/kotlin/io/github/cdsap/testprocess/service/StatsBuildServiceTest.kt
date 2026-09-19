package io.github.cdsap.testprocess.service

import org.gradle.tooling.events.OperationCompletionListener
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsBuildServiceTest {

    @Test
    fun doesNotImplementUnusedOperationCompletionListener() {
        val serviceType = StatsBuildService::class.java
        assertFalse(
            "StatsBuildService must not implement OperationCompletionListener " +
                "unless registered with BuildEventsListenerRegistry",
            OperationCompletionListener::class.java.isAssignableFrom(serviceType)
        )
        assertTrue(
            "StatsBuildService still uses AutoCloseable.close() as the end-of-build hook",
            AutoCloseable::class.java.isAssignableFrom(serviceType)
        )
        assertFalse(
            "empty onFinish override must not remain after dropping OperationCompletionListener",
            serviceType.declaredMethods.any { it.name == "onFinish" }
        )
    }
}
