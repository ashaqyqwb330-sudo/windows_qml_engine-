import os
import sys
from PySide6.QtCore import QCoreApplication, Qt, QUrl, QEvent
from PySide6.QtGui import QGuiApplication, QIcon
from PySide6.QtQml import QQmlApplicationEngine
from engine import EngineBackend

class GoldenApplication(QGuiApplication):
    def __init__(self, sys_argv):
        super().__init__(sys_argv)
        self.backend = None

    def set_backend(self, backend):
        self.backend = backend

    def event(self, event):
        if event.type() == QEvent.FileOpen and self.backend:
            file_path = event.file()
            if os.path.exists(file_path):
                if os.path.isdir(file_path):
                    self.backend.set_pending_folder(file_path)
                elif os.path.isfile(file_path):
                    self.backend.set_pending_file(file_path)
            return True
        return super().event(event)

def main():
    # Setup styling environment flags for smooth Windows rendering (handled automatically in Qt 6)
    os.environ["QT_QUICK_CONTROLS_STYLE"] = "Basic"
    # QCoreApplication.setAttribute(Qt.AA_EnableHighDpiScaling, True)
    # QCoreApplication.setAttribute(Qt.AA_UseHighDpiPixmaps, True)

    app = GoldenApplication(sys.argv)
    
    # Professional App configuration
    app.setApplicationName("GoldenPlatformPro")
    app.setOrganizationName("GoldenPlatform")
    app.setOrganizationDomain("goldenplatform.org")

    # Initialize Backend
    backend = EngineBackend()
    app.set_backend(backend)

    # Handle sys.argv if a path or shared text is passed on start
    if len(sys.argv) > 1:
        start_path = sys.argv[1]
        if os.path.exists(start_path):
            if os.path.isdir(start_path):
                backend.set_pending_folder(start_path)
            elif os.path.isfile(start_path):
                backend.set_pending_file(start_path)
        else:
            # If it's not a command line flag, treat as shared text
            if not start_path.startswith("-"):
                backend.set_pending_shared_text(start_path)

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
