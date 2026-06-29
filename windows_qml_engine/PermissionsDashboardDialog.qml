import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

Dialog {
    id: root
    title: backend.appLanguage === "ar" ? "🛡️ لوحة الصلاحيات والأذونات" : "🛡️ Permissions & Privileges Dashboard"
    anchors.centerIn: parent
    width: 540
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
            "title": { "ar": "🛡️ لوحة الصلاحيات والأذونات", "en": "🛡️ Permissions & Privileges" },
            "subtitle": { "ar": "إدارة أذونات نظام التشغيل وحالة تفعيل الصلاحيات المتقدمة للفولاذ البرمجي", "en": "Manage operating system permissions and advanced app privilege integrations" },
            "admin_title": { "ar": "امتيازات المسؤول (Administrator)", "en": "Administrator Privileges" },
            "admin_desc": { "ar": "تسمح بالوصول الكامل لمجلدات النظام وتشغيل العمليات المحمية.", "en": "Allows full system directory access and running protected processes." },
            "file_title": { "ar": "صلاحية الوصول الكامل للملفات", "en": "Broad Filesystem Access" },
            "file_desc": { "ar": "لقراءة وتحديث مستودعات مشاريع الأكواد المدمجة والمخصصة.", "en": "To read and update customized source code repositories on local disk." },
            "overlay_title": { "ar": "صلاحية الظهور فوق التطبيقات", "en": "Overlay Bubble Window" },
            "overlay_desc": { "ar": "خاصة بالفقاعة العائمة والنافذة الذهبية للوصول السريع.", "en": "Allows the mini floating golden bubble to draw over other windows." },
            "notify_title": { "ar": "إشعارات سطح المكتب (Notifications)", "en": "Desktop Push Notifications" },
            "notify_desc": { "ar": "لإرسال تنبيهات وتأكيدات بصرية فورية عند إتمام العمليات.", "en": "Sends real-time toast alerts and process completions to Windows." },
            "active": { "ar": "ممنوحة ✅", "en": "Granted ✅" },
            "inactive": { "ar": "غير ممنوحة ❌", "en": "Denied ❌" },
            "btn_settings": { "ar": "تعديل الصلاحية ⚙️", "en": "Modify Settings ⚙️" },
            "btn_refresh": { "ar": "تحديث فوري 🔄", "en": "Force Refresh 🔄" },
            "btn_close": { "ar": "إغلاق النافذة ❌", "en": "Close Window ❌" },
            "success_toast": { "ar": "تم تحديث حالة صلاحيات النظام بنجاح.", "en": "System permissions statuses re-evaluated and updated." }
        };
        var isAr = (backend.appLanguage === "ar");
        return translations[key] ? (isAr ? translations[key]["ar"] : translations[key]["en"]) : "";
    }

    // Permission states
    property bool hasAdmin: false
    property bool hasFilesystem: false
    property bool hasOverlay: false
    property bool hasNotification: false

    function refreshPermissions() {
        hasAdmin = backend.is_admin()
        hasFilesystem = backend.has_full_filesystem_access()
        hasOverlay = backend.has_overlay_permission()
        hasNotification = backend.has_notification_permission()
    }

    onOpened: {
        refreshPermissions()
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

            ScrollView {
                Layout.fillWidth: true
                Layout.fillHeight: true
                clip: true

                ColumnLayout {
                    width: parent.width - 15
                    spacing: 10

                    // 1. Admin permission
                    Rectangle {
                        Layout.fillWidth: true
                        height: 75
                        color: slateBg
                        border.color: borderSlate
                        border.width: 1
                        radius: 8

                        RowLayout {
                            anchors.fill: parent
                            anchors.margins: 10
                            spacing: 12

                            Text {
                                text: "🔑"
                                font.pixelSize: 22
                            }

                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: 2
                                Text {
                                    text: root.getTxt("admin_title")
                                    color: textSilver
                                    font.bold: true
                                    font.pixelSize: 11
                                }
                                Text {
                                    text: root.getTxt("admin_desc")
                                    color: textGray
                                    font.pixelSize: 9
                                    wrapMode: Text.Wrap
                                    Layout.fillWidth: true
                                }
                            }

                            ColumnLayout {
                                spacing: 4
                                Text {
                                    text: root.hasAdmin ? root.getTxt("active") : root.getTxt("inactive")
                                    color: root.hasAdmin ? successGreen : errorRed
                                    font.bold: true
                                    font.pixelSize: 10
                                    Layout.alignment: Qt.AlignHCenter
                                }
                                Button {
                                    text: root.getTxt("btn_settings")
                                    onClicked: {
                                        backend.open_admin_settings()
                                    }
                                    background: Rectangle {
                                        color: parent.hovered ? borderSlate : "transparent"
                                        border.color: borderSlate
                                        radius: 4
                                    }
                                    contentItem: Text {
                                        text: parent.text
                                        font.pixelSize: 9
                                        color: textSilver
                                        horizontalAlignment: Text.AlignHCenter
                                    }
                                }
                            }
                        }
                    }

                    // 2. Filesystem Access permission
                    Rectangle {
                        Layout.fillWidth: true
                        height: 75
                        color: slateBg
                        border.color: borderSlate
                        border.width: 1
                        radius: 8

                        RowLayout {
                            anchors.fill: parent
                            anchors.margins: 10
                            spacing: 12

                            Text {
                                text: "📂"
                                font.pixelSize: 22
                            }

                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: 2
                                Text {
                                    text: root.getTxt("file_title")
                                    color: textSilver
                                    font.bold: true
                                    font.pixelSize: 11
                                }
                                Text {
                                    text: root.getTxt("file_desc")
                                    color: textGray
                                    font.pixelSize: 9
                                    wrapMode: Text.Wrap
                                    Layout.fillWidth: true
                                }
                            }

                            ColumnLayout {
                                spacing: 4
                                Text {
                                    text: root.hasFilesystem ? root.getTxt("active") : root.getTxt("inactive")
                                    color: root.hasFilesystem ? successGreen : errorRed
                                    font.bold: true
                                    font.pixelSize: 10
                                    Layout.alignment: Qt.AlignHCenter
                                }
                                Button {
                                    text: root.getTxt("btn_settings")
                                    onClicked: {
                                        backend.open_filesystem_settings()
                                    }
                                    background: Rectangle {
                                        color: parent.hovered ? borderSlate : "transparent"
                                        border.color: borderSlate
                                        radius: 4
                                    }
                                    contentItem: Text {
                                        text: parent.text
                                        font.pixelSize: 9
                                        color: textSilver
                                        horizontalAlignment: Text.AlignHCenter
                                    }
                                }
                            }
                        }
                    }

                    // 3. Overlay Permission
                    Rectangle {
                        Layout.fillWidth: true
                        height: 75
                        color: slateBg
                        border.color: borderSlate
                        border.width: 1
                        radius: 8

                        RowLayout {
                            anchors.fill: parent
                            anchors.margins: 10
                            spacing: 12

                            Text {
                                text: "🎯"
                                font.pixelSize: 22
                            }

                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: 2
                                Text {
                                    text: root.getTxt("overlay_title")
                                    color: textSilver
                                    font.bold: true
                                    font.pixelSize: 11
                                }
                                Text {
                                    text: root.getTxt("overlay_desc")
                                    color: textGray
                                    font.pixelSize: 9
                                    wrapMode: Text.Wrap
                                    Layout.fillWidth: true
                                }
                            }

                            ColumnLayout {
                                spacing: 4
                                Text {
                                    text: root.hasOverlay ? root.getTxt("active") : root.getTxt("inactive")
                                    color: root.hasOverlay ? successGreen : errorRed
                                    font.bold: true
                                    font.pixelSize: 10
                                    Layout.alignment: Qt.AlignHCenter
                                }
                                Button {
                                    text: root.getTxt("btn_settings")
                                    onClicked: {
                                        backend.open_overlay_settings()
                                    }
                                    background: Rectangle {
                                        color: parent.hovered ? borderSlate : "transparent"
                                        border.color: borderSlate
                                        radius: 4
                                    }
                                    contentItem: Text {
                                        text: parent.text
                                        font.pixelSize: 9
                                        color: textSilver
                                        horizontalAlignment: Text.AlignHCenter
                                    }
                                }
                            }
                        }
                    }

                    // 4. Notifications Permission
                    Rectangle {
                        Layout.fillWidth: true
                        height: 75
                        color: slateBg
                        border.color: borderSlate
                        border.width: 1
                        radius: 8

                        RowLayout {
                            anchors.fill: parent
                            anchors.margins: 10
                            spacing: 12

                            Text {
                                text: "🔔"
                                font.pixelSize: 22
                            }

                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: 2
                                Text {
                                    text: root.getTxt("notify_title")
                                    color: textSilver
                                    font.bold: true
                                    font.pixelSize: 11
                                }
                                Text {
                                    text: root.getTxt("notify_desc")
                                    color: textGray
                                    font.pixelSize: 9
                                    wrapMode: Text.Wrap
                                    Layout.fillWidth: true
                                }
                            }

                            ColumnLayout {
                                spacing: 4
                                Text {
                                    text: root.hasNotification ? root.getTxt("active") : root.getTxt("inactive")
                                    color: root.hasNotification ? successGreen : errorRed
                                    font.bold: true
                                    font.pixelSize: 10
                                    Layout.alignment: Qt.AlignHCenter
                                }
                                Button {
                                    text: root.getTxt("btn_settings")
                                    onClicked: {
                                        backend.open_notification_settings()
                                    }
                                    background: Rectangle {
                                        color: parent.hovered ? borderSlate : "transparent"
                                        border.color: borderSlate
                                        radius: 4
                                    }
                                    contentItem: Text {
                                        text: parent.text
                                        font.pixelSize: 9
                                        color: textSilver
                                        horizontalAlignment: Text.AlignHCenter
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Controls
            RowLayout {
                Layout.fillWidth: true
                spacing: 10

                Button {
                    text: root.getTxt("btn_refresh")
                    Layout.fillWidth: true
                    onClicked: {
                        root.refreshPermissions()
                        backend.notificationSent(
                            backend.appLanguage === "ar" ? "تحديث الأذونات" : "Permissions Updated",
                            root.getTxt("success_toast"),
                            "success"
                        )
                    }
                }

                Button {
                    text: root.getTxt("btn_close")
                    implicitWidth: 160
                    onClicked: root.close()
                }
            }
        }
    }
}
