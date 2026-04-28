package dev.niz.gemmalauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendClientTest {
    @Test
    fun parseBackendStatusReadsHealthyPayload() {
        val status = parseBackendStatus(
            """{"status":"ok","actor_online":true,"actor_model":"gemma-4-e4b-it-q4_k_m.gguf"}"""
        )

        assertTrue(status.checked)
        assertTrue(status.online)
        assertTrue(status.actorOnline)
        assertEquals("gemma-4-e4b-it-q4_k_m.gguf", status.actorModel)
        assertEquals("Agent runtime is reachable.", status.detail)
    }

    @Test
    fun parseBackendStatusHandlesActorOutage() {
        val status = parseBackendStatus(
            """{"status":"ok","actor_online":false}"""
        )

        assertTrue(status.checked)
        assertTrue(status.online)
        assertFalse(status.actorOnline)
        assertEquals("Backend is reachable, but the actor model is not ready yet.", status.detail)
        assertFalse(status.agentReady)
    }
}
