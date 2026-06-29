import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

Dialog {
    id: root
    title: backend.appLanguage === "ar" ? "📋 نتائج التشخيص الفنية" : "📋 Technical Diagnostic Results"
    anchors.centerIn: parent
    width: 580
    height: 600
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
            "title": { "ar": "📋 نتائج التشخيص الفنية", "en": "📋 Technical Diagnostic Results" },
            "subtitle": { "ar": "تحليل تفصيلي لمكونات نظام التشغيل والخدمات والاتصال السحابي المدمج", "en": "Detailed analysis of operating system services, components, and cloud integrations" },
            "checklist_lbl": { "ar": "🔍 مخرجات الفحص الفوري والمكونات النشطة:", "en": "🔍 Real-time Component Verification Status:" },
            "raw_lbl": { "ar": "📄 التقرير الفني الشامل وغير المنسق:", "en": "📄 Comprehensive Raw Technical Report:" },
            "btn_copy": { "ar": "نسخ التقرير بالكامل 📋", "en": "Copy Full Report 📋" },
            "btn_close": { "ar": "إغلاق النافذة ❌", "en": "Close Window ❌" },
            "success_toast": { "ar": "تم نسخ التقرير التشخيصي بالكامل إلى الحافظة بنجاح.", "en": "Diagnostic report copied to clipboard successfully." }
        };
        var isAr = (backend.appLanguage === "ar");
        return translations[key] ? (isAr ? translations[key]["ar"] : translations[key]["en"]) : "";
    }

    // Property to receive diagnostic data as JSON string or parsed object
    property string diagnosticDataRaw: ""
    property var diagData: null

    onDiagnosticDataRawChanged: {
        try {
            diagData = JSON.parse(diagnosticDataRaw)
            if (diagData && diagData.items) {
                checklistModel.clear()
                for (var i = 0; i < diagData.items.length; i++) {
                    checklistModel.append(diagData.items[i])
                }
            }
        } catch (e) {
            diagData = null
            checklistModel.clear()
        }
    }

    ListModel {
        id: checklistModel
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
            anchors.margins: 14
            spacing: 12

            // Checklist section
            Text {
                text: root.getTxt("checklist_lbl")
                color: metallicGold
                font.bold: true
                font.pixelSize: 12
            }

            // Visual checklist list view
            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: 180
                color: slateBg
                border.color: borderSlate
                border.width: 1
                radius: 8

                ListView {
                    id: checklistView
                    anchors.fill: parent
                    anchors.margins: 6
                    model: checklistModel
                    clip: true
                    spacing: 6

                    delegate: Rectangle {
                        width: checklistView.width - 12
                        height: 48
                        color: borderSlate
                        radius: 6
                        border.color: model.status === "success" ? successGreen : (model.status === "warning" ? warningOrange : errorRed)
                        border.width: 1

                        RowLayout {
                            anchors.fill: parent
                            anchors.margins: 8
                            spacing: 12

                            Text {
                                text: model.status === "success" ? "✅" : (model.status === "warning" ? "⚠️" : "❌")
                                font.pixelSize: 16
                            }

                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: 1

                                Text {
                                    text: backend.appLanguage === "ar" ? model.name_ar : model.name_en
                                    color: textSilver
                                    font.bold: true
                                    font.pixelSize: 11
                                }

                                Text {
                                    text: backend.appLanguage === "ar" ? model.details_ar : model.details_en
                                    color: textGray
                                    font.pixelSize: 9
                                    elide: Text.ElideRight
                                    Layout.fillWidth: true
                                }
                            }
                        }
                    }
                }
            }

            // Raw report section
            Text {
                text: root.getTxt("raw_lbl")
                color: metallicGold
                font.bold: true
                font.pixelSize: 12
            }

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
                        id: rawTextArea
                        text: {
                            if (!root.diagData) return ""
                            return backend.appLanguage === "ar" ? root.diagData.raw_report_ar : root.diagData.raw_report_en
                        }
                        color: textSilver
                        font.family: "Consolas"
                        font.pixelSize: 10.5
                        readOnly: true
                        wrapMode: Text.Wrap
                        background: null
                    }
                }
            }

            // Bottom controls
            RowLayout {
                Layout.fillWidth: true
                spacing: 10

                Button {
                    text: root.getTxt("btn_copy")
                    Layout.fillWidth: true
                    onClicked: {
                        backend.set_clipboard_text(rawTextArea.text)
                        backend.notificationSent(
                            backend.appLanguage === "ar" ? "نسخ التقرير" : "Report Copied",
                            root.getTxt("success_toast"),
                            "success"
                        )
                    }
                }

                Button {
                    text: root.getTxt("btn_close")
                    implicitWidth: 150
                    onClicked: root.close()
                }
            }
        }
    }
}
