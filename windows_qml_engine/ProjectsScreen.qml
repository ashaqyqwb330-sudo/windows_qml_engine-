import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

Rectangle {
    id: root
    color: "transparent"

    // Localization helper
    function getTxt(key) {
        var translations = {
            "title": { "ar": "📁 إدارة المشاريع وهياكل القوالب المتقدمة", "en": "📁 Advanced Projects & Templates Manager" },
            "desc": { "ar": "تحكم بالكامل بمستودعات ومشاريع التطوير، وقم ببناء واستيراد وتصدير هياكل المجلدات والقوالب بصيغة JSON بلمسة واحدة.", "en": "Take full control of your workspaces. Build, import, export, and manage folder structures or JSON templates in one unified, high-performance screen." },
            "btn_export_active": { "ar": "تصدير هيكل المشروع النشط 📤", "en": "Export Active Project Template 📤" },
            "btn_import_template": { "ar": "استيراد قالب مشروع جديد 🛠️", "en": "Import New Project Template 🛠️" },
            "btn_demo_template": { "ar": "نسخ نموذج تجريبي 📝", "en": "Copy Demo JSON Template 📝" },
            "active_status": { "ar": "🟢 نشط حالياً", "en": "🟢 Currently Active" },
            "inactive_status": { "ar": "⚪ غير نشط", "en": "⚪ Inactive" },
            "btn_activate": { "ar": "تفعيل كالمشروع النشط 🎯", "en": "Activate Workspace 🎯" },
            "btn_export": { "ar": "تصدير القالب 📋", "en": "Export Template 📋" },
            "btn_delete": { "ar": "حذف من المنصة 🗑️", "en": "Remove Workspace 🗑️" },
            "created_lbl": { "ar": "تاريخ الربط:", "en": "Registered on:" },
            "path_lbl": { "ar": "المسار الحقيقي للقرص:", "en": "Physical Disk Path:" },
            "no_projects_lbl": { "ar": "لم يتم تسجيل مشاريع مخصصة حتى الآن في قاعدة البيانات.", "en": "No custom project workspaces registered in the local database yet." }
        };
        var isAr = (backend.appLanguage === "ar");
        return translations[key] ? (isAr ? translations[key]["ar"] : translations[key]["en"]) : "";
    }

    // Interactive template helper
    function getProjectStats(name) {
        try {
            var str = backend.get_project_details(name);
            var data = JSON.parse(str);
            if (data.success) {
                return (backend.appLanguage === "ar" ? "📁 مجلدات: " : "📁 Folders: ") + data.details.folder_count + " | 📄 " + (backend.appLanguage === "ar" ? "ملفات: " : "Files: ") + data.details.file_count;
            }
        } catch(e) {}
        return "...";
    }

    // Clipboard Project Template Auto-Detection State
    property string detectedClipboardJson: ""
    property bool showClipboardBanner: false

    Timer {
        id: clipboardCheckTimer
        interval: 2000
        repeat: true
        running: true
        onTriggered: {
            var text = backend.get_clipboard_text().trim();
            if (text.startsWith("{") && text.endsWith("}") && text.indexOf("\"folders\"") !== -1) {
                if (text !== detectedClipboardJson) {
                    detectedClipboardJson = text;
                    showClipboardBanner = true;
                }
            } else {
                showClipboardBanner = false;
            }
        }
    }

    // Dialog References
    ImportTemplateScreen {
        id: importDialog
    }

    // Confirmation Modal State
    property string projectToDelete: ""
    
    Rectangle {
        id: confirmDialog
        visible: projectToDelete !== ""
        anchors.fill: parent
        color: "#AA090D16"
        z: 99999

        // Prevent clicking through
        MouseArea {
            anchors.fill: parent
            hoverEnabled: true
        }

        Rectangle {
            width: 420
            height: 180
            color: cardSlateBg
            border.color: borderSlate
            border.width: 1.5
            radius: 10
            anchors.centerIn: parent

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 20
                spacing: 15

                Text {
                    text: (backend.appLanguage === "ar" ? "⚠️ تأكيد حذف الربط" : "⚠️ Confirm Unlinking Workspace")
                    color: errorRed
                    font.bold: true
                    font.pixelSize: 15
                }

                Text {
                    text: (backend.appLanguage === "ar" ? "هل أنت متأكد من رغبتك في إلغاء ربط المشروع '" + projectToDelete + "' من قاعدة البيانات؟\n(ملاحظة: لن يتم حذف ملفات المشروع من القرص مطلقا)" : "Are you sure you want to remove '" + projectToDelete + "' from the SQLite database?\n(Note: Your files on disk will NOT be affected)")
                    color: textSilver
                    font.pixelSize: 11
                    wrapMode: Text.Wrap
                    Layout.fillWidth: true
                }

                RowLayout {
                    Layout.fillWidth: true
                    spacing: 15
                    
                    Button {
                        id: cancelDelBtn
                        text: (backend.appLanguage === "ar" ? "تراجع" : "Cancel")
                        implicitHeight: 32
                        Layout.fillWidth: true
                        background: Rectangle { color: "transparent"; border.color: borderSlate; radius: 5 }
                        contentItem: Text { text: cancelDelBtn.text; color: textSilver; font.bold: true; horizontalAlignment: Text.AlignHCenter }
                        onClicked: projectToDelete = ""
                    }

                    Button {
                        id: confirmDelBtn
                        text: (backend.appLanguage === "ar" ? "تأكيد الحذف 🗑️" : "Confirm Delete 🗑️")
                        implicitHeight: 32
                        Layout.fillWidth: true
                        background: Rectangle { color: errorRed; radius: 5 }
                        contentItem: Text { text: confirmDelBtn.text; color: "#FFFFFF"; font.bold: true; horizontalAlignment: Text.AlignHCenter }
                        onClicked: {
                            var name = projectToDelete;
                            projectToDelete = "";
                            var success = backend.delete_project(name);
                            if (success) {
                                refreshDatabase();
                            }
                        }
                    }
                }
            }
        }
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 20
        spacing: 15

        // Header and Information
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

        // Live Clipboard Detect Banner
        Rectangle {
            id: clipDetectBanner
            Layout.fillWidth: true
            Layout.preferredHeight: 45
            color: "#1B3B22"
            border.color: successGreen
            border.width: 1
            radius: 8
            visible: showClipboardBanner

            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 15
                anchors.rightMargin: 15
                
                Text {
                    text: (backend.appLanguage === "ar" ? "📋 تم كشف قالب مشروع JSON صالح في الحافظة! هل تريد معاينته واستيراده الآن؟" : "📋 Valid JSON project template detected in clipboard! Preview and import now?")
                    color: successGreen
                    font.bold: true
                    font.pixelSize: 11
                }
                
                Spacer { Layout.fillWidth: true }

                Button {
                    id: bannerDismissBtn
                    text: (backend.appLanguage === "ar" ? "تجاهل ❌" : "Dismiss ❌")
                    implicitHeight: 26
                    background: Rectangle { color: "transparent"; radius: 4 }
                    contentItem: Text { text: bannerDismissBtn.text; color: textSilver; font.pixelSize: 10 }
                    onClicked: {
                        showClipboardBanner = false;
                        detectedClipboardJson = "dismissed";
                    }
                }

                Button {
                    id: bannerImportBtn
                    text: (backend.appLanguage === "ar" ? "معاينة واستيراد سريع 🛠️" : "Preview & Import 🛠️")
                    implicitHeight: 28
                    background: Rectangle { color: successGreen; radius: 4 }
                    contentItem: Text { text: bannerImportBtn.text; color: "#0F131D"; font.bold: true; font.pixelSize: 11 }
                    onClicked: {
                        showClipboardBanner = false;
                        importDialog.open();
                        importDialog.activeMode = "json";
                        importDialog.projectNameInput.text = "ImportedWorkspace";
                        importDialog.syncJsonToVisual();
                    }
                }
            }
        }

        // Advanced Control Actions Row
        RowLayout {
            Layout.fillWidth: true
            spacing: 12

            Button {
                id: exportActiveBtn
                text: root.getTxt("btn_export_active")
                implicitHeight: 38
                Layout.fillWidth: true
                background: Rectangle {
                    color: exportActiveBtn.pressed ? borderSlate : (exportActiveBtn.hovered ? "#212A3E" : "transparent")
                    border.color: metallicGold
                    radius: 6
                }
                contentItem: Text {
                    text: exportActiveBtn.text
                    color: metallicGold
                    font.bold: true
                    font.pixelSize: 11
                    horizontalAlignment: Text.AlignHCenter
                }
                onClicked: {
                    var activeName = projectSelector.currentText;
                    if (activeName === "Default" || activeName === "الافتراضي") {
                        backend.notificationSent.emit("تصدير القالب", "لا يمكن تصدير قالب المشروع الافتراضي.", "warning");
                        return;
                    }
                    var jsonStr = backend.export_project_to_json(activeName);
                    backend.set_clipboard_text(jsonStr);
                    backend.log_action("success", "تم تصدير قالب المشروع '" + activeName + "' للحافظة.");
                    backend.notificationSent.emit("تصدير القالب", "تم تصدير هيكل المشروع '" + activeName + "' بنجاح ونسخه للحافظة بصيغة JSON.", "success");
                }
            }

            Button {
                id: importTemplateBtn
                text: root.getTxt("btn_import_template")
                implicitHeight: 38
                Layout.fillWidth: true
                background: Rectangle {
                    color: importTemplateBtn.pressed ? "#C59231" : (importTemplateBtn.hovered ? softGold : metallicGold)
                    radius: 6
                }
                contentItem: Text {
                    text: importTemplateBtn.text
                    color: "#0F131D"
                    font.bold: true
                    font.pixelSize: 11
                    horizontalAlignment: Text.AlignHCenter
                }
                onClicked: {
                    importDialog.open();
                    importDialog.loadDefaultTemplate();
                    importDialog.activeMode = "visual";
                }
            }

            Button {
                id: demoTemplateBtn
                text: root.getTxt("btn_demo_template")
                implicitHeight: 38
                Layout.fillWidth: true
                background: Rectangle {
                    color: demoTemplateBtn.pressed ? borderSlate : (demoTemplateBtn.hovered ? "#212A3E" : "transparent")
                    border.color: borderSlate
                    radius: 6
                }
                contentItem: Text {
                    text: demoTemplateBtn.text
                    color: textSilver
                    font.pixelSize: 11
                    horizontalAlignment: Text.AlignHCenter
                }
                onClicked: {
                    var demo = {
                        "name": "AndroidComposeFullApp",
                        "path": "",
                        "folders": [
                            { "name_ar": "قاعدة البيانات والموديل", "path_en": "app/src/main/java/com/example/data", "file_types": [".kt", ".json"], "keywords": ["Room", "Dao", "Entity"] },
                            { "name_ar": "الواجهات والنظام التفاعلي", "path_en": "app/src/main/java/com/example/ui", "file_types": [".kt"], "keywords": ["Composable", "Theme", "Scaffold"] },
                            { "name_ar": "مجلد الموارد والترجمة", "path_en": "app/src/main/res/values", "file_types": [".xml"], "keywords": ["resources", "strings"] }
                        ]
                    };
                    backend.set_clipboard_text(JSON.stringify(demo, null, 4));
                    backend.notificationSent.emit("نسخ القالب", "تم نسخ كود قالب Android Jetpack Compose تجريبي إلى الحافظة بنجاح.", "success");
                }
            }
        }

        // Workspace Project List Grid/Rows
        Rectangle {
            Layout.fillWidth: true
            Layout.fillHeight: true
            color: cardSlateBg
            border.color: borderSlate
            radius: 8

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 12
                spacing: 8

                Text {
                    text: backend.appLanguage === "ar" ? "📊 قائمة مستودعات المشاريع الحالية (" + projectsModel.count + "):" : "📊 Registered Projects List (" + projectsModel.count + "):"
                    color: metallicGold
                    font.bold: true
                    font.pixelSize: 13
                }

                ScrollView {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    clip: true

                    ListView {
                        id: projectsListView
                        model: projectsModel
                        width: parent.width - 15
                        spacing: 12
                        
                        delegate: Rectangle {
                            id: projectCard
                            width: parent.width
                            height: 110
                            color: "#0F131D"
                            border.color: (model.name === projectSelector.currentText) ? metallicGold : borderSlate
                            border.width: (model.name === projectSelector.currentText) ? 1.5 : 1
                            radius: 8

                            // Make hoverable
                            property bool isHovered: false
                            MouseArea {
                                anchors.fill: parent
                                hoverEnabled: true
                                onEntered: projectCard.isHovered = true
                                onExited: projectCard.isHovered = false
                                onClicked: {
                                    // Clicking card can also activate
                                    activateProject(model.name);
                                }
                            }

                            RowLayout {
                                anchors.fill: parent
                                anchors.margins: 12
                                spacing: 15

                                // Project Icon and Core Details
                                ColumnLayout {
                                    Layout.fillWidth: true
                                    spacing: 4

                                    RowLayout {
                                        spacing: 8
                                        Text {
                                            text: (model.name === projectSelector.currentText) ? "📂" : "📁"
                                            font.pixelSize: 18
                                        }
                                        Text {
                                            text: model.name
                                            color: (model.name === projectSelector.currentText) ? metallicGold : textSilver
                                            font.bold: true
                                            font.pixelSize: 13
                                        }
                                        
                                        Rectangle {
                                            id: statusBadge
                                            width: 80
                                            height: 18
                                            color: (model.name === projectSelector.currentText) ? "#1B3B22" : "#212A3E"
                                            radius: 10
                                            border.color: (model.name === projectSelector.currentText) ? successGreen : borderSlate
                                            
                                            Text {
                                                text: (model.name === projectSelector.currentText) ? root.getTxt("active_status") : root.getTxt("inactive_status")
                                                color: (model.name === projectSelector.currentText) ? successGreen : textSilver
                                                font.pixelSize: 8
                                                anchors.centerIn: parent
                                            }
                                        }
                                    }

                                    Text {
                                        text: root.getTxt("path_lbl") + " " + model.path
                                        color: "#808A9D"
                                        font.pixelSize: 10
                                        elide: Text.ElideMiddle
                                        Layout.fillWidth: true
                                    }

                                    RowLayout {
                                        spacing: 15
                                        Text {
                                            text: root.getTxt("created_lbl") + " " + (model.created_at || "-")
                                            color: "#808A9D"
                                            font.pixelSize: 10
                                        }
                                        Text {
                                            text: getProjectStats(model.name)
                                            color: softGold
                                            font.bold: true
                                            font.pixelSize: 10
                                        }
                                    }
                                }

                                // Interactive Action Buttons Column
                                ColumnLayout {
                                    spacing: 6
                                    Layout.alignment: Qt.AlignVCenter

                                    // Activate Button
                                    Button {
                                        id: actBtn
                                        text: root.getTxt("btn_activate")
                                        implicitHeight: 25
                                        implicitWidth: 120
                                        visible: model.name !== projectSelector.currentText
                                        background: Rectangle { color: actBtn.pressed ? "#1E2A3A" : (actBtn.hovered ? "#2C3E50" : "#1A252F"); border.color: metallicGold; radius: 4 }
                                        contentItem: Text { text: actBtn.text; color: metallicGold; font.bold: true; font.pixelSize: 10; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                                        onClicked: {
                                            activateProject(model.name);
                                        }
                                    }

                                    // Export Button
                                    Button {
                                        id: expBtn
                                        text: root.getTxt("btn_export")
                                        implicitHeight: 25
                                        implicitWidth: 120
                                        background: Rectangle { color: expBtn.pressed ? "#212A3E" : (expBtn.hovered ? "#2D3E50" : "transparent"); border.color: borderSlate; radius: 4 }
                                        contentItem: Text { text: expBtn.text; color: textSilver; font.pixelSize: 10; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                                        onClicked: {
                                            if (model.name === "Default" || model.name === "الافتراضي") {
                                                backend.notificationSent.emit("تصدير القالب", "لا يمكن تصدير قالب المشروع الافتراضي.", "warning");
                                                return;
                                            }
                                            var jsonStr = backend.export_project_to_json(model.name);
                                            backend.set_clipboard_text(jsonStr);
                                            backend.log_action("success", "تم تصدير قالب المشروع '" + model.name + "' ونسخه للحافظة.");
                                            backend.notificationSent.emit("تصدير القالب", "تم نسخ كود قالب المشروع '" + model.name + "' بصيغة JSON إلى الحافظة بنجاح.", "success");
                                        }
                                    }

                                    // Delete Button
                                    Button {
                                        id: delBtn
                                        text: root.getTxt("btn_delete")
                                        implicitHeight: 25
                                        implicitWidth: 120
                                        visible: model.name !== "Default" && model.name !== "الافتراضي" && model.name !== projectSelector.currentText
                                        background: Rectangle { color: delBtn.pressed ? "#401010" : (delBtn.hovered ? "#300808" : "transparent"); border.color: errorRed; radius: 4 }
                                        contentItem: Text { text: delBtn.text; color: errorRed; font.pixelSize: 10; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                                        onClicked: {
                                            projectToDelete = model.name;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
