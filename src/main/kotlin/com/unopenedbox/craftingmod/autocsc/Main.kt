package com.unopenedbox.craftingmod.autocsc

import tornadofx.launch

fun main(args:Array<String>) {
    if (args.isEmpty()) {
        launch<MainApp>(args)
    } else {
        println("This is GUI Program :(")
    }
}