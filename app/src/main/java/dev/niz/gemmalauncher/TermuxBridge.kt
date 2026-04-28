package dev.niz.gemmalauncher

import android.content.Intent

const val TERMUX_PACKAGE_NAME = "com.termux"
const val TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
private const val TERMUX_RUN_COMMAND_SERVICE_NAME = "com.termux.app.RunCommandService"
private const val TERMUX_RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
private const val TERMUX_EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
private const val TERMUX_EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
private const val TERMUX_EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
private const val TERMUX_EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
private const val TERMUX_EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_LABEL"
private const val TERMUX_EXTRA_COMMAND_DESCRIPTION = "com.termux.RUN_COMMAND_DESCRIPTION"
private const val TERMUX_EXTRA_COMMAND_HELP = "com.termux.RUN_COMMAND_HELP"
private const val TERMUX_BASH_PATH = "/data/data/com.termux/files/usr/bin/bash"

const val GEMMA_PROJECT_ROOT = "/data/data/com.termux/files/home/gemma-4-mobile-agent"
const val GEMMA_BACKEND_START_SCRIPT = "$GEMMA_PROJECT_ROOT/tools/start_backend_from_launcher.sh"

fun buildTermuxBridgeStatus(
    termuxInstalled: Boolean,
    runCommandPermissionGranted: Boolean,
    detailOverride: String? = null,
): TermuxBridgeStatus {
    val detail = detailOverride ?: when {
        !termuxInstalled -> "Termux is not installed. The launcher can still route native apps and phone actions."
        !runCommandPermissionGranted -> "Grant Gemma Launcher the Android permission 'Run commands in Termux environment'."
        else -> "Launcher can ask Termux to start or restart Gemma. You should not need to browse any directories."
    }
    return TermuxBridgeStatus(
        termuxInstalled = termuxInstalled,
        runCommandPermissionGranted = runCommandPermissionGranted,
        canDispatchCommands = termuxInstalled && runCommandPermissionGranted,
        detail = detail,
    )
}

fun buildBackendControlIntent(restart: Boolean): Intent {
    return Intent().apply {
        setClassName(TERMUX_PACKAGE_NAME, TERMUX_RUN_COMMAND_SERVICE_NAME)
        action = TERMUX_RUN_COMMAND_ACTION
        putExtra(TERMUX_EXTRA_COMMAND_PATH, TERMUX_BASH_PATH)
        putExtra(TERMUX_EXTRA_ARGUMENTS, buildBackendControlArguments(restart))
        putExtra(TERMUX_EXTRA_WORKDIR, GEMMA_PROJECT_ROOT)
        putExtra(TERMUX_EXTRA_BACKGROUND, true)
        putExtra(TERMUX_EXTRA_COMMAND_LABEL, if (restart) "Restart Gemma backend" else "Start Gemma backend")
        putExtra(
            TERMUX_EXTRA_COMMAND_DESCRIPTION,
            "Starts the local Gemma backend for the launcher."
        )
        putExtra(
            TERMUX_EXTRA_COMMAND_HELP,
            "If this fails, open Termux and set `allow-external-apps=true` in ~/.termux/termux.properties, then restart Termux."
        )
    }
}

fun buildBackendControlArguments(restart: Boolean): Array<String> {
    return if (restart) {
        arrayOf(GEMMA_BACKEND_START_SCRIPT, "--restart")
    } else {
        arrayOf(GEMMA_BACKEND_START_SCRIPT)
    }
}
