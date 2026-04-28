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
            "Launcher can ask Termux to start or restart Gemma. You should not need to browse any directories.",
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
            "Grant Gemma Launcher the Android permission 'Run commands in Termux environment'.",
            status.detail,
        )
    }

    @Test
    fun buildBackendControlArgumentsAddsRestartFlag() {
        assertArrayEquals(
            arrayOf(GEMMA_BACKEND_START_SCRIPT, "--restart"),
            buildBackendControlArguments(restart = true)
        )
        assertArrayEquals(
            arrayOf(GEMMA_BACKEND_START_SCRIPT),
            buildBackendControlArguments(restart = false)
        )
    }

}
