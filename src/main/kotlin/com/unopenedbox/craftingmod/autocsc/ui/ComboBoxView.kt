package com.unopenedbox.craftingmod.autocsc.ui

import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleStringProperty
import javafx.scene.Parent
import tornadofx.*

class ComboBoxView : View() {
    val items = SimpleListProperty<String>()
    val value = SimpleStringProperty()
    val selected get() = value.get()
    override val root = combobox<String>(value, items)
}