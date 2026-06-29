import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

Rectangle {
    id: executorDashboardScreen
    color: "transparent"

    // Translation helper
    function getTxt(key) {
        var translations = {
            "title": { "ar": "⚡ لوحة تحكم المنفذ والأتمتة الذكية", "en": "⚡ Intelligent Command Executor & Automation Hub" },
            "subtitle": { "ar": "اقتراحات ذكية، قوالب أوامر مرنة، إكمال تلقائي للأكواد، وسجل تنفيذ تاريخي", "en": "Smart recommendations, flexible command templates, auto-completion, and historic execution logging" },
            "project_lbl": { "ar": "المشروع النشط:", "en": "Active Workspace:" },
            "tab_dashboard": { "ar": "📊 اللوحة والتحكم", "en": "📊 Dashboard & Control" },
            "tab_history": { "ar": "📜 سجل الأوامر", "en": "📜 Command History" },
            "tab_templates": { "ar": "🛠️ قوالب الأتمتة", "en": "🛠️ Automation Templates" },
            
            "smart_suggestions": { "ar": "💡 توصيات واقتراحات ذكية لك:", "en": "💡 Smart Recommendations For You:" },
            "quick_shortcuts": { "ar": "⚡ اختصارات سريعة لملء المحرر:", "en": "⚡ Fast Editor Shortcuts:" },
            "manual_editor": { "ar": "💻 محرر الأوامر اليدوي والبرمجة الآمنة:", "en": "💻 Command Editor & Safe Scripting:" },
            "console_output": { "ar": "🖥️ شاشة مخرجات المنفذ:", "en": "🖥️ Executor Console Output:" },
            
            "btn_run": { "ar": "تشغيل التوجيه الحالي ⚡", "en": "Run Current Command ⚡" },
            "btn_dry_run": { "ar": "محاكاة آمنة (Dry Run) 🔍", "en": "Simulate Safe Run (Dry Run) 🔍" },
            "btn_clear": { "ar": "مسح المحرر 🧹", "en": "Clear Editor 🧹" },
            "btn_clear_history": { "ar": "مسح السجل 🗑️", "en": "Clear Log History 🗑️" },
            
            "lbl_history_empty": { "ar": "سجل الأوامر فارغ حالياً.", "en": "Command execution log is empty." },
            "lbl_suggestions_empty": { "ar": "لا توجد اقتراحات ذكية حالية. المشروع يبدو منظماً!", "en": "No smart suggestions found. Your workspace is tidy!" },
            "pro_tips": { "ar": "💡 نصائح للمحترفين:", "en": "💡 Pro Developer Tips:" },
            "tip_1": { "ar": "• استخدم التوجيه @executor لتنفيذ أتمتة سريعة للملفات دون فتح موجه الأوامر.", "en": "• Use @executor directives to build file automations without leaving your QML interface." },
            "tip_2": { "ar": "• أمر duplicates يفحص البصمة الرقمية MD5 للملفات لإيجاد النسخ المكررة بأمان تام.", "en": "• The duplicates command scans files via MD5 checksum to discover exact cloned content." },
            "tip_3": { "ar": "• يمكنك دمج عدة توجيهات وحفظها كملف دفعي لترتيب مشروعك بضغطة زر.", "en": "• You can merge multiple commands into batch scripts to manage entire structures instantly." },
            
            "tpl_organize": { "ar": "تنظيم المجلد النشط", "en": "Organize Active Folder" },
            "tpl_backup": { "ar": "نسخ احتياطي ذكي", "en": "Smart Project Backup" },
            "tpl_rename": { "ar": "إعادة تسمية ذكية للملفات", "en": "Batch File Renamer" },
            "tpl_duplicates": { "ar": "البحث عن المكررات", "en": "Find Cloned Content" },
            "tpl_report": { "ar": "تقارير إحصائية كاملة", "en": "Comprehensive Folder Stats" },
            
            "lbl_folder_path": { "ar": "مسار المجلد المستهدف:", "en": "Target Folder Path:" },
            "lbl_organize_opt": { "ar": "معيار الفرز والتنظيم:", "en": "Sorting Criteria:" },
            "opt_ext": { "ar": "فرز حسب نوع وامتداد الملف (Images, Docs...)", "en": "Sort by File Type/Extension (Images, Docs...)" },
            "opt_date": { "ar": "فرز حسب تاريخ آخر تعديل (السنة والشهر)", "en": "Sort by Modification Date (Year-Month)" },
            "lbl_rename_pattern": { "ar": "نمط التسمية الجديد (استخدم {num} للتسلسل):", "en": "Rename Pattern (use {num} for sequence):" },
            "btn_generate_commands": { "ar": "توليد كود الأوامر بالمحرر 🛠️", "en": "Generate Commands inside Editor 🛠️" },
            
            "status_completed": { "ar": "اكتمل بنجاح", "en": "Success" },
            "status_failed": { "ar": "فشل التنفيذ", "en": "Failed" }
        };
        var isAr = (backend.appLanguage === "ar");
        return translations[key] ? (isAr ? translations[key]["ar"] : translations[key]["en"]) : "";
    }

    property string currentSubTab: "dashboard" // "dashboard", "templates", "history"
    property string activeProject: backend.get_active_project()

    ListModel { id: suggestionsModel }
    ListModel { id: commandHistoryModel }
    ListModel { id: autocompleteModel }

    // Colors
    readonly property color metallicGold: "#D4AF37"
    readonly property color softGold: "#F3E5AB"
    readonly property color textSilver: "#E2E8F0"
    readonly property color textGray: "#94A3B8"
    readonly property color cardSlateBg: "#151B26"
    readonly property color slateBg: "#0F131D"
    readonly property color borderSlate: "#2A3547"
    readonly property color successGreen: "#10B981"
    readonly property color errorRed: "#EF4444"

    // Timer to periodically update state
    Timer {
        interval: 2000
        running: true
        repeat: true
        onTriggered: {
            refreshSuggestionsAndData()
        }
    }

    Component.onCompleted: {
        refreshSuggestionsAndData()
    }

    function refreshSuggestionsAndData() {
        activeProject = backend.get_active_project()
        
        // 1. Smart Suggestions
        try {
            var suggs = JSON.parse(backend.get_smart_suggestions(activeProject))
            suggestionsModel.clear()
            for (var i = 0; i < suggs.length; i++) {
                suggestionsModel.append(suggs[i])
            }
        } catch (e) {
            console.log("Suggestions parse error: " + e)
        }

        // 2. Command History
        try {
            var hist = JSON.parse(backend.get_command_history_json())
            commandHistoryModel.clear()
            for (var j = 0; j < hist.length; j++) {
                // Ensure expanded property is initialized
                hist[j].expanded = false
                commandHistoryModel.append(hist[j])
            }
        } catch (e) {
            console.log("Command history parse error: " + e)
        }
    }

    function updateAutocomplete(text) {
        try {
            var suggs = JSON.parse(backend.get_typeahead_suggestions(text))
            autocompleteModel.clear()
            // Only show auto-complete if user typed something and we have suggestions
            if (text.trim().length > 0 && suggs.length > 0) {
                for (var i = 0; i < Math.min(suggs.length, 5); i++) {
                    autocompleteModel.append(suggs[i])
                }
                autocompletePopup.open()
            } else {
                autocompletePopup.close()
            }
        } catch (e) {
            console.log("Autocomplete update error: " + e)
        }
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 20
        spacing: 12

        // HEADER BLOCK
        RowLayout {
            Layout.fillWidth: true
            ColumnLayout {
                spacing: 2
                Text {
                    text: executorDashboardScreen.getTxt("title")
                    color: metallicGold
                    font.bold: true
                    font.pixelSize: 18
                }
                Text {
                    text: executorDashboardScreen.getTxt("subtitle")
                    color: textGray
                    font.pixelSize: 11
                }
            }
            Spacer { Layout.fillWidth: true }

            RowLayout {
                spacing: 8
                Text {
                    text: executorDashboardScreen.getTxt("project_lbl")
                    color: textSilver
                    font.pixelSize: 11
                }
                Rectangle {
                    color: cardSlateBg
                    border.color: borderSlate
                    radius: 6
                    width: 140
                    height: 30
                    Text {
                        anchors.centerIn: parent
                        text: executorDashboardScreen.activeProject !== "" ? executorDashboardScreen.activeProject : "Default"
                        color: softGold
                        font.bold: true
                        font.pixelSize: 11
                    }
                }
            }
        }

        // NAVIGATION SUB-TABS
        RowLayout {
            spacing: 8
            Layout.fillWidth: true
            
            Button {
                text: executorDashboardScreen.getTxt("tab_dashboard")
                flat: executorDashboardScreen.currentSubTab !== "dashboard"
                background: Rectangle {
                    color: executorDashboardScreen.currentSubTab === "dashboard" ? cardSlateBg : "transparent"
                    border.color: executorDashboardScreen.currentSubTab === "dashboard" ? metallicGold : "transparent"
                    radius: 6
                }
                onClicked: executorDashboardScreen.currentSubTab = "dashboard"
            }

            Button {
                text: executorDashboardScreen.getTxt("tab_templates")
                flat: executorDashboardScreen.currentSubTab !== "templates"
                background: Rectangle {
                    color: executorDashboardScreen.currentSubTab === "templates" ? cardSlateBg : "transparent"
                    border.color: executorDashboardScreen.currentSubTab === "templates" ? metallicGold : "transparent"
                    radius: 6
                }
                onClicked: executorDashboardScreen.currentSubTab = "templates"
            }

            Button {
                text: executorDashboardScreen.getTxt("tab_history")
                flat: executorDashboardScreen.currentSubTab !== "history"
                background: Rectangle {
                    color: executorDashboardScreen.currentSubTab === "history" ? cardSlateBg : "transparent"
                    border.color: executorDashboardScreen.currentSubTab === "history" ? metallicGold : "transparent"
                    radius: 6
                }
                onClicked: executorDashboardScreen.currentSubTab = "history"
            }
        }

        // VIEW PORT FOR SUB-TABS
        StackLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            currentIndex: executorDashboardScreen.currentSubTab === "dashboard" ? 0 : (executorDashboardScreen.currentSubTab === "templates" ? 1 : 2)

            // SUB-TAB 0: DASHBOARD CONTROLS
            RowLayout {
                spacing: 12
                Layout.fillWidth: true
                Layout.fillHeight: true

                // Left Column: Suggestions, Code Editor & Console Output
                ColumnLayout {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    Layout.preferredWidth: 650
                    spacing: 10

                    // Smart Suggestions Area
                    Text {
                        text: executorDashboardScreen.getTxt("smart_suggestions")
                        color: metallicGold
                        font.bold: true
                        font.pixelSize: 12
                    }

                    Rectangle {
                        Layout.fillWidth: true
                        Layout.preferredHeight: 70
                        color: cardSlateBg
                        border.color: borderSlate
                        radius: 8
                        clip: true

                        ListView {
                            anchors.fill: parent
                            anchors.margins: 8
                            model: suggestionsModel
                            orientation: ListView.Horizontal
                            spacing: 10
                            delegate: Rectangle {
                                width: 230
                                height: 54
                                color: "#1E293B"
                                border.color: metallicGold
                                radius: 6
                                RowLayout {
                                    anchors.fill: parent
                                    anchors.margins: 6
                                    Text {
                                        text: model.icon
                                        font.pixelSize: 16
                                    }
                                    ColumnLayout {
                                        Layout.fillWidth: true
                                        spacing: 1
                                        Text {
                                            text: model.title
                                            color: textSilver
                                            font.bold: true
                                            font.pixelSize: 10
                                            elide: Text.ElideRight
                                        }
                                        Text {
                                            text: model.description
                                            color: textGray
                                            font.pixelSize: 8
                                            elide: Text.ElideRight
                                            Layout.fillWidth: true
                                        }
                                    }
                                    Button {
                                        text: backend.appLanguage === "ar" ? "تفويض ⚡" : "Apply"
                                        implicitWidth: 50
                                        implicitHeight: 24
                                        onClicked: {
                                            if (model.command === "export") {
                                                commandEditor.text = "export"
                                            } else if (model.command === "duplicates") {
                                                commandEditor.text = "duplicates"
                                            } else if (model.command === "selftest") {
                                                commandEditor.text = "selftest"
                                            } else {
                                                commandEditor.text = "scan"
                                            }
                                        }
                                    }
                                }
                            }
                            
                            Text {
                                anchors.centerIn: parent
                                visible: suggestionsModel.count === 0
                                text: executorDashboardScreen.getTxt("lbl_suggestions_empty")
                                color: textGray
                                font.pixelSize: 10
                            }
                        }
                    }

                    // Quick Shortcuts Block
                    Text {
                        text: executorDashboardScreen.getTxt("quick_shortcuts")
                        color: metallicGold
                        font.bold: true
                        font.pixelSize: 11
                    }

                    RowLayout {
                        Layout.fillWidth: true
                        spacing: 6
                        
                        Button {
                            text: "🔍 scan"
                            implicitHeight: 28
                            onClicked: commandEditor.text = "scan"
                        }
                        Button {
                            text: "📦 export"
                            implicitHeight: 28
                            onClicked: commandEditor.text = "export"
                        }
                        Button {
                            text: "⚠️ duplicates"
                            implicitHeight: 28
                            onClicked: commandEditor.text = "duplicates"
                        }
                        Button {
                            text: "📊 project"
                            implicitHeight: 28
                            onClicked: commandEditor.text = "project"
                        }
                        Button {
                            text: "🛡️ selftest"
                            implicitHeight: 28
                            onClicked: commandEditor.text = "selftest"
                        }
                    }

                    // Manual Editor Title
                    Text {
                        text: executorDashboardScreen.getTxt("manual_editor")
                        color: metallicGold
                        font.bold: true
                        font.pixelSize: 12
                    }

                    // Editor + Autocomplete Box
                    Rectangle {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        Layout.preferredHeight: 120
                        color: cardSlateBg
                        border.color: borderSlate
                        radius: 8

                        ScrollView {
                            anchors.fill: parent
                            TextArea {
                                id: commandEditor
                                placeholderText: backend.appLanguage === "ar" ? "اكتب الأمر هنا (مثال: duplicates أو project)..." : "Type executor command here (e.g. duplicates, project)..."
                                color: textSilver
                                font.family: "Consolas"
                                font.pixelSize: 12
                                wrapMode: TextEdit.Wrap
                                selectByMouse: true
                                focus: true
                                onTextChanged: {
                                    executorDashboardScreen.updateAutocomplete(text)
                                }
                            }
                        }

                        // Auto-complete suggestions overlay dropdown
                        Popup {
                            id: autocompletePopup
                            x: 10
                            y: commandEditor.cursorRectangle.y + 24
                            width: 250
                            height: Math.min(autocompleteModel.count * 40 + 10, 180)
                            padding: 5
                            background: Rectangle {
                                color: slateBg
                                border.color: metallicGold
                                border.width: 1.5
                                radius: 6
                            }

                            ListView {
                                anchors.fill: parent
                                model: autocompleteModel
                                spacing: 2
                                delegate: Rectangle {
                                    width: parent.width
                                    height: 36
                                    color: "transparent"
                                    
                                    MouseArea {
                                        anchors.fill: parent
                                        hoverEnabled: true
                                        onEntered: parent.color = "#1E293B"
                                        onExited: parent.color = "transparent"
                                        onClicked: {
                                            commandEditor.text = model.command
                                            autocompletePopup.close()
                                        }
                                    }

                                    RowLayout {
                                        anchors.fill: parent
                                        anchors.margins: 4
                                        spacing: 8
                                        Text {
                                            text: "⚡"
                                        }
                                        ColumnLayout {
                                            Layout.fillWidth: true
                                            spacing: 1
                                            Text {
                                                text: model.command
                                                color: softGold
                                                font.bold: true
                                                font.pixelSize: 10
                                            }
                                            Text {
                                                text: model.desc
                                                color: textGray
                                                font.pixelSize: 8
                                                elide: Text.ElideRight
                                                Layout.fillWidth: true
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Action Buttons Row
                    RowLayout {
                        Layout.fillWidth: true
                        spacing: 10

                        Button {
                            text: executorDashboardScreen.getTxt("btn_run")
                            Layout.fillWidth: true
                            Layout.preferredHeight: 38
                            background: Rectangle {
                                color: successGreen
                                radius: 6
                            }
                            onClicked: {
                                if (commandEditor.text.trim() === "") return
                                var output = backend.execute_command_advanced(commandEditor.text, executorDashboardScreen.activeProject, false)
                                consoleOutputText.text = output
                                executorDashboardScreen.refreshSuggestionsAndData()
                            }
                        }

                        Button {
                            text: executorDashboardScreen.getTxt("btn_dry_run")
                            Layout.fillWidth: true
                            Layout.preferredHeight: 38
                            onClicked: {
                                if (commandEditor.text.trim() === "") return
                                var output = backend.execute_command_advanced(commandEditor.text, executorDashboardScreen.activeProject, true)
                                consoleOutputText.text = output
                            }
                        }

                        Button {
                            text: executorDashboardScreen.getTxt("btn_clear")
                            Layout.preferredWidth: 100
                            Layout.preferredHeight: 38
                            onClicked: {
                                commandEditor.text = ""
                                consoleOutputText.text = ""
                            }
                        }
                    }

                    // Console Output Area
                    Text {
                        text: executorDashboardScreen.getTxt("console_output")
                        color: metallicGold
                        font.bold: true
                        font.pixelSize: 12
                    }

                    Rectangle {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        Layout.preferredHeight: 140
                        color: slateBg
                        border.color: borderSlate
                        radius: 8

                        ScrollView {
                            anchors.fill: parent
                            TextArea {
                                id: consoleOutputText
                                readOnly: true
                                font.family: "Consolas"
                                font.pixelSize: 11
                                color: "#10B981"
                                placeholderText: backend.appLanguage === "ar" ? "ستظهر مخرجات التنفيذ الفورية والتقارير هنا..." : "Execution outputs and safety logs will appear here..."
                                wrapMode: TextEdit.Wrap
                                selectByMouse: true
                                background: null
                            }
                        }
                    }
                }

                // Right Column: Pro tips panel
                ColumnLayout {
                    Layout.fillHeight: true
                    Layout.preferredWidth: 260
                    spacing: 12

                    Rectangle {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        color: cardSlateBg
                        border.color: borderSlate
                        radius: 10
                        padding: 15

                        ColumnLayout {
                            anchors.fill: parent
                            anchors.margins: 14
                            spacing: 12

                            Text {
                                text: executorDashboardScreen.getTxt("pro_tips")
                                color: metallicGold
                                font.bold: true
                                font.pixelSize: 14
                            }

                            Text {
                                text: executorDashboardScreen.getTxt("tip_1")
                                color: textSilver
                                font.pixelSize: 10.5
                                wrapMode: Text.WordWrap
                                Layout.fillWidth: true
                            }

                            Text {
                                text: executorDashboardScreen.getTxt("tip_2")
                                color: textSilver
                                font.pixelSize: 10.5
                                wrapMode: Text.WordWrap
                                Layout.fillWidth: true
                            }

                            Text {
                                text: executorDashboardScreen.getTxt("tip_3")
                                color: textSilver
                                font.pixelSize: 10.5
                                wrapMode: Text.WordWrap
                                Layout.fillWidth: true
                            }

                            Spacer { Layout.fillHeight: true }

                            Rectangle {
                                Layout.fillWidth: true
                                height: 80
                                color: slateBg
                                border.color: borderSlate
                                radius: 8
                                ColumnLayout {
                                    anchors.centerIn: parent
                                    spacing: 4
                                    Text {
                                        text: "🛡️ Safe Engine"
                                        color: metallicGold
                                        font.bold: true
                                        font.pixelSize: 11
                                    }
                                    Text {
                                        text: backend.appLanguage === "ar" ? "بيئة التنفيذ مؤمنة بالكامل" : "Execution environment is sandbox safe"
                                        color: textGray
                                        font.pixelSize: 8
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SUB-TAB 1: FLEXIBLE AUTOMATION TEMPLATES VIEW
            ScrollView {
                Layout.fillWidth: true
                Layout.fillHeight: true
                ColumnLayout {
                    width: parent.width
                    spacing: 15

                    // Template 1: Folder Auto-Organization Card
                    Rectangle {
                        Layout.fillWidth: true
                        height: 220
                        color: cardSlateBg
                        border.color: borderSlate
                        radius: 8
                        
                        ColumnLayout {
                            anchors.fill: parent
                            anchors.margins: 15
                            spacing: 10

                            RowLayout {
                                Text {
                                    text: "📁 " + executorDashboardScreen.getTxt("tpl_organize")
                                    color: metallicGold
                                    font.bold: true
                                    font.pixelSize: 13
                                }
                                Spacer { Layout.fillWidth: true }
                            }

                            RowLayout {
                                spacing: 10
                                Layout.fillWidth: true
                                Text {
                                    text: executorDashboardScreen.getTxt("lbl_folder_path")
                                    color: textSilver
                                    font.pixelSize: 11
                                }
                                TextField {
                                    id: organizeFolderPath
                                    text: backend._base_dir
                                    placeholderText: "e.g. C:/Projects"
                                    Layout.fillWidth: true
                                }
                            }

                            RowLayout {
                                spacing: 10
                                Layout.fillWidth: true
                                Text {
                                    text: executorDashboardScreen.getTxt("lbl_organize_opt")
                                    color: textSilver
                                    font.pixelSize: 11
                                }
                                ComboBox {
                                    id: organizeCriteriaCombo
                                    model: [executorDashboardScreen.getTxt("opt_ext"), executorDashboardScreen.getTxt("opt_date")]
                                    Layout.fillWidth: true
                                }
                            }

                            Button {
                                text: executorDashboardScreen.getTxt("btn_generate_commands")
                                Layout.fillWidth: true
                                onClicked: {
                                    var opt = organizeCriteriaCombo.currentIndex === 0 ? "by_extension" : "by_date"
                                    var plan = backend.generate_organize_plan(organizeFolderPath.text, opt)
                                    commandEditor.text = plan
                                    executorDashboardScreen.currentSubTab = "dashboard"
                                }
                            }
                        }
                    }

                    // Template 2: Batch Rename Card
                    Rectangle {
                        Layout.fillWidth: true
                        height: 220
                        color: cardSlateBg
                        border.color: borderSlate
                        radius: 8

                        ColumnLayout {
                            anchors.fill: parent
                            anchors.margins: 15
                            spacing: 10

                            RowLayout {
                                Text {
                                    text: "🏷️ " + executorDashboardScreen.getTxt("tpl_rename")
                                    color: metallicGold
                                    font.bold: true
                                    font.pixelSize: 13
                                }
                                Spacer { Layout.fillWidth: true }
                            }

                            RowLayout {
                                spacing: 10
                                Layout.fillWidth: true
                                Text {
                                    text: executorDashboardScreen.getTxt("lbl_folder_path")
                                    color: textSilver
                                    font.pixelSize: 11
                                }
                                TextField {
                                    id: renameFolderPath
                                    text: backend._base_dir
                                    placeholderText: "e.g. C:/Photos"
                                    Layout.fillWidth: true
                                }
                            }

                            RowLayout {
                                spacing: 10
                                Layout.fillWidth: true
                                Text {
                                    text: executorDashboardScreen.getTxt("lbl_rename_pattern")
                                    color: textSilver
                                    font.pixelSize: 11
                                }
                                TextField {
                                    id: renamePatternText
                                    text: "document_{num}"
                                    placeholderText: "e.g. img_backup_{num}"
                                    Layout.fillWidth: true
                                }
                            }

                            Button {
                                text: executorDashboardScreen.getTxt("btn_generate_commands")
                                Layout.fillWidth: true
                                onClicked: {
                                    var plan = backend.generate_rename_plan(renameFolderPath.text, renamePatternText.text)
                                    commandEditor.text = plan
                                    executorDashboardScreen.currentSubTab = "dashboard"
                                }
                            }
                        }
                    }
                }
            }

            // SUB-TAB 2: DETAILED COMMAND LOG HISTORY
            ColumnLayout {
                Layout.fillWidth: true
                Layout.fillHeight: true
                spacing: 10

                RowLayout {
                    Layout.fillWidth: true
                    Text {
                        text: "📜 " + executorDashboardScreen.getTxt("tab_history")
                        color: metallicGold
                        font.bold: true
                        font.pixelSize: 13
                    }
                    Spacer { Layout.fillWidth: true }
                    Button {
                        text: executorDashboardScreen.getTxt("btn_clear_history")
                        onClicked: {
                            backend.clear_command_history()
                            executorDashboardScreen.refreshSuggestionsAndData()
                        }
                    }
                }

                Rectangle {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    color: cardSlateBg
                    border.color: borderSlate
                    radius: 8

                    ListView {
                        anchors.fill: parent
                        anchors.margins: 10
                        model: commandHistoryModel
                        spacing: 8
                        clip: true
                        delegate: Rectangle {
                            width: parent.width
                            height: model.expanded ? 160 : 54
                            color: "#1A2230"
                            border.color: model.status === "completed" ? successGreen : errorRed
                            radius: 6

                            Behavior on height {
                                NumberAnimation { duration: 150 }
                            }

                            ColumnLayout {
                                anchors.fill: parent
                                anchors.margins: 10
                                spacing: 4

                                RowLayout {
                                    Layout.fillWidth: true
                                    Text {
                                        text: model.status === "completed" ? "✅" : "❌"
                                        font.pixelSize: 14
                                    }
                                    ColumnLayout {
                                        spacing: 1
                                        Text {
                                            text: model.command
                                            color: softGold
                                            font.bold: true
                                            font.pixelSize: 11
                                        }
                                        Text {
                                            text: model.created_at
                                            color: textGray
                                            font.pixelSize: 8
                                        }
                                    }
                                    Spacer { Layout.fillWidth: true }
                                    
                                    Rectangle {
                                        width: 80
                                        height: 22
                                        color: model.status === "completed" ? "#064E3B" : "#7F1D1D"
                                        radius: 4
                                        Text {
                                            anchors.centerIn: parent
                                            text: model.status === "completed" ? executorDashboardScreen.getTxt("status_completed") : executorDashboardScreen.getTxt("status_failed")
                                            color: textSilver
                                            font.bold: true
                                            font.pixelSize: 9
                                        }
                                    }

                                    Button {
                                        text: backend.appLanguage === "ar" ? "إعادة تشغيل ⚡" : "Re-run"
                                        implicitHeight: 24
                                        onClicked: {
                                            var output = backend.execute_command_advanced(model.command, executorDashboardScreen.activeProject, false)
                                            consoleOutputText.text = output
                                            executorDashboardScreen.currentSubTab = "dashboard"
                                        }
                                    }

                                    Button {
                                        text: model.expanded ? "▲" : "▼"
                                        implicitWidth: 30
                                        implicitHeight: 24
                                        onClicked: {
                                            model.expanded = !model.expanded
                                        }
                                    }
                                }

                                // Expanded area output console preview
                                Rectangle {
                                    visible: model.expanded
                                    Layout.fillWidth: true
                                    Layout.fillHeight: true
                                    color: slateBg
                                    border.color: borderSlate
                                    radius: 4
                                    clip: true
                                    ScrollView {
                                        anchors.fill: parent
                                        TextArea {
                                            text: model.output
                                            readOnly: true
                                            color: textSilver
                                            font.family: "Consolas"
                                            font.pixelSize: 9
                                            wrapMode: TextEdit.Wrap
                                            selectByMouse: true
                                            background: null
                                        }
                                    }
                                }
                            }
                        }

                        Text {
                            anchors.centerIn: parent
                            visible: commandHistoryModel.count === 0
                            text: executorDashboardScreen.getTxt("lbl_history_empty")
                            color: textGray
                            font.pixelSize: 11
                        }
                    }
                }
            }
        }
    }
}
