import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import QtQuick.Dialogs

ApplicationWindow {
    id: window
    width: 1150
    height: 800
    visible: true
    title: "المنصة الذهبية الذكية للويندوز Pro - Golden Platform Engine v2.0"

    // Set layout mirroring dynamically for Arabic/English RTL/LTR layouts
    LayoutMirroring.enabled: backend.appLanguage === "ar"
    LayoutMirroring.childrenInherit: true

    // Load active theme parameters
    property var activeThemeColors: loadThemeColors(backend.activeTheme)

    readonly property color slateBg: activeThemeColors.slateBg
    readonly property color cardSlateBg: activeThemeColors.cardSlateBg
    readonly property color borderSlate: activeThemeColors.borderSlate
    readonly property color metallicGold: activeThemeColors.metallicGold
    readonly property color softGold: activeThemeColors.softGold
    readonly property color textSilver: activeThemeColors.textSilver
    
    // Auxiliary feedback colors
    readonly property color successGreen: "#10B981"
    readonly property color warningOrange: "#F59E0B"
    readonly property color errorRed: "#EF4444"
    readonly property color textGray: "#64748B"

    // Mock backward compatibility helpers
    property int projectSelectorCurrentIndex: 0
    QtObject {
        id: projectSelector
        property string currentText: backend.activeProject
        property alias currentIndex: window.projectSelectorCurrentIndex
    }
    QtObject {
        id: codeTextArea
        property string text: ""
    }

    // Theme Selector Loader
    function loadThemeColors(themeId) {
        var themes = {
            "golden_slate": {
                "slateBg": "#05070E", "cardSlateBg": "#0D1127", "borderSlate": "#1E295D",
                "metallicGold": "#FCD34D", "softGold": "#FDE047", "textSilver": "#E4E9FC"
            },
            "emerald_oasis": {
                "slateBg": "#030A07", "cardSlateBg": "#091D15", "borderSlate": "#1B4D3E",
                "metallicGold": "#FCD34D", "softGold": "#E6C645", "textSilver": "#E2F1EC"
            },
            "royal_crimson": {
                "slateBg": "#0F0206", "cardSlateBg": "#21050E", "borderSlate": "#541225",
                "metallicGold": "#FCD34D", "softGold": "#FCD34D", "textSilver": "#FCE4EC"
            },
            "deep_space": {
                "slateBg": "#030303", "cardSlateBg": "#121212", "borderSlate": "#2D2D2D",
                "metallicGold": "#FCD34D", "softGold": "#FFF59D", "textSilver": "#EEEEEE"
            },
            "oceanic_blue": {
                "slateBg": "#020B14", "cardSlateBg": "#071B2D", "borderSlate": "#153E5C",
                "metallicGold": "#FCD34D", "softGold": "#81D4FA", "textSilver": "#E1F5FE"
            },
            "pastel_mint": {
                "slateBg": "#FAFDFC", "cardSlateBg": "#F0F7F4", "borderSlate": "#CCE3DE",
                "metallicGold": "#D97706", "softGold": "#FBBF24", "textSilver": "#1E293B"
            },
            "solar_amber": {
                "slateBg": "#140D02", "cardSlateBg": "#2D1C05", "borderSlate": "#5C3E0C",
                "metallicGold": "#FCD34D", "softGold": "#FFE082", "textSilver": "#FFF8E1"
            },
            "charcoal": {
                "slateBg": "#111111", "cardSlateBg": "#1A1A1A", "borderSlate": "#333333",
                "metallicGold": "#E5A93B", "softGold": "#E5A93B", "textSilver": "#E0E0E0"
            },
            "cyberpunk": {
                "slateBg": "#0F051D", "cardSlateBg": "#1D0D33", "borderSlate": "#4E129B",
                "metallicGold": "#F15BB5", "softGold": "#00F5D4", "textSilver": "#FFEEFF"
            },
            "arctic_frost": {
                "slateBg": "#F4F7F6", "cardSlateBg": "#E9EFF1", "borderSlate": "#B0C4DE",
                "metallicGold": "#4682B4", "softGold": "#87CEFA", "textSilver": "#2F4F4F"
            }
        };
        return themes[themeId] || themes["golden_slate"];
    }

    // Dynamic translation system
    function getTxt(key) {
        var translations = {
            "title": { "ar": "المنصة الذهبية الذكية Pro - الإصدار النهائي 2.0", "en": "Golden Intelligent Platform Pro - Ultimate v2.0" },
            "active_workspace": { "ar": "مجلد العمل النشط:", "en": "Active Workspace Folder:" },
            "change_folder": { "ar": "تغيير 📂", "en": "Change 📂" },
            "tab_extractor": { "ar": "معالج ومستخرج الكود", "en": "Code Extractor & Builder" },
            "tab_pack": { "ar": "تجميع وصنع الحزم", "en": "Directory Pack Creator" },
            "tab_capture": { "ar": "الالتقاط الذكي الفوري", "en": "Smart Capture & Styles" },
            "tab_treedoc": { "ar": "نظام TreeDoc Pro", "en": "TreeDoc Pro Documentation" },
            "tab_gemini": { "ar": "مساعد Gemini AI", "en": "Gemini AI Assistant" },
            "tab_quick": { "ar": "منصة المنفذ والأتمتة", "en": "Executor & Automation" },
            "tab_link_automator": { "ar": "مؤتمت الروابط والدردشات", "en": "Link & Chat Automator" },
            "tab_projects_advanced": { "ar": "إدارة المشاريع المتقدمة", "en": "Advanced Project Manager" },
            "tab_source_export": { "ar": "التصدير الذاتي للمصدر", "en": "Developer Self-Exporter" },
            "tab_prompts": { "ar": "مستودع التوجيهات", "en": "AIPromptHub Guide" },
            "tab_help": { "ar": "مركز المساعدة الدعم", "en": "Help & Learning Center" },
            "tab_browser": { "ar": "متصفح الملفات المحلي", "en": "Local File Browser" },
            "tab_storyteller": { "ar": "يوميات الأحداث", "en": "Visual Storyteller" },
            "tab_status_dash": { "ar": "لوحة جودة الخدمة والحالة", "en": "Status & Quality Dashboard" },
            "tab_dashboard": { "ar": "لوحة الإدارة والإعدادات", "en": "Dashboard & Preferences" },
            "clipboard_monitor": { "ar": "💡 مراقبة الحافظة التلقائية", "en": "💡 Automatic Clipboard Monitor" },
            "clipboard_desc": { "ar": "يقوم المحرك بقراءة الحافظة وتنبيهك عند كشف حزم البناء.", "en": "The engine auto-detects builder packages directly from your clipboard." },
            "clipboard_active": { "ar": "نشط ومراقب 🟢", "en": "Active & Monitoring 🟢" },
            "clipboard_inactive": { "ar": "متوقف 🔴", "en": "Stopped 🔴" },
            "project_selector_lbl": { "ar": "توجيه الملفات للمشروع:", "en": "Direct extracted files to project:" },
            "placeholder_extractor": { "ar": "الصق كود الحزمة البرمجية هنا...\n\nمثال:\n// @builder:file main.py\nprint('أهلاً بك!')\n// @builder:end", "en": "Paste your builder package code here...\n\nExample:\n// @builder:file main.py\nprint('Welcome!')\n// @builder:end" },
            "btn_apply": { "ar": "⚡ تطبيق ومعالجة الحزمة", "en": "⚡ Process & Apply Package" },
            "btn_sample": { "ar": "إدراج قالب تجريبي 📝", "en": "Insert Demo Template 📝" },
            "btn_clear": { "ar": "مسح 🧹", "en": "Clear 🧹" },
            "folder_to_pack": { "ar": "المجلد المراد تجميعه:", "en": "Folder to package:" },
            "not_selected": { "ar": "لم يتم اختيار مجلد بعد", "en": "No folder selected yet" },
            "browse": { "ar": "استعراض 📂", "en": "Browse 📂" },
            "ignore_patterns": { "ar": "أنماط التجاهل والاستبعاد:", "en": "Ignore patterns (comma-separated):" },
            "btn_pack": { "ar": "📦 تجميع المجلد كحزمة بناء", "en": "📦 Package Folder to Builder" },
            "btn_copy": { "ar": "نسخ الكود للحافظة 📋", "en": "Copy Package Code 📋" },
            "beautifier_title": { "ar": "🧠 نظام الالتقاط الذكي والتجميل المرئي", "en": "🧠 Smart Capture & Document Beautifier" },
            "theme_selector_lbl": { "ar": "المظهر والسمة المخصصة:", "en": "Select Document Theme:" },
            "placeholder_capture": { "ar": "الصق كود، نص عادي أو مستند Markdown هنا لتجميله بلمح البصر...", "en": "Paste code, plain text or Markdown to beautify in a flash..." },
            "btn_capture": { "ar": "🧠⚡ التقاط ومعالجة مرئية", "en": "🧠⚡ Capture & Beautify" },
            "btn_paste": { "ar": "لصق من الحافظة 📋", "en": "Paste from Clipboard 📋" },
            "history_lbl": { "ar": "📜 السجل المحفوظ للالتقاط الذكي (SQLite):", "en": "📜 Saved Smart Captures (SQLite):" },
            "open_file": { "ar": "فتح الملف 📂", "en": "Open File 📂" },
            "treedoc_desc": { "ar": "🌲 نظام TreeDoc Pro - التوثيق الشجري التفاعلي للمجلدات", "en": "🌲 TreeDoc Pro - Interactive Directory Tree Documentation" },
            "export_format": { "ar": "صيغة التصدير:", "en": "Export format:" },
            "btn_generate_tree": { "ar": "🌲 توليد التقرير التفاعلي", "en": "🌲 Generate Interactive Tree" },
            "clean_history": { "ar": "تنظيف السجل 🧹", "en": "Clean Log 🧹" },
            "gemini_input_placeholder": { "ar": "اطرح سؤالك البرمجي على Gemini...", "en": "Ask Gemini any coding question..." },
            "btn_send": { "ar": "إرسال 🚀", "en": "Send 🚀" },
            "settings_title": { "ar": "📊 لوحة التحكم والإعدادات", "en": "📊 Dashboard & Preferences" },
            "api_key_lbl": { "ar": "مفتاح Gemini API الخاص بك:", "en": "Your Gemini API Key:" },
            "btn_save_key": { "ar": "حفظ المفتاح 🔑", "en": "Save API Key 🔑" },
            "sqlite_log_lbl": { "ar": "📜 سجل العمليات والنظام (SQLite Log):", "en": "📜 Operation & Event Logs (SQLite):" },
            "status_connected": { "ar": "المحرك نشط ومتصل بقاعدة البيانات المحلية 🟢", "en": "Engine active and connected to local SQLite 🟢" }
        };
        var isAr = (backend.appLanguage === "ar");
        var item = translations[key];
        if (item) {
            return isAr ? item.ar : item.en;
        }
        return key;
    }

    background: Rectangle {
        color: slateBg
        gradient: Gradient {
            GradientStop { position: 0.0; color: slateBg }
            GradientStop { position: 0.6; color: slateBg }
            GradientStop { position: 1.0; color: cardSlateBg }
        }
    }

    // Helper to refresh SQLite data metrics instantly
    function refreshDatabase() {
        // Fetch projects
        var projStr = backend.get_projects_json()
        var projList = JSON.parse(projStr)
        projectsModel.clear()
        projectsModel.append({ "name": backend.appLanguage === "ar" ? "الافتراضي" : "Default", "path": backend.baseDir, "created_at": "-", "template_json": "" })
        for (var i = 0; i < projList.length; i++) {
            projectsModel.append({
                "name": projList[i].name,
                "path": projList[i].path,
                "created_at": projList[i].created_at || "",
                "template_json": projList[i].template_json || ""
            })
        }

        // Fetch logs
        var logStr = backend.get_logs_json()
        var logList = JSON.parse(logStr)
        logsModel.clear()
        for (var j = 0; j < logList.length; j++) {
            logsModel.append({
                "type": logList[j].type,
                "message": logList[j].message,
                "created_at": logList[j].created_at
            })
        }

        // Fetch captures
        var capStr = backend.get_captures_json()
        var capList = JSON.parse(capStr)
        capturesModel.clear()
        for (var k = 0; k < capList.length; k++) {
            capturesModel.append({
                "title": capList[k].title,
                "type": capList[k].capture_type,
                "file_path": capList[k].file_path,
                "theme": capList[k].theme,
                "created_at": capList[k].created_at
            })
        }

        // Fetch chats
        var chatStr = backend.get_chats_json()
        var chatList = JSON.parse(chatStr)
        chatsModel.clear()
        for (var l = 0; l < chatList.length; l++) {
            chatsModel.append({
                "role": chatList[l].role,
                "message": chatList[l].message
            })
        }

        // Fetch custom styles
        var styleStr = backend.get_styles_json()
        var styleList = JSON.parse(styleStr)
        stylesModel.clear()
        for (var m = 0; m < styleList.length; m++) {
            stylesModel.append({
                "name": styleList[m].name,
                "css_code": styleList[m].css_code
            })
        }

        // Fetch storyteller entries
        try {
            var storiesStr = backend.get_stories_json()
            var storiesList = JSON.parse(storiesStr)
            storytellerModel.clear()
            for (var s = 0; s < storiesList.length; s++) {
                storytellerModel.append({
                    "time": storiesList[s].time || "",
                    "title": storiesList[s].title || "",
                    "desc": storiesList[s].desc || "",
                    "icon": storiesList[s].icon || "📦",
                    "details": storiesList[s].details || "",
                    "success_count": storiesList[s].success_count || 0,
                    "info_count": storiesList[s].info_count || 0,
                    "error_count": storiesList[s].error_count || 0
                })
            }
        } catch(e) { console.log("Storyteller load error:", e) }
        
        geminiApiKeyInput.text = backend.get_gemini_api_key()
        canvasChart.requestPaint()
    }

    function activateProject(projectName) {
        for (var i = 0; i < projectsModel.count; i++) {
            if (projectsModel.get(i).name === projectName) {
                projectSelector.currentIndex = i;
                backend.activeProject = projectName;
                backend.log_action("info", "تم تفعيل المشروع: " + projectName);
                return true;
            }
        }
        return false;
    }

    Component.onCompleted: {
        refreshDatabase()
        if (backend.pendingFile !== "") {
            fileOpenerDialog.openFile(backend.pendingFile)
        }
        if (backend.pendingFolder !== "") {
            folderOpenerDialog.openFolder(backend.pendingFolder)
        }
        if (backend.pendingSharedText !== "") {
            shareSelectionDialog.loadSharedText(backend.pendingSharedText)
        }
    }

    // SQLite data store proxies
    ListModel { id: projectsModel }
    ListModel { id: logsModel }
    ListModel { id: capturesModel }
    ListModel { id: chatsModel }
    ListModel { id: stylesModel }
    ListModel { id: browserModel }
    ListModel { id: storytellerModel }

    // Desktop Custom Animated Toast Notification Engine
    Rectangle {
        id: toastBox
        width: 320
        height: 75
        color: cardSlateBg
        border.color: metallicGold
        border.width: 1
        radius: 8
        anchors.bottom: statusBar.top
        anchors.bottomMargin: 15
        anchors.right: parent.right
        anchors.rightMargin: 15
        z: 9999
        opacity: 0

        RowLayout {
            anchors.fill: parent
            anchors.margins: 12
            spacing: 10

            Rectangle {
                width: 36
                height: 36
                radius: 18
                color: "#1E295D"
                Text {
                    id: toastIcon
                    text: "🔔"
                    anchors.centerIn: parent
                    font.pixelSize: 18
                }
            }

            ColumnLayout {
                spacing: 2
                Layout.fillWidth: true
                Text {
                    id: toastTitle
                    text: ""
                    color: metallicGold
                    font.bold: true
                    font.pixelSize: 12
                }
                Text {
                    id: toastMsg
                    text: ""
                    color: textSilver
                    font.pixelSize: 11
                    wrapMode: Text.Wrap
                    Layout.fillWidth: true
                }
            }
        }

        Behavior on opacity { NumberAnimation { duration: 300 } }
        
        Timer {
            id: toastTimer
            interval: 4000
            onTriggered: toastBox.opacity = 0
        }

        function trigger(title, message, type) {
            toastTitle.text = title
            toastMsg.text = message
            if (type === "success") {
                toastIcon.text = "✅"
            } else if (type === "warning") {
                toastIcon.text = "⚠️"
            } else {
                toastIcon.text = "ℹ️"
            }
            toastBox.opacity = 0.95
            toastTimer.restart()
        }
    }

    // Frameless Golden Bubble Overlay Drawer (Interactive quick controls)
    Window {
        id: bubbleOverlay
        width: 250
        height: 180
        visible: backend.bubbleEnabled
        flags: Qt.WindowStaysOnTopHint | Qt.FramelessWindowHint | Qt.Tool
        color: "transparent"
        x: window.x + window.width - 270
        y: window.y + window.height - 240

        Rectangle {
            anchors.fill: parent
            color: "#E5A93B"
            radius: 12
            border.color: "#FFFFFF"
            border.width: 2
            opacity: dragArea.containsPress ? 0.9 : 0.85

            MouseArea {
                id: dragArea
                anchors.fill: parent
                drag.target: bubbleOverlay
                
                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 10
                    spacing: 8

                    RowLayout {
                        Text {
                            text: backend.appLanguage === "ar" ? "👑 فقاعة الاختصار الذهبية" : "👑 Golden Short Bubble"
                            color: "#05070E"
                            font.bold: true
                            font.pixelSize: 11
                        }
                        Spacer { Layout.fillWidth: true }
                        Button {
                            text: "❌"
                            flat: true
                            implicitWidth: 20
                            implicitHeight: 20
                            onClicked: bubbleOverlay.visible = false
                        }
                    }

                    Rectangle {
                        Layout.fillWidth: true
                        height: 1
                        color: "#05070E"
                    }

                    Button {
                        text: backend.appLanguage === "ar" ? "⚡ لصق ومعالجة الحزمة" : "⚡ Paste & Extract Pack"
                        Layout.fillWidth: true
                        onClicked: {
                            var clip = backend.get_clipboard_text()
                            if (clip) {
                                codeTextArea.text = clip
                                backend.process_text_directives_for_project(clip, projectSelector.currentText)
                            }
                        }
                    }

                    Button {
                        text: backend.appLanguage === "ar" ? "🧠 معالجة بصرية فورية" : "🧠 Quick Smart Capture"
                        Layout.fillWidth: true
                        onClicked: {
                            var clip = backend.get_clipboard_text()
                            if (clip) {
                                backend.smart_capture_content_v2(clip, "space")
                            }
                        }
                    }

                    Text {
                        text: backend.appLanguage === "ar" ? "اسحب لنقل الفقاعة الذهبية" : "Drag to move bubble anywhere"
                        color: "#3A2E12"
                        font.pixelSize: 9
                        horizontalAlignment: Text.AlignHCenter
                        Layout.fillWidth: true
                    }
                }
            }
        }
    }

    // Top Header Navigation Bar
    Rectangle {
        id: headerBar
        width: parent.width
        height: 75
        color: cardSlateBg
        border.color: borderSlate
        border.width: 1

        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 20
            anchors.rightMargin: 20
            spacing: 15

            RowLayout {
                spacing: 12
                Rectangle {
                    width: 44
                    height: 44
                    color: "transparent"
                    border.color: metallicGold
                    border.width: 2
                    radius: 10
                    Text {
                        anchors.centerIn: parent
                        text: "👑"
                        font.pixelSize: 22
                    }
                }
                ColumnLayout {
                    spacing: 2
                    Text {
                        text: getTxt("title")
                        color: textSilver
                        font.bold: true
                        font.pixelSize: 18
                    }
                    Text {
                        text: backend.appLanguage === "ar" ? "نظام المعالجة والتصدير المتكامل للمحترفين" : "Ultimate Code Builder & Secure Platform"
                        color: textGray
                        font.pixelSize: 11
                    }
                }
            }

            Spacer { Layout.fillWidth: true }

            // Bilingual Quick Toggle
            RowLayout {
                spacing: 8
                Text { text: "العربية"; color: backend.appLanguage === "ar" ? metallicGold : textGray; font.pixelSize: 11 }
                Switch {
                    checked: backend.appLanguage === "en"
                    onCheckedChanged: {
                        backend.appLanguage = checked ? "en" : "ar"
                    }
                }
                Text { text: "English"; color: backend.appLanguage === "en" ? metallicGold : textGray; font.pixelSize: 11 }
            }

            // Theme Switcher dropdown
            RowLayout {
                spacing: 8
                Text { text: backend.appLanguage === "ar" ? "المظهر:" : "Theme:"; color: textGray; font.pixelSize: 11 }
                ComboBox {
                    id: globalThemeSelector
                    model: ["golden_slate", "emerald_oasis", "royal_crimson", "deep_space", "oceanic_blue", "pastel_mint", "solar_amber", "charcoal", "cyberpunk", "arctic_frost"]
                    currentIndex: model.indexOf(backend.activeTheme)
                    onCurrentTextChanged: {
                        if (currentText) {
                            backend.activeTheme = currentText
                        }
                    }
                    implicitWidth: 140
                }
            }

            // Permissions Dashboard Trigger
            Button {
                text: "🛡️"
                Layout.preferredHeight: 38
                Layout.preferredWidth: 38
                hoverEnabled: true
                ToolTip.visible: hovered
                ToolTip.text: backend.appLanguage === "ar" ? "لوحة الأذونات والصلاحيات" : "Permissions & Privileges"
                onClicked: permissionsDashboardDialog.open()
                background: Rectangle {
                    color: parent.hovered ? borderSlate : "transparent"
                    border.color: parent.hovered ? metallicGold : borderSlate
                    border.width: 1
                    radius: 8
                }
                contentItem: Text {
                    text: parent.text
                    font.pixelSize: 18
                    horizontalAlignment: Text.AlignHCenter
                    verticalAlignment: Text.AlignVCenter
                }
            }

            // Service Status Dashboard Trigger
            Button {
                text: "🩺"
                Layout.preferredHeight: 38
                Layout.preferredWidth: 38
                hoverEnabled: true
                ToolTip.visible: hovered
                ToolTip.text: backend.appLanguage === "ar" ? "حالة الخدمة والصحة" : "Service Status & Health"
                onClicked: statusDashboardDialog.open()
                background: Rectangle {
                    color: parent.hovered ? borderSlate : "transparent"
                    border.color: parent.hovered ? metallicGold : borderSlate
                    border.width: 1
                    radius: 8
                }
                contentItem: Text {
                    text: parent.text
                    font.pixelSize: 18
                    horizontalAlignment: Text.AlignHCenter
                    verticalAlignment: Text.AlignVCenter
                }
            }

            // Active Workspace Path Info Display
            RowLayout {
                spacing: 10
                Text {
                    text: getTxt("active_workspace")
                    color: textGray
                    font.pixelSize: 12
                }
                Rectangle {
                    color: slateBg
                    border.color: borderSlate
                    border.width: 1
                    radius: 8
                    implicitWidth: 260
                    implicitHeight: 38
                    
                    RowLayout {
                        anchors.fill: parent
                        anchors.leftMargin: 12
                        anchors.rightMargin: 12
                        
                        Text {
                            text: backend.baseDir
                            color: textSilver
                            font.family: "Consolas"
                            font.pixelSize: 11
                            elide: Text.ElideLeft
                            Layout.fillWidth: true
                        }
                        
                        Button {
                            text: getTxt("change_folder")
                            flat: true
                            Layout.preferredHeight: 28
                            onClicked: folderDialog.open()
                            background: Rectangle {
                                color: parent.hovered ? borderSlate : "transparent"
                                radius: 4
                            }
                            contentItem: Text {
                                text: parent.text
                                color: metallicGold
                                font.bold: true
                                font.pixelSize: 11
                                horizontalAlignment: Text.AlignHCenter
                                verticalAlignment: Text.AlignVCenter
                            }
                        }
                    }
                }
            }
        }
    }

    // Main Workspace Navigation Drawer / Stack Layout
    RowLayout {
        anchors.top: headerBar.bottom
        anchors.bottom: statusBar.top
        anchors.left: parent.left
        anchors.right: parent.right
        spacing: 0

        // Sidebar Navigation Container
        Rectangle {
            Layout.fillHeight: true
            Layout.preferredWidth: 260
            color: cardSlateBg
            border.color: borderSlate
            border.width: 1

            ColumnLayout {
                anchors.fill: parent
                anchors.topMargin: 15
                anchors.bottomMargin: 15
                spacing: 8

                Text {
                    text: backend.appLanguage === "ar" ? "🧭 الأقسام والواجهات" : "🧭 Navigation Hub"
                    color: metallicGold
                    font.bold: true
                    font.pixelSize: 13
                    Layout.alignment: Qt.AlignHCenter
                    Layout.bottomMargin: 5
                }

                ScrollView {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    clip: true

                    Column {
                        width: 250
                        spacing: 6
                        
                        Repeater {
                            model: [
                                { "icon": "⚙️", "key": "tab_extractor", "idx": 0 },
                                { "icon": "📦", "key": "tab_pack", "idx": 1 },
                                { "icon": "🧠", "key": "tab_capture", "idx": 2 },
                                { "icon": "🌲", "key": "tab_treedoc", "idx": 3 },
                                { "icon": "💬", "key": "tab_gemini", "idx": 4 },
                                { "icon": "🚀", "key": "tab_quick", "idx": 5 },
                                { "icon": "📚", "key": "tab_prompts", "idx": 6 },
                                { "icon": "💡", "key": "tab_help", "idx": 7 },
                                { "icon": "📂", "key": "tab_browser", "idx": 8 },
                                { "icon": "📖", "key": "tab_storyteller", "idx": 9 },
                                { "icon": "🛡️", "key": "tab_status_dash", "idx": 10 },
                                { "icon": "📊", "key": "tab_dashboard", "idx": 11 },
                                { "icon": "🔗", "key": "tab_link_automator", "idx": 12 },
                                { "icon": "📁", "key": "tab_projects_advanced", "idx": 13 },
                                { "icon": "📤", "key": "tab_source_export", "idx": 14 }
                            ]
                            
                            Button {
                                id: navBtn
                                text: modelData.icon + "  " + getTxt(modelData.key)
                                width: 230
                                height: 38
                                anchors.horizontalCenter: parent.horizontalCenter
                                checkable: true
                                checked: mainStack.currentIndex === modelData.idx
                                onClicked: mainStack.currentIndex = modelData.idx

                                background: Rectangle {
                                    color: navBtn.checked ? borderSlate : "transparent"
                                    radius: 6
                                    border.color: navBtn.checked ? metallicGold : "transparent"
                                    border.width: 1
                                }
                                contentItem: Text {
                                    text: navBtn.text
                                    color: navBtn.checked ? metallicGold : textSilver
                                    font.bold: true
                                    font.pixelSize: 11
                                    verticalAlignment: Text.AlignVCenter
                                    horizontalAlignment: Text.AlignLeft
                                    leftPadding: 10
                                }
                            }
                        }
                    }
                }

                // Side controller widget (Telemetry controls)
                Rectangle {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 140
                    Layout.leftMargin: 15
                    Layout.rightMargin: 15
                    color: slateBg
                    border.color: borderSlate
                    border.width: 1
                    radius: 8
                    
                    ColumnLayout {
                        anchors.fill: parent
                        anchors.margins: 12
                        spacing: 8
                        
                        Text {
                            text: getTxt("clipboard_monitor")
                            color: metallicGold
                            font.bold: true
                            font.pixelSize: 12
                        }
                        Text {
                            text: getTxt("clipboard_desc")
                            color: textGray
                            font.pixelSize: 10
                            wrapMode: Text.Wrap
                            Layout.fillWidth: true
                        }
                        
                        RowLayout {
                            Text {
                                text: backend.get_clipboard_monitor_enabled() ? getTxt("clipboard_active") : getTxt("clipboard_inactive")
                                color: backend.get_clipboard_monitor_enabled() ? successGreen : errorRed
                                font.pixelSize: 10
                                font.bold: true
                            }
                            Spacer { Layout.fillWidth: true }
                            Switch {
                                checked: backend.get_clipboard_monitor_enabled()
                                onCheckedChanged: {
                                    backend.set_clipboard_monitor_enabled(checked)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Core App Views Stack Layout
        StackLayout {
            id: mainStack
            Layout.fillWidth: true
            Layout.fillHeight: true

            // TAB 0: Extractor / Code Parser / Intelligent Monitor
            MonitorScreen {
                Layout.fillWidth: true
                Layout.fillHeight: true
            }

            // TAB 1: Directory Packaging Tool
            Rectangle {
                color: "transparent"
                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 20
                    spacing: 12

                    Text {
                        text: "📦 " + getTxt("tab_pack")
                        color: metallicGold
                        font.bold: true
                        font.pixelSize: 18
                    }

                    Rectangle {
                        Layout.fillWidth: true
                        Layout.preferredHeight: 65
                        color: cardSlateBg
                        border.color: borderSlate
                        radius: 8
                        RowLayout {
                            anchors.fill: parent
                            anchors.margins: 12
                            Text { text: getTxt("folder_to_pack"); color: textSilver; font.bold: true }
                            Rectangle {
                                Layout.fillWidth: true
                                height: 34
                                color: slateBg
                                border.color: borderSlate
                                radius: 4
                                Text {
                                    id: folderToPackText
                                    anchors.centerIn: parent
                                    text: getTxt("not_selected")
                                    color: textGray
                                    font.pixelSize: 11
                                }
                            }
                            Button {
                                text: getTxt("browse")
                                onClicked: packFolderDialog.open()
                            }
                        }
                    }

                    RowLayout {
                        Text { text: getTxt("ignore_patterns"); color: textSilver; font.pixelSize: 11 }
                        TextField {
                            id: ignorePatternsInput
                            text: "venv, .git, build, dist"
                            placeholderText: "e.g. venv, .git, build"
                            Layout.fillWidth: true
                        }
                    }

                    ScrollView {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        TextArea {
                            id: packOutputArea
                            placeholderText: backend.appLanguage === "ar" ? "سيظهر كود الحزمة المغلف هنا..." : "Packaged code builder output will show here..."
                            color: textSilver
                            font.family: "Consolas"
                            font.pixelSize: 11
                            background: Rectangle {
                                color: cardSlateBg
                                border.color: borderSlate
                                border.width: 1
                                radius: 8
                            }
                            selectByMouse: true
                            wrapMode: TextEdit.NoWrap
                        }
                    }

                    RowLayout {
                        Button {
                            text: getTxt("btn_pack")
                            enabled: folderToPackText.text !== getTxt("not_selected")
                            onClicked: {
                                backend.pack_directory_v2(folderToPackText.text, ignorePatternsInput.text)
                            }
                        }
                        Button {
                            text: getTxt("btn_copy")
                            enabled: packOutputArea.text !== ""
                            onClicked: {
                                packOutputArea.selectAll()
                                packOutputArea.copy()
                                toastBox.trigger(backend.appLanguage === "ar" ? "عملية الحافظة" : "Clipboard Action", backend.appLanguage === "ar" ? "تم نسخ كود الحزمة بنجاح." : "Package code copied successfully.", "success")
                            }
                        }
                    }
                }
            }

            // TAB 2: Smart Capture & Style Bank
            Rectangle {
                color: "transparent"
                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 20
                    spacing: 12

                    RowLayout {
                        Text {
                            text: getTxt("beautifier_title")
                            color: metallicGold
                            font.bold: true
                            font.pixelSize: 18
                        }
                        Spacer { Layout.fillWidth: true }
                        
                        Text { text: getTxt("theme_selector_lbl"); color: textSilver; font.pixelSize: 11 }
                        ComboBox {
                            id: themeSelector
                            model: ["space", "oasis", "academic", "dark", "light"]
                            implicitWidth: 120
                        }
                    }

                    ScrollView {
                        Layout.fillWidth: true
                        Layout.preferredHeight: 180
                        TextArea {
                            id: captureInputArea
                            placeholderText: getTxt("placeholder_capture")
                            color: textSilver
                            font.family: "Consolas"
                            font.pixelSize: 12
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
                        Button {
                            text: getTxt("btn_capture")
                            onClicked: {
                                backend.smart_capture_content_v2(captureInputArea.text, themeSelector.currentText)
                            }
                        }
                        Button {
                            text: getTxt("btn_paste")
                            onClicked: captureInputArea.paste()
                        }
                    }

                    // Style Bank Custom Snippets List
                    Text { text: backend.appLanguage === "ar" ? "🎨 بنك التصاميم والأنماط (Style Bank):" : "🎨 Dynamic Style Bank Repository:"; color: metallicGold; font.bold: true; font.pixelSize: 12 }
                    
                    ScrollView {
                        Layout.fillWidth: true
                        Layout.preferredHeight: 100
                        ListView {
                            model: stylesModel
                            orientation: ListView.Horizontal
                            spacing: 10
                            delegate: Rectangle {
                                width: 220
                                height: 80
                                color: cardSlateBg
                                border.color: borderSlate
                                radius: 8
                                ColumnLayout {
                                    anchors.fill: parent
                                    anchors.margins: 10
                                    Text { text: model.name; color: textSilver; font.bold: true; font.pixelSize: 11; elide: Text.ElideRight; Layout.fillWidth: true }
                                    RowLayout {
                                        Button {
                                            text: backend.appLanguage === "ar" ? "نسخ 📋" : "Copy 📋"
                                            implicitHeight: 24
                                            onClicked: {
                                                captureInputArea.text = model.css_code
                                                toastBox.trigger("Style Bank", "Loaded custom styling template.", "info")
                                            }
                                        }
                                        Button {
                                            text: backend.appLanguage === "ar" ? "حذف ❌" : "Delete ❌"
                                            implicitHeight: 24
                                            onClicked: backend.delete_style(model.name)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Text { text: getTxt("history_lbl"); color: metallicGold; font.bold: true; font.pixelSize: 12 }

                    ScrollView {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        ListView {
                            id: capturesListView
                            model: capturesModel
                            spacing: 8
                            delegate: Rectangle {
                                width: capturesListView.width - 20
                                height: 50
                                color: cardSlateBg
                                border.color: borderSlate
                                radius: 6
                                RowLayout {
                                    anchors.fill: parent
                                    anchors.leftMargin: 15
                                    anchors.rightMargin: 15
                                    Text { text: "📌 " + model.title; color: textSilver; font.bold: true; Layout.fillWidth: true }
                                    Text { text: "[" + model.type + "]"; color: metallicGold; font.pixelSize: 10 }
                                    Text { text: model.created_at; color: textGray; font.pixelSize: 10 }
                                    Button {
                                        text: getTxt("open_file")
                                        onClicked: Qt.openUrlExternally("file:///" + model.file_path)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // TAB 3: TreeDoc Pro interactive viewer
            TreeDocDashboardScreen {
                Layout.fillWidth: true
                Layout.fillHeight: true
            }

            // TAB 4: Gemini AI Copilot
            Rectangle {
                color: "transparent"
                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 20
                    spacing: 12

                    RowLayout {
                        Text {
                            text: "💬 " + getTxt("tab_gemini")
                            color: metallicGold
                            font.bold: true
                            font.pixelSize: 18
                        }
                        Spacer { Layout.fillWidth: true }
                        Button {
                            text: getTxt("clean_history")
                            onClicked: backend.clear_chats()
                        }
                    }

                    ScrollView {
                        id: chatScrollView
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        ListView {
                            id: chatView
                            model: chatsModel
                            spacing: 10
                            delegate: Rectangle {
                                width: chatView.width - 20
                                height: Math.max(chatText.implicitHeight + 25, 45)
                                color: model.role === "user" ? borderSlate : cardSlateBg
                                border.color: model.role === "user" ? metallicGold : borderSlate
                                radius: 8
                                RowLayout {
                                    anchors.fill: parent
                                    anchors.margins: 12
                                    Text {
                                        text: model.role === "user" ? (backend.appLanguage === "ar" ? "🙋‍♂️ أنت:" : "🙋‍♂️ You:") : (backend.appLanguage === "ar" ? "🤖 مساعد Gemini:" : "🤖 Gemini:")
                                        color: metallicGold
                                        font.bold: true
                                        Layout.preferredWidth: 100
                                    }
                                    Text {
                                        id: chatText
                                        text: model.message
                                        color: textSilver
                                        font.pixelSize: 12
                                        wrapMode: Text.Wrap
                                        Layout.fillWidth: true
                                    }
                                }
                            }
                        }
                    }

                    RowLayout {
                        spacing: 10
                        TextField {
                            id: geminiMsgInput
                            placeholderText: getTxt("gemini_input_placeholder")
                            color: textSilver
                            background: Rectangle {
                                color: cardSlateBg
                                border.color: borderSlate
                                radius: 6
                            }
                            Layout.fillWidth: true
                        }
                        Button {
                            text: getTxt("btn_send")
                            onClicked: {
                                if (geminiMsgInput.text.trim() !== "") {
                                    chatsModel.append({ "role": "user", "message": geminiMsgInput.text })
                                    backend.ask_gemini_async(geminiMsgInput.text)
                                    geminiMsgInput.text = ""
                                }
                            }
                        }
                    }
                }
            }

            // TAB 5: Executor Dashboard Screen
            ExecutorDashboardScreen {
                Layout.fillWidth: true
                Layout.fillHeight: true
            }

            // TAB 6: AIPromptHub Guide
            Rectangle {
                color: "transparent"
                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 20
                    spacing: 12

                    Text {
                        text: backend.appLanguage === "ar" ? "📚 مستودع التوجيهات الفائقة (AIPromptHub)" : "📚 Master Instructions Guide (AIPromptHub)"
                        color: metallicGold
                        font.bold: true
                        font.pixelSize: 18
                    }

                    Text {
                        text: backend.appLanguage === "ar" ? "تعلم كيفية صياغة وتوجيه الأكواد لمساعدات الذكاء الاصطناعي لتطبيق التعديلات والعمليات آلياً." : "Learn how to format build package templates so our engine can automatically direct & deploy code changes."
                        color: textSilver
                        font.pixelSize: 12
                        wrapMode: Text.Wrap
                        Layout.fillWidth: true
                    }

                    ScrollView {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        clip: true

                        ColumnLayout {
                            width: parent.width - 20
                            spacing: 15

                            // Section 1: @builder Directive
                            Rectangle {
                                Layout.fillWidth: true
                                Layout.preferredHeight: 180
                                color: cardSlateBg
                                border.color: borderSlate
                                radius: 8
                                ColumnLayout {
                                    anchors.fill: parent
                                    anchors.margins: 12
                                    spacing: 6
                                    RowLayout {
                                        Text { text: "📝 " + (backend.appLanguage === "ar" ? "توجيه استخراج الأكواد @builder" : "The @builder Directive Template"); color: metallicGold; font.bold: true; font.pixelSize: 13 }
                                        Spacer { Layout.fillWidth: true }
                                        Button {
                                            text: backend.appLanguage === "ar" ? "نسخ القالب 📋" : "Copy Template 📋"
                                            onClicked: {
                                                var demo = "// @builder:file app/src/main/java/MainActivity.kt\npackage com.example\nimport android.os.Bundle\n\n// كود التطبيق يوضع هنا\n// @builder:end"
                                                backend.execute_command_advanced("clipboard " + demo, "", false)
                                                backend.notificationSent("نسخ", "تم نسخ قالب @builder إلى الحافظة.", "success")
                                            }
                                        }
                                    }
                                    Text {
                                        text: backend.appLanguage === "ar" ? "أفضل صيغة لإرشاد المحرك لمسار حفظ الملف تلقائياً:" : "Provide this header layout to let the system auto-extract directly into correct directories:"
                                        color: textGray; font.pixelSize: 11
                                    }
                                    Rectangle {
                                        Layout.fillWidth: true
                                        Layout.preferredHeight: 60
                                        color: slateBg
                                        radius: 4
                                        Text {
                                            anchors.centerIn: parent
                                            text: "// @builder:file path/to/target/file.kt\n...\n// @builder:end"
                                            color: warningOrange
                                            font.family: "Courier"
                                            font.pixelSize: 11
                                        }
                                    }
                                }
                            }

                            // Section 2: @executor commands
                            Rectangle {
                                Layout.fillWidth: true
                                Layout.preferredHeight: 180
                                color: cardSlateBg
                                border.color: borderSlate
                                radius: 8
                                ColumnLayout {
                                    anchors.fill: parent
                                    anchors.margins: 12
                                    spacing: 6
                                    RowLayout {
                                        Text { text: "🖥️ " + (backend.appLanguage === "ar" ? "توجيهات التنفيذ الذكي للأوامر" : "The Command Line Automations"); color: metallicGold; font.bold: true; font.pixelSize: 13 }
                                        Spacer { Layout.fillWidth: true }
                                        Button {
                                            text: backend.appLanguage === "ar" ? "نسخ قائمة الأوامر 📋" : "Copy Command List 📋"
                                            onClicked: {
                                                var cmdList = "scan, report, chart, duplicates, selftest, template, move, rename, delete, copy-safe"
                                                backend.execute_command_advanced("clipboard " + cmdList, "", false)
                                                backend.notificationSent("نسخ", "تم نسخ قائمة الأوامر إلى الحافظة.", "success")
                                            }
                                        }
                                    }
                                    Text {
                                        text: backend.appLanguage === "ar" ? "الأوامر المدعومة للتحكم الآلي بالمشاريع بدون شاشات رسومية:" : "Available terminal automation actions directly supported inside chat loops:"
                                        color: textGray; font.pixelSize: 11
                                    }
                                    Rectangle {
                                        Layout.fillWidth: true
                                        Layout.preferredHeight: 60
                                        color: slateBg
                                        radius: 4
                                        Text {
                                            anchors.centerIn: parent
                                            text: "scan [فحص], report [تقرير], chart [مخطط], duplicates [تكرار], selftest [اختبار]"
                                            color: successGreen
                                            font.bold: true
                                            font.pixelSize: 11
                                        }
                                    }
                                }
                            }

                            // Section 3: ChatGPT/DeepSeek Import
                            Rectangle {
                                Layout.fillWidth: true
                                Layout.preferredHeight: 140
                                color: cardSlateBg
                                border.color: borderSlate
                                radius: 8
                                ColumnLayout {
                                    anchors.fill: parent
                                    anchors.margins: 12
                                    spacing: 6
                                    Text { text: "🤖 " + (backend.appLanguage === "ar" ? "مستخرج كتل المحادثات التلقائي" : "ChatLink Direct Processor"); color: metallicGold; font.bold: true; font.pixelSize: 13 }
                                    Text {
                                        text: backend.appLanguage === "ar" ? "يمكن للمحرك قراءة ردود المساعدات الشهيرة مثل ChatGPT أو DeepSeek واستخلاص كتل الكود المحددة بـ ``` وتوجيهها للمجلدات المناسبة بالذكاء الاصطناعي." : "Our ChatLink system parses Markdown code blocks (```) inside raw text, auto-detecting the proper target folder based on the code's context dynamically."
                                        color: textSilver
                                        font.pixelSize: 11
                                        wrapMode: Text.Wrap
                                        Layout.fillWidth: true
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // TAB 7: Help Center
            Rectangle {
                color: "transparent"
                RowLayout {
                    anchors.fill: parent
                    anchors.margins: 20
                    spacing: 20

                    // Left Column: FAQ & Dictionary
                    ColumnLayout {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        spacing: 12

                        Text {
                            text: "💡 " + (backend.appLanguage === "ar" ? "قاموس المصطلحات والأسئلة الشائعة" : "Knowledge Base & Glossary")
                            color: metallicGold
                            font.bold: true
                            font.pixelSize: 16
                        }

                        ScrollView {
                            Layout.fillWidth: true
                            Layout.fillHeight: true
                            clip: true

                            ColumnLayout {
                                width: parent.width - 15
                                spacing: 10

                                // FAQ 1
                                Rectangle {
                                    Layout.fillWidth: true
                                    Layout.preferredHeight: 75
                                    color: cardSlateBg
                                    border.color: borderSlate
                                    radius: 6
                                    ColumnLayout {
                                        anchors.fill: parent; anchors.margins: 8
                                        Text { text: backend.appLanguage === "ar" ? "س: ما هي ميزة النسخ الاحتياطي التلقائي؟" : "Q: How does auto-backup work?"; color: metallicGold; font.bold: true; font.pixelSize: 11 }
                                        Text { text: backend.appLanguage === "ar" ? "ج: قبل كتابة أو تحديث أي ملف بالكمبيوتر، يحتفظ محركنا بنسخة احتياطية في مجلد Backups لضمان عدم فقدان عملك." : "A: Before replacing or saving any source files, the engine archives a backup copy in the Backups folder."; color: textSilver; font.pixelSize: 10; wrapMode: Text.Wrap; Layout.fillWidth: true }
                                    }
                                }

                                // FAQ 2
                                Rectangle {
                                    Layout.fillWidth: true
                                    Layout.preferredHeight: 75
                                    color: cardSlateBg
                                    border.color: borderSlate
                                    radius: 6
                                    ColumnLayout {
                                        anchors.fill: parent; anchors.margins: 8
                                        Text { text: backend.appLanguage === "ar" ? "س: كيف أقوم بربط مشاريع جديدة بالمنصة؟" : "Q: How do I map new projects?"; color: metallicGold; font.bold: true; font.pixelSize: 11 }
                                        Text { text: backend.appLanguage === "ar" ? "ج: من تبويبة الإعدادات، يمكنك تحديد مسارات العمل النشطة وسيتم إضافتها تلقائياً لقائمة المشاريع الموثوقة." : "A: You can set paths in the configuration dashboard to immediately track and manage custom workspaces."; color: textSilver; font.pixelSize: 10; wrapMode: Text.Wrap; Layout.fillWidth: true }
                                    }
                                }

                                // FAQ 3
                                Rectangle {
                                    Layout.fillWidth: true
                                    Layout.preferredHeight: 75
                                    color: cardSlateBg
                                    border.color: borderSlate
                                    radius: 6
                                    ColumnLayout {
                                        anchors.fill: parent; anchors.margins: 8
                                        Text { text: backend.appLanguage === "ar" ? "س: ما هو نظام TreeDoc Pro؟" : "Q: What is TreeDoc Pro?"; color: metallicGold; font.bold: true; font.pixelSize: 11 }
                                        Text { text: backend.appLanguage === "ar" ? "ج: نظام يولد خريطة شجرية تفاعلية للمجلدات مع فحص الأمان لمشاركة هيكل الكود مع الذكاء الاصطناعي." : "A: An interactive visual representation of folders designed to provide full-context code schemas to LLMs."; color: textSilver; font.pixelSize: 10; wrapMode: Text.Wrap; Layout.fillWidth: true }
                                    }
                                }
                            }
                        }
                    }

                    // Right Column: Scenario Simulator
                    Rectangle {
                        Layout.preferredWidth: 320
                        Layout.fillHeight: true
                        color: cardSlateBg
                        border.color: borderSlate
                        radius: 8
                        ColumnLayout {
                            anchors.fill: parent
                            anchors.margins: 15
                            spacing: 10

                            Text {
                                text: "🎮 " + (backend.appLanguage === "ar" ? "محاكي السيناريوهات التفاعلي" : "Scenario Simulator")
                                color: metallicGold
                                font.bold: true
                                font.pixelSize: 14
                            }

                            Text {
                                text: backend.appLanguage === "ar" ? "اختر سيناريو لتشاهد وتتعلم كيف يتعامل المحرك معه:" : "Pick an operational scenario to view the underlying engine flow:"
                                color: textGray
                                font.pixelSize: 10
                                wrapMode: Text.Wrap
                                Layout.fillWidth: true
                            }

                            ComboBox {
                                id: scenarioCombo
                                Layout.fillWidth: true
                                model: backend.appLanguage === "ar" ? 
                                    ["توليد حزمة بناء", "استخراج كود محاكي", "فحص الخصوصية"] : 
                                    ["Create Build Pack", "Auto-Extract Code", "Privacy Inspection"]
                            }

                            Button {
                                text: backend.appLanguage === "ar" ? "🎮 تشغيل المحاكاة" : "🎮 Simulate Flow"
                                Layout.fillWidth: true
                                onClicked: {
                                    if (scenarioCombo.currentIndex === 0) {
                                        simulatorText.text = backend.appLanguage === "ar" ? 
                                            "1. قراءة مجلد العمل النشط...\n2. استبعاد المجلدات المتجاهلة (ignore dirs)...\n3. قراءة محتوى الملفات وترميز UTF-8...\n4. دمج الملفات تحت توجيهات @builder...\n5. وضع الحزمة النهائية في حافظة النظام لتكون جاهزة للمساعد!" :
                                            "1. Reading active workspace directory...\n2. Applying ignore patterns...\n3. Compiling text contents...\n4. Injecting @builder delimiters...\n5. Pushing finalized package code directly to clipboard!"
                                    } else if (scenarioCombo.currentIndex === 1) {
                                        simulatorText.text = backend.appLanguage === "ar" ?
                                            "1. استلام حزمة الكود المنسوخة...\n2. فحص توجيهات @builder:file...\n3. إنشاء نسخة احتياطية (Backup) للملفات السابقة...\n4. كتابة الملفات الجديدة في المسار المحدد...\n5. تسجيل نجاح العملية في قاعدة SQLite." :
                                            "1. Receiving copied package buffer...\n2. Matching @builder:file patterns...\n3. Making safety backups of affected files...\n4. Writing source codes to paths...\n5. Logging transaction to SQLite."
                                    } else {
                                        simulatorText.text = backend.appLanguage === "ar" ?
                                            "1. إجراء فحص لكافة ملفات الكود...\n2. فحص نمطي (Regex) للأنماط الحساسة (Keys)...\n3. تشفير القيم المكتشفة بالكامل...\n4. توليد مستند تقرير HTML أنيق للمشروع..." :
                                            "1. Scanning workspace source files...\n2. Running Regex for keys and tokens...\n3. Masking values dynamically...\n4. Compiling premium HTML audit report..."
                                    }
                                }
                            }

                            Spacer { Layout.fillHeight: true }

                            Rectangle {
                                Layout.fillWidth: true
                                Layout.preferredHeight: 180
                                color: slateBg
                                border.color: borderSlate
                                radius: 6
                                ScrollView {
                                    anchors.fill: parent
                                    anchors.margins: 8
                                    Text {
                                        id: simulatorText
                                        text: backend.appLanguage === "ar" ? "اضغط على زر تشغيل المحاكاة لعرض الخطوات..." : "Press Simulate Flow to view results..."
                                        color: "#38BDF8"
                                        font.family: "Courier"
                                        font.pixelSize: 11
                                        wrapMode: Text.Wrap
                                        width: 280
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // TAB 8: Local File Browser
            Rectangle {
                id: localFileBrowserTab
                color: "transparent"
                RowLayout {
                    anchors.fill: parent
                    anchors.margins: 20
                    spacing: 15

                    // Left Pane: File explorer
                    ColumnLayout {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        spacing: 10

                        RowLayout {
                            spacing: 8
                            Text {
                                text: "📂 " + (backend.appLanguage === "ar" ? "متصفح المجلد الحالي:" : "Workspace Browser:")
                                color: metallicGold; font.bold: true; font.pixelSize: 14
                            }
                            TextField {
                                id: browserPathInput
                                text: backend.baseDir
                                color: textSilver
                                font.pixelSize: 11
                                background: Rectangle { color: cardSlateBg; border.color: borderSlate; radius: 4 }
                                Layout.fillWidth: true
                                onAccepted: {
                                    localFileBrowserTab.loadLocalFiles(text)
                                }
                            }
                            Button {
                                text: "⬆️"
                                onClicked: {
                                    // simple go up directory logic
                                    var current = browserPathInput.text.replace(/\\/g, "/")
                                    var parts = current.split("/")
                                    if (parts.length > 1) {
                                        parts.pop()
                                        var upPath = parts.join("/")
                                        if (upPath === "" || upPath === "file:") upPath = "/"
                                        browserPathInput.text = upPath
                                        localFileBrowserTab.loadLocalFiles(upPath)
                                    }
                                }
                            }
                            Button {
                                text: "🔄"
                                onClicked: localFileBrowserTab.loadLocalFiles(browserPathInput.text)
                            }
                        }

                        ScrollView {
                            Layout.fillWidth: true
                            Layout.fillHeight: true
                            clip: true
                            ListView {
                                id: fileBrowserListView
                                model: browserModel
                                spacing: 5
                                delegate: Rectangle {
                                    width: fileBrowserListView.width - 20
                                    height: 45
                                    color: cardSlateBg
                                    border.color: borderSlate
                                    radius: 6
                                    RowLayout {
                                        anchors.fill: parent; anchors.leftMargin: 10; anchors.rightMargin: 10
                                        Text {
                                            text: model.is_dir ? "📁" : "📄"
                                            font.pixelSize: 16
                                        }
                                        ColumnLayout {
                                            Layout.fillWidth: true
                                            spacing: 2
                                            Text { text: model.name; color: textSilver; font.bold: model.is_dir; font.pixelSize: 11 }
                                            Text { text: model.is_dir ? (backend.appLanguage === "ar" ? "مجلد" : "Directory") : (model.size + " bytes | " + model.modified); color: textGray; font.pixelSize: 9 }
                                        }
                                        Button {
                                            text: model.is_dir ? (backend.appLanguage === "ar" ? "دخول 🚪" : "Enter") : (backend.appLanguage === "ar" ? "تعديل ✍️" : "Edit")
                                            onClicked: {
                                                if (model.is_dir) {
                                                    browserPathInput.text = model.path
                                                    localFileBrowserTab.loadLocalFiles(model.path)
                                                } else {
                                                    editFilePathLabel.text = model.path
                                                    editorTextArea.text = backend.read_local_file(model.path)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Right Pane: Editor & Executor
                    Rectangle {
                        Layout.preferredWidth: 350
                        Layout.fillHeight: true
                        color: cardSlateBg
                        border.color: borderSlate
                        radius: 8
                        ColumnLayout {
                            anchors.fill: parent
                            anchors.margins: 12
                            spacing: 8

                            RowLayout {
                                Text { text: "✍️ " + (backend.appLanguage === "ar" ? "محرر الكود النشط" : "Live Code Editor"); color: metallicGold; font.bold: true; font.pixelSize: 13 }
                                Spacer { Layout.fillWidth: true }
                                Button {
                                    text: backend.appLanguage === "ar" ? "تشغيل ▶️" : "Run ▶️"
                                    onClicked: {
                                        if (editFilePathLabel.text !== "") {
                                            var ok = backend.run_local_file(editFilePathLabel.text)
                                            if (ok) {
                                                backend.notificationSent("تشغيل ملف", "تم إطلاق الملف بنجاح بالنظام.", "success")
                                            } else {
                                                backend.notificationSent("خطأ تشغيل", "فشل تشغيل الملف أو النظام غير مدعوم.", "error")
                                            }
                                        }
                                    }
                                }
                            }

                            Text {
                                id: editFilePathLabel
                                text: ""
                                color: textGray
                                font.pixelSize: 9
                                elide: Text.ElideMiddle
                                Layout.fillWidth: true
                            }

                            Rectangle {
                                Layout.fillWidth: true
                                Layout.fillHeight: true
                                color: slateBg
                                border.color: borderSlate
                                radius: 4
                                ScrollView {
                                    anchors.fill: parent
                                    TextArea {
                                        id: editorTextArea
                                        placeholderText: backend.appLanguage === "ar" ? "اختر ملفاً من اليسار للبدء في تحريره وحفظه..." : "Select a file to edit here..."
                                        color: textSilver
                                        font.family: "Courier"
                                        font.pixelSize: 11
                                        wrapMode: Text.Wrap
                                    }
                                }
                            }

                            RowLayout {
                                spacing: 10
                                Layout.fillWidth: true
                                Button {
                                    text: backend.appLanguage === "ar" ? "💾 حفظ التعديلات" : "💾 Save Changes"
                                    Layout.fillWidth: true
                                    onClicked: {
                                        if (editFilePathLabel.text !== "") {
                                            var ok = backend.write_local_file(editFilePathLabel.text, editorTextArea.text)
                                            if (ok) {
                                                backend.notificationSent("حفظ ملف", "تم تحديث الملف بنجاح وعمل نسخة احتياطية.", "success")
                                                refreshDatabase()
                                            } else {
                                                backend.notificationSent("خطأ في الحفظ", "فشل كتابة وتحديث الملف.", "error")
                                            }
                                        }
                                    }
                                }
                                Button {
                                    text: backend.appLanguage === "ar" ? "إلغاء ❌" : "Cancel ❌"
                                    onClicked: {
                                        editorTextArea.text = ""
                                        editFilePathLabel.text = ""
                                    }
                                }
                            }
                        }
                    }
                }

                // Helper JavaScript to parse JSON and load files into ListModel
                function loadLocalFiles(path) {
                    try {
                        var jsonStr = backend.list_local_directory(path)
                        var list = JSON.parse(jsonStr)
                        browserModel.clear()
                        for (var i = 0; i < list.length; i++) {
                            browserModel.append({
                                "name": list[i].name,
                                "path": list[i].path,
                                "is_dir": list[i].is_dir,
                                "size": list[i].size,
                                "modified": list[i].modified
                            })
                        }
                    } catch (e) { console.log("File browser load error:", e) }
                }

                Component.onCompleted: {
                    localFileBrowserTab.loadLocalFiles(backend.baseDir)
                }
            }

            // TAB 9: Visual Storyteller Journal
            Rectangle {
                color: "transparent"
                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 20
                    spacing: 12

                    RowLayout {
                        Text {
                            text: "📖 " + (backend.appLanguage === "ar" ? "يوميات الأحداث وسيناريوهات الأتمتة" : "Visual Storyteller Timeline")
                            color: metallicGold; font.bold: true; font.pixelSize: 18
                        }
                        Spacer { Layout.fillWidth: true }
                        Button {
                            text: backend.appLanguage === "ar" ? "تحديث اليوميات 🔄" : "Refresh Stories 🔄"
                            onClicked: {
                                refreshDatabase()
                            }
                        }
                    }

                    Text {
                        text: backend.appLanguage === "ar" ? "يقوم هذا النظام بتجميع السجلات وربطها بنقاط زمنية محددة لتلخيص ما تم إنجازه بشكل قصصي متكامل." : "The engine aggregates micro-actions and database logs into hourly descriptive visual story cards."
                        color: textSilver
                        font.pixelSize: 11
                        wrapMode: Text.Wrap
                        Layout.fillWidth: true
                    }

                    ScrollView {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        clip: true
                        ListView {
                            id: storytellerListView
                            model: storytellerModel
                            spacing: 12
                            delegate: Rectangle {
                                width: storytellerListView.width - 20
                                height: detailsArea.visible ? 200 : 90
                                color: cardSlateBg
                                border.color: borderSlate
                                radius: 8
                                ColumnLayout {
                                    anchors.fill: parent
                                    anchors.margins: 12
                                    spacing: 6

                                    RowLayout {
                                        Text { text: model.icon; font.pixelSize: 22 }
                                        ColumnLayout {
                                            spacing: 2
                                            Text { text: model.title; color: metallicGold; font.bold: true; font.pixelSize: 13 }
                                            Text { text: model.time; color: textGray; font.pixelSize: 10 }
                                        }
                                        Spacer { Layout.fillWidth: true }
                                        RowLayout {
                                            spacing: 8
                                            Rectangle { color: "#113B24"; radius: 4; width: 45; height: 20; Text { text: "✓ " + model.success_count; color: successGreen; font.bold: true; font.pixelSize: 10; anchors.centerIn: parent } }
                                            Rectangle { color: "#3B111A"; radius: 4; width: 45; height: 20; Text { text: "✗ " + model.error_count; color: errorRed; font.bold: true; font.pixelSize: 10; anchors.centerIn: parent } }
                                        }
                                        Button {
                                            text: detailsArea.visible ? "▲" : "▼"
                                            onClicked: detailsArea.visible = !detailsArea.visible
                                        }
                                    }

                                    Text {
                                        text: model.desc
                                        color: textSilver
                                        font.pixelSize: 11
                                        wrapMode: Text.Wrap
                                        Layout.fillWidth: true
                                    }

                                    Rectangle {
                                        id: detailsArea
                                        visible: false
                                        Layout.fillWidth: true
                                        Layout.fillHeight: true
                                        color: slateBg
                                        radius: 6
                                        ScrollView {
                                            anchors.fill: parent
                                            anchors.margins: 8
                                            Text {
                                                text: model.details
                                                color: textSilver
                                                font.family: "Courier"
                                                font.pixelSize: 10
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // TAB 10: Status & Quality Dashboard
            Rectangle {
                color: "transparent"
                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 20
                    spacing: 15

                    Text {
                        text: "🛡️ " + (backend.appLanguage === "ar" ? "لوحة جودة الخدمة والصحة العامة للفحص الذاتي" : "System Diagnostics & Self-Test Panel")
                        color: metallicGold; font.bold: true; font.pixelSize: 18
                    }

                    RowLayout {
                        spacing: 15
                        Layout.fillWidth: true
                        Layout.preferredHeight: 120

                        // Widget 1: Health Status
                        Rectangle {
                            Layout.fillWidth: true
                            Layout.fillHeight: true
                            color: cardSlateBg
                            border.color: borderSlate
                            radius: 8
                            ColumnLayout {
                                anchors.fill: parent; anchors.margins: 12; spacing: 5
                                Text { text: "🩺 " + (backend.appLanguage === "ar" ? "حالة النظام" : "General Health"); color: metallicGold; font.bold: true; font.pixelSize: 12 }
                                RowLayout {
                                    Rectangle { width: 10; height: 10; radius: 5; color: successGreen }
                                    Text { text: backend.appLanguage === "ar" ? "قاعدة SQLite: متصلة ونشطة" : "SQLite DB: Connected"; color: textSilver; font.pixelSize: 11 }
                                }
                                RowLayout {
                                    Rectangle { width: 10; height: 10; radius: 5; color: backend.get_gemini_api_key() !== "" ? successGreen : warningOrange }
                                    Text { text: backend.get_gemini_api_key() !== "" ? "Gemini AI: ONLINE 🟢" : "Gemini AI: KEY MISSING 🟡"; color: textSilver; font.pixelSize: 11 }
                                }
                            }
                        }

                        // Widget 2: Overlay Bubble Control
                        Rectangle {
                            Layout.fillWidth: true
                            Layout.fillHeight: true
                            color: cardSlateBg
                            border.color: borderSlate
                            radius: 8
                            ColumnLayout {
                                anchors.fill: parent; anchors.margins: 12; spacing: 5
                                Text { text: "🎯 " + (backend.appLanguage === "ar" ? "الفقاعة العائمة" : "Floating Overlay"); color: metallicGold; font.bold: true; font.pixelSize: 12 }
                                Text { text: backend.appLanguage === "ar" ? "تسمح بالالتقاط الفوري أعلى النوافذ." : "Enables quick overlay actions."; color: textGray; font.pixelSize: 10 }
                                RowLayout {
                                    Text { text: backend.bubbleEnabled ? "نشط 🟢" : "معطل 🔴"; color: backend.bubbleEnabled ? successGreen : errorRed; font.bold: true; font.pixelSize: 11 }
                                    Spacer { Layout.fillWidth: true }
                                    Switch {
                                        checked: backend.bubbleEnabled
                                        onCheckedChanged: {
                                            backend.bubbleEnabled = checked
                                            bubbleOverlay.visible = checked
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Self-Test Terminal Section
                    RowLayout {
                        Text { text: "🧪 " + (backend.appLanguage === "ar" ? "أداة الفحص الذاتي التفاعلية:" : "Self-Diagnostic Runner:"); color: metallicGold; font.bold: true; font.pixelSize: 13 }
                        Spacer { Layout.fillWidth: true }
                        Button {
                            text: backend.appLanguage === "ar" ? "تشغيل الفحص الفوري ⚡" : "Run Self-Test ⚡"
                            onClicked: {
                                selfTestResultArea.text = backend.execute_command_advanced("selftest", "", false)
                                backend.notificationSent("فحص ذاتي", "اكتمل فحص جودة الخدمة بنجاح.", "success")
                            }
                        }
                    }

                    Rectangle {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        color: slateBg
                        border.color: borderSlate
                        radius: 8
                        ScrollView {
                            anchors.fill: parent
                            anchors.margins: 12
                            TextArea {
                                id: selfTestResultArea
                                readOnly: true
                                placeholderText: backend.appLanguage === "ar" ? "اضغط على زر تشغيل الفحص لعرض تقرير صحة ومكونات النظام..." : "Diagnostics output will appear here..."
                                color: "#38BDF8"
                                font.family: "Courier"
                                font.pixelSize: 11
                                wrapMode: Text.Wrap
                            }
                        }
                    }
                }
            }

            // TAB 11: Interactive Admin Dashboard & Custom Canvas Charts
            Rectangle {
                color: "transparent"
                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 20
                    spacing: 12

                    Text {
                        text: getTxt("settings_title")
                        color: metallicGold
                        font.bold: true
                        font.pixelSize: 18
                    }

                    // Secure Gemini credentials panel
                    Rectangle {
                        Layout.fillWidth: true
                        Layout.preferredHeight: 65
                        color: cardSlateBg
                        border.color: borderSlate
                        radius: 8
                        RowLayout {
                            anchors.fill: parent
                            anchors.margins: 12
                            Text { text: getTxt("api_key_lbl"); color: textSilver; font.bold: true }
                            TextField {
                                id: geminiApiKeyInput
                                passwordCharacter: "*"
                                echoMode: TextInput.Password
                                placeholderText: "Enter AI Studio key..."
                                color: textSilver
                                background: Rectangle {
                                    color: slateBg
                                    border.color: borderSlate
                                    radius: 4
                                }
                                Layout.fillWidth: true
                            }
                            Button {
                                text: getTxt("btn_save_key")
                                onClicked: {
                                    backend.set_gemini_api_key(geminiApiKeyInput.text)
                                }
                            }
                        }
                    }

                    // Dynamic charts mapping telemetry inside SQLite
                    RowLayout {
                        spacing: 20
                        Layout.fillWidth: true
                        Layout.preferredHeight: 180

                        // Custom interactive Canvas chart
                        Rectangle {
                            Layout.fillWidth: true
                            Layout.fillHeight: true
                            color: cardSlateBg
                            border.color: borderSlate
                            radius: 8
                            
                            ColumnLayout {
                                anchors.fill: parent
                                anchors.margins: 12
                                Text { text: backend.appLanguage === "ar" ? "📊 مؤشرات وإحصائيات المنصة (SQLite Telemetry):" : "📊 SQLite Database Diagnostics:"; color: metallicGold; font.bold: true; font.pixelSize: 12 }
                                
                                Canvas {
                                    id: canvasChart
                                    Layout.fillWidth: true
                                    Layout.fillHeight: true
                                    onPaint: {
                                        var ctx = getContext("2d");
                                        ctx.clearRect(0, 0, width, height);

                                        // Telemetry stats
                                        var countLogs = logsModel.count;
                                        var countCaptures = capturesModel.count;
                                        var countStyles = stylesModel.count;

                                        // Simple normalized bar graphs
                                        var barWidth = 40;
                                        var maxVal = Math.max(countLogs, countCaptures, countStyles, 5);

                                        // Label Logs
                                        ctx.fillStyle = metallicGold;
                                        var barH1 = (countLogs / maxVal) * (height - 40);
                                        ctx.fillRect(40, height - barH1 - 20, barWidth, barH1);
                                        ctx.fillStyle = textSilver;
                                        ctx.fillText(backend.appLanguage === "ar" ? "السجلات" : "Logs", 40, height - 5);

                                        // Label Captures
                                        ctx.fillStyle = successGreen;
                                        var barH2 = (countCaptures / maxVal) * (height - 40);
                                        ctx.fillRect(120, height - barH2 - 20, barWidth, barH2);
                                        ctx.fillStyle = textSilver;
                                        ctx.fillText(backend.appLanguage === "ar" ? "الالتقاطات" : "Captures", 120, height - 5);

                                        // Label Styles
                                        ctx.fillStyle = warningOrange;
                                        var barH3 = (countStyles / maxVal) * (height - 40);
                                        ctx.fillRect(200, height - barH3 - 20, barWidth, barH3);
                                        ctx.fillStyle = textSilver;
                                        ctx.fillText(backend.appLanguage === "ar" ? "التصاميم" : "Styles", 200, height - 5);
                                    }
                                }
                            }
                        }

                        // System Bubble Configuration Widget
                        Rectangle {
                            Layout.preferredWidth: 320
                            Layout.fillHeight: true
                            color: cardSlateBg
                            border.color: borderSlate
                            radius: 8
                            ColumnLayout {
                                anchors.fill: parent
                                anchors.margins: 12
                                spacing: 10
                                Text { text: backend.appLanguage === "ar" ? "🎯 الفقاعة الذهبية الذكية (Overlay):" : "🎯 Floating Golden Bubble Window:"; color: metallicGold; font.bold: true; font.pixelSize: 12 }
                                Text { text: backend.appLanguage === "ar" ? "نافذة تطفو أعلى التطبيقات للوصول الفوري واستخراج الأكواد مباشرة من الشاشة." : "A persistent mini-widget floating above standard Windows applications for quick access."; color: textGray; font.pixelSize: 10; wrapMode: Text.Wrap; Layout.fillWidth: true }
                                RowLayout {
                                    Text { text: backend.bubbleEnabled ? "نشط 🟢" : "معطل 🔴"; color: backend.bubbleEnabled ? successGreen : errorRed; font.bold: true; font.pixelSize: 11 }
                                    Spacer { Layout.fillWidth: true }
                                    Switch {
                                        checked: backend.bubbleEnabled
                                        onCheckedChanged: {
                                            backend.bubbleEnabled = checked
                                            bubbleOverlay.visible = checked
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Event Log listing
                    RowLayout {
                        Text { text: getTxt("sqlite_log_lbl"); color: metallicGold; font.bold: true }
                        Spacer { Layout.fillWidth: true }
                        Button {
                            text: getTxt("btn_clear")
                            onClicked: backend.clear_logs()
                        }
                    }

                    ScrollView {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        ListView {
                            id: logsListView
                            model: logsModel
                            spacing: 6
                            delegate: Rectangle {
                                width: logsListView.width - 20
                                height: 40
                                color: model.type === "error" ? "#3B111A" : (model.type === "success" ? "#113B24" : cardSlateBg)
                                border.color: borderSlate
                                radius: 6
                                RowLayout {
                                    anchors.fill: parent
                                    anchors.leftMargin: 12
                                    anchors.rightMargin: 12
                                    Text { text: model.message; color: textSilver; font.pixelSize: 11; Layout.fillWidth: true }
                                    Text { text: model.created_at; color: textGray; font.pixelSize: 9 }
                                }
                            }
                        }
                    }
                }
            }

        // TAB 12: Link & Chat Automator Screen
        LinkAutomatorScreen {
            Layout.fillWidth: true
            Layout.fillHeight: true
        }

        // TAB 13: Advanced Projects Screen
        ProjectsScreen {
            Layout.fillWidth: true
            Layout.fillHeight: true
        }

        // TAB 14: Source Export Screen
        SourceExportScreen {
            Layout.fillWidth: true
            Layout.fillHeight: true
        }
    }
}

    // Bottom Status Bar
    Rectangle {
        id: statusBar
        width: parent.width
        height: 40
        anchors.bottom: parent.bottom
        color: cardSlateBg
        border.color: borderSlate
        border.width: 1

        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 20
            anchors.rightMargin: 20

            RowLayout {
                spacing: 8
                Rectangle {
                    width: 8
                    height: 8
                    radius: 4
                    color: successGreen
                }
                Text {
                    id: statusLabelText
                    text: getTxt("status_connected")
                    color: textSilver
                    font.pixelSize: 11
                }
            }

            Spacer { Layout.fillWidth: true }

            Text {
                text: "v2.0 Ultimate Pro | " + (backend.appLanguage === "ar" ? "المنصة الذهبية" : "Golden Platform")
                color: textGray
                font.pixelSize: 10
            }
        }
    }

    // Dialog folders
    FolderDialog {
        id: folderDialog
        title: "اختر مجلد العمل النشط"
        onAccepted: {
            backend.baseDir = selectedFolder.toString()
        }
    }

    FolderDialog {
        id: packFolderDialog
        title: "اختر المجلد المراد تجميعه كحزمة"
        onAccepted: {
            folderToPackText.text = backend.clean_path_url(selectedFolder.toString())
        }
    }

    // Connection hooks back to Python backend signals
    Connections {
        target: backend

        function onLogAdded(type, msg) {
            statusLabelText.text = msg
            if (type === "success") {
                statusLabelText.color = successGreen
            } else if (type === "error") {
                statusLabelText.color = errorRed
            } else {
                statusLabelText.color = textSilver
            }
            refreshDatabase()
        }

        function onProcessingFinished(success, message, details) {
            statusLabelText.text = message
            statusLabelText.color = success ? successGreen : errorRed
            refreshDatabase()
            
            resultDialogText.text = details ? (message + "\n\n" + details) : message
            resultDialog.open()
        }

        function onPackCreated(text, fileCount) {
            packOutputArea.text = text
            statusLabelText.text = (backend.appLanguage === "ar" ? "✅ تم تجميع " : "✅ Packaged ") + fileCount + (backend.appLanguage === "ar" ? " ملفاً بنجاح!" : " files successfully!")
            statusLabelText.color = successGreen
            refreshDatabase()
        }

        function onCaptureResult(status, fileName, path) {
            refreshDatabase()
        }

        function onClipboardBuilderDetected(text) {
            clipboardBannerText.text = backend.appLanguage === "ar" ? "📋 تم كشف حزمة بناء برمجية صالحة في الحافظة! انقر للمعالجة." : "📋 Valid builder package detected in clipboard! Click to process."
            clipboardBannerRect.tagText = text
        }

        function onGeminiResponse(success, reply) {
            chatsModel.append({ "role": "gemini", "message": reply })
            refreshDatabase()
        }

        function onTreeDocCreated(format, path) {
            if (format !== "txt" && format !== "json") {
                Qt.openUrlExternally("file:///" + path)
            }
            refreshDatabase()
        }

        function onDbUpdated() {
            refreshDatabase()
        }

        function onNotificationSent(title, msg, type) {
            toastBox.trigger(title, msg, type)
        }
    }

    // Modal popup results dialog
    Dialog {
        id: resultDialog
        title: "تقرير المعالجة والتنفيذ"
        standardButtons: Dialog.Ok
        anchors.centerIn: parent
        width: 550
        height: 380
        modal: true
        
        background: Rectangle {
            color: cardSlateBg
            border.color: borderSlate
            radius: 8
        }

        header: Rectangle {
            width: parent.width
            height: 45
            color: borderSlate
            radius: 8
            Text {
                anchors.centerIn: parent
                text: backend.appLanguage === "ar" ? "📊 تقرير المعالجة والتنفيذ الفني" : "📊 Build Execution Report"
                color: metallicGold
                font.bold: true
                font.pixelSize: 14
            }
        }

        contentItem: ScrollView {
            anchors.fill: parent
            anchors.margins: 15
            TextArea {
                id: resultDialogText
                text: ""
                color: textSilver
                font.pixelSize: 11
                readOnly: true
                wrapMode: TextEdit.Wrap
                background: null
            }
        }
    }

    StatusDashboardDialog {
        id: statusDashboardDialog
    }

    PermissionsDashboardDialog {
        id: permissionsDashboardDialog
    }

    FileOpenerDialog {
        id: fileOpenerDialog
    }

    FolderOpenerDialog {
        id: folderOpenerDialog
    }

    ShareSelectionDialog {
        id: shareSelectionDialog
    }

    Connections {
        target: backend
        function onFileOpenRequested(filePath) {
            fileOpenerDialog.openFile(filePath)
        }
        function onFolderOpenRequested(folderPath) {
            folderOpenerDialog.openFolder(folderPath)
        }
        function onSharedTextRequested(text) {
            shareSelectionDialog.loadSharedText(text)
        }
    }

    function openFileInEditor(filePath) {
        if (!filePath) return;
        mainStack.currentIndex = 8;
        editFilePathLabel.text = filePath;
        editorTextArea.text = backend.read_local_file(filePath);
        
        // Find parent directory
        var cleanPath = filePath.replace(/\\/g, "/");
        var lastSlash = cleanPath.lastIndexOf("/");
        if (lastSlash !== -1) {
            var dirPath = cleanPath.substring(0, lastSlash);
            browserPathInput.text = dirPath;
            localFileBrowserTab.loadLocalFiles(dirPath);
        } else {
            browserPathInput.text = backend.baseDir;
            localFileBrowserTab.loadLocalFiles(backend.baseDir);
        }
    }
}
