package com.unopenedbox.craftingmod.autocsc.adb

import org.apache.commons.exec.ExecuteException
import org.apache.commons.exec.ExecuteResultHandler
import org.apache.commons.exec.LogOutputStream
import java.util.*
import kotlin.collections.ArrayList

class AdbLogcatWatcher(private val resolver:(detected:Boolean) -> Unit) : LogOutputStream(), ExecuteResultHandler {
    private var resolved = false

    override fun processLine(line: String, logLevel: Int) {
        // PreconfigService
        if (line.contains("Send CP_CFG_READ_DONE")) {
            println("[AdbLogcatWatcher] Detected kill switch")
            resolved = true
            resolver(true)
        }
    }

    override fun onProcessComplete(exitValue: Int) {
        println("[AdbLogcatWatcher] onProcessComplete")
        if (!resolved) {
            resolver(false)
        }
    }

    override fun onProcessFailed(e: ExecuteException) {
        println("[AdbLogcatWatcher] onProcessFailed")
        if (!resolved) {
            resolver(false)
        }
    }

}