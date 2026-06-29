import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

Dialog {
    id: root
    title: backend.appLanguage === "ar" ? "🩺 لوحة حالة الخدمات والصحة" : "🩺 Service Status & Health"
    anchors.centerIn: parent
    width: 520
    height: 550
    modal: true

    background: Rectangle {
        color: cardSlateBg
        border.color: borderSlate
        border.width: 1.5
        radius: 12
    }

    // Localization Dictionary
    function getTxt(key) {
        var translations = {
            "title": { "ar": "🩺 لوحة حالة الخدمات والصحة", "en": "🩺 Service Status & Health" },
            "subtitle": { "ar": "مراقبة وإدارة الخدمات النشطة ومؤشرات صحة النظام الفورية", "en": "Monitor and manage active services and real-time health indicators" },
            "clip_title": { "ar": "مراقب الحافظة التلقائي", "en": "Clipboard Monitor Daemon" },
            "clip_desc": { "ar": "يرصد حزم الكود @builder في الحافظة لمعالجتها تلقائياً.", "en": "Monitors the clipboard for builder packages to process them instantly." },
            "gemini_title": { "ar": "اتصال مساعد الذكاء الاصطناعي Gemini", "en": "Gemini AI Assistant Integration" },
            "gemini_desc": { "ar": "مفتاح الربط والمزامنة السحابية للنماذج الذكية.", "en": "Cloud connection and synchronization key for generative smart models." },
            "bubble_title": { "ar": "الفقاعة الذهبية العائمة (Overlay)", "en": "Floating Overlay Bubble" },
            "bubble_desc": { "ar": "شريط صغير يعلو النوافذ لاستخراج الكود والوصول السريع.", "en": "A persistent mini-widget for quick code extraction and access." },
            "project_title": { "ar": "مستودع العمل النشط حالياً", "en": "Currently Active Workspace" },
            "project_desc": { "ar": "المجلد أو المشروع الذي يتم توجيه الأكواد المكتشفة إليه.", "en": "The target folder where extracted source files are routed." },
            "logs_title": { "ar": "سجلات وقاعدة بيانات SQLite", "en": "SQLite Engine Action Logs" },
            "logs_desc": { "ar": "عدد العمليات والعمليات المصغرة المسجلة محلياً.", "en": "Count of micro-actions and operations logged in the local DB." },
            "btn_test": { "ar": "فحص سريع للنظام ⚡", "en": "Run Quick Self-Test ⚡" },
            "btn_refresh": { "ar": "تحديث فوري 🔄", "en": "Force Refresh 🔄" },
            "btn_close": { "ar": "إغلاق النافذة ❌", "en": "Close Window ❌" },
            "btn_permissions": { "ar": "إدارة الأذونات 🛡️", "en": "Permissions 🛡️" },
            "active": { "ar": "نشط 🟢", "en": "Active 🟢" },
            "inactive": { "ar": "معطل 🔴", "en": "Inactive 🔴" },
            "configured": { "ar": "تم التكوين ✅", "en": "Configured ✅" },
            "not_configured": { "ar": "مفتاح مفقود ⚠️", "en": "Key Missing ⚠️" },
            "diagnostic_report": { "ar": "تقرير الفحص الفوري والمكونات الذكية:", "en": "Real-time Diagnostic and System Report:" }
        };
        var isAr = (backend.appLanguage === "ar");
        return translations[key] ? (isAr ? translations[key]["ar"] : translations[key]["en"]) : "";
    }

    // Dialog state variables
    property bool clipRunning: false
    property bool geminiReady: false
    property bool bubbleEnabled: false
    property string activeProj: "Default"
    property int logsCount: 0
    property string selfTestOutput: ""

    function refreshStatuses() {
        clipRunning = backend.is_clipboard_monitor_running()
        geminiReady = backend.is_gemini_api_configured()
        bubbleEnabled = backend.is_bubble_enabled()
        activeProj = backend.get_active_project()
        logsCount = backend.get_logs_count()
    }

    onOpened: {
        refreshStatuses()
        selfTestOutput = ""
    }

    header: Rectangle {
        width: parent.width
        height: 60
        color: borderSlate
        radius: 12

        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 10
            spacing: 2

            Text {
                Layout.alignment: Qt.AlignCenter
                text: root.getTxt("title")
                color: metallicGold
                font.bold: true
                font.pixelSize: 15
            }
            Text {
                Layout.alignment: Qt.AlignCenter
                text: root.getTxt("subtitle")
                color: textGray
                font.pixelSize: 10
            }
        }
    }

    contentItem: Item {
        anchors.fill: parent

        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 12
            spacing: 12

            ScrollView {
                Layout.fillWidth: true
                Layout.preferredHeight: 320
                clip: true

                ColumnLayout {
                    width: parent.width - 15
                    spacing: 8

                    // 1. Clipboard Card
                    Rectangle {
                        Layout.fillWidth: true
                        height: 58
                        color: slateBg
                        border.color: borderSlate
                        border.width: 1
                        radius: 8

                        RowLayout {
                            anchors.fill: parent
                            anchors.margins: 10
                            spacing: 10

                            Text {
                                text: "📋"
                                font.pixelSize: 20
                            }

                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: 1
                                Text {
                                    text: root.getTxt("clip_title")
                                    color: textSilver
                                    font.bold: true
                                    font.pixelSize: 11
                                }
                                Text {
                                    text: root.getTxt("clip_desc")
                                    color: textGray
                                    font.pixelSize: 9
                                    wrapMode: Text.Wrap
                                    Layout.fillWidth: true
                                }
                            }

                            ColumnLayout {
                                spacing: 2
                                Text {
                                    text: root.clipRunning ? root.getTxt("active") : root.getTxt("inactive")
                                    color: root.clipRunning ? successGreen : errorRed
                                    font.bold: true
                                    font.pixelSize: 10
                                }
                                Switch {
                                    id: clipSwitch
                                    checked: root.clipRunning
                                    onCheckedChanged: {
                                        if (checked !== root.clipRunning) {
                                            backend.set_clipboard_monitor_enabled(checked)
                                            root.refreshStatuses()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Gemini Card
                    Rectangle {
                        Layout.fillWidth: true
                        height: 58
                        color: slateBg
                        border.color: borderSlate
                        border.width: 1
                        radius: 8

                        RowLayout {
                            anchors.fill: parent
                            anchors.margins: 10
                            spacing: 10

                            Text {
                                text: "🤖"
                                font.pixelSize: 20
                            }

                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: 1
                                Text {
                                    text: root.getTxt("gemini_title")
                                    color: textSilver
                                    font.bold: true
                                    font.pixelSize: 11
                                }
                                Text {
                                    text: root.getTxt("gemini_desc")
                                    color: textGray
                                    font.pixelSize: 9
                                    wrapMode: Text.Wrap
                                    Layout.fillWidth: true
                                }
                            }

                            Text {
                                text: root.geminiReady ? root.getTxt("configured") : root.getTxt("not_configured")
                                color: root.geminiReady ? successGreen : warningOrange
                                font.bold: true
                                font.pixelSize: 10
                            }
                        }
                    }

                    // 3. Floating Overlay Card
                    Rectangle {
                        Layout.fillWidth: true
                        height: 58
                        color: slateBg
                        border.color: borderSlate
                        border.width: 1
                        radius: 8

                        RowLayout {
                            anchors.fill: parent
                            anchors.margins: 10
                            spacing: 10

                            Text {
                                text: "🎯"
                                font.pixelSize: 20
                            }

                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: 1
                                Text {
                                    text: root.getTxt("bubble_title")
                                    color: textSilver
                                    font.bold: true
                                    font.pixelSize: 11
                                }
                                Text {
                                    text: root.getTxt("bubble_desc")
                                    color: textGray
                                    font.pixelSize: 9
                                    wrapMode: Text.Wrap
                                    Layout.fillWidth: true
                                }
                            }

                            ColumnLayout {
                                spacing: 2
                                Text {
                                    text: root.bubbleEnabled ? root.getTxt("active") : root.getTxt("inactive")
                                    color: root.bubbleEnabled ? successGreen : errorRed
                                    font.bold: true
                                    font.pixelSize: 10
                                }
                                Switch {
                                    checked: root.bubbleEnabled
                                    onCheckedChanged: {
                                        if (checked !== root.bubbleEnabled) {
                                            backend.bubbleEnabled = checked
                                            root.refreshStatuses()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Active Project Card
                    Rectangle {
                        Layout.fillWidth: true
                        height: 58
                        color: slateBg
                        border.color: borderSlate
                        border.width: 1
                        radius: 8

                        RowLayout {
                            anchors.fill: parent
                            anchors.margins: 10
                            spacing: 10

                            Text {
                                text: "📁"
                                font.pixelSize: 20
                            }

                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: 1
                                Text {
                                    text: root.getTxt("project_title")
                                    color: textSilver
                                    font.bold: true
                                    font.pixelSize: 11
                                }
                                Text {
                                    text: root.getTxt("project_desc")
                                    color: textGray
                                    font.pixelSize: 9
                                    wrapMode: Text.Wrap
                                    Layout.fillWidth: true
                                }
                            }

                            Rectangle {
                                color: borderSlate
                                border.color: metallicGold
                                border.width: 1
                                radius: 4
                                implicitWidth: 100
                                height: 26
                                Text {
                                    anchors.centerIn: parent
                                    text: root.activeProj
                                    color: textSilver
                                    font.bold: true
                                    font.pixelSize: 10
                                    elide: Text.ElideRight
                                    width: 90
                                    horizontalAlignment: Text.AlignHCenter
                                }
                            }
                        }
                    }

                    // 5. Database Log Count Card
                    Rectangle {
                        Layout.fillWidth: true
                        height: 58
                        color: slateBg
                        border.color: borderSlate
                        border.width: 1
                        radius: 8

                        RowLayout {
                            anchors.fill: parent
                            anchors.margins: 10
                            spacing: 10

                            Text {
                                text: "📊"
                                font.pixelSize: 20
                            }

                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: 1
                                Text {
                                    text: root.getTxt("logs_title")
                                    color: textSilver
                                    font.bold: true
                                    font.pixelSize: 11
                                }
                                Text {
                                    text: root.getTxt("logs_desc")
                                    color: textGray
                                    font.pixelSize: 9
                                    wrapMode: Text.Wrap
                                    Layout.fillWidth: true
                                }
                            }

                            Rectangle {
                                color: borderSlate
                                radius: 4
                                implicitWidth: 60
                                height: 26
                                Text {
                                    anchors.centerIn: parent
                                    text: root.logsCount.toString()
                                    color: metallicGold
                                    font.bold: true
                                    font.pixelSize: 11
                                }
                            }
                        }
                    }
                }
            }

            // Real-time Self-diagnostic Area
            Text {
                text: root.getTxt("diagnostic_report")
                color: textSilver
                font.bold: true
                font.pixelSize: 11
            }

            Rectangle {
                Layout.fillWidth: true
                Layout.fillHeight: true
                color: slateBg
                border.color: borderSlate
                border.width: 1
                radius: 6

                ScrollView {
                    anchors.fill: parent
                    anchors.margins: 6
                    clip: true

                    TextArea {
                        id: reportText
                        text: root.selfTestOutput === "" ? (backend.appLanguage === "ar" ? "اضغط على زر الفحص السريع لبدء اختبار مكونات النظام..." : "Press Run Self-Test button to run live diagnostics...") : root.selfTestOutput
                        color: root.selfTestOutput === "" ? textGray : textSilver
                        font.family: "Consolas"
                        font.pixelSize: 10
                        readOnly: true
                        wrapMode: Text.Wrap
                        background: null
                    }
                }
            }

            // Action Buttons
            RowLayout {
                Layout.fillWidth: true
                spacing: 8

                Button {
                    text: root.getTxt("btn_test")
                    Layout.fillWidth: true
                    onClicked: {
                        var rawReport = backend.get_self_test_report()
                        try {
                            var rep = JSON.parse(rawReport)
                            var isAr = (backend.appLanguage === "ar")
                            root.selfTestOutput = isAr ? rep.raw_report_ar : rep.raw_report_en
                            
                            diagResultDialog.diagnosticDataRaw = rawReport
                            diagResultDialog.open()
                            
                            backend.notificationSent(
                                isAr ? "فحص ذاتي" : "Self-Diagnostic",
                                isAr ? "اكتمل فحص حالة الخدمات بنجاح ونقلك للتفاصيل." : "System services diagnostic completed successfully and opened details.",
                                "success"
                            )
                        } catch (e) {
                            root.selfTestOutput = rawReport
                        }
                    }
                }

                Button {
                    text: root.getTxt("btn_permissions")
                    implicitWidth: 120
                    onClicked: {
                        permissionsDashboardDialog.open()
                    }
                }

                Button {
                    text: root.getTxt("btn_refresh")
                    implicitWidth: 120
                    onClicked: {
                        root.refreshStatuses()
                        backend.notificationSent(backend.appLanguage === "ar" ? "تحديث" : "Refresh", backend.appLanguage === "ar" ? "تم تحديث البيانات." : "Statuses updated.", "info")
                    }
                }

                Button {
                    text: root.getTxt("btn_close")
                    implicitWidth: 120
                    onClicked: root.close()
                }
            }
        }
    }

    DiagnosticResultDialog {
        id: diagResultDialog
    }
}
