package com.unopenedbox.craftingmod.autocsc

import com.unopenedbox.craftingmod.autocsc.adb.AdbDeviceType
import com.unopenedbox.craftingmod.autocsc.adb.AdbUtil
import com.unopenedbox.craftingmod.autocsc.ui.ComboBoxView
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.geometry.Pos
import javafx.scene.control.Alert
import javafx.scene.control.ComboBox
import javafx.scene.control.TextArea
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.Priority
import javafx.scene.paint.Color
import javafx.scene.paint.Paint
import javafx.scene.text.Font
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import org.apache.commons.exec.ExecuteWatchdog
import tornadofx.*
import java.util.concurrent.Executors
import kotlin.coroutines.suspendCoroutine

class MainView : View() {

    private val adbPath = SimpleStringProperty()
    private val adbInstalled = SimpleBooleanProperty()
    private val runningWatchdog = SimpleBooleanProperty()
    private val deviceSDK = SimpleIntegerProperty()
    private val logText = SimpleStringProperty()

    private var resetWatchdog:ExecuteWatchdog? = null
    private val deviceID:String get() = deviceListBox.selected
    private val deviceListBox:ComboBoxView by inject()
    private val preventResetDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private lateinit var logTextBox:TextArea

    override val root = vbox {
        title = "AutoCSC"
        // properties setting
        minWidth = 300.toDouble()
        minHeight = 200.toDouble()
        paddingLeft = 10
        paddingRight = 10
        paddingTop = 10
        paddingBottom = 10
        this.spacing = 10.toDouble()
        label("버전: 1.0.1, 배포: develoid 벨붕") {
            font = Font("NanumBarunGothic", 16.toDouble())
        }
        // ADB 설치 여부
        checkbox("adb 설치 여부", adbInstalled) {
            isDisable = true
        }
        // ADB 경로
        textfield(adbPath) {
            isEditable = false
        }
        hbox {
            label("디바이스 ID: ")
            add(deviceListBox)
        }
        hbox {
            label("디바이스 SDK: ")
            label(deviceSDK)
        }
        button("새로고침") {
            action {
                initState()
            }
        }
        button("1. 고오대비 테마 설치하기") {
            action {
                GlobalScope.launch {
                    log(AdbUtil.unInstallTheme(deviceID))
                    log(AdbUtil.openLowConstructTheme(deviceID))
                    launch(Dispatchers.JavaFx) {
                        alert(Alert.AlertType.INFORMATION,
                            "고오대비 테마 설치",
                            "고오대비 테마를 밑의 다운로드 버튼을 눌러 설치만 하고, 적용은 하지않고, " +
                                    "방금 누른 버튼 밑에 밑의 테마 선택창 열기를 선택해 주세요.")
                    }
                }
            }
        }
        hbox {
            spacing = 10.toDouble()
            button("테마 스토어 열기") {
                action {
                    GlobalScope.launch {
                        log("Opening theme store")
                        log(AdbUtil.openThemeStore(deviceID))
                    }
                }
            }
            button("테마 선택창 열기") {
                action {
                    GlobalScope.launch {
                        log("Opening theme selector")
                        log(AdbUtil.openThemeChooser(deviceID))
                    }
                }
            }
        }
        hbox {
            spacing = 10.toDouble()
            button("2. CSC 테마 설치") {
                action {
                    GlobalScope.launch {
                        log("Installing Theme")
                        log(AdbUtil.installTheme(deviceID))
                        launch(Dispatchers.JavaFx) {
                            alert(Alert.AlertType.INFORMATION,
                                "설치 완료",
                                "설치가 완료되었습니다. 휴대폰을 키고 CSC 테마를 선택해서 무료 체험을 눌러주세요.\n" +
                                        "테마 적용 중 진행이 안될 때는 화면을 끄고 10초간 기다려 주세요.")
                        }
                    }
                }
            }
        }
        hbox {
            spacing = 10.toDouble()
            checkbox("공장초기화 방지", runningWatchdog) {
                isDisable = true
            }
            button("3. 설정창 열기") {
                action {
                    GlobalScope.launch {
                        resetWatchdog?.apply {
                            if (isWatching) {
                                destroyProcess()
                            }
                        }
                        launch(preventResetDispatcher) {
                            delay(500)
                            runningWatchdog.set(true)
                            resetWatchdog = AdbUtil.watchFactoryReset(deviceID) {detected ->
                                runningWatchdog.set(false)
                                if (detected) {
                                    GlobalScope.launch {
                                        AdbUtil.forceReboot(deviceID)
                                    }
                                } else {
                                    initState()
                                }
                            }
                        }
                        log("Opening EULA Activity")
                        log(AdbUtil.openEULAInfo(deviceID))
                        launch(Dispatchers.JavaFx) {
                            alert(Alert.AlertType.WARNING, "주의",
                                "CSC를 바꾸시기 전, 버튼 옆 공장초기화 방지가 켜져있는지 반드시 확인해 주세요.\n" +
                                        "비활성화시 데이터가 완전히 날라갑니다.")
                        }
                    }
                }
            }
        }

        textarea(logText) {
            isWrapText = true
            alignment = Pos.TOP_LEFT
            minHeight = 400.toDouble()
            minWidth = 200.toDouble()
            isEditable = false
            textProperty().addListener { _, _, _ ->
                this.scrollTop = Double.MAX_VALUE
            }
            logTextBox = this
        }
    }.apply {
        initState()
    }

    private fun initState() {
        GlobalScope.launch {
            val path = AdbUtil.getADBPath()
            launch (Dispatchers.JavaFx) {
                adbPath.set(path)
                adbInstalled.set(path != null)
                log("Path: $path")
            }
            if (path != null) {
                val devices = AdbUtil.getDevices()
                launch (Dispatchers.JavaFx) {
                    deviceListBox.items.set(
                        FXCollections.observableArrayList(
                            devices.filter {it.type == AdbDeviceType.NORMAL}.map {it.id}
                        )
                    )
                    deviceListBox.items.let {
                        if (it.size >= 1) {
                            deviceListBox.value.set(it[0])
                        }
                    }
                }
                log(AdbUtil.getDevices().joinToString(","))
            }
        }
    }
    private fun log(str:String) {
        println(str)
        logText.set(logText.get() + str + "\n")
        logTextBox.appendText("")
    }
}