import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import QtQuick.Dialogs

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
    readonly property color textGray: "#808A9D"
    readonly property color successGreen: "#10B981"
    readonly property color errorRed: "#EF4444"
    readonly property color purpleAccent: "#8B5CF6"

    // Localization helper
    function getTxt(key) {
        var translations = {
            "title": { "ar": "🌴 لوحة مستكشف التقارير الشجرية التفاعلية Pro", "en": "🌴 Interactive TreeDoc Pro Dashboard" },
            "desc": { "ar": "قم بتحليل ومسح أي مجلد على القرص ضوئياً بلمسة واحدة لإنتاج مخطط تفاعلي كامل وتوليد تقارير برمجية مهيكلة.", "en": "Analyze and scan any folder on disk to generate interactive visual structures, filter files, and export professional reports." },
            "target_folder": { "ar": "المجلد المستهدف للمسح الشجري:", "en": "Target Directory to Scan:" },
            "btn_scan": { "ar": "إطلاق المسح والتحليل الشجري ⚡", "en": "Launch Tree Analysis & Scan ⚡" },
            "stats_folders": { "ar": "إجمالي المجلدات", "en": "Total Folders" },
            "stats_files": { "ar": "إجمالي الملفات", "en": "Total Files" },
            "stats_size": { "ar": "الحجم الكلي", "en": "Total Size" },
            "stats_time": { "ar": "زمن المعالجة", "en": "Processing Time" },
            "tab_tree": { "ar": "🌴 شجرة مستكشف الملفات", "en": "🌴 Interactive Tree" },
            "tab_chart": { "ar": "📊 توزيع ونسب أنواع الملفات", "en": "📊 File Distribution" },
            "tab_reports": { "ar": "📥 تصدير التقارير المهيكلة", "en": "📥 Export Reports" },
            "search_placeholder": { "ar": "بحث سريع في الملفات والمجلدات...", "en": "Quick filter files or folders..." },
            "details_header": { "ar": "ℹ️ تفاصيل ومواصفات العنصر المحدد:", "en": "ℹ️ Selected Item Specifications:" },
            "details_none": { "ar": "اضغط على أي ملف في الشجرة أعلاه لعرض خصائصه التفصيلية ومعاينته هنا.", "en": "Select any file in the tree above to view details, copy path, or launch preview." },
            "name": { "ar": "الاسم:", "en": "Name:" },
            "path": { "ar": "المسار الكامل:", "en": "Full Path:" },
            "size": { "ar": "الحجم:", "en": "Size:" },
            "mtime": { "ar": "آخر تعديل:", "en": "Last Modified:" },
            "btn_preview": { "ar": "معاينة الملف 🔍", "en": "Preview File 🔍" },
            "btn_copy_path": { "ar": "نسخ مسار الملف 📋", "en": "Copy File Path 📋" },
            "export_options": { "ar": "خيارات تصدير التقارير الشجرية المتطورة:", "en": "Advanced Tree Report Exporters:" },
            "export_desc": { "ar": "اختر صيغة التقرير المناسبة لك ليقوم النظام ببناء ملف معتمد متوافق مع معايير المطورين وحفظه تلقائياً في دليل الحفظ.", "en": "Export tree analysis as standardized developer documents instantly, saved under TreeDocs backups folder." },
            "status": { "ar": "الحالة:", "en": "Status:" },
            "status_idle": { "ar": "بانتظار المسح الشجري...", "en": "Waiting for folder scan..." },
            "status_scanned": { "ar": "تم المسح والتحليل بنجاح!", "en": "Scan and analysis completed successfully!" },
            "file_type_code": { "ar": "ملفات برمجية", "en": "Source Code" },
            "file_type_text": { "ar": "مستندات نصوص", "en": "Text Docs" },
            "file_type_media": { "ar": "ملفات وسائط", "en": "Media Files" },
            "file_type_other": { "ar": "أخرى", "en": "Other Files" },
            "chart_legend": { "ar": "وسيلة إيضاح توزيع الملفات على القرص:", "en": "Disk Storage Distribution Legend:" },
            "btn_expand_all": { "ar": "توسيع الكل 📂", "en": "Expand All 📂" },
            "btn_collapse_all": { "ar": "طي الكل 📁", "en": "Collapse All 📁" },
            "preview_title": { "ar": "🔍 نافذة معاينة المحتوى الذكية", "en": "🔍 Smart Content Previewer" },
            "preview_rich": { "ar": "معاينة منسقة (Rich)", "en": "Rich Formatting" },
            "preview_raw": { "ar": "كود مصدر خام", "en": "Raw Code View" }
        };
        var isAr = (backend.appLanguage === "ar");
        return translations[key] ? (isAr ? translations[key]["ar"] : translations[key]["en"]) : "";
    }

    // Active States
    property string activeTab: "tree" // "tree", "chart", "reports"
    property string currentFolderPath: ""
    property var fullScanData: null
    property var allTreeItems: []       // Flat list of all items in the scanned tree
    property var filteredTreeItems: []  // List of items filtered by search input
    property string searchQuery: ""
    
    // Selection State
    property var selectedItem: null
    property string previewContent: ""
    property bool previewIsRich: false

    // Function to recurse and build flat tree items
    function buildFlatTree(node, depth, parentPath) {
        var list = [];
        var item = {
            "name": node.name,
            "path": node.path,
            "type": node.type,
            "size_bytes": node.size_bytes,
            "size_formatted": node.size_formatted,
            "mtime": node.mtime,
            "extension": node.extension || "",
            "depth": depth,
            "parentPath": parentPath,
            "expanded": (depth < 1), // Root is expanded by default
            "visible": (depth <= 1)   // Depth 0 and 1 visible by default
        };
        list.push(item);

        if (node.children) {
            for (var i = 0; i < node.children.length; i++) {
                var childList = buildFlatTree(node.children[i], depth + 1, node.path);
                list = list.concat(childList);
            }
        }
        return list;
    }

    // Process scanned JSON data
    function handleScanResult(jsonStr) {
        try {
            var data = JSON.parse(jsonStr);
            if (data.success) {
                fullScanData = data;
                
                // Stats
                statsFoldersText.text = data.stats.total_folders.toString();
                statsFilesText.text = data.stats.total_files.toString();
                statsSizeText.text = data.stats.total_size_formatted;
                statsTimeText.text = data.stats.scan_time_ms.toString() + " ms";
                
                // Tree Flattening
                allTreeItems = buildFlatTree(data.tree, 0, "");
                applyFilterAndVisibility();
                
                // Repaint Canvas
                chartCanvas.requestPaint();
            } else {
                backend.notificationSent.emit("فشل المسح", data.message, "warning");
            }
        } catch(e) {
            backend.notificationSent.emit("فشل التحليل", "حدث خطأ في قراءة البيانات: " + e.message, "warning");
        }
    }

    // Refresh display based on expanded states & search query
    function applyFilterAndVisibility() {
        var query = searchQuery.trim().toLowerCase();
        var list = [];

        // Determine if an item is visible based on parent expansion
        // We can pre-compute parent expanded state maps to avoid expensive searches
        var expandedMap = {};
        for (var i = 0; i < allTreeItems.length; i++) {
            if (allTreeItems[i].type === "directory") {
                expandedMap[allTreeItems[i].path] = allTreeItems[i].expanded;
            }
        }

        function isParentChainExpanded(item) {
            var parent = item.parentPath;
            while (parent !== "") {
                if (expandedMap[parent] === false) {
                    return false;
                }
                // Find parent of parent
                var found = false;
                for (var j = 0; j < allTreeItems.length; j++) {
                    if (allTreeItems[j].path === parent) {
                        parent = allTreeItems[j].parentPath;
                        found = true;
                        break;
                    }
                }
                if (!found) break;
            }
            return true;
        }

        for (var k = 0; k < allTreeItems.length; k++) {
            var item = allTreeItems[k];
            var matchesQuery = (query === "" || item.name.toLowerCase().indexOf(query) !== -1 || item.extension.toLowerCase().indexOf(query) !== -1);
            
            if (query === "") {
                // If no query, standard expansion-based visibility rules apply
                if (item.depth === 0 || isParentChainExpanded(item)) {
                    list.push(item);
                }
            } else {
                // If there's a search query, show matches and their parents
                if (matchesQuery) {
                    list.push(item);
                }
            }
        }
        filteredTreeItems = list;
        treeListView.model = filteredTreeItems;
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 20
        spacing: 12

        // Top Header
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
        }

        // Folder Scan Bar Card
        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: 65
            color: cardSlateBg
            border.color: borderSlate
            radius: 8

            RowLayout {
                anchors.fill: parent
                anchors.margins: 12
                spacing: 12

                Text {
                    text: root.getTxt("target_folder")
                    color: textSilver
                    font.bold: true
                    font.pixelSize: 11
                }

                Rectangle {
                    Layout.fillWidth: true
                    height: 34
                    color: slateBg
                    border.color: borderSlate
                    radius: 4

                    RowLayout {
                        anchors.fill: parent
                        anchors.margins: 2
                        spacing: 8

                        TextField {
                            id: folderPathField
                            Layout.fillWidth: true
                            color: textSilver
                            font.pixelSize: 11
                            placeholderText: backend.appLanguage === "ar" ? "حدد المجلد أو اكتب مساره المطلق هنا..." : "Select folder or type absolute path..."
                            background: null
                            text: currentFolderPath
                            onTextChanged: currentFolderPath = text
                        }

                        Button {
                            text: "📁"
                            implicitWidth: 32
                            implicitHeight: 30
                            background: Rectangle {
                                color: borderSlate
                                radius: 3
                            }
                            onClicked: treeFolderDialog.open()
                        }
                    }
                }

                Button {
                    id: scanBtn
                    text: root.getTxt("btn_scan")
                    implicitHeight: 34
                    enabled: currentFolderPath !== ""

                    background: Rectangle {
                        color: scanBtn.pressed ? "#C59231" : (scanBtn.hovered ? softGold : metallicGold)
                        radius: 5
                    }
                    contentItem: Text {
                        text: scanBtn.text
                        color: "#0B0F19"
                        font.bold: true
                        font.pixelSize: 11
                        horizontalAlignment: Text.AlignHCenter
                        verticalAlignment: Text.AlignVCenter
                        leftPadding: 15
                        rightPadding: 15
                    }

                    onClicked: {
                        var resStr = backend.get_tree_data_json(currentFolderPath);
                        handleScanResult(resStr);
                    }
                }
            }
        }

        // Stats Cards Grid
        RowLayout {
            Layout.fillWidth: true
            spacing: 12

            // Card 1: Folders
            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: 60
                color: cardSlateBg
                border.color: borderSlate
                radius: 6
                Column {
                    anchors.centerIn: parent
                    spacing: 4
                    Text { text: root.getTxt("stats_folders"); color: textGray; font.pixelSize: 10; anchors.horizontalCenter: parent.horizontalCenter }
                    Text { id: statsFoldersText; text: "0"; color: metallicGold; font.bold: true; font.pixelSize: 15; anchors.horizontalCenter: parent.horizontalCenter }
                }
            }

            // Card 2: Files
            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: 60
                color: cardSlateBg
                border.color: borderSlate
                radius: 6
                Column {
                    anchors.centerIn: parent
                    spacing: 4
                    Text { text: root.getTxt("stats_files"); color: textGray; font.pixelSize: 10; anchors.horizontalCenter: parent.horizontalCenter }
                    Text { id: statsFilesText; text: "0"; color: textSilver; font.bold: true; font.pixelSize: 15; anchors.horizontalCenter: parent.horizontalCenter }
                }
            }

            // Card 3: Size
            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: 60
                color: cardSlateBg
                border.color: borderSlate
                radius: 6
                Column {
                    anchors.centerIn: parent
                    spacing: 4
                    Text { text: root.getTxt("stats_size"); color: textGray; font.pixelSize: 10; anchors.horizontalCenter: parent.horizontalCenter }
                    Text { id: statsSizeText; text: "0 B"; color: successGreen; font.bold: true; font.pixelSize: 15; anchors.horizontalCenter: parent.horizontalCenter }
                }
            }

            // Card 4: Scan Time
            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: 60
                color: cardSlateBg
                border.color: borderSlate
                radius: 6
                Column {
                    anchors.centerIn: parent
                    spacing: 4
                    Text { text: root.getTxt("stats_time"); color: textGray; font.pixelSize: 10; anchors.horizontalCenter: parent.horizontalCenter }
                    Text { id: statsTimeText; text: "0 ms"; color: softGold; font.bold: true; font.pixelSize: 15; anchors.horizontalCenter: parent.horizontalCenter }
                }
            }
        }

        // Tab selection row
        RowLayout {
            Layout.fillWidth: true
            spacing: 8

            Button {
                id: tabTreeBtn
                text: root.getTxt("tab_tree")
                Layout.fillWidth: true
                implicitHeight: 32
                background: Rectangle {
                    color: root.activeTab === "tree" ? borderSlate : cardSlateBg
                    border.color: root.activeTab === "tree" ? metallicGold : borderSlate
                    radius: 4
                }
                contentItem: Text {
                    text: tabTreeBtn.text
                    color: root.activeTab === "tree" ? metallicGold : textSilver
                    font.bold: true
                    font.pixelSize: 11
                    horizontalAlignment: Text.AlignHCenter
                    verticalAlignment: Text.AlignVCenter
                }
                onClicked: root.activeTab = "tree"
            }

            Button {
                id: tabChartBtn
                text: root.getTxt("tab_chart")
                Layout.fillWidth: true
                implicitHeight: 32
                background: Rectangle {
                    color: root.activeTab === "chart" ? borderSlate : cardSlateBg
                    border.color: root.activeTab === "chart" ? metallicGold : borderSlate
                    radius: 4
                }
                contentItem: Text {
                    text: tabChartBtn.text
                    color: root.activeTab === "chart" ? metallicGold : textSilver
                    font.bold: true
                    font.pixelSize: 11
                    horizontalAlignment: Text.AlignHCenter
                    verticalAlignment: Text.AlignVCenter
                }
                onClicked: {
                    root.activeTab = "chart"
                    chartCanvas.requestPaint();
                }
            }

            Button {
                id: tabReportsBtn
                text: root.getTxt("tab_reports")
                Layout.fillWidth: true
                implicitHeight: 32
                background: Rectangle {
                    color: root.activeTab === "reports" ? borderSlate : cardSlateBg
                    border.color: root.activeTab === "reports" ? metallicGold : borderSlate
                    radius: 4
                }
                contentItem: Text {
                    text: tabReportsBtn.text
                    color: root.activeTab === "reports" ? metallicGold : textSilver
                    font.bold: true
                    font.pixelSize: 11
                    horizontalAlignment: Text.AlignHCenter
                    verticalAlignment: Text.AlignVCenter
                }
                onClicked: root.activeTab = "reports"
            }
        }

        // Dynamic Main Panel
        Rectangle {
            Layout.fillWidth: true
            Layout.fillHeight: true
            color: cardSlateBg
            border.color: borderSlate
            radius: 8

            // ============================================
            // TAB 1: Tree Explorer (Hierarchy List)
            // ============================================
            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 10
                spacing: 8
                visible: root.activeTab === "tree"

                // Controls: Search & Expand/Collapse All
                RowLayout {
                    Layout.fillWidth: true
                    spacing: 10

                    Rectangle {
                        Layout.fillWidth: true
                        height: 30
                        color: slateBg
                        border.color: borderSlate
                        radius: 4
                        RowLayout {
                            anchors.fill: parent
                            anchors.margins: 2
                            Text { text: "  🔍 "; color: textGray; font.pixelSize: 11 }
                            TextField {
                                id: searchInput
                                Layout.fillWidth: true
                                color: textSilver
                                font.pixelSize: 11
                                placeholderText: root.getTxt("search_placeholder")
                                background: null
                                onTextChanged: {
                                    root.searchQuery = text;
                                    root.applyFilterAndVisibility();
                                }
                            }
                        }
                    }

                    Button {
                        text: root.getTxt("btn_expand_all")
                        implicitHeight: 28
                        background: Rectangle { color: "transparent"; border.color: borderSlate; radius: 4 }
                        contentItem: Text { text: parent.text; color: textSilver; font.pixelSize: 10; leftPadding: 8; rightPadding: 8 }
                        onClicked: {
                            for (var i = 0; i < allTreeItems.length; i++) {
                                allTreeItems[i].expanded = true;
                            }
                            applyFilterAndVisibility();
                        }
                    }

                    Button {
                        text: root.getTxt("btn_collapse_all")
                        implicitHeight: 28
                        background: Rectangle { color: "transparent"; border.color: borderSlate; radius: 4 }
                        contentItem: Text { text: parent.text; color: textSilver; font.pixelSize: 10; leftPadding: 8; rightPadding: 8 }
                        onClicked: {
                            for (var i = 0; i < allTreeItems.length; i++) {
                                allTreeItems[i].expanded = false;
                            }
                            applyFilterAndVisibility();
                        }
                    }
                }

                // Scrollable List
                ScrollView {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    clip: true

                    ListView {
                        id: treeListView
                        anchors.fill: parent
                        spacing: 2
                        delegate: Rectangle {
                            width: treeListView.width
                            height: 28
                            color: root.selectedItem && root.selectedItem.path === modelData.path ? borderSlate : "transparent"
                            radius: 4

                            RowLayout {
                                anchors.fill: parent
                                anchors.margins: 4
                                spacing: 6

                                // Indentation Space
                                Item {
                                    width: modelData.depth * 18
                                    height: 1
                                }

                                // Expand/Collapse Arrow (Directories Only)
                                Text {
                                    text: modelData.type === "directory" ? (modelData.expanded ? "▼" : "▶") : ""
                                    color: metallicGold
                                    font.bold: true
                                    font.pixelSize: 9
                                    visible: modelData.type === "directory"
                                    implicitWidth: 12
                                    MouseArea {
                                        anchors.fill: parent
                                        onClicked: {
                                            modelData.expanded = !modelData.expanded;
                                            root.applyFilterAndVisibility();
                                        }
                                    }
                                }

                                // Icon
                                Text {
                                    text: modelData.type === "directory" ? "📁" : "📄"
                                    font.pixelSize: 12
                                }

                                // Name & Size Badge
                                Text {
                                    text: modelData.name
                                    color: modelData.type === "directory" ? softGold : textSilver
                                    font.bold: modelData.type === "directory"
                                    font.pixelSize: 11
                                    Layout.fillWidth: true
                                }

                                Text {
                                    text: modelData.size_formatted
                                    color: successGreen
                                    font.pixelSize: 9
                                    rightPadding: 10
                                }
                            }

                            MouseArea {
                                anchors.fill: parent
                                onClicked: {
                                    root.selectedItem = modelData;
                                    if (modelData.type === "directory") {
                                        modelData.expanded = !modelData.expanded;
                                        root.applyFilterAndVisibility();
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ============================================
            // TAB 2: Visual Donut Chart
            // ============================================
            RowLayout {
                anchors.fill: parent
                anchors.margins: 15
                spacing: 20
                visible: root.activeTab === "chart"

                // Donut Canvas
                Rectangle {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    Layout.preferredWidth: 1
                    color: "transparent"

                    Canvas {
                        id: chartCanvas
                        anchors.fill: parent
                        onPaint: {
                            var ctx = getContext("2d");
                            ctx.reset();
                            
                            var cx = width / 2;
                            var cy = height / 2;
                            var radius = Math.min(width, height) / 2 - 20;
                            
                            if (radius <= 0) return;
                            
                            if (!root.fullScanData) {
                                // Empty state
                                ctx.beginPath();
                                ctx.arc(cx, cy, radius, 0, 2 * Math.PI);
                                ctx.strokeStyle = "#212A3E";
                                ctx.lineWidth = 4;
                                ctx.stroke();
                                return;
                            }
                            
                            var chartData = root.fullScanData.chart;
                            var total = 0;
                            for (var cat in chartData) {
                                total += chartData[cat].count;
                            }
                            
                            if (total === 0) {
                                ctx.beginPath();
                                ctx.arc(cx, cy, radius, 0, 2 * Math.PI);
                                ctx.strokeStyle = "#212A3E";
                                ctx.lineWidth = 4;
                                ctx.stroke();
                                return;
                            }
                            
                            var startAngle = 0;
                            var colors = {
                                "Code": "#D4AF37",
                                "Text": "#E2E8F0",
                                "Media": "#10B981",
                                "Other": "#8B5CF6"
                            };
                            
                            for (var ckey in colors) {
                                var val = chartData[ckey] ? chartData[ckey].count : 0;
                                if (val === 0) continue;
                                
                                var sliceAngle = (val / total) * 2 * Math.PI;
                                ctx.beginPath();
                                ctx.moveTo(cx, cy);
                                ctx.arc(cx, cy, radius, startAngle, startAngle + sliceAngle);
                                ctx.closePath();
                                
                                ctx.fillStyle = colors[ckey];
                                ctx.fill();
                                
                                startAngle += sliceAngle;
                            }
                            
                            // Center circle for Donut look
                            ctx.beginPath();
                            ctx.arc(cx, cy, radius * 0.5, 0, 2 * Math.PI);
                            ctx.fillStyle = "#161D2C"; // matching cardSlateBg
                            ctx.fill();
                        }
                    }
                }

                // Legend
                ColumnLayout {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    Layout.preferredWidth: 1
                    spacing: 12

                    Text {
                        text: root.getTxt("chart_legend")
                        color: metallicGold
                        font.bold: true
                        font.pixelSize: 13
                    }

                    Column {
                        spacing: 8
                        Layout.fillWidth: true

                        // Code
                        RowLayout {
                            spacing: 8
                            Rectangle { width: 12; height: 12; color: "#D4AF37"; radius: 2 }
                            Text {
                                text: root.getTxt("file_type_code") + " (Code):"
                                color: textSilver
                                font.pixelSize: 11
                            }
                            Spacer { Layout.fillWidth: true }
                            Text {
                                text: root.fullScanData ? root.fullScanData.chart.Code.count.toString() + " ملفات (" + root.fullScanData.chart.Code.size_formatted + ")" : "0"
                                color: softGold
                                font.bold: true
                                font.pixelSize: 11
                            }
                        }

                        // Text
                        RowLayout {
                            spacing: 8
                            Rectangle { width: 12; height: 12; color: "#E2E8F0"; radius: 2 }
                            Text {
                                text: root.getTxt("file_type_text") + " (Text):"
                                color: textSilver
                                font.pixelSize: 11
                            }
                            Spacer { Layout.fillWidth: true }
                            Text {
                                text: root.fullScanData ? root.fullScanData.chart.Text.count.toString() + " ملفات (" + root.fullScanData.chart.Text.size_formatted + ")" : "0"
                                color: textSilver
                                font.bold: true
                                font.pixelSize: 11
                            }
                        }

                        // Media
                        RowLayout {
                            spacing: 8
                            Rectangle { width: 12; height: 12; color: "#10B981"; radius: 2 }
                            Text {
                                text: root.getTxt("file_type_media") + " (Media):"
                                color: textSilver
                                font.pixelSize: 11
                            }
                            Spacer { Layout.fillWidth: true }
                            Text {
                                text: root.fullScanData ? root.fullScanData.chart.Media.count.toString() + " ملفات (" + root.fullScanData.chart.Media.size_formatted + ")" : "0"
                                color: successGreen
                                font.bold: true
                                font.pixelSize: 11
                            }
                        }

                        // Other
                        RowLayout {
                            spacing: 8
                            Rectangle { width: 12; height: 12; color: "#8B5CF6"; radius: 2 }
                            Text {
                                text: root.getTxt("file_type_other") + " (Other):"
                                color: textSilver
                                font.pixelSize: 11
                            }
                            Spacer { Layout.fillWidth: true }
                            Text {
                                text: root.fullScanData ? root.fullScanData.chart.Other.count.toString() + " ملفات (" + root.fullScanData.chart.Other.size_formatted + ")" : "0"
                                color: purpleAccent
                                font.bold: true
                                font.pixelSize: 11
                            }
                        }
                    }

                    Spacer { Layout.fillHeight: true }
                }
            }

            // ============================================
            // TAB 3: Export Structured Reports
            // ============================================
            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 15
                spacing: 12
                visible: root.activeTab === "reports"

                Text {
                    text: root.getTxt("export_options")
                    color: metallicGold
                    font.bold: true
                    font.pixelSize: 13
                }

                Text {
                    text: root.getTxt("export_desc")
                    color: textSilver
                    font.pixelSize: 11
                    wrapMode: Text.Wrap
                    Layout.fillWidth: true
                }

                GridLayout {
                    columns: 2
                    rowSpacing: 10
                    columnSpacing: 15
                    Layout.fillWidth: true

                    // Export HTML Button
                    Button {
                        id: expHtmlBtn
                        Layout.fillWidth: true
                        implicitHeight: 36
                        text: "🌐 تصدير كصفحة إنترنت تفاعلية (HTML)"
                        background: Rectangle { color: "transparent"; border.color: metallicGold; radius: 5 }
                        contentItem: Text { text: expHtmlBtn.text; color: metallicGold; font.bold: true; font.pixelSize: 11; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                        onClicked: {
                            if (currentFolderPath) {
                                backend.generate_treedoc(currentFolderPath, "html");
                            }
                        }
                    }

                    // Export JSON Button
                    Button {
                        id: expJsonBtn
                        Layout.fillWidth: true
                        implicitHeight: 36
                        text: "⚙️ تصدير كتقرير مهيكل للمبرمجين (JSON)"
                        background: Rectangle { color: "transparent"; border.color: textSilver; radius: 5 }
                        contentItem: Text { text: expJsonBtn.text; color: textSilver; font.bold: true; font.pixelSize: 11; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                        onClicked: {
                            if (currentFolderPath) {
                                backend.generate_treedoc(currentFolderPath, "json");
                            }
                        }
                    }

                    // Export PDF Button
                    Button {
                        id: expPdfBtn
                        Layout.fillWidth: true
                        implicitHeight: 36
                        text: "📄 تصدير كتقرير جاهز للطباعة (PDF)"
                        background: Rectangle { color: "transparent"; border.color: successGreen; radius: 5 }
                        contentItem: Text { text: expPdfBtn.text; color: successGreen; font.bold: true; font.pixelSize: 11; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                        onClicked: {
                            if (currentFolderPath) {
                                backend.generate_treedoc(currentFolderPath, "pdf");
                            }
                        }
                    }

                    // Export CSV Button
                    Button {
                        id: expCsvBtn
                        Layout.fillWidth: true
                        implicitHeight: 36
                        text: "📊 تصدير كجدول بيانات مفرود (CSV)"
                        background: Rectangle { color: "transparent"; border.color: softGold; radius: 5 }
                        contentItem: Text { text: expCsvBtn.text; color: softGold; font.bold: true; font.pixelSize: 11; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                        onClicked: {
                            if (currentFolderPath) {
                                backend.generate_treedoc(currentFolderPath, "csv");
                            }
                        }
                    }
                }

                Spacer { Layout.fillHeight: true }
            }
        }

        // Selected File Details Box (at the bottom of the screen)
        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: 120
            color: cardSlateBg
            border.color: borderSlate
            radius: 8

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 10
                spacing: 6

                Text {
                    text: root.getTxt("details_header")
                    color: metallicGold
                    font.bold: true
                    font.pixelSize: 11
                }

                // State empty
                Text {
                    visible: !root.selectedItem
                    text: root.getTxt("details_none")
                    color: textGray
                    font.pixelSize: 11
                    horizontalAlignment: Text.AlignHCenter
                    verticalAlignment: Text.AlignVCenter
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                }

                // Active State Details
                RowLayout {
                    visible: root.selectedItem !== null
                    Layout.fillWidth: true
                    spacing: 15

                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 4

                        RowLayout {
                            spacing: 6
                            Text { text: root.getTxt("name"); color: textGray; font.bold: true; font.pixelSize: 10 }
                            Text { text: root.selectedItem ? root.selectedItem.name : ""; color: softGold; font.bold: true; font.pixelSize: 11 }
                        }

                        RowLayout {
                            spacing: 6
                            Text { text: root.getTxt("path"); color: textGray; font.bold: true; font.pixelSize: 10 }
                            Text { text: root.selectedItem ? root.selectedItem.path : ""; color: textSilver; font.pixelSize: 10; wrapMode: Text.Wrap; Layout.fillWidth: true }
                        }

                        RowLayout {
                            spacing: 12
                            RowLayout {
                                spacing: 4
                                Text { text: root.getTxt("size"); color: textGray; font.bold: true; font.pixelSize: 10 }
                                Text { text: root.selectedItem ? root.selectedItem.size_formatted : ""; color: successGreen; font.pixelSize: 11 }
                            }
                            RowLayout {
                                spacing: 4
                                Text { text: root.getTxt("mtime"); color: textGray; font.bold: true; font.pixelSize: 10 }
                                Text { text: root.selectedItem ? root.selectedItem.mtime : ""; color: textSilver; font.pixelSize: 10 }
                            }
                        }
                    }

                    ColumnLayout {
                        spacing: 8

                        Button {
                            id: copyPathBtn
                            text: root.getTxt("btn_copy_path")
                            implicitWidth: 140
                            implicitHeight: 28
                            background: Rectangle { color: "transparent"; border.color: successGreen; radius: 4 }
                            contentItem: Text { text: copyPathBtn.text; color: successGreen; font.bold: true; font.pixelSize: 10; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                            onClicked: {
                                if (root.selectedItem) {
                                    backend.set_clipboard_text(root.selectedItem.path);
                                    backend.notificationSent.emit("مسار الملف", "تم نسخ مسار الملف للحافظة بنجاح.", "success");
                                }
                            }
                        }

                        Button {
                            id: previewBtn
                            text: root.getTxt("btn_preview")
                            implicitWidth: 140
                            implicitHeight: 28
                            visible: root.selectedItem && root.selectedItem.type === "file"
                            background: Rectangle { color: metallicGold; radius: 4 }
                            contentItem: Text { text: previewBtn.text; color: "#0B0F19"; font.bold: true; font.pixelSize: 10; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                            onClicked: {
                                if (root.selectedItem && root.selectedItem.type === "file") {
                                    var content = backend.read_file_text(root.selectedItem.path);
                                    root.previewContent = content;
                                    root.previewIsRich = (root.selectedItem.extension.toLowerCase() === ".html" || root.selectedItem.extension.toLowerCase() === ".md");
                                    previewDialog.visible = true;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Floating Preview Dialog (Simulated Overlay Dialog)
    Rectangle {
        id: previewDialog
        visible: false
        anchors.fill: parent
        color: "#E0000000" // Semitransparent dark overlay
        z: 9999

        MouseArea {
            anchors.fill: parent // Consume click to make modal
        }

        Rectangle {
            width: parent.width * 0.85
            height: parent.height * 0.85
            anchors.centerIn: parent
            color: slateBg
            border.color: borderSlate
            radius: 10

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 15
                spacing: 12

                RowLayout {
                    Layout.fillWidth: true
                    spacing: 10

                    Text {
                        text: root.getTxt("preview_title") + " - " + (root.selectedItem ? root.selectedItem.name : "")
                        color: metallicGold
                        font.bold: true
                        font.pixelSize: 14
                    }

                    Spacer { Layout.fillWidth: true }

                    // View Toggles
                    RowLayout {
                        spacing: 8
                        RadioButton {
                            id: previewRichRadio
                            checked: root.previewIsRich
                            text: root.getTxt("preview_rich")
                            contentItem: Text { text: previewRichRadio.text; color: textSilver; font.pixelSize: 10; leftPadding: 20 }
                            onClicked: root.previewIsRich = true
                        }
                        RadioButton {
                            id: previewRawRadio
                            checked: !root.previewIsRich
                            text: root.getTxt("preview_raw")
                            contentItem: Text { text: previewRawRadio.text; color: textSilver; font.pixelSize: 10; leftPadding: 20 }
                            onClicked: root.previewIsRich = false
                        }
                    }

                    Button {
                        text: "❌"
                        implicitWidth: 30
                        implicitHeight: 30
                        background: Rectangle { color: "transparent" }
                        onClicked: previewDialog.visible = false
                    }
                }

                // Preview Box
                Rectangle {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    color: "#05070E"
                    border.color: borderSlate
                    radius: 6

                    ScrollView {
                        anchors.fill: parent
                        clip: true

                        TextArea {
                            id: previewTextArea
                            text: root.previewContent
                            color: textSilver
                            font.family: root.previewIsRich ? "Segoe UI" : "Consolas"
                            font.pixelSize: 11
                            textFormat: root.previewIsRich ? TextEdit.RichText : TextEdit.PlainText
                            wrapMode: TextEdit.Wrap
                            readOnly: true
                            selectByMouse: true
                            background: null
                        }
                    }
                }
            }
        }
    }

    // Folder Dialog Instance
    FolderDialog {
        id: treeFolderDialog
        title: root.getTxt("target_folder")
        onAccepted: {
            currentFolderPath = backend.clean_path_url(selectedFolder.toString());
        }
    }
}
