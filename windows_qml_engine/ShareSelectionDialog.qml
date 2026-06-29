import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

Dialog {
    id: root
    title: backend.appLanguage === "ar" ? "🔗 معالج المحتوى والمشاركة الذكي" : "🔗 Intelligent Share & Route"
    anchors.centerIn: parent
    width: 580
    height: 560
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
            "title": { "ar": "🔗 معالج المحتوى والمشاركة الذكي", "en": "🔗 Intelligent Share & Route" },
            "subtitle": { "ar": "استلام وتحليل المحتوى والروابط الممررة من النظام ومعالجتها فوراً", "en": "Instantly analyze and route shared texts or links passed from system apps" },
            "preview_title": { "ar": "👀 معاينة النص المشترك:", "en": "👀 Shared Text Preview:" },
            "project_lbl": { "ar": "المشروع المستهدف (لتوجيه الحزم والأوامر):", "en": "Target Project Workspace (For routing):" },
            "btn_cancel": { "ar": "إلغاء العمليات ❌", "en": "Cancel ❌" },
            "success_title": { "ar": "نجاح العملية", "en": "Action Executed" },
            "toast_builder": { "ar": "تم تطبيق حزمة @builder واستخراج الملفات بنجاح للمشروع.", "en": "Successfully applied @builder package and written files to workspace." },
            "toast_executor": { "ar": "تم تنفيذ الأوامر البرمجية والسيناريوهات المرفقة بنجاح.", "en": "All bundled automation scenarios and script tasks executed successfully." },
            "toast_beautified": { "ar": "تم تجميل المستند وحفظه بنجاح بصيغة HTML.", "en": "Document successfully formatted and saved to Smart Inbox as premium HTML." },
            "toast_captured": { "ar": "تم التقاط النص وحفظه بنجاح كمسودة سريعة.", "en": "Content successfully captured and saved as a structured quick memo." }
        };
        var isAr = (backend.appLanguage === "ar");
        return translations[key] ? (isAr ? translations[key]["ar"] : translations[key]["en"]) : "";
    }

    property string currentSharedText: ""
    property string textPreview: ""

    ListModel {
        id: actionsModel
    }

    ListModel {
        id: projectsListModel
    }

    // Public method to load shared text
    function loadSharedText(text) {
        currentSharedText = text
        textPreview = text.length > 200 ? text.substring(0, 200) + "..." : text
        
        // Load actions from backend
        var actionsJson = backend.get_available_actions(text)
        actionsModel.clear()
        try {
            var actions = JSON.parse(actionsJson)
            for (var i = 0; i < actions.length; i++) {
                actionsModel.append(actions[i])
            }
        } catch (e) {
            console.log("Error loading shared actions: " + e)
        }
        
        // Load projects list
        loadProjectsList()
        
        root.open()
    }

    function loadProjectsList() {
        projectsListModel.clear()
        projectsListModel.append({ "name": "Default" })
        try {
            var projs = JSON.parse(backend.get_projects_json())
            for (var i = 0; i < projs.length; i++) {
                if (projs[i].name !== "Default") {
                    projectsListModel.append({ "name": projs[i].name })
                }
            }
        } catch (e) {
            console.log("Error loading projects list: " + e)
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

            // Shared Text Preview
            Text {
                text: root.getTxt("preview_title")
                color: softGold
                font.bold: true
                font.pixelSize: 11
            }

            Rectangle {
                Layout.fillWidth: true
                height: 75
                color: slateBg
                border.color: borderSlate
                border.width: 1
                radius: 8

                ScrollView {
                    anchors.fill: parent
                    anchors.margins: 8
                    clip: true

                    TextArea {
                        text: root.textPreview
                        color: textSilver
                        font.family: "Segoe UI"
                        font.pixelSize: 10.5
                        wrapMode: Text.Wrap
                        readOnly: true
                        selectByMouse: true
                    }
                }
            }

            // Project Selector ComboBox
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 4

                Text {
                    text: root.getTxt("project_lbl")
                    color: textGray
                    font.pixelSize: 10
                }

                ComboBox {
                    id: projectSelector
                    Layout.fillWidth: true
                    model: projectsListModel
                    textRole: "name"
                    currentIndex: 0

                    background: Rectangle {
                        color: slateBg
                        border.color: borderSlate
                        border.width: 1
                        radius: 6
                    }

                    contentItem: Text {
                        text: projectSelector.currentText
                        color: textSilver
                        font.pixelSize: 11
                        verticalAlignment: Text.AlignVCenter
                        leftPadding: 10
                        rightPadding: 10
                    }
                }
            }

            // Actions List Header
            Text {
                text: backend.appLanguage === "ar" ? "🎯 الإجراءات الذكية المقترحة للمحتوى:" : "🎯 Recommended Smart Actions for Content:"
                color: softGold
                font.bold: true
                font.pixelSize: 11
            }

            // Actions ListView
            ListView {
                id: actionsListView
                Layout.fillWidth: true
                Layout.fillHeight: true
                model: actionsModel
                spacing: 8
                clip: true

                delegate: Rectangle {
                    width: actionsListView.width
                    height: 62
                    color: model.recommended ? "#1D2333" : slateBg
                    border.color: model.recommended ? metallicGold : borderSlate
                    border.width: model.recommended ? 1.5 : 1
                    radius: 8

                    property bool hovered: false

                    MouseArea {
                        anchors.fill: parent
                        hoverEnabled: true
                        onEntered: parent.hovered = true
                        onExited: parent.hovered = false
                        onClicked: {
                            var resultJson = backend.execute_shared_action(model.id, root.currentSharedText, projectSelector.currentText)
                            try {
                                var res = JSON.parse(resultJson)
                                if (res.success) {
                                    var toastMsg = ""
                                    if (model.id === "builder") toastMsg = root.getTxt("toast_builder")
                                    else if (model.id === "executor") toastMsg = root.getTxt("toast_executor")
                                    else if (model.id === "beautify") toastMsg = root.getTxt("toast_beautified")
                                    else if (model.id === "capture") toastMsg = root.getTxt("toast_captured")

                                    backend.notificationSent(
                                        root.getTxt("success_title"),
                                        toastMsg,
                                        "success"
                                    )
                                    // Refresh the main db stats just in case
                                    if (typeof refreshDatabase === "function") {
                                        refreshDatabase()
                                    }
                                    root.close()
                                } else {
                                    backend.notificationSent(
                                        backend.appLanguage === "ar" ? "فشل العملية" : "Action Failed",
                                        res.message,
                                        "error"
                                    )
                                }
                            } catch (e) {
                                console.log("Error running action: " + e)
                            }
                        }
                    }

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
                                layoutDirection: backend.appLanguage === "ar" ? Qt.RightToLeft : Qt.LeftToRight

                                Text {
                                    text: backend.appLanguage === "ar" ? model.label_ar : model.label_en
                                    color: model.recommended ? metallicGold : textSilver
                                    font.bold: true
                                    font.pixelSize: 11.5
                                }

                                Rectangle {
                                    visible: model.recommended
                                    color: metallicGold
                                    radius: 10
                                    width: 60
                                    height: 16
                                    Text {
                                        anchors.centerIn: parent
                                        text: backend.appLanguage === "ar" ? "موصى به" : "RECOM"
                                        color: "#0F131D"
                                        font.bold: true
                                        font.pixelSize: 8
                                    }
                                }
                            }

                            Text {
                                text: backend.appLanguage === "ar" ? model.description_ar : model.description_en
                                color: textGray
                                font.pixelSize: 9.5
                                wrapMode: Text.Wrap
                                Layout.fillWidth: true
                            }
                        }

                        Text {
                            text: "⚡"
                            font.pixelSize: 18
                            opacity: parent.hovered ? 1.0 : 0.6
                        }
                    }
                }
            }

            // Cancel Button
            Button {
                text: root.getTxt("btn_cancel")
                Layout.fillWidth: true
                Layout.preferredHeight: 38
                onClicked: root.close()
            }
        }
    }
}
