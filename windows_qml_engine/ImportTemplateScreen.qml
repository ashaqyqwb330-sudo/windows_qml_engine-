import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

Popup {
    id: importDialog
    width: 780
    height: 580
    modal: true
    focus: true
    closePolicy: Popup.CloseOnEscape | Popup.CloseOnPressOutside
    
    // Theme references (matching main.qml)
    readonly property color slateBg: "#0B0F19"
    readonly property color cardSlateBg: "#161D2C"
    readonly property color borderSlate: "#212A3E"
    readonly property color metallicGold: "#D4AF37"
    readonly property color softGold: "#F3E5AB"
    readonly property color textSilver: "#E2E8F0"
    readonly property color successGreen: "#10B981"
    readonly property color errorRed: "#EF4444"

    background: Rectangle {
        color: slateBg
        border.color: borderSlate
        border.width: 1.5
        radius: 12
        
        // Beautiful header bar
        Rectangle {
            id: headerBar
            width: parent.width
            height: 45
            color: cardSlateBg
            radius: 12
            anchors.top: parent.top
            
            // Mask bottom corners
            Rectangle {
                width: parent.width
                height: 10
                color: cardSlateBg
                anchors.bottom: parent.bottom
            }

            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 15
                anchors.rightMargin: 15
                
                Text {
                    text: (backend.appLanguage === "ar" ? "🛠️ محرر قوالب المشاريع المتقدم" : "🛠️ Advanced Project Template Editor")
                    color: metallicGold
                    font.bold: true
                    font.pixelSize: 14
                }
                
                Spacer { Layout.fillWidth: true }
                
                Button {
                    text: "❌"
                    flat: true
                    onClicked: importDialog.close()
                    background: null
                    contentItem: Text {
                        text: parent.text
                        color: textSilver
                        font.bold: true
                        font.pixelSize: 12
                    }
                }
            }
        }
    }

    // ListModel to store visual folder definitions
    ListModel {
        id: visualFoldersModel
    }

    // State properties
    property string activeMode: "visual" // "visual" or "json"
    property string currentJsonError: ""
    
    // Initialize with default template
    Component.onCompleted: {
        loadDefaultTemplate()
    }

    function loadDefaultTemplate() {
        var defaultJson = {
            "name": (backend.appLanguage === "ar" ? "مشروع_ذكي_جديد" : "My_Smart_Project"),
            "path": "",
            "folders": [
                {
                    "name_ar": "النماذج وقواعد البيانات",
                    "path_en": "models",
                    "file_types": ".kt, .py, .json",
                    "keywords": "class, data, Room, database"
                },
                {
                    "name_ar": "العرض والواجهات",
                    "path_en": "views",
                    "file_types": ".kt, .qml",
                    "keywords": "Composable, view, UI, Layout"
                }
            ]
        };
        
        projectNameInput.text = defaultJson.name;
        projectPathInput.text = "";
        
        visualFoldersModel.clear();
        for (var i = 0; i < defaultJson.folders.length; i++) {
            visualFoldersModel.append(defaultJson.folders[i]);
        }
        
        syncVisualToJson();
    }

    // Bidirectional sync: Visual -> JSON
    function syncVisualToJson() {
        var foldersList = [];
        for (var i = 0; i < visualFoldersModel.count; i++) {
            var item = visualFoldersModel.get(i);
            
            // Parse comma-separated string to arrays
            var typesArr = item.file_types.split(",").map(function(s) { return s.trim(); }).filter(function(s) { return s !== ""; });
            var keysArr = item.keywords.split(",").map(function(s) { return s.trim(); }).filter(function(s) { return s !== ""; });
            
            foldersList.push({
                "name_ar": item.name_ar,
                "path_en": item.path_en,
                "file_types": typesArr,
                "keywords": keysArr
            });
        }
        
        var fullTemplate = {
            "name": projectNameInput.text.trim(),
            "path": projectPathInput.text.trim(),
            "folders": foldersList
        };
        
        jsonTextArea.text = JSON.stringify(fullTemplate, null, 4);
        currentJsonError = "";
    }

    // Bidirectional sync: JSON -> Visual
    function syncJsonToVisual() {
        try {
            var parsed = JSON.parse(jsonTextArea.text);
            if (!parsed.name) {
                currentJsonError = "خطأ: يجب تحديد حقل الاسم للمشروع (name)";
                return false;
            }
            
            projectNameInput.text = parsed.name;
            projectPathInput.text = parsed.path || "";
            
            visualFoldersModel.clear();
            var folders = parsed.folders || [];
            for (var i = 0; i < folders.length; i++) {
                var f = folders[i];
                var typesStr = Array.isArray(f.file_types) ? f.file_types.join(", ") : (f.file_types || "");
                var keysStr = Array.isArray(f.keywords) ? f.keywords.join(", ") : (f.keywords || "");
                
                visualFoldersModel.append({
                    "name_ar": f.name_ar || "",
                    "path_en": f.path_en || "",
                    "file_types": typesStr,
                    "keywords": keysStr
                });
            }
            currentJsonError = "";
            return true;
        } catch (e) {
            currentJsonError = "خطأ في قراءة JSON: " + e.message;
            return false;
        }
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.topMargin: 55
        anchors.margins: 15
        spacing: 12

        // Top Controls: Project name/path & Mode Switcher
        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: 90
            color: cardSlateBg
            border.color: borderSlate
            radius: 8
            
            GridLayout {
                anchors.fill: parent
                anchors.margins: 10
                columns: 3
                rowSpacing: 8
                columnSpacing: 10

                // Project Name Input
                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 4
                    Text {
                        text: (backend.appLanguage === "ar" ? "✍️ اسم المشروع الجديد:" : "✍️ New Project Name:")
                        color: metallicGold
                        font.bold: true
                        font.pixelSize: 11
                    }
                    TextField {
                        id: projectNameInput
                        Layout.fillWidth: true
                        placeholderText: "e.g., SpaceTrackerApp"
                        color: textSilver
                        font.pixelSize: 11
                        background: Rectangle {
                            color: slateBg
                            border.color: borderSlate
                            radius: 4
                        }
                    }
                }

                // Custom Save Path Input
                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 4
                    Text {
                        text: (backend.appLanguage === "ar" ? "📂 مسار مخصص (اختياري):" : "📂 Custom Path (Optional):")
                        color: metallicGold
                        font.bold: true
                        font.pixelSize: 11
                    }
                    TextField {
                        id: projectPathInput
                        Layout.fillWidth: true
                        placeholderText: (backend.appLanguage === "ar" ? "اتركه فارغاً للحفظ في مجلد العمل" : "Leave empty to use base workspace")
                        color: textSilver
                        font.pixelSize: 11
                        background: Rectangle {
                            color: slateBg
                            border.color: borderSlate
                            radius: 4
                        }
                    }
                }

                // Mode Toggle Button
                ColumnLayout {
                    Layout.alignment: Qt.AlignBottom
                    spacing: 4
                    Button {
                        id: modeToggleBtn
                        text: activeMode === "visual" ? "🧑‍💻 " + (backend.appLanguage === "ar" ? "الوضع المتقدم JSON" : "Switch to JSON Mode") : "🎨 " + (backend.appLanguage === "ar" ? "الوضع البصري" : "Switch to Visual Editor")
                        implicitWidth: 160
                        implicitHeight: 32
                        
                        background: Rectangle {
                            color: modeToggleBtn.pressed ? borderSlate : (modeToggleBtn.hovered ? "#212A3E" : "transparent")
                            border.color: metallicGold
                            radius: 6
                        }
                        contentItem: Text {
                            text: modeToggleBtn.text
                            color: metallicGold
                            font.bold: true
                            font.pixelSize: 11
                            horizontalAlignment: Text.AlignHCenter
                            verticalAlignment: Text.AlignVCenter
                        }
                        
                        onClicked: {
                            if (activeMode === "visual") {
                                syncVisualToJson();
                                activeMode = "json";
                            } else {
                                if (syncJsonToVisual()) {
                                    activeMode = "visual";
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Workspace Panels
        StackLayout {
            id: editorStack
            Layout.fillWidth: true
            Layout.fillHeight: true
            currentIndex: activeMode === "visual" ? 0 : 1

            // TAB 0: VISUAL FOLDER EDITOR
            Rectangle {
                color: "transparent"
                border.color: borderSlate
                radius: 8
                
                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 10
                    spacing: 10

                    RowLayout {
                        Layout.fillWidth: true
                        Text {
                            text: (backend.appLanguage === "ar" ? "📂 هيكل شجرة مجلدات المشروع:" : "📂 Project Directory Tree Structure:")
                            color: textSilver
                            font.bold: true
                            font.pixelSize: 12
                        }
                        Spacer { Layout.fillWidth: true }
                        
                        Button {
                            id: addFolderBtn
                            text: (backend.appLanguage === "ar" ? "➕ إضافة مجلد جديد" : "➕ Add New Folder")
                            implicitWidth: 130
                            implicitHeight: 28
                            
                            background: Rectangle {
                                color: "#0F321E"
                                border.color: successGreen
                                radius: 4
                            }
                            contentItem: Text {
                                text: addFolderBtn.text
                                color: successGreen
                                font.bold: true
                                font.pixelSize: 11
                                horizontalAlignment: Text.AlignHCenter
                                verticalAlignment: Text.AlignVCenter
                            }
                            onClicked: {
                                visualFoldersModel.append({
                                    "name_ar": (backend.appLanguage === "ar" ? "مجلد جديد" : "New Folder"),
                                    "path_en": "new_folder",
                                    "file_types": ".kt, .py",
                                    "keywords": "class"
                                });
                            }
                        }
                    }

                    // Visual List of Folders
                    ScrollView {
                        Layout.fillWidth: true
                        Layout.fillHeight: true
                        clip: true

                        ListView {
                            id: folderListView
                            model: visualFoldersModel
                            width: parent.width - 15
                            spacing: 8
                            delegate: Rectangle {
                                width: parent.width
                                height: 95
                                color: cardSlateBg
                                border.color: borderSlate
                                radius: 6

                                RowLayout {
                                    anchors.fill: parent
                                    anchors.margins: 8
                                    spacing: 10

                                    // Form Columns
                                    GridLayout {
                                        Layout.fillWidth: true
                                        columns: 4
                                        rowSpacing: 4
                                        columnSpacing: 8

                                        // Arabic Label
                                        ColumnLayout {
                                            Layout.fillWidth: true
                                            Text { text: (backend.appLanguage === "ar" ? "الاسم العربي:" : "Label (AR):"); color: metallicGold; font.pixelSize: 9 }
                                            TextField {
                                                text: model.name_ar
                                                font.pixelSize: 10
                                                Layout.fillWidth: true
                                                background: Rectangle { color: slateBg; radius: 4; border.color: borderSlate }
                                                onTextChanged: visualFoldersModel.setProperty(index, "name_ar", text)
                                            }
                                        }

                                        // English Path
                                        ColumnLayout {
                                            Layout.fillWidth: true
                                            Text { text: (backend.appLanguage === "ar" ? "مسار المجلد (EN):" : "Sub-path (EN):"); color: metallicGold; font.pixelSize: 9 }
                                            TextField {
                                                text: model.path_en
                                                font.pixelSize: 10
                                                Layout.fillWidth: true
                                                background: Rectangle { color: slateBg; radius: 4; border.color: borderSlate }
                                                onTextChanged: visualFoldersModel.setProperty(index, "path_en", text)
                                            }
                                        }

                                        // File Types
                                        ColumnLayout {
                                            Layout.fillWidth: true
                                            Text { text: (backend.appLanguage === "ar" ? "الامتدادات (مثال: .kt, .py):" : "File types (.kt, .py):"); color: metallicGold; font.pixelSize: 9 }
                                            TextField {
                                                text: model.file_types
                                                font.pixelSize: 10
                                                Layout.fillWidth: true
                                                background: Rectangle { color: slateBg; radius: 4; border.color: borderSlate }
                                                onTextChanged: visualFoldersModel.setProperty(index, "file_types", text)
                                            }
                                        }

                                        // Keywords
                                        ColumnLayout {
                                            Layout.fillWidth: true
                                            Text { text: (backend.appLanguage === "ar" ? "الكلمات المفتاحية:" : "Keywords:"); color: metallicGold; font.pixelSize: 9 }
                                            TextField {
                                                text: model.keywords
                                                font.pixelSize: 10
                                                Layout.fillWidth: true
                                                background: Rectangle { color: slateBg; radius: 4; border.color: borderSlate }
                                                onTextChanged: visualFoldersModel.setProperty(index, "keywords", text)
                                            }
                                        }
                                    }

                                    // Action buttons for folders
                                    Button {
                                        id: delFolderBtn
                                        text: "🗑️"
                                        implicitWidth: 32
                                        implicitHeight: 32
                                        background: Rectangle {
                                            color: delFolderBtn.pressed ? "#401010" : (delFolderBtn.hovered ? "#300808" : "transparent")
                                            border.color: errorRed
                                            radius: 4
                                        }
                                        contentItem: Text {
                                            text: parent.text
                                            font.pixelSize: 12
                                            horizontalAlignment: Text.AlignHCenter
                                            verticalAlignment: Text.AlignVCenter
                                        }
                                        onClicked: {
                                            visualFoldersModel.remove(index);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // TAB 1: RAW JSON SYNTAX EDITOR
            Rectangle {
                color: "transparent"
                border.color: borderSlate
                radius: 8

                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 10
                    spacing: 8

                    Text {
                        text: (backend.appLanguage === "ar" ? "🧑‍💻 تحرير قالب الهيكل بصيغة JSON مباشرة:" : "🧑‍💻 Edit Project Template Schema directly in JSON:")
                        color: textSilver
                        font.bold: true
                        font.pixelSize: 12
                    }

                    // JSON Live syntax error banner
                    Rectangle {
                        Layout.fillWidth: true
                        Layout.preferredHeight: 30
                        color: "#2C1013"
                        border.color: errorRed
                        radius: 4
                        visible: currentJsonError !== ""
                        RowLayout {
                            anchors.fill: parent
                            anchors.margins: 8
                            Text {
                                text: currentJsonError
                                color: errorRed
                                font.pixelSize: 10
                                wrapMode: Text.Wrap
                            }
                        }
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

                            TextArea {
                                id: jsonTextArea
                                color: textSilver
                                font.family: "Consolas"
                                font.pixelSize: 11
                                selectByMouse: true
                                wrapMode: TextEdit.Wrap
                                background: null
                                onTextChanged: {
                                    // Soft validate
                                    try {
                                        JSON.parse(text);
                                        currentJsonError = "";
                                    } catch (e) {
                                        currentJsonError = "خطأ في الصياغة: " + e.message;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Action Buttons: Load Demo, Cancel, and Build & Activate
        RowLayout {
            Layout.fillWidth: true
            spacing: 15

            Button {
                id: demoBtn
                text: (backend.appLanguage === "ar" ? "نسخ قالب أندرويد نموذجي 📱" : "Use Android Demo Template 📱")
                implicitHeight: 38
                Layout.preferredWidth: 200
                background: Rectangle {
                    color: "transparent"
                    border.color: metallicGold
                    radius: 6
                }
                contentItem: Text {
                    text: demoBtn.text
                    color: metallicGold
                    font.bold: true
                    font.pixelSize: 11
                    horizontalAlignment: Text.AlignHCenter
                    verticalAlignment: Text.AlignVCenter
                }
                onClicked: {
                    var androidDemo = {
                        "name": "AndroidJetpackComposeApp",
                        "path": "",
                        "folders": [
                            { "name_ar": "التحكم والموديل والبيانات", "path_en": "app/src/main/java/com/example/data", "file_types": ".kt, .json", "keywords": "Room, Entity, Dao, Repository, ViewModel" },
                            { "name_ar": "واجهات وتصميم الكومبوز", "path_en": "app/src/main/java/com/example/ui", "file_types": ".kt", "keywords": "Composable, Theme, Scaffold, Button" },
                            { "name_ar": "الموارد وملفات المصادر", "path_en": "app/src/main/res/values", "file_types": ".xml", "keywords": "resources, string, color" }
                        ]
                    };
                    
                    projectNameInput.text = androidDemo.name;
                    projectPathInput.text = "";
                    visualFoldersModel.clear();
                    for (var i = 0; i < androidDemo.folders.length; i++) {
                        visualFoldersModel.append(androidDemo.folders[i]);
                    }
                    syncVisualToJson();
                }
            }

            Spacer { Layout.fillWidth: true }

            Button {
                id: cancelBtn
                text: (backend.appLanguage === "ar" ? "إلغاء 🗑️" : "Cancel 🗑️")
                implicitHeight: 38
                Layout.preferredWidth: 100
                background: Rectangle {
                    color: "transparent"
                    border.color: borderSlate
                    radius: 6
                }
                contentItem: Text {
                    text: cancelBtn.text
                    color: textSilver
                    font.pixelSize: 11
                    horizontalAlignment: Text.AlignHCenter
                    verticalAlignment: Text.AlignVCenter
                }
                onClicked: {
                    importDialog.close()
                }
            }

            Button {
                id: submitBtn
                text: (backend.appLanguage === "ar" ? "بناء وتفعيل المشروع الجديد ⚡" : "Build & Activate New Project ⚡")
                implicitHeight: 38
                Layout.preferredWidth: 260
                
                background: Rectangle {
                    color: submitBtn.pressed ? "#C59231" : (submitBtn.hovered ? softGold : metallicGold)
                    radius: 6
                }
                contentItem: Text {
                    text: submitBtn.text
                    color: "#0F131D"
                    font.bold: true
                    font.pixelSize: 12
                    horizontalAlignment: Text.AlignHCenter
                    verticalAlignment: Text.AlignVCenter
                }
                onClicked: {
                    if (projectNameInput.text.trim() === "") {
                        backend.log_action("error", "فشل البناء: يجب تزويد اسم للمشروع");
                        backend.notificationSent.emit("خطأ البناء", "يرجى كتابة اسم للمشروع أولاً.", "warning");
                        return;
                    }
                    
                    if (activeMode === "visual") {
                        syncVisualToJson();
                    } else {
                        if (!syncJsonToVisual()) {
                            backend.notificationSent.emit("خطأ JSON", "يرجى تصحيح أخطاء JSON أولاً.", "warning");
                            return;
                        }
                    }
                    
                    var resultStr = backend.add_project_from_json(jsonTextArea.text);
                    var result = JSON.parse(resultStr);
                    if (result.success) {
                        // Activate it instantly in UI!
                        refreshDatabase();
                        activateProject(projectNameInput.text.trim());
                        importDialog.close();
                    } else {
                        backend.notificationSent.emit("خطأ الاستيراد", result.message, "warning");
                    }
                }
            }
        }
    }
}
