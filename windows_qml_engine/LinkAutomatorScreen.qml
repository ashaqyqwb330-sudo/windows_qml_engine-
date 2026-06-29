import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

Rectangle {
    id: root
    color: "transparent"

    // Localization dictionary
    function getTxt(key) {
        var translations = {
            "title": { "ar": "🔗 معالج ومؤتمت الروابط الذكي (LinkAutomator)", "en": "🔗 Intelligent Link Automator (LinkAutomator)" },
            "desc": { "ar": "قم بجلب كتل الأكواد والنصوص تلقائياً من روابط ChatGPT و DeepSeek و Pastebin و Gist وحفظها بلمح البصر في المجلدات المناسبة للعمل.", "en": "Automatically fetch and parse code blocks or text from ChatGPT, DeepSeek, Pastebin, and Gist links, saving them instantly in targeted workspace folders." },
            "url_label": { "ar": "رابط المحادثة أو الملف المصدري للتنزيل:", "en": "Chat Link or Source Code URL to Fetch:" },
            "url_placeholder": { "ar": "أدخل أو الصق الرابط هنا (مثال: https://chat.deepseek.com/share/...)", "en": "Enter or paste URL here (e.g., https://chat.deepseek.com/share/...)" },
            "paste_btn": { "ar": "لصق من الحافظة 📋", "en": "Paste from Clipboard 📋" },
            "download_btn": { "ar": "تحميل واستخراج وتوجيه المحتوى ⚡", "en": "Download, Extract & Route ⚡" },
            "project_lbl": { "ar": "المشروع المستهدف لحفظ الملفات:", "en": "Target Project for Saved Files:" },
            "mode_lbl": { "ar": "خيارات الاستخراج والتصفية الذكية:", "en": "Smart Extraction & Filtering Mode:" },
            "status_idle": { "ar": "جاهز ومستعد لجلب البيانات ومعالجتها.", "en": "Ready to fetch and process links." },
            "results_title": { "ar": "📊 نتائج الاستخراج والملخص الفني", "en": "📊 Extraction Results & Summary" },
            "stats_lbl": { "ar": "الإحصائيات السريعة ومؤشر الحالة:", "en": "Quick Stats & Status Indicator:" },
            "code_count_lbl": { "ar": "الأكواد المستخرجة:", "en": "Extracted Code Blocks:" },
            "text_count_lbl": { "ar": "الكلمات المكتشفة:", "en": "Extracted Words:" },
            "saved_files_lbl": { "ar": "سجل وتفاصيل الملفات المحفوظة والمؤمنة:", "en": "Summary of Saved and Secured Files:" }
        };
        var isAr = (backend.appLanguage === "ar");
        return translations[key] ? (isAr ? translations[key]["ar"] : translations[key]["en"]) : "";
    }

    // State properties for live UI feedback
    property int statusProgress: 0
    property string statusText: getTxt("status_idle")
    property int codeCount: 0
    property int textCount: 0
    property string savedSummary: ""
    property bool isProcessing: false

    // Sync with backend signals
    Connections {
        target: backend
        
        function onLinkProgress(pct, msg) {
            root.statusProgress = pct;
            root.statusText = msg;
            root.isProcessing = true;
        }
        
        function onLinkProcessingFinished(success, msg, codes, texts, summary) {
            root.isProcessing = false;
            if (success) {
                root.statusProgress = 100;
                root.statusText = msg;
                root.codeCount = codes;
                root.textCount = texts;
                root.savedSummary = summary;
                // Refresh local database tables to update UI
                if (typeof refreshDatabase === "function") {
                    refreshDatabase();
                }
            } else {
                root.statusProgress = 0;
                root.statusText = "❌ " + msg;
                root.savedSummary = msg;
            }
        }
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 20
        spacing: 15

        // Header Section
        RowLayout {
            Layout.fillWidth: true
            spacing: 10
            Text {
                text: getTxt("title")
                color: metallicGold
                font.bold: true
                font.pixelSize: 18
            }
            Spacer { Layout.fillWidth: true }
        }

        Text {
            text: getTxt("desc")
            color: textSilver
            font.pixelSize: 11
            wrapMode: Text.Wrap
            Layout.fillWidth: true
            Layout.bottomMargin: 5
        }

        // Main Workspace Split
        RowLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            spacing: 15

            // Left Card: Downloader and Controls Form
            Rectangle {
                Layout.fillWidth: true
                Layout.fillHeight: true
                Layout.preferredWidth: 450
                color: cardSlateBg
                border.color: borderSlate
                radius: 8

                ScrollView {
                    anchors.fill: parent
                    clip: true
                    
                    ColumnLayout {
                        width: parent.width - 24
                        anchors.margins: 12
                        spacing: 15

                        // Target Project
                        ColumnLayout {
                            Layout.fillWidth: true
                            spacing: 4
                            Text {
                                text: root.getTxt("project_lbl")
                                color: metallicGold
                                font.bold: true
                                font.pixelSize: 11
                            }
                            ComboBox {
                                id: projectCombo
                                model: projectsModel
                                textRole: "name"
                                Layout.fillWidth: true
                            }
                        }

                        // Extraction Mode Choice
                        ColumnLayout {
                            Layout.fillWidth: true
                            spacing: 4
                            Text {
                                text: root.getTxt("mode_lbl")
                                color: metallicGold
                                font.bold: true
                                font.pixelSize: 11
                            }
                            ComboBox {
                                id: modeCombo
                                Layout.fillWidth: true
                                model: [
                                    { "textAr": "📥 استخراج الأكواد وتوجيهها تلقائياً", "textEn": "📥 Extract & Route Code Blocks", "value": "code" },
                                    { "textAr": "📄 استخراج المحتوى النصي بالكامل للمشروع", "textEn": "📄 Extract Full Text/Markdown Document", "value": "text" },
                                    { "textAr": "🧠 تجميل وإرسال لصندوق الالتقاط الذكي", "textEn": "🧠 Send to Smart Capture Inbox", "value": "capture" }
                                ]
                                textRole: (backend.appLanguage === "ar" ? "textAr" : "textEn")
                            }
                        }

                        // URL Input Label & TextField
                        ColumnLayout {
                            Layout.fillWidth: true
                            spacing: 4
                            Text {
                                text: root.getTxt("url_label")
                                color: metallicGold
                                font.bold: true
                                font.pixelSize: 11
                            }
                            TextField {
                                id: urlInput
                                Layout.fillWidth: true
                                placeholderText: root.getTxt("url_placeholder")
                                color: textSilver
                                font.pixelSize: 11
                                selectByMouse: true
                                background: Rectangle {
                                    color: "#0F131D"
                                    border.color: urlInput.activeFocus ? metallicGold : borderSlate
                                    radius: 6
                                }
                            }
                        }

                        // Clipboard Helper Button
                        Button {
                            id: pasteBtn
                            text: root.getTxt("paste_btn")
                            Layout.fillWidth: true
                            implicitHeight: 36
                            
                            background: Rectangle {
                                color: pasteBtn.pressed ? borderSlate : (pasteBtn.hovered ? "#212A3E" : "transparent")
                                border.color: metallicGold
                                border.width: 1
                                radius: 6
                            }
                            contentItem: Text {
                                text: pasteBtn.text
                                color: metallicGold
                                font.bold: true
                                font.pixelSize: 11
                                horizontalAlignment: Text.AlignHCenter
                                verticalAlignment: Text.AlignVCenter
                            }
                            onClicked: {
                                urlInput.text = backend.get_clipboard_text()
                            }
                        }

                        // Spacer to push button down
                        Item {
                            Layout.fillHeight: true
                            implicitHeight: 20
                        }

                        // Action Run Button
                        Button {
                            id: downloadBtn
                            text: root.getTxt("download_btn")
                            Layout.fillWidth: true
                            implicitHeight: 42
                            enabled: urlInput.text.trim() !== "" && !root.isProcessing
                            
                            background: Rectangle {
                                color: !downloadBtn.enabled ? "#2E2E2E" : (downloadBtn.pressed ? "#C59231" : (downloadBtn.hovered ? softGold : metallicGold))
                                radius: 6
                            }
                            contentItem: Text {
                                text: downloadBtn.text
                                color: !downloadBtn.enabled ? "#757575" : "#0F131D"
                                font.bold: true
                                font.pixelSize: 12
                                horizontalAlignment: Text.AlignHCenter
                                verticalAlignment: Text.AlignVCenter
                            }
                            onClicked: {
                                root.statusProgress = 10;
                                root.statusText = "جاري الاتصال بقاعدة البيانات وتحضير الطلب الشبكي...";
                                var url = urlInput.text.trim();
                                var proj = projectCombo.currentText;
                                var mode = modeCombo.model[modeCombo.currentIndex].value;
                                backend.download_chat_link_async(url, proj, mode);
                            }
                        }
                    }
                }
            }

            // Right Card: Results and Summary Log Output
            Rectangle {
                Layout.fillWidth: true
                Layout.fillHeight: true
                Layout.preferredWidth: 350
                color: cardSlateBg
                border.color: borderSlate
                radius: 8

                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 12
                    spacing: 12

                    Text {
                        text: root.getTxt("results_title")
                        color: metallicGold
                        font.bold: true
                        font.pixelSize: 13
                    }

                    // Progress Area
                    Rectangle {
                        Layout.fillWidth: true
                        Layout.preferredHeight: 65
                        color: "#0F131D"
                        border.color: borderSlate
                        radius: 6
                        
                        ColumnLayout {
                            anchors.fill: parent
                            anchors.margins: 8
                            spacing: 4

                            Text {
                                text: root.statusText
                                color: textSilver
                                font.pixelSize: 10
                                wrapMode: Text.Wrap
                                Layout.fillWidth: true
                            }

                            // Custom smooth Progress Bar
                            Rectangle {
                                id: progressContainer
                                Layout.fillWidth: true
                                height: 8
                                color: "#161D2C"
                                radius: 4
                                border.color: borderSlate
                                visible: root.statusProgress > 0

                                Rectangle {
                                    id: progressFill
                                    height: parent.height
                                    width: parent.width * (root.statusProgress / 100.0)
                                    color: metallicGold
                                    radius: 4
                                    Behavior on width { NumberAnimation { duration: 200 } }
                                }
                            }
                        }
                    }

                    // Live Statistics Grid
                    Text {
                        text: root.getTxt("stats_lbl")
                        color: metallicGold
                        font.bold: true
                        font.pixelSize: 11
                        Layout.topMargin: 5
                    }

                    RowLayout {
                        Layout.fillWidth: true
                        spacing: 10
                        
                        // Code Blocks Card
                        Rectangle {
                            Layout.fillWidth: true
                            Layout.preferredHeight: 60
                            color: "#0F131D"
                            border.color: borderSlate
                            radius: 6

                            ColumnLayout {
                                anchors.centerIn: parent
                                spacing: 2
                                Text {
                                    text: root.getTxt("code_count_lbl")
                                    color: textSilver
                                    font.pixelSize: 9
                                }
                                Text {
                                    text: root.codeCount.toString()
                                    color: metallicGold
                                    font.bold: true
                                    font.pixelSize: 18
                                }
                            }
                        }

                        // Words Card
                        Rectangle {
                            Layout.fillWidth: true
                            Layout.preferredHeight: 60
                            color: "#0F131D"
                            border.color: borderSlate
                            radius: 6

                            ColumnLayout {
                                anchors.centerIn: parent
                                spacing: 2
                                Text {
                                    text: root.getTxt("text_count_lbl")
                                    color: textSilver
                                    font.pixelSize: 9
                                }
                                Text {
                                    text: root.textCount.toString()
                                    color: softGold
                                    font.bold: true
                                    font.pixelSize: 18
                                }
                            }
                        }
                    }

                    // Saved Files Summary Area
                    Text {
                        text: root.getTxt("saved_files_lbl")
                        color: metallicGold
                        font.bold: true
                        font.pixelSize: 11
                        Layout.topMargin: 5
                    }

                    Rectangle {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        color: "#0F131D"
                        border.color: borderSlate
                        radius: 6

                        ScrollView {
                            anchors.fill: parent
                            clip: true
                            anchors.margins: 8

                            TextArea {
                                id: summaryArea
                                text: root.savedSummary || "لم يتم معالجة روابط برمجية بعد في هذه الجلسة.\nNo links processed yet in this session."
                                color: textSilver
                                font.family: "Consolas"
                                font.pixelSize: 11
                                readOnly: true
                                wrapMode: TextEdit.Wrap
                                background: null
                            }
                        }
                    }
                }
            }
        }
    }
}
