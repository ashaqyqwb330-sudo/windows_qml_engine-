import os
import sys
from PySide6.QtCore import QCoreApplication, Qt, QUrl
from PySide6.QtGui import QGuiApplication, QIcon
from PySide6.QtQml import QQmlApplicationEngine
from engine import EngineBackend

def main():
    # Setup styling environment flags for smooth Windows rendering (handled automatically in Qt 6)
    os.environ["QT_QUICK_CONTROLS_STYLE"] = "Basic"
    # QCoreApplication.setAttribute(Qt.AA_EnableHighDpiScaling, True)
    # QCoreApplication.setAttribute(Qt.AA_UseHighDpiPixmaps, True)

    app = QGuiApplication(sys.argv)
    
    # Professional App configuration
    app.setApplicationName("GoldenPlatformPro")
    app.setOrganizationName("GoldenPlatform")
    app.setOrganizationDomain("goldenplatform.org")

    # Initialize Backend
    backend = EngineBackend()

    # Initialize QML Engine
    engine = QQmlApplicationEngine()
    
    # Expose Backend to QML globally
    engine.rootContext().setContextProperty("backend", backend)

    # Load Main window
    qml_file = os.path.join(os.path.dirname(__file__), "main.qml")
    engine.load(QUrl.fromLocalFile(qml_file))

    if not engine.rootObjects():
        sys.exit(-1)

    sys.exit(app.exec())

if __name__ == "__main__":
    main()
