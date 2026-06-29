import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

Dialog {
    id: root
    title: backend.appLanguage === "ar" ? "📂 مستكشف ومعالج المجلدات" : "📂 Folder Explorer & Processor"
    anchors.centerIn: parent
    width: 520
    height: 380
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
            "title": { "ar": "📂 مستكشف ومعالج المجلدات", "en": "📂 Folder Explorer & Processor" },
            "subtitle": { "ar": "تحليل مجلدات العمل بالكامل وإعداد تقارير وحزم البناء الذكية", "en": "Analyze workspace directories, compile tree maps, and bundle code packages" },
            "folder_info": { "ar": "خصائص المجلد المفتوح ومساره:", "en": "Opened folder attributes & location:" },
            "files_lbl": { "ar": "عدد الملفات المكتشفة:", "en": "Total detected files:" },
            "size_lbl": { "ar": "الحجم الإجمالي على القرص:", "en": "Total disk size:" },
            "btn_tree": { "ar": "📊 تقرير شجري تفاعلي", "en": "📊 Interactive Tree Report" },
            "btn_pack": { "ar": "📦 حزمة بناء مجمعة", "en": "📦 Bundle Build Package" },
            "btn_cancel": { "ar": "إلغاء العمليات ❌", "en": "Cancel Operation ❌" },
            "toast_tree": { "ar": "جاري زراعة وتوليد التقرير الشجري التفاعلي للمجلد وحفظه.", "en": "Cultivating and saving the interactive tree structural map for the directory." },
            "toast_pack": { "ar": "جاري تجميع ملفات المجلد وتغليفها تحت توجيهات @builder.", "en": "Gathering and packaging directory files inside @builder delimiters." }
        };
        var isAr = (backend.appLanguage === "ar");
        return translations[key] ? (isAr ? translations[key]["ar"] : translations[key]["en"]) : "";
    }

    property string currentFolderPath: ""
    property string currentFolderName: ""
    property int detectedFilesCount: 0
    property string totalFolderSize: ""

    // Public method to load folder
    function openFolder(folderPath) {
        var resultJson = backend.handle_folder_open(folderPath)
        try {
            var data = JSON.parse(resultJson)
            if (data.success) {
                currentFolderPath = data.path
                currentFolderName = data.name
                detectedFilesCount = data.file_count
                totalFolderSize = data.size_formatted
                root.open()
            } else {
                backend.notificationSent(
                    backend.appLanguage === "ar" ? "فشل فتح المجلد" : "Folder Open Failed",
                    backend.appLanguage === "ar" ? "تعذر قراءة خصائص المجلد المحدد." : "Unable to read specified folder attributes.",
                    "error"
                )
            }
        } catch (e) {
            console.log("Error processing handle_folder_open: " + e)
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
            spacing: 14

            // Folder Properties Card
            Rectangle {
                Layout.fillWidth: true
                Layout.fillHeight: true
                color: slateBg
                border.color: borderSlate
                border.width: 1
                radius: 8

                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 14
                    spacing: 8

                    RowLayout {
                        spacing: 10
                        Text {
                            text: "📁"
                            font.pixelSize: 22
                        }
                        Text {
                            text: root.currentFolderName
                            color: textSilver
                            font.bold: true
                            font.pixelSize: 12
                            elide: Text.ElideRight
                            Layout.fillWidth: true
                        }
                    }

                    Rectangle {
                        Layout.fillWidth: true
                        height: 1
                        color: borderSlate
                    }

                    // Properties
                    GridLayout {
                        columns: 2
                        columnSpacing: 10
                        rowSpacing: 6
                        Layout.fillWidth: true

                        Text {
                            text: root.getTxt("files_lbl")
                            color: textGray
                            font.pixelSize: 10
                        }
                        Text {
                            text: root.detectedFilesCount.toString()
                            color: softGold
                            font.bold: true
                            font.pixelSize: 11
                        }

                        Text {
                            text: root.getTxt("size_lbl")
                            color: textGray
                            font.pixelSize: 10
                        }
                        Text {
                            text: root.totalFolderSize
                            color: softGold
                            font.bold: true
                            font.pixelSize: 11
                        }
                    }

                    Rectangle {
                        Layout.fillWidth: true
                        height: 1
                        color: borderSlate
                    }

                    Text {
                        text: root.currentFolderPath
                        color: textGray
                        font.pixelSize: 9
                        elide: Text.ElideMiddle
                        Layout.fillWidth: true
                    }
                }
            }

            // Action Buttons
            RowLayout {
                Layout.fillWidth: true
                spacing: 12

                // Action 1: Tree Report
                Button {
                    text: root.getTxt("btn_tree")
                    Layout.fillWidth: true
                    Layout.preferredHeight: 40
                    onClicked: {
                        backend.generate_treedoc(root.currentFolderPath, "html")
                        backend.notificationSent(
                            backend.appLanguage === "ar" ? "تقرير شجري" : "Tree Report",
                            root.getTxt("toast_tree"),
                            "success"
                        )
                        root.close()
                    }
                }

                // Action 2: Build Package
                Button {
                    text: root.getTxt("btn_pack")
                    Layout.fillWidth: true
                    Layout.preferredHeight: 40
                    onClicked: {
                        backend.pack_directory_v2(root.currentFolderPath, "")
                        backend.notificationSent(
                            backend.appLanguage === "ar" ? "تجميع الحزم" : "Package Directory",
                            root.getTxt("toast_pack"),
                            "success"
                        )
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
