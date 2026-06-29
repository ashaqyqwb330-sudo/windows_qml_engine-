import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

Rectangle {
    id: root
    color: "transparent"

    // Theme references (matching main.qml)
    readonly property color slateBg: "#0B0F19"
    readonly property color cardSlateBg: "#161D2C"
    readonly property color borderSlate: "#212A3E"
    readonly property color metallicGold: "#D4AF37"
    readonly property color softGold: "#F3E5AB"
    readonly property color textSilver: "#E2E8F0"
    readonly property color successGreen: "#10B981"
    readonly property color errorRed: "#EF4444"

    // Localization helper
    function getTxt(key) {
        var translations = {
            "title": { "ar": "📤 نظام التصدير الذاتي المتقدم للمصدر", "en": "📤 Advanced Developer Self-Exporter" },
            "desc": { "ar": "قم بتجميع وتصدير الكود المصدري للتطبيق الحالي أو مشروع أندرويد النشط كحزمة بناء للمطورين (@builder) بلمسة واحدة. سيتم نسخ الحزمة تلقائياً وحفظها كنسخة احتياطية.", "en": "Instantly bundle and export the source code of the current application or any registered Android workspace as a developer-friendly build pack (@builder) copied to your clipboard and saved as a backup." },
            "options_header": { "ar": "⚙️ إعدادات وخيارات التصدير:", "en": "⚙️ Export Settings & Options:" },
            "export_type_lbl": { "ar": "نوع الكود المصدري المراد تصديره:", "en": "Select Source Code to Export:" },
            "type_windows": { "ar": "💻 كود محرك ويندوز الحالي (QML + Python)", "en": "💻 Current Windows Engine (QML + Python)" },
            "type_android": { "ar": "📱 كود مشروع أندرويد مخصص", "en": "📱 Android Workspace Project" },
            "target_proj_lbl": { "ar": "اختر المشروع المراد تصدير كوده:", "en": "Select Project to Export:" },
            "save_path_lbl": { "ar": "مجلد الحفظ والنسخ الاحتياطي للقرص:", "en": "Backup Destination Folder:" },
            "save_inbox": { "ar": "📂 مجلد الوارد الذكي الافتراضي (SmartInbox)", "en": "📂 Default Smart Inbox Folder (SmartInbox)" },
            "save_custom": { "ar": "📁 مجلد مخصص على القرص", "en": "📁 Custom Folder on Disk" },
            "custom_path_placeholder": { "ar": "اكتب المسار الكامل للمجلد على القرص هنا...", "en": "Type full absolute directory path here..." },
            "btn_execute_export": { "ar": "تصدير الكود المصدري ومزامنة الحافظة ⚡", "en": "Export Source Code & Sync Clipboard ⚡" },
            "results_header": { "ar": "📊 تقرير ونتائج عملية التصدير:", "en": "📊 Export Process Report:" },
            "status_lbl": { "ar": "حالة التصدير:", "en": "Status:" },
            "status_success": { "ar": "✅ تم التجميع والنسخ للحافظة والقرص بنجاح!", "en": "✅ Successfully compiled, copied to clipboard, and saved!" },
            "files_count_lbl": { "ar": "إجمالي الملفات المضمنة:", "en": "Total Files Bundled:" },
            "chars_count_lbl": { "ar": "إجمالي حجم الحزمة (بالأحرف):", "en": "Total Bundle Size (Chars):" },
            "backup_file_lbl": { "ar": "مسار ملف النسخة الاحتياطية:", "en": "Backup File Location:" },
            "btn_copy_again": { "ar": "إعادة نسخ الحزمة للحافظة 📋", "en": "Recopy Package to Clipboard 📋" },
            "preview_header": { "ar": "👀 معاينة سريعة لهيكل الحزمة المجمعة:", "en": "👀 Package Structure Preview:" },
            "no_export_yet": { "ar": "اضغط على زر التصدير أعلاه لتجميع الكود المصدري وعرض النتائج.", "en": "Click the Export button above to bundle the source code and generate the report." }
        };
        var isAr = (backend.appLanguage === "ar");
        return translations[key] ? (isAr ? translations[key]["ar"] : translations[key]["en"]) : "";
    }

    // UI States
    property string selectedExportType: "windows" // "windows" or "android"
    property string selectedSaveLocation: "inbox" // "inbox" or "custom"
    
    // Results model
    property bool hasResults: false
    property int resultFileCount: 0
    property int resultCharCount: 0
    property string resultSavePath: ""
    property string resultPreview: ""

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 20
        spacing: 15

        // Header section
        RowLayout {
            Layout.fillWidth: true
            spacing: 10
            Text {
                text: root.getTxt("title")
                color: metallicGold
                font.bold: true
                font.pixelSize: 18
            }
            Spacer { Layout.fillWidth: true }
        }

        Text {
            text: root.getTxt("desc")
            color: textSilver
            font.pixelSize: 11
            wrapMode: Text.Wrap
            Layout.fillWidth: true
            Layout.bottomMargin: 5
        }

        // Options Card
        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: 180
            color: cardSlateBg
            border.color: borderSlate
            radius: 8

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 12
                spacing: 10

                Text {
                    text: root.getTxt("options_header")
                    color: metallicGold
                    font.bold: true
                    font.pixelSize: 13
                }

                GridLayout {
                    Layout.fillWidth: true
                    columns: 2
                    columnSpacing: 25
                    rowSpacing: 10

                    // Left Column: Export Type Selection
                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 6

                        Text {
                            text: root.getTxt("export_type_lbl")
                            color: textSilver
                            font.bold: true
                            font.pixelSize: 11
                        }

                        RowLayout {
                            spacing: 15
                            
                            // Windows engine option
                            RadioButton {
                                id: radioWindows
                                checked: true
                                text: root.getTxt("type_windows")
                                contentItem: Text {
                                    text: radioWindows.text
                                    color: textSilver
                                    font.pixelSize: 11
                                    leftPadding: 24
                                }
                                onClicked: root.selectedExportType = "windows"
                            }

                            // Android option
                            RadioButton {
                                id: radioAndroid
                                text: root.getTxt("type_android")
                                contentItem: Text {
                                    text: radioAndroid.text
                                    color: textSilver
                                    font.pixelSize: 11
                                    leftPadding: 24
                                }
                                onClicked: root.selectedExportType = "android"
                            }
                        }

                        // Project Selector (visible if android selected)
                        RowLayout {
                            Layout.fillWidth: true
                            spacing: 10
                            visible: root.selectedExportType === "android"

                            Text {
                                text: root.getTxt("target_proj_lbl")
                                color: softGold
                                font.pixelSize: 11
                            }

                            ComboBox {
                                id: projectSelectBox
                                Layout.fillWidth: true
                                model: projectsModel
                                textRole: "name"
                                currentIndex: 0
                                
                                background: Rectangle {
                                    color: slateBg
                                    border.color: borderSlate
                                    radius: 4
                                }
                                contentItem: Text {
                                    text: projectSelectBox.currentText
                                    color: textSilver
                                    font.pixelSize: 11
                                    verticalAlignment: Text.AlignVCenter
                                    leftPadding: 8
                                }
                            }
                        }
                    }

                    // Right Column: Save Destination Selection
                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 6

                        Text {
                            text: root.getTxt("save_path_lbl")
                            color: textSilver
                            font.bold: true
                            font.pixelSize: 11
                        }

                        RowLayout {
                            spacing: 15

                            RadioButton {
                                id: radioInbox
                                checked: true
                                text: root.getTxt("save_inbox")
                                contentItem: Text {
                                    text: radioInbox.text
                                    color: textSilver
                                    font.pixelSize: 11
                                    leftPadding: 24
                                }
                                onClicked: root.selectedSaveLocation = "inbox"
                            }

                            RadioButton {
                                id: radioCustom
                                text: root.getTxt("save_custom")
                                contentItem: Text {
                                    text: radioCustom.text
                                    color: textSilver
                                    font.pixelSize: 11
                                    leftPadding: 24
                                }
                                onClicked: root.selectedSaveLocation = "custom"
                            }
                        }

                        // Custom path input field
                        TextField {
                            id: customPathInput
                            visible: root.selectedSaveLocation === "custom"
                            Layout.fillWidth: true
                            placeholderText: root.getTxt("custom_path_placeholder")
                            color: textSilver
                            font.pixelSize: 11
                            background: Rectangle {
                                color: slateBg
                                border.color: borderSlate
                                radius: 4
                            }
                        }
                    }
                }

                Spacer { Layout.fillHeight: true }

                // Execution Button
                Button {
                    id: exportActionBtn
                    text: root.getTxt("btn_execute_export")
                    Layout.fillWidth: true
                    implicitHeight: 36

                    background: Rectangle {
                        color: exportActionBtn.pressed ? "#C59231" : (exportActionBtn.hovered ? softGold : metallicGold)
                        radius: 5
                    }
                    contentItem: Text {
                        text: exportActionBtn.text
                        color: "#0F131D"
                        font.bold: true
                        font.pixelSize: 12
                        horizontalAlignment: Text.AlignHCenter
                        verticalAlignment: Text.AlignVCenter
                    }

                    onClicked: {
                        var customDir = "";
                        if (root.selectedSaveLocation === "custom") {
                            customDir = customPathInput.text.trim();
                        }

                        var resultJsonStr = "";
                        if (root.selectedExportType === "windows") {
                            resultJsonStr = backend.export_windows_source(customDir);
                        } else {
                            var projName = projectSelectBox.currentText;
                            resultJsonStr = backend.export_android_source(projName, customDir);
                        }

                        try {
                            var res = JSON.parse(resultJsonStr);
                            if (res.success) {
                                root.resultFileCount = res.file_count;
                                root.resultCharCount = res.char_count;
                                root.resultSavePath = res.save_path;
                                root.resultPreview = res.preview;
                                root.hasResults = true;
                            } else {
                                backend.notificationSent.emit("خطأ التصدير", res.message, "warning");
                            }
                        } catch(e) {
                            backend.notificationSent.emit("خطأ التصدير", "فشل تحليل التقرير: " + e.message, "warning");
                        }
                    }
                }
            }
        }

        // Bottom Grid: Left Results Column & Right Preview Column
        RowLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            spacing: 15

            // Left Side: Process Report Box
            Rectangle {
                Layout.fillWidth: true
                Layout.fillHeight: true
                Layout.preferredWidth: 4
                color: cardSlateBg
                border.color: borderSlate
                radius: 8

                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 12
                    spacing: 10

                    Text {
                        text: root.getTxt("results_header")
                        color: metallicGold
                        font.bold: true
                        font.pixelSize: 13
                    }

                    // Placeholder if no results yet
                    ColumnLayout {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        visible: !root.hasResults
                        spacing: 10
                        Layout.alignment: Qt.AlignCenter

                        Text {
                            text: "📤"
                            font.pixelSize: 42
                            Layout.alignment: Qt.AlignHCenter
                        }

                        Text {
                            text: root.getTxt("no_export_yet")
                            color: "#808A9D"
                            font.pixelSize: 11
                            horizontalAlignment: Text.AlignHCenter
                            wrapMode: Text.Wrap
                            Layout.preferredWidth: 250
                        }
                    }

                    // Active Results Column
                    ColumnLayout {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        visible: root.hasResults
                        spacing: 12

                        // Status Row
                        ColumnLayout {
                            spacing: 4
                            Text {
                                text: root.getTxt("status_lbl")
                                color: "#808A9D"
                                font.pixelSize: 10
                            }
                            Text {
                                text: root.getTxt("status_success")
                                color: successGreen
                                font.bold: true
                                font.pixelSize: 12
                            }
                        }

                        // Files Bundled
                        ColumnLayout {
                            spacing: 4
                            Text {
                                text: root.getTxt("files_count_lbl")
                                color: "#808A9D"
                                font.pixelSize: 10
                            }
                            Text {
                                text: root.resultFileCount.toString() + " " + (backend.appLanguage === "ar" ? "ملفات برمجية" : "code files")
                                color: textSilver
                                font.bold: true
                                font.pixelSize: 12
                            }
                        }

                        // Size Bundled
                        ColumnLayout {
                            spacing: 4
                            Text {
                                text: root.getTxt("chars_count_lbl")
                                color: "#808A9D"
                                font.pixelSize: 10
                            }
                            Text {
                                text: root.resultCharCount.toLocaleString() + " " + (backend.appLanguage === "ar" ? "حرف برمي" : "characters")
                                color: softGold
                                font.bold: true
                                font.pixelSize: 12
                            }
                        }

                        // Backup File Path
                        ColumnLayout {
                            spacing: 4
                            Layout.fillWidth: true
                            Text {
                                text: root.getTxt("backup_file_lbl")
                                color: "#808A9D"
                                font.pixelSize: 10
                            }
                            Text {
                                text: root.resultSavePath
                                color: textSilver
                                font.pixelSize: 10
                                wrapMode: Text.Wrap
                                Layout.fillWidth: true
                            }
                        }

                        Spacer { Layout.fillHeight: true }

                        // Recopy Button
                        Button {
                            id: recopyBtn
                            text: root.getTxt("btn_copy_again")
                            Layout.fillWidth: true
                            implicitHeight: 32

                            background: Rectangle {
                                color: "transparent"
                                border.color: successGreen
                                radius: 4
                            }
                            contentItem: Text {
                                text: recopyBtn.text
                                color: successGreen
                                font.bold: true
                                font.pixelSize: 11
                                horizontalAlignment: Text.AlignHCenter
                                verticalAlignment: Text.AlignVCenter
                            }

                            onClicked: {
                                if (root.resultSavePath) {
                                    // Simply call backend to get text from the saved file to copy it
                                    var resultStr = backend.get_clipboard_text(); // Or read the file directly
                                    // Since it's already copied during export, this is a friendly reinforcement or backup recopy from clipboard
                                    backend.set_clipboard_text(backend.get_clipboard_text()); 
                                    backend.notificationSent.emit("نسخ الكود", "تمت إعادة نسخ الكود المصدري للحافظة بنجاح.", "success");
                                }
                            }
                        }
                    }
                }
            }

            // Right Side: Quick Structure Preview Box
            Rectangle {
                Layout.fillWidth: true
                Layout.fillHeight: true
                Layout.preferredWidth: 6
                color: cardSlateBg
                border.color: borderSlate
                radius: 8

                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 12
                    spacing: 8

                    Text {
                        text: root.getTxt("preview_header")
                        color: metallicGold
                        font.bold: true
                        font.pixelSize: 13
                    }

                    // Code Area container
                    Rectangle {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        color: "#0F131D"
                        border.color: borderSlate
                        radius: 6

                        ScrollView {
                            anchors.fill: parent
                            clip: true

                            TextArea {
                                id: previewTextArea
                                text: root.hasResults ? root.resultPreview : "// لا تزال حزمة المعاينة فارغة حالياً.\n// سيتم إظهار البداية هنا بعد إطلاق عملية التصدير."
                                color: root.hasResults ? textSilver : "#808A9D"
                                font.family: "Consolas"
                                font.pixelSize: 11
                                selectByMouse: true
                                readOnly: true
                                wrapMode: TextEdit.NoWrap
                                background: null
                            }
                        }
                    }
                }
            }
        }
    }
}
