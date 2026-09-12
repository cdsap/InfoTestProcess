package io.github.cdsap.testprocess.model

import kotlinx.serialization.Serializable

@Serializable
data class TestProcess(val task: String, val executor: String, val max: String)
