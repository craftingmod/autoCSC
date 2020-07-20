package com.unopenedbox.craftingmod.autocsc.adb

import kotlinx.coroutines.*
import org.apache.commons.exec.*
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.environment.EnvironmentUtils
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Paths
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object AdbUtil {
    private val cwd = Paths.get("").toAbsolutePath().toString()
    private val storePkg = "com.samsung.android.themestore"
    suspend fun getADBPath():String? {
        val out = try {
            exec(adbCmd("version"))
        } catch (e:IOException) {
            return null
        }
        val versionPrefix = "Android Debug Bridge version"
        if (out.indexOf(versionPrefix) < 0) {
            println(out)
            return null
        }
        print("ADB Version: ")
        println(out.substring(out.indexOf(versionPrefix) + versionPrefix.length + 1, out.indexOf("\n")))
        println("CSCTheme: " + Paths.get(cwd, "csctheme.apk"))
        val installedPrefix = "Installed as"
        return out.substring(out.indexOf(installedPrefix) + installedPrefix.length + 1)
    }
    suspend fun getDevices():List<AdbDevice> {
        val devices = mutableListOf<AdbDevice>()
        val out = try {
            exec(adbCmd("devices"))
        } catch (e:IOException) {
            return listOf()
        }
        val listPrefix = "List of devices attached"
        if (out.indexOf(listPrefix) >= 0) {
            val list = out.substring(out.indexOf(listPrefix) + listPrefix.length).trim().split(Regex("\\s+"))
            if (list.size >= 2) {
                for (i in list.indices step 2) {
                    val id = list[i]
                    val type = list[i + 1]
                    devices.add(
                        AdbDevice(when (type) {
                            "unauthorized" -> AdbDeviceType.UNAUTHORIZED
                            "device" -> AdbDeviceType.NORMAL
                            "recovery" -> AdbDeviceType.RECOVERY
                            else -> AdbDeviceType.UNKNOWN
                        }, id))
                }
            }
        }
        return devices
    }
    suspend fun getSDK(deviceID:String):Int {
        val out = exec(adbCmd("-s", deviceID, "shell", "\"getprop ro.build.version.sdk\""))
        println(out.trim())
        return out.trim().toIntOrNull() ?: -1
    }
    suspend fun unInstallTheme(deviceID: String):String {
        return execLog(adbShell(deviceID, "pm", "uninstall", "com.samsung.High_contrast_theme_II"))
    }
    suspend fun installTheme(deviceID:String):String {
        var log = ""
        log += execLog(adbShell(deviceID,"pm", "uninstall", storePkg))
        log += execLog(adbShell(deviceID, "pm", "clear", storePkg))
        log += unInstallTheme(deviceID)
        log += execLog(adbCmdID(deviceID, "install", "\"${Paths.get(cwd, "csctheme.apk")}\""))
        log += execLog(adbShell(deviceID, "am", "kill", "com.android.settings"))
        // log += execLog(adbStartActivity(deviceID, "$storePkg/$storePkg.activity.MainActivity"))
        delay(1000)
        log += openThemeChooser(deviceID)
        return log
    }
    suspend fun openLowConstructTheme(deviceID: String):String {
        // Low Construct Theme II
        val uri = "themestore://ProductDetail/000002723865?contentsType=THEMES"
        return execLog(adbShell(deviceID,
            "am", "start", "-a", "android.intent.action.VIEW", "-d", uri, storePkg))
    }
    suspend fun openThemeStore(deviceID: String):String {
        return execLog(adbStartActivity(deviceID, "$storePkg/$storePkg.activity.MainActivity"))
    }
    suspend fun openThemeChooser(deviceID:String):String {
        var log = ""
        log += execLog(adbStartActivity(deviceID, "$storePkg/$storePkg.activity.bixby.MyDeviceMainActivityForBixby"))
        log += execLog(adbStartActivity(deviceID, "$storePkg/$storePkg.activity.ActivityMyDeviceMain"))
        return log
    }
    suspend fun openEULAInfo(deviceID:String):String {
        val settingPkg = "com.android.settings"
        return execLog(adbStartActivity(deviceID,
            "$settingPkg/$settingPkg.Settings\\\$LegalInformationActivity"))
    }
    suspend fun watchFactoryReset(deviceID:String, callback:(Boolean) -> Unit):ExecuteWatchdog {
        // clear first
        exec(adbCmdID(deviceID, "logcat", "-c"))
        // this must be run in non-main context
        val commands = adbCmdID(deviceID, "logcat")
        val executor = DefaultExecutor()
        val watchDog = ExecuteWatchdog(Long.MAX_VALUE)
        coroutineScope {
            launch {
                val watcher = AdbLogcatWatcher {detected ->
                    if (detected) {
                        callback(true)
                    } else {
                        // watchDog.destroyProcess()
                        callback(false)
                    }
                }

                executor.streamHandler = PumpStreamHandler(watcher)
                executor.watchdog = watchDog
                try {
                    executor.execute(commands, watcher)
                } catch (e:IOException) {
                    e.printStackTrace()
                    callback(false)
                }
            }
        }
        return watchDog
    }
    suspend fun forceReboot(deviceID: String) {
        exec(adbCmdID(deviceID, "reboot"))
    }
    private suspend fun execLog(cmd:CommandLine):String {
        var text = "> "
        text += cmd.toStrings().joinToString(" ")
        text += "\n"
        text += try {
            exec(cmd)
        } catch (e:IOException) {
            "Failed to launch"
        }
        text += "\n"
        return text
    }
    private fun adbStartActivity(deviceID: String, componentName:String):CommandLine {
        return adbShell(deviceID, "am", "start", "-a",  "android.intent.action.MAIN", "-n", componentName)
    }
    private fun adbShell(deviceID: String, vararg params:String):CommandLine {
        return adbCmdID(deviceID, "shell", "\"${params.joinToString(" ")}\"")
    }
    private fun adbCmdID(deviceID: String, vararg params:String):CommandLine {
        return adbCmd("-s", deviceID, *params)
    }
    private fun adbCmd(vararg params:String):CommandLine {
        return CommandLine.parse("adb").apply {
            for (param in params) {
                addArgument(param, false)
            }
        }
    }
    private suspend fun exec(cmd:CommandLine):String {
        return withContext(Dispatchers.IO) {
            suspendCoroutine<String> { cont ->
                val executor = DefaultExecutor()
                val stream = AdbStream()
                executor.streamHandler = PumpStreamHandler(stream)

                val resultHandler = object : DefaultExecuteResultHandler() {
                    override fun onProcessComplete(exitValue: Int) {
                        cont.resume(stream.getOutputs())
                    }

                    override fun onProcessFailed(e: ExecuteException) {
                        cont.resumeWithException(e)
                    }
                }
                println("[ADB] ${cmd.toStrings().joinToString(" ")}")
                try {
                    executor.execute(cmd, EnvironmentUtils.getProcEnvironment(), resultHandler)
                } catch (e:IOException) {
                    e.printStackTrace()
                    cont.resumeWithException(e)
                }
            }
        }
    }
}