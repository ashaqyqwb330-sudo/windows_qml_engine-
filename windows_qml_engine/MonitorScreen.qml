import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

Rectangle {
    id: monitorScreen
    color: "transparent"

    // Dictionary of localizations
    function getTxt(key) {
        var translations = {
            "title": { "ar": "🖥️ الشاشة والمراقب الذكي", "en": "🖥️ Intelligent System Monitor & AI Hub" },
            "subtitle": { "ar": "مراقبة الحافظة، إدارة طابور المهام، تصدير السجلات المتقدم، وتحرير الملفات", "en": "Clipboard automation, task queue runner, advanced log reports, and direct file editing" },
            "project_lbl": { "ar": "المشروع النشط:", "en": "Active Workspace:" },
            "tab_extractor": { "ar": "⚙️ معالج التوجيهات", "en": "⚙️ Instruction Parser" },
            "tab_clipboard": { "ar": "📋 سجل الحافظة", "en": "📋 Clipboard History" },
            "tab_tasks": { "ar": "⚙️ طابور المهام الذكية", "en": "⚙️ AI Task Hub" },
            "tab_export": { "ar": "📊 التقارير والتصدير", "en": "📊 Diagnostics & Export" },
            "btn_apply": { "ar": "تطبيق التوجيهات البرمجية ⚡", "en": "Apply Code Directives ⚡" },
            "btn_sample": { "ar": "إدراج حزمة تجريبية 📝", "en": "Insert Demo Pack 📝" },
            "btn_clear": { "ar": "مسح المحتوى ❌", "en": "Clear Content ❌" },
            "search_holder": { "ar": "بحث في العناصر...", "en": "Search entries..." },
            "type_all": { "ar": "الكل", "en": "All Types" },
            "btn_delete": { "ar": "حذف", "en": "Delete" },
            "btn_copy": { "ar": "نسخ", "en": "Copy" },
            "btn_run": { "ar": "تنفيذ", "en": "Execute" },
            "lbl_no_data": { "ar": "لا توجد عناصر مسجلة حالياً.", "en": "No entries available yet." },
            "btn_add_task": { "ar": "➕ إضافة مهمة جديدة", "en": "➕ Add Smart Task" },
            "task_title": { "ar": "عنوان المهمة:", "en": "Task Title:" },
            "task_cmd": { "ar": "الأمر البرمجي (shell command):", "en": "Shell Command:" },
            "btn_run_task": { "ar": "تشغيل ⚡", "en": "Execute ⚡" },
            "btn_clear_completed": { "ar": "مكنسة المهام 🧹", "en": "Clear Inactive 🧹" },
            "export_title": { "ar": "تصدير السجلات المتقدم:", "en": "Advanced Logs Exporter:" },
            "export_html": { "ar": "تصدير كتقرير تفاعلي HTML 🌐", "en": "Export as Interactive HTML 🌐" },
            "export_txt": { "ar": "تصدير كتقرير نصي TXT 📄", "en": "Export as Text Document TXT 📄" },
            "export_csv": { "ar": "تصدير كجدول CSV 📊", "en": "Export as Spreadsheet CSV 📊" },
            "export_json": { "ar": "تصدير كملف مهيكل JSON 📦", "en": "Export as JSON Object 📦" },
            "option_hide_sensitive": { "ar": "تعمية وحماية البيانات الحساسة (مفاتيح API)", "en": "Mask & obfuscate sensitive API keys and credentials" },
            "option_html_theme": { "ar": "سمة التقرير البصري (HTML Theme):", "en": "HTML Report Color Palette:" },
            "opt_theme_gold": { "ar": "ذهبي ملكي (Gold Slate)", "en": "Royal Gold (Gold Slate)" },
            "opt_theme_dark": { "ar": "أسود معتم (Infinite Dark)", "en": "Midnight (Infinite Dark)" },
            "opt_theme_light": { "ar": "ناصع البياض (Classic Light)", "en": "Classic Silver (Classic Light)" },
            "stats_title": { "ar": "مؤشرات أداء العمليات (Diagnostics Stats):", "en": "System Diagnostic Indicators:" },
            "total_logs": { "ar": "إجمالي السجلات:", "en": "Total Logs:" },
            "success_rate": { "ar": "معدل النجاح:", "en": "Success Rate:" },
            "most_active": { "ar": "النشاط الأكثر تكراراً:", "en": "Most Frequent Type:" }
        };
        var isAr = (backend.appLanguage === "ar");
        return translations[key] ? (isAr ? translations[key]["ar"] : translations[key]["en"]) : "";
    }

    property string currentSubTab: "extractor" // "extractor", "clipboard", "tasks", "export"
    property string statsJson: '{"total":0,"success":0,"error":0,"info":0,"warning":0,"success_rate":0,"most_active":"N/A"}'

    ListModel { id: clipboardHistoryModel }
    ListModel { id: taskQueueModel }
    ListModel { id: recentGeneratedFilesModel }

    // Timer to refresh stats and tables periodically
    Timer {
        interval: 1500
        running: true
        repeat: true
        onTriggered: {
            refreshStatsAndData()
        }
    }

    Component.onCompleted: {
        refreshStatsAndData()
    }

    function refreshStatsAndData() {
        // Stats
        statsJson = backend.get_advanced_logs_stats()
        
        // Clipboard History
        var clips = JSON.parse(backend.get_clipboard_history_json(clipTypeFilter.currentText === "All" || clipTypeFilter.currentIndex === 0 ? "all" : clipTypeFilter.currentText.toLowerCase(), clipSearchInput.text))
        clipboardHistoryModel.clear()
        for (var i = 0; i < clips.length; i++) {
            clipboardHistoryModel.append(clips[i])
        }

        // Tasks
        var tasks = JSON.parse(backend.get_tasks_json())
        taskQueueModel.clear()
        for (var j = 0; j < tasks.length; j++) {
            taskQueueModel.append(tasks[j])
        }

        // Recent Generated Files
        try {
            var files = JSON.parse(backend.get_extracted_files_json())
            recentGeneratedFilesModel.clear()
            // show latest 10
            var limit = Math.min(files.length, 10)
            for (var k = 0; k < limit; k++) {
                recentGeneratedFilesModel.append(files[k])
            }
        } catch (e) {
            console.log("Error loading recent files: " + e)
        }
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 20
        spacing: 12

        // HEADER
        RowLayout {
            Layout.fillWidth: true
            ColumnLayout {
                spacing: 2
                Text {
                    text: monitorScreen.getTxt("title")
                    color: metallicGold
                    font.bold: true
                    font.pixelSize: 18
                }
                Text {
                    text: monitorScreen.getTxt("subtitle")
                    color: textGray
                    font.pixelSize: 11
                }
            }
            Spacer { Layout.fillWidth: true }

            RowLayout {
                spacing: 8
                Text {
                    text: monitorScreen.getTxt("project_lbl")
                    color: textSilver
                    font.pixelSize: 11
                }
                ComboBox {
                    id: activeProjectCombo
                    model: projectsModel
                    textRole: "name"
                    currentIndex: 0
                    implicitWidth: 140
                    onCurrentTextChanged: {
                        if (currentText !== "") {
                            backend.activeProject = currentText
                        }
                    }
                }
            }
        }

        // TOP STATS CARD with Visual Color-Coded Bar Chart
        Rectangle {
            Layout.fillWidth: true
            height: 85
            color: cardSlateBg
            border.color: borderSlate
            border.width: 1.5
            radius: 10

            property var statsObj: JSON.parse(monitorScreen.statsJson)

            RowLayout {
                anchors.fill: parent
                anchors.margins: 14
                spacing: 20

                // Total Logs
                ColumnLayout {
                    spacing: 4
                    Text {
                        text: monitorScreen.getTxt("total_logs")
                        color: textGray
                        font.pixelSize: 10
                    }
                    Text {
                        text: parent.parent.statsObj.total
                        color: textSilver
                        font.bold: true
                        font.pixelSize: 20
                    }
                }

                // Success Rate
                ColumnLayout {
                    spacing: 4
                    Text {
                        text: monitorScreen.getTxt("success_rate")
                        color: textGray
                        font.pixelSize: 10
                    }
                    Text {
                        text: parent.parent.statsObj.success_rate + " %"
                        color: parent.parent.statsObj.success_rate > 80 ? "#10B981" : "#EF4444"
                        font.bold: true
                        font.pixelSize: 20
                    }
                }

                // Most Active
                ColumnLayout {
                    spacing: 4
                    Text {
                        text: monitorScreen.getTxt("most_active")
                        color: textGray
                        font.pixelSize: 10
                    }
                    Text {
                        text: parent.parent.statsObj.most_active
                        color: softGold
                        font.bold: true
                        font.pixelSize: 14
                    }
                }

                // Visual multi-color bar chart proportional distribution
                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 6

                    Text {
                        text: monitorScreen.getTxt("stats_title")
                        color: textSilver
                        font.pixelSize: 10
                    }

                    // Multi-colored bar
                    Rectangle {
                        Layout.fillWidth: true
                        height: 12
                        color: "#232A3B"
                        radius: 6
                        clip: true

                        Row {
                            anchors.fill: parent
                            
                            // Success green block
                            Rectangle {
                                height: parent.height
                                color: "#10B981"
                                width: parent.width * (parent.parent.parent.parent.statsObj.total > 0 ? (parent.parent.parent.parent.statsObj.success / parent.parent.parent.parent.statsObj.total) : 0)
                            }
                            // Error red block
                            Rectangle {
                                height: parent.height
                                color: "#EF4444"
                                width: parent.width * (parent.parent.parent.parent.statsObj.total > 0 ? (parent.parent.parent.parent.statsObj.error / parent.parent.parent.parent.statsObj.total) : 0)
                            }
                            // Info blue block
                            Rectangle {
                                height: parent.height
                                color: "#3B82F6"
                                width: parent.width * (parent.parent.parent.parent.statsObj.total > 0 ? (parent.parent.parent.parent.statsObj.info / parent.parent.parent.parent.statsObj.total) : 0)
                            }
                            // Warning gold/orange block
                            Rectangle {
                                height: parent.height
                                color: "#F59E0B"
                                width: parent.width * (parent.parent.parent.parent.statsObj.total > 0 ? (parent.parent.parent.parent.statsObj.warning / parent.parent.parent.parent.statsObj.total) : 0)
                            }
                        }
                    }

                    // Legend markers row
                    RowLayout {
                        spacing: 12
                        Rectangle { color: "#10B981"; radius: 4; width: 8; height: 8 }
                        Text { text: "Success (" + parent.parent.parent.statsObj.success + ")"; color: textGray; font.pixelSize: 9 }
                        
                        Rectangle { color: "#EF4444"; radius: 4; width: 8; height: 8 }
                        Text { text: "Error (" + parent.parent.parent.statsObj.error + ")"; color: textGray; font.pixelSize: 9 }

                        Rectangle { color: "#3B82F6"; radius: 4; width: 8; height: 8 }
                        Text { text: "Info (" + parent.parent.parent.statsObj.info + ")"; color: textGray; font.pixelSize: 9 }
                    }
                }
            }
        }

        // MIDDLE TABS SELECTOR ROW
        RowLayout {
            Layout.fillWidth: true
            spacing: 6

            Repeater {
                model: [
                    { "id": "extractor", "lbl": "tab_extractor" },
                    { "id": "clipboard", "lbl": "tab_clipboard" },
                    { "id": "tasks", "lbl": "tab_tasks" },
                    { "id": "export", "lbl": "tab_export" }
                ]

                Button {
                    id: tabBtn
                    text: monitorScreen.getTxt(modelData.lbl)
                    checkable: true
                    checked: monitorScreen.currentSubTab === modelData.id
                    implicitHeight: 34
                    onClicked: monitorScreen.currentSubTab = modelData.id

                    background: Rectangle {
                        color: tabBtn.checked ? borderSlate : cardSlateBg
                        border.color: tabBtn.checked ? metallicGold : borderSlate
                        border.width: 1
                        radius: 6
                    }
                    contentItem: Text {
                        text: tabBtn.text
                        color: tabBtn.checked ? metallicGold : textSilver
                        font.bold: true
                        font.pixelSize: 11
                        horizontalAlignment: Text.AlignHCenter
                        verticalAlignment: Text.AlignVCenter
                    }
                }
            }
        }

        // BODY AREA - STACK-LIKE SECTIONS
        Rectangle {
            Layout.fillWidth: true
            Layout.fillHeight: true
            color: "transparent"

            // ------------------ SUB-TAB 1: EXTRACTOR / CODE PARSER ------------------
            ColumnLayout {
                anchors.fill: parent
                visible: monitorScreen.currentSubTab === "extractor"
                spacing: 10

                // Dynamic floating Clipboard Builder banner overlay
                Rectangle {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 40
                    color: borderSlate
                    border.color: metallicGold
                    radius: 8
                    visible: clipboardBannerText.text !== ""
                    
                    RowLayout {
                        anchors.fill: parent
                        anchors.leftMargin: 15
                        anchors.rightMargin: 15
                        
                        Text {
                            id: clipboardBannerText
                            text: ""
                            color: textSilver
                            font.bold: true
                            font.pixelSize: 11.5
                        }
                        Spacer { Layout.fillWidth: true }
                        Button {
                            text: backend.appLanguage === "ar" ? "تطبيق الحزمة البرمجية ⚡" : "Apply Builder Pack ⚡"
                            implicitHeight: 26
                            onClicked: {
                                codeTextArea.text = clipboardBannerRect.tagText
                                backend.process_text_directives_for_project(clipboardBannerRect.tagText, activeProjectCombo.currentText)
                                clipboardBannerText.text = ""
                            }
                        }
                        Button {
                            text: backend.appLanguage === "ar" ? "تجاهل ❌" : "Ignore ❌"
                            implicitHeight: 26
                            onClicked: clipboardBannerText.text = ""
                        }
                    }
                    property string tagText: ""
                }

                ScrollView {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    TextArea {
                        id: codeTextArea
                        placeholderText: backend.appLanguage === "ar" ? "ألصق التوجيهات البرمجية أو أكواد @builder هنا لتنقيبها وبنائها تلقائياً..." : "Paste script directives or @builder code packs here to process and write structure instantly..."
                        color: textSilver
                        font.family: "Consolas"
                        font.pixelSize: 11.5
                        background: Rectangle {
                            color: cardSlateBg
                            border.color: borderSlate
                            border.width: 1
                            radius: 8
                        }
                        selectByMouse: true
                        wrapMode: TextEdit.Wrap
                    }
                }

                RowLayout {
                    spacing: 12
                    Button {
                        text: monitorScreen.getTxt("btn_apply")
                        Layout.preferredWidth: 200
                        Layout.preferredHeight: 38
                        onClicked: {
                            backend.process_text_directives_for_project(codeTextArea.text, activeProjectCombo.currentText)
                        }
                    }
                    Button {
                        text: monitorScreen.getTxt("btn_sample")
                        Layout.preferredHeight: 38
                        onClicked: {
                            codeTextArea.text = "// @builder:file tests/app_v2.py\nprint('Golden Platform Pro Engine running safe code!')\n// @builder:end\n\n// @executor: python tests/app_v2.py"
                        }
                    }
                    Button {
                        text: monitorScreen.getTxt("btn_clear")
                        Layout.preferredHeight: 38
                        onClicked: codeTextArea.text = ""
                    }
                }
            }

            // ------------------ SUB-TAB 2: CLIPBOARD HISTORY ------------------
            ColumnLayout {
                anchors.fill: parent
                visible: monitorScreen.currentSubTab === "clipboard"
                spacing: 10

                // Search & Filter row
                RowLayout {
                    Layout.fillWidth: true
                    spacing: 8

                    TextField {
                        id: clipSearchInput
                        placeholderText: monitorScreen.getTxt("search_holder")
                        Layout.fillWidth: true
                        implicitHeight: 36
                        color: textSilver
                        background: Rectangle {
                            color: cardSlateBg
                            border.color: borderSlate
                            radius: 6
                        }
                        onTextChanged: refreshStatsAndData()
                    }

                    ComboBox {
                        id: clipTypeFilter
                        model: ["All", "Builder", "Url", "Code", "Text"]
                        implicitWidth: 110
                        implicitHeight: 36
                        onCurrentIndexChanged: refreshStatsAndData()
                    }
                }

                // List
                ListView {
                    id: clipListView
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    model: clipboardHistoryModel
                    spacing: 8
                    clip: true

                    delegate: Rectangle {
                        width: clipListView.width
                        height: 70
                        color: cardSlateBg
                        border.color: borderSlate
                        radius: 8

                        RowLayout {
                            anchors.fill: parent
                            anchors.margins: 10
                            spacing: 12
                            layoutDirection: backend.appLanguage === "ar" ? Qt.RightToLeft : Qt.LeftToRight

                            // Type icon/badge
                            Rectangle {
                                width: 44
                                height: 44
                                color: "#1F2330"
                                radius: 6
                                Text {
                                    anchors.centerIn: parent
                                    text: {
                                        if (model.capture_type === "clip_builder") return "📦"
                                        if (model.capture_type === "clip_url") return "🔗"
                                        if (model.capture_type === "clip_code") return "💻"
                                        return "📄"
                                    }
                                    font.pixelSize: 18
                                }
                            }

                            // Info
                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: 2

                                Text {
                                    text: model.title
                                    color: softGold
                                    font.bold: true
                                    font.pixelSize: 11
                                    elide: Text.ElideRight
                                }
                                Text {
                                    text: model.created_at
                                    color: textGray
                                    font.pixelSize: 9
                                }
                            }

                            // Actions
                            RowLayout {
                                spacing: 4
                                Button {
                                    text: monitorScreen.getTxt("btn_copy")
                                    implicitWidth: 50
                                    implicitHeight: 28
                                    onClicked: {
                                        backend.set_clipboard_text(model.content)
                                        backend.notificationSent("Copied", "Content copied to clipboard", "success")
                                    }
                                }
                                Button {
                                    text: monitorScreen.getTxt("btn_run")
                                    implicitWidth: 50
                                    implicitHeight: 28
                                    visible: model.capture_type === "clip_builder"
                                    onClicked: {
                                        backend.process_text_directives_for_project(model.content, activeProjectCombo.currentText)
                                    }
                                }
                                Button {
                                    text: monitorScreen.getTxt("btn_delete")
                                    implicitWidth: 50
                                    implicitHeight: 28
                                    onClicked: {
                                        backend.delete_clipboard_entry(model.id)
                                        refreshStatsAndData()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ------------------ SUB-TAB 3: SMART TASK HUB ------------------
            ColumnLayout {
                anchors.fill: parent
                visible: monitorScreen.currentSubTab === "tasks"
                spacing: 10

                RowLayout {
                    Layout.fillWidth: true
                    Button {
                        text: monitorScreen.getTxt("btn_add_task")
                        implicitHeight: 36
                        onClicked: addTaskPopup.open()
                    }
                    Spacer { Layout.fillWidth: true }
                    Button {
                        text: monitorScreen.getTxt("btn_clear_completed")
                        implicitHeight: 36
                        onClicked: {
                            backend.clear_completed_tasks()
                            refreshStatsAndData()
                        }
                    }
                }

                // List of Tasks
                ListView {
                    id: taskListView
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    model: taskQueueModel
                    spacing: 8
                    clip: true

                    delegate: Rectangle {
                        width: taskListView.width
                        height: 75
                        color: cardSlateBg
                        border.color: borderSlate
                        radius: 8

                        RowLayout {
                            anchors.fill: parent
                            anchors.margins: 10
                            spacing: 12
                            layoutDirection: backend.appLanguage === "ar" ? Qt.RightToLeft : Qt.LeftToRight

                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: 2

                                RowLayout {
                                    spacing: 6
                                    Text {
                                        text: model.title
                                        color: textSilver
                                        font.bold: true
                                        font.pixelSize: 11.5
                                    }
                                    Rectangle {
                                        color: model.status === "completed" ? "#10B981" : (model.status === "running" ? "#3B82F6" : (model.status === "failed" ? "#EF4444" : "#F59E0B"))
                                        width: 65
                                        height: 16
                                        radius: 4
                                        Text {
                                            anchors.centerIn: parent
                                            text: model.status.toUpperCase()
                                            color: "white"
                                            font.bold: true
                                            font.pixelSize: 8
                                        }
                                    }
                                }
                                Text {
                                    text: model.command
                                    color: textGray
                                    font.pixelSize: 9
                                    font.family: "Consolas"
                                    elide: Text.ElideRight
                                    Layout.fillWidth: true
                                }
                            }

                            RowLayout {
                                spacing: 4
                                Button {
                                    text: "⚡ Run"
                                    implicitWidth: 55
                                    implicitHeight: 28
                                    visible: model.status !== "running"
                                    onClicked: {
                                        backend.run_task_async(model.id)
                                    }
                                }
                                Button {
                                    text: "📄 Logs"
                                    implicitWidth: 55
                                    implicitHeight: 28
                                    onClicked: {
                                        taskLogsOutput.text = model.output === "" ? "No logs output yet." : model.output
                                        taskLogsPopup.open()
                                    }
                                }
                                Button {
                                    text: "❌"
                                    implicitWidth: 32
                                    implicitHeight: 28
                                    onClicked: {
                                        backend.delete_task(model.id)
                                        refreshStatsAndData()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ------------------ SUB-TAB 4: DIAGNOSTICS & EXPORTS ------------------
            ColumnLayout {
                anchors.fill: parent
                visible: monitorScreen.currentSubTab === "export"
                spacing: 15

                Text {
                    text: monitorScreen.getTxt("export_title")
                    color: softGold
                    font.bold: true
                    font.pixelSize: 12
                }

                // Export Options Group
                Rectangle {
                    Layout.fillWidth: true
                    implicitHeight: 110
                    color: cardSlateBg
                    border.color: borderSlate
                    radius: 8

                    ColumnLayout {
                        anchors.fill: parent
                        anchors.margins: 14
                        spacing: 8

                        RowLayout {
                            CheckBox {
                                id: optHideSensitive
                                text: monitorScreen.getTxt("option_hide_sensitive")
                                checked: true
                            }
                        }

                        RowLayout {
                            spacing: 8
                            Text {
                                text: monitorScreen.getTxt("option_html_theme")
                                color: textSilver
                                font.pixelSize: 11
                            }
                            ComboBox {
                                id: optHtmlTheme
                                model: ["gold", "dark", "light"]
                                currentIndex: 0
                                implicitWidth: 100
                            }
                        }
                    }
                }

                // Grid of Exporters
                GridLayout {
                    columns: 2
                    Layout.fillWidth: true
                    columnSpacing: 10
                    rowSpacing: 10

                    Button {
                        text: monitorScreen.getTxt("export_html")
                        Layout.fillWidth: true
                        implicitHeight: 44
                        onClicked: runExport("html")
                    }

                    Button {
                        text: monitorScreen.getTxt("export_txt")
                        Layout.fillWidth: true
                        implicitHeight: 44
                        onClicked: runExport("txt")
                    }

                    Button {
                        text: monitorScreen.getTxt("export_csv")
                        Layout.fillWidth: true
                        implicitHeight: 44
                        onClicked: runExport("csv")
                    }

                    Button {
                        text: monitorScreen.getTxt("export_json")
                        Layout.fillWidth: true
                        implicitHeight: 44
                        onClicked: runExport("json")
                    }
                }

                // Title of Generated Files
                Text {
                    text: backend.appLanguage === "ar" ? "📂 أحدث الملفات البرمجية التي تم إنشاؤها وتوجيهها للمشروع:" : "📂 Recent Project Script Files & Artifacts:"
                    color: softGold
                    font.bold: true
                    font.pixelSize: 12
                }

                // List of Generated Files
                ListView {
                    id: recentFilesListView
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    model: recentGeneratedFilesModel
                    spacing: 6
                    clip: true

                    delegate: Rectangle {
                        width: recentFilesListView.width
                        height: 38
                        color: "#181D29"
                        border.color: borderSlate
                        radius: 6

                        RowLayout {
                            anchors.fill: parent
                            anchors.margins: 8
                            spacing: 10
                            layoutDirection: backend.appLanguage === "ar" ? Qt.RightToLeft : Qt.LeftToRight

                            Text {
                                text: "📄"
                            }
                            Text {
                                text: model.filename
                                color: textSilver
                                font.bold: true
                                font.pixelSize: 11
                                Layout.fillWidth: true
                                elide: Text.ElideRight
                            }
                            Text {
                                text: (model.size / 1024).toFixed(2) + " KB"
                                color: textGray
                                font.pixelSize: 10
                            }
                            Button {
                                text: backend.appLanguage === "ar" ? "📝 تحرير" : "📝 Edit"
                                implicitWidth: 60
                                implicitHeight: 24
                                onClicked: {
                                    fileEditorPopup.openFileForEdit(model.filepath)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // EXPORT HANDLER
    function runExport(format) {
        var options = {
            "hide_sensitive": optHideSensitive.checked,
            "html_theme": optHtmlTheme.currentText,
            "save_dir": backend.baseDir
        }
        var resJson = backend.export_logs_advanced(format, JSON.stringify(options))
        try {
            var res = JSON.parse(resJson)
            if (res.success) {
                backend.notificationSent(
                    backend.appLanguage === "ar" ? "تم التصدير بنجاح" : "Report Exported",
                    (backend.appLanguage === "ar" ? "تم حفظ التقرير في: " : "Report successfully saved to: ") + res.filename,
                    "success"
                )
            } else {
                backend.notificationSent("Export Failed", res.error, "error")
            }
        } catch (e) {
            console.log("Export parser error: " + e)
        }
    }

    // ADD TASK POPUP DIALOG
    Dialog {
        id: addTaskPopup
        title: backend.appLanguage === "ar" ? "➕ إضافة مهمة جديدة للطابور" : "➕ Queue New Smart Task"
        anchors.centerIn: parent
        width: 420
        height: 280
        modal: true
        background: Rectangle { color: cardSlateBg; border.color: borderSlate; radius: 10 }

        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 14
            spacing: 10

            Text { text: monitorScreen.getTxt("task_title"); color: textSilver; font.pixelSize: 11 }
            TextField {
                id: taskTitleIn
                Layout.fillWidth: true
                color: textSilver
                background: Rectangle { color: slateBg; border.color: borderSlate; radius: 6 }
                placeholderText: "e.g. Build Project"
            }

            Text { text: monitorScreen.getTxt("task_cmd"); color: textSilver; font.pixelSize: 11 }
            TextField {
                id: taskCmdIn
                Layout.fillWidth: true
                color: textSilver
                background: Rectangle { color: slateBg; border.color: borderSlate; radius: 6 }
                placeholderText: "e.g. python build.py"
            }

            Spacer { Layout.fillHeight: true }

            RowLayout {
                spacing: 8
                Button {
                    text: monitorScreen.getTxt("btn_run_task")
                    Layout.fillWidth: true
                    onClicked: {
                        if (taskTitleIn.text !== "") {
                            backend.add_task(taskTitleIn.text, "custom", taskCmdIn.text)
                            taskTitleIn.text = ""
                            taskCmdIn.text = ""
                            addTaskPopup.close()
                            refreshStatsAndData()
                        }
                    }
                }
                Button {
                    text: "Cancel"
                    Layout.fillWidth: true
                    onClicked: addTaskPopup.close()
                }
            }
        }
    }

    // TASK LOGS POPUP DIALOG
    Dialog {
        id: taskLogsPopup
        title: "📄 Task Outputs & Execution Log"
        anchors.centerIn: parent
        width: 500
        height: 380
        modal: true
        background: Rectangle { color: cardSlateBg; border.color: borderSlate; radius: 10 }

        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 12
            spacing: 8

            ScrollView {
                Layout.fillWidth: true
                Layout.fillHeight: true
                TextArea {
                    id: taskLogsOutput
                    font.family: "Consolas"
                    font.pixelSize: 10.5
                    color: textSilver
                    readOnly: true
                    background: Rectangle { color: slateBg; border.color: borderSlate; radius: 6 }
                }
            }

            Button {
                text: "Close"
                Layout.fillWidth: true
                onClicked: taskLogsPopup.close()
            }
        }
    }

    // FILE EDITOR DIALOG
    Dialog {
        id: fileEditorPopup
        title: backend.appLanguage === "ar" ? "📝 محرر ومعدل الملفات المدمج" : "📝 Integrated File Editor & Viewer"
        anchors.centerIn: parent
        width: 620
        height: 520
        modal: true
        background: Rectangle { color: cardSlateBg; border.color: borderSlate; radius: 12 }

        property string currentPath: ""

        function openFileForEdit(filepath) {
            currentPath = filepath
            var cleanPath = filepath.replace(/\\/g, "/")
            var filename = cleanPath.substring(cleanPath.lastIndexOf("/") + 1)
            fileEditorTitle.text = "Editing: " + filename
            fileEditorPath.text = filepath
            fileEditorText.text = backend.read_local_file(filepath)
            
            // Stats
            var txt = fileEditorText.text
            var lines = txt.split("\n").length
            var words = txt.split(/\s+/).filter(function(w){return w !== ""}).length
            fileEditorStats.text = "Lines: " + lines + " | Words: " + words + " | Chars: " + txt.length
            
            fileEditorPopup.open()
        }

        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 12
            spacing: 10

            RowLayout {
                spacing: 8
                Text {
                    id: fileEditorTitle
                    color: metallicGold
                    font.bold: true
                    font.pixelSize: 13
                }
                Spacer { Layout.fillWidth: true }
                Text {
                    id: fileEditorStats
                    color: textGray
                    font.pixelSize: 10
                }
            }

            Text {
                id: fileEditorPath
                color: textGray
                font.pixelSize: 9.5
                elide: Text.ElideMiddle
                Layout.fillWidth: true
            }

            RowLayout {
                Layout.fillWidth: true
                spacing: 6
                TextField {
                    id: fileEditorSearch
                    placeholderText: "Find in file..."
                    Layout.fillWidth: true
                    implicitHeight: 30
                    color: textSilver
                    background: Rectangle { color: slateBg; border.color: borderSlate; radius: 4 }
                    onTextChanged: {
                        // basic search highlight or positioning could go here
                    }
                }
            }

            ScrollView {
                Layout.fillWidth: true
                Layout.fillHeight: true
                TextArea {
                    id: fileEditorText
                    font.family: "Consolas"
                    font.pixelSize: 11
                    color: textSilver
                    background: Rectangle { color: slateBg; border.color: borderSlate; radius: 6 }
                    selectByMouse: true
                    wrapMode: TextEdit.Wrap
                }
            }

            RowLayout {
                spacing: 8
                Button {
                    text: backend.appLanguage === "ar" ? "حفظ التعديلات 💾" : "Save Changes 💾"
                    Layout.fillWidth: true
                    onClicked: {
                        var success = backend.write_local_file(fileEditorPopup.currentPath, fileEditorText.text)
                        if (success) {
                            backend.notificationSent("Saved", "File saved successfully!", "success")
                            fileEditorPopup.close()
                            refreshStatsAndData()
                        } else {
                            backend.notificationSent("Save Failed", "Could not write to file", "error")
                        }
                    }
                }
                Button {
                    text: "Close"
                    Layout.fillWidth: true
                    onClicked: fileEditorPopup.close()
                }
            }
        }
    }
}
