package dev.niz.gemmalauncher

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxBridgeTest {
    @Test
    fun buildTermuxBridgeStatusMarksDispatchReadyWhenInstalledAndGranted() {
        val status = buildTermuxBridgeStatus(
            termuxInstalled = true,
            runCommandPermissionGranted = true,
        )

        assertTrue(status.termuxInstalled)
        assertTrue(status.runCommandPermissionGranted)
        assertTrue(status.canDispatchCommands)
        assertEquals(
            "Launcher can ask Termux to start or restart the Gemma backend.",
            status.detail,
        )
    }

    @Test
    fun buildTermuxBridgeStatusExplainsMissingPermission() {
        val status = buildTermuxBridgeStatus(
            termuxInstalled = true,
            runCommandPermissionGranted = false,
        )

        assertTrue(status.termuxInstalled)
        assertFalse(status.runCommandPermissionGranted)
        assertFalse(status.canDispatchCommands)
        assertEquals(
            "Grant Gemma Launcher the Termux Run Command permission so it can start Gemma automatically.",
            status.detail,
        )
    }

    @Test
    fun buildBackendControlArgumentsAddsRestartFlag() {
        assertArrayEquals(arrayOf("--restart"), buildBackendControlArguments(restart = true))
        assertArrayEquals(emptyArray(), buildBackendControlArguments(restart = false))
    }
}
