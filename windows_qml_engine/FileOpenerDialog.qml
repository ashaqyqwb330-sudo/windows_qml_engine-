import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

Dialog {
    id: root
    title: backend.appLanguage === "ar" ? "📁 معالج الملفات الذكي" : "📁 Intelligent File Opener"
    anchors.centerIn: parent
    width: 560
    height: 520
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
            "title": { "ar": "📁 معالج الملفات الذكي", "en": "📁 Intelligent File Opener" },
            "subtitle": { "ar": "قراءة وتحليل الملفات المفتوحة ومعالجتها فورا بالأدوات الذهبية", "en": "Analyze and instantly process opened files with specialized golden engines" },
            "file_info": { "ar": "خصائص ومسار الملف المفتوح:", "en": "Opened file properties & path:" },
            "preview_title": { "ar": "👀 معاينة سريعة لمحتوى الملف (أول 500 حرف):", "en": "👀 Fast preview of file content (First 500 chars):" },
            "btn_capture": { "ar": "🧠 التقاط ذكي", "en": "🧠 Smart Capture" },
            "btn_builder": { "ar": "📦 حزمة بناء", "en": "📦 Build Package" },
            "btn_beautify": { "ar": "🎨 تحويل وتجميل", "en": "🎨 Convert & Beautify" },
            "btn_editor": { "ar": "📂 فتح في المحرر", "en": "📂 Open in Editor" },
            "btn_cancel": { "ar": "إلغاء ❌", "en": "Cancel ❌" },
            "toast_captured": { "ar": "تم إرسال الملف إلى صندوق الالتقاط الذكي وحفظه بصيغة HTML.", "en": "File processed and successfully routed to Smart Capture Inbox." },
            "toast_builder": { "ar": "تم تغليف وتصدير الملف بصيغة @builder وحفظه في الحافظة.", "en": "File wrapped as a @builder package and saved to clipboard." },
            "toast_beautified": { "ar": "تم معالجة المستند وتجميل أسلوب عرضه بالكامل.", "en": "Document processed and beautifully formatted." }
        };
        var isAr = (backend.appLanguage === "ar");
        return translations[key] ? (isAr ? translations[key]["ar"] : translations[key]["en"]) : "";
    }

    property string currentFilePath: ""
    property string currentFileName: ""
    property string currentFileSize: ""
    property string filePreviewText: ""
    property string fullFileContent: ""

    // Public method to load file
    function openFile(filePath) {
        var resultJson = backend.handle_file_open(filePath)
        try {
            var data = JSON.parse(resultJson)
            if (data.success) {
                currentFilePath = data.path
                currentFileName = data.name
                currentFileSize = data.size_formatted
                filePreviewText = data.preview
                fullFileContent = backend.read_local_file(filePath)
                root.open()
            } else {
                backend.notificationSent(
                    backend.appLanguage === "ar" ? "فشل فتح الملف" : "File Open Failed",
                    backend.appLanguage === "ar" ? "تعذر قراءة خصائص الملف المحدد." : "Unable to read specified file attributes.",
                    "error"
                )
            }
        } catch (e) {
            console.log("Error processing handle_file_open: " + e)
        }
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
                horizontalAlignment: Text.AlignHCenter
                width: parent.width - 20
                elide: Text.ElideRight
            }
        }
    }

    contentItem: Item {
        anchors.fill: parent

        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 14
            spacing: 12

            // File properties
            Rectangle {
                Layout.fillWidth: true
                height: 55
                color: slateBg
                border.color: borderSlate
                border.width: 1
                radius: 8

                RowLayout {
                    anchors.fill: parent
                    anchors.margins: 10
                    spacing: 10

                    Text {
                        text: "📄"
                        font.pixelSize: 22
                    }

                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 2
                        Text {
                            text: root.currentFileName + " (" + root.currentFileSize + ")"
                            color: textSilver
                            font.bold: true
                            font.pixelSize: 11
                            elide: Text.ElideRight
                        }
                        Text {
                            text: root.currentFilePath
                            color: textGray
                            font.pixelSize: 9
                            elide: Text.ElideMiddle
                            Layout.fillWidth: true
                        }
                    }
                }
            }

            // Preview header
            Text {
                text: root.getTxt("preview_title")
                color: softGold
                font.bold: true
                font.pixelSize: 10
            }

            // File Content Preview Area
            Rectangle {
                Layout.fillWidth: true
                Layout.fillHeight: true
                color: slateBg
                border.color: borderSlate
                border.width: 1
                radius: 8

                ScrollView {
                    anchors.fill: parent
                    anchors.margins: 8
                    clip: true

                    TextArea {
                        text: root.filePreviewText
                        color: textSilver
                        font.family: "Courier"
                        font.pixelSize: 11
                        wrapMode: Text.Wrap
                        readOnly: true
                        selectByMouse: true
                        placeholderText: backend.appLanguage === "ar" ? "محتوى الملف فارغ..." : "File is empty..."
                    }
                }
            }

            // Grid of Specialized Actions
            GridLayout {
                Layout.fillWidth: true
                columns: 2
                columnSpacing: 10
                rowSpacing: 10

                // Action 1: Smart Capture
                Button {
                    text: root.getTxt("btn_capture")
                    Layout.fillWidth: true
                    Layout.preferredHeight: 38
                    onClicked: {
                        backend.smart_capture_content_v2(root.fullFileContent, "space")
                        backend.notificationSent(
                            backend.appLanguage === "ar" ? "التقاط ذكي" : "Smart Capture",
                            root.getTxt("toast_captured"),
                            "success"
                        )
                        root.close()
                    }
                }

                // Action 2: Build Package
                Button {
                    text: root.getTxt("btn_builder")
                    Layout.fillWidth: true
                    Layout.preferredHeight: 38
                    onClicked: {
                        var packageText = "// @builder:file " + root.currentFileName + "\n" + root.fullFileContent + "\n// @builder:end";
                        backend.set_clipboard_text(packageText)
                        backend.notificationSent(
                            backend.appLanguage === "ar" ? "حزمة بناء" : "Build Package",
                            root.getTxt("toast_builder"),
                            "success"
                        )
                        root.close()
                    }
                }

                // Action 3: Convert & Beautify
                Button {
                    text: root.getTxt("btn_beautify")
                    Layout.fillWidth: true
                    Layout.preferredHeight: 38
                    onClicked: {
                        backend.smart_capture_content_v2(root.fullFileContent, "gold")
                        backend.notificationSent(
                            backend.appLanguage === "ar" ? "تجميل وتنسيق" : "Beautify & Style",
                            root.getTxt("toast_beautified"),
                            "success"
                        )
                        root.close()
                    }
                }

                // Action 4: Open in Editor
                Button {
                    text: root.getTxt("btn_editor")
                    Layout.fillWidth: true
                    Layout.preferredHeight: 38
                    onClicked: {
                        openFileInEditor(root.currentFilePath)
                        root.close()
                    }
                }
            }

            // Close
            Button {
                text: root.getTxt("btn_cancel")
                Layout.fillWidth: true
                Layout.preferredHeight: 38
                onClicked: root.close()
            }
        }
    }
}
