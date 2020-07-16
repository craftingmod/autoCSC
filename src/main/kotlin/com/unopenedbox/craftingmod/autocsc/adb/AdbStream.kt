package com.unopenedbox.craftingmod.autocsc.adb

import org.apache.commons.exec.LogOutputStream
import java.util.*
import kotlin.collections.ArrayList

class AdbStream : LogOutputStream() {
    private val lines:LinkedList<String> = LinkedList()
    override fun processLine(line: String, logLevel: Int) {
        println(line)
        lines.add(line)
    }
    fun getLines():ArrayList<String> {
        val arrList = arrayListOf<String>()
        for (str in lines) {
            arrList.add(str)
        }
        return arrList
    }
    fun getOutputs():String {
        var out = ""
        for (str in lines) {
            out += str
            out += "\n"
        }
        return if (out.isNotEmpty()) {
            out.substring(0, out.length - 1)
        } else {
            out
        }
    }
}