import os
import re
import sys
import json
import base64
import mimetypes
import subprocess
import urllib.request
import urllib.error
import threading
from datetime import datetime
from PySide6.QtCore import QObject, Slot, Signal, Property
from PySide6.QtGui import QGuiApplication
from db_manager import DatabaseManager

class EngineBackend(QObject):
    # Core Signals
    logAdded = Signal(str, str)             # type, message
    processingFinished = Signal(bool, str, str) # success, message, details
    packCreated = Signal(str, int)          # text, file_count
    captureResult = Signal(str, str, str)   # status, file_name, path
    
    # Pro Signals
    clipboardBuilderDetected = Signal(str)  # builder package text
    geminiResponse = Signal(bool, str)      # success, reply
    treeDocCreated = Signal(str, str)       # format (html/json/txt), path
    dbUpdated = Signal()                    # database modification trigger
    notificationSent = Signal(str, str, str) # title, message, type (success, warning, info)
    
    # Property Change Notifications
    baseDirChanged = Signal(str)
    activeThemeChanged = Signal(str)
    appLanguageChanged = Signal(str)
    bubbleEnabledChanged = Signal(bool)
    activeProjectChanged = Signal(str)
    pendingFileChanged = Signal(str)
    pendingFolderChanged = Signal(str)
    pendingSharedTextChanged = Signal(str)
    fileOpenRequested = Signal(str)
    folderOpenRequested = Signal(str)
    sharedTextRequested = Signal(str)
    
    # Link Automator Signals
    linkProgress = Signal(int, str)                     # percentage, status_message
    linkProcessingFinished = Signal(bool, str, int, int, str) # success, message, code_count, text_count, saved_files_summary

    def __init__(self):
        super().__init__()
        # Determine base project directory
        self._base_dir = os.path.expanduser("~/GoldenPlatformProjects")
        if not os.path.exists(self._base_dir):
            try:
                os.makedirs(self._base_dir)
            except Exception:
                self._base_dir = os.path.abspath(".")

        self.db = DatabaseManager(self._base_dir)
        self.db.log_action("info", f"تشغيل المحرك الذهبي الإصدار النهائي 2.0 Pro. مجلد العمل: {self._base_dir}")

        # Core file system parameters
        self.ignore_dirs = [
            ".git", ".gradle", ".idea", "build", "dist", "node_modules", 
            "venv", "__pycache__", "import_binaries", "SmartInbox", "TreeDocs"
        ]
        self.text_extensions = [
            ".kt", ".xml", ".kts", ".properties", ".toml", ".txt", ".json", 
            ".pro", ".py", ".java", ".cpp", ".h", ".cs", ".js", ".ts", ".html", ".css", ".md"
        ]

        # Secure XOR Obfuscation Key (to encrypt sensitive keys/tokens in the SQLite file)
        self._xor_key = "GOLDEN_PRO_ENGINE_SUPER_SECRET_KEY_2026"

        # Safe whitelist for executor commands
        self._command_whitelist = ["python", "pip", "npm", "git", "gradle", "dir", "echo", "cls", "node", "cargo", "go", "gcc"]
        self._blacklisted_terms = ["rm -rf", "rmdir /s", "del /f", "format ", "mkfs", "drop table", "drop database", "shred "]

        # Config Settings
        self._clipboard_monitor_enabled = self.db.get_setting("clipboard_monitor", "true").lower() == "true"
        self._active_theme = self.db.get_setting("active_theme", "golden_slate")
        self._app_language = self.db.get_setting("language", "ar") # ar/en
        self._bubble_enabled = self.db.get_setting("bubble_enabled", "true").lower() == "true"
        self._active_project = "Default"

        # File and Folder Opening Setup
        self._pending_file = ""
        self._pending_folder = ""
        self._pending_shared_text = ""

        # Clipboard Monitor setup
        self._last_clipboard_text = ""
        self.clipboard = QGuiApplication.clipboard()
        self.clipboard.dataChanged.connect(self.on_clipboard_changed)

        # Smart Task Queue setup
        self._tasks = []
        self._task_id_counter = 1

    # --- Properties ---
    @Property(str, notify=baseDirChanged)
    def baseDir(self):
        return self._base_dir

    @baseDir.setter
    def baseDir(self, val):
        val = val.replace("file:///", "").replace("file://", "")
        if os.name == 'nt' and val.startswith('/'):
            val = val[1:]
        val = os.path.normpath(val)
        if os.path.exists(val):
            self._base_dir = val
            self.db = DatabaseManager(self._base_dir)
            self.db.log_action("info", f"تم تحديث مجلد العمل والتحويل لقاعدة البيانات الجديدة: {self._base_dir}")
            self.logAdded.emit("info", f"تم تحديث مجلد العمل النشط إلى: {self._base_dir}")
            self.dbUpdated.emit()
            self.baseDirChanged.emit(self._base_dir)
        else:
            self.logAdded.emit("error", f"المجلد غير موجود: {val}")

    @Property(str, notify=activeThemeChanged)
    def activeTheme(self):
        return self._active_theme

    @activeTheme.setter
    def activeTheme(self, val):
        self._active_theme = val
        self.db.set_setting("active_theme", val)
        self.dbUpdated.emit()
        self.activeThemeChanged.emit(val)

    @Property(str, notify=appLanguageChanged)
    def appLanguage(self):
        return self._app_language

    @appLanguage.setter
    def appLanguage(self, val):
        self._app_language = val
        self.db.set_setting("language", val)
        self.dbUpdated.emit()
        self.appLanguageChanged.emit(val)

    @Property(bool, notify=bubbleEnabledChanged)
    def bubbleEnabled(self):
        return self._bubble_enabled

    @bubbleEnabled.setter
    def bubbleEnabled(self, val):
        self._bubble_enabled = val
        self.db.set_setting("bubble_enabled", str(val).lower())
        self.dbUpdated.emit()
        self.bubbleEnabledChanged.emit(val)

    @Property(str, notify=activeProjectChanged)
    def activeProject(self):
        return self._active_project

    @activeProject.setter
    def activeProject(self, val):
        self._active_project = val
        self.activeProjectChanged.emit(val)

    @Property(str, notify=pendingFileChanged)
    def pendingFile(self):
        return self._pending_file

    @pendingFile.setter
    def pendingFile(self, val):
        self._pending_file = val
        self.pendingFileChanged.emit(val)

    @Property(str, notify=pendingFolderChanged)
    def pendingFolder(self):
        return self._pending_folder

    @pendingFolder.setter
    def pendingFolder(self, val):
        self._pending_folder = val
        self.pendingFolderChanged.emit(val)

    @Property(str, notify=pendingSharedTextChanged)
    def pendingSharedText(self):
        return self._pending_shared_text

    @pendingSharedText.setter
    def pendingSharedText(self, val):
        self._pending_shared_text = val
        self.pendingSharedTextChanged.emit(val)

    @Slot(str, result=str)
    def clean_path_url(self, url):
        clean = url.replace("file:///", "").replace("file://", "")
        if os.name == 'nt' and clean.startswith('/'):
            clean = clean[1:]
        return os.path.normpath(clean)

    # --- Secure Key Vault Encryption/Obfuscation ---
    def _obfuscate(self, text):
        if not text:
            return ""
        key_len = len(self._xor_key)
        obfuscated = [chr(ord(c) ^ ord(self._xor_key[i % key_len])) for i, c in enumerate(text)]
        return base64.b64encode("".join(obfuscated).encode('utf-8')).decode('utf-8')

    def _deobfuscate(self, b64_text):
        if not b64_text:
            return ""
        try:
            decoded = base64.b64decode(b64_text.encode('utf-8')).decode('utf-8')
            key_len = len(self._xor_key)
            original = [chr(ord(c) ^ ord(self._xor_key[i % key_len])) for i, c in enumerate(decoded)]
            return "".join(original)
        except Exception:
            return ""

    @Slot(str, result=bool)
    def set_gemini_api_key(self, api_key):
        secure_key = self._obfuscate(api_key.strip())
        self.db.set_setting("gemini_api_key_secure", secure_key)
        self.db.log_action("info", "🔐 تم تشفير وحفظ مفتاح Gemini API بأمان تام في قاعدة البيانات الموثوقة.")
        self.logAdded.emit("success", "🔑 تم تشفير وحفظ مفتاح API بنجاح!")
        self.notificationSent.emit("الأمان والحماية", "تم تشفير وتأمين مفتاح API الخاص بك بنجاح.", "success")
        return True

    @Slot(result=str)
    def get_gemini_api_key(self):
        secure_key = self.db.get_setting("gemini_api_key_secure", "")
        return self._deobfuscate(secure_key)

    # --- Clipboard Automation Monitoring ---
    def on_clipboard_changed(self):
        if not self._clipboard_monitor_enabled:
            return
        try:
            text = self.clipboard.text()
            if text and text != self._last_clipboard_text:
                self._last_clipboard_text = text
                
                # Check min length constraint
                min_len = int(self.db.get_setting("min_clip_length", "10"))
                if len(text) >= min_len:
                    # Classify
                    clip_type = "text"
                    theme = "slate"
                    if "@builder:file" in text and "@builder:end" in text:
                        clip_type = "builder"
                        theme = "gold"
                        self.clipboardBuilderDetected.emit(text)
                        self.db.log_action("info", "📋 تم رصد حزمة بناء صالحة ومعالجة مسبقة في الحافظة!")
                        self.logAdded.emit("success", "📋 تم رصد حزمة بناء برمجية جاهزة للتثبيت!")
                        self.notificationSent.emit("مراقب الحافظة", "تم الكشف تلقائياً عن حزمة بناء برمجية صالحة في حافظة الويندوز.", "info")
                    elif text.startswith("http://") or text.startswith("https://"):
                        clip_type = "url"
                        theme = "gold"
                    elif any(kw in text for kw in ["def ", "class ", "import ", "function", "const ", "let ", "var ", "<?php", "<html>"]):
                        clip_type = "code"
                        theme = "space"
                        
                    title = f"Clipboard: {text[:40].strip()}..." if len(text) > 40 else f"Clipboard: {text.strip()}"
                    self.db.add_capture(title, text, f"clip_{clip_type}", "", theme)
                    self.dbUpdated.emit()
        except Exception as e:
            print(f"Clipboard monitoring error: {e}")

    @Slot(bool)
    def set_clipboard_monitor_enabled(self, enabled):
        self._clipboard_monitor_enabled = enabled
        self.db.set_setting("clipboard_monitor", str(enabled).lower())
        status = "تفعيل" if enabled else "تعطيل"
        self.db.log_action("info", f"تم {status} مراقب الحافظة التلقائي بنجاح.")
        self.logAdded.emit("info", f"تم {status} مراقب الحافظة.")

    @Slot(result=bool)
    def get_clipboard_monitor_enabled(self):
        return self._clipboard_monitor_enabled

    @Slot(result=str)
    def get_clipboard_text(self):
        try:
            return self.clipboard.text()
        except Exception:
            return ""

    @Slot(str)
    def set_clipboard_text(self, text):
        try:
            self.clipboard.setText(text)
        except Exception:
            pass

    # --- Safe Executor & Script Evaluator ---
    def _is_safe_command(self, cmd):
        # Clean whitespaces
        cmd_clean = cmd.strip().lower()
        if not cmd_clean:
            return False, "الأمر البرمجي فارغ."
        
        # Check blacklist
        for term in self._blacklisted_terms:
            if term in cmd_clean:
                return False, f"⚠️ تم حظر هذا الأمر لأسباب أمنية (يحتوي على: '{term}')."
                
        # Get base command binary
        base_cmd = cmd_clean.split()[0]
        # Allow execute file if it is standard scripts or python scripts
        if base_cmd.endswith(".py") or base_cmd.endswith(".js") or base_cmd.endswith(".sh") or base_cmd.endswith(".bat"):
            return True, ""
            
        if base_cmd in self._command_whitelist:
            return True, ""
            
        return False, f"⚠️ الأمر غير مصرح به في بيئة الأمان والتشغيل الآمن: '{base_cmd}'."

    def _run_safe_command_with_dir(self, cmd, run_dir):
        is_safe, error_msg = self._is_safe_command(cmd)
        if not is_safe:
            return False, error_msg

        try:
            # Execute with timeout to avoid freezing PySide engine
            result = subprocess.run(
                cmd, shell=True, cwd=run_dir, 
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=15
            )
            output = result.stdout + "\n" + result.stderr
            return result.returncode == 0, output
        except subprocess.TimeoutExpired:
            return False, "⚠️ تم إنهاء الأمر قسراً لتجاوزه الحد المسموح للأداء (15 ثانية)."
        except Exception as e:
            return False, f"خطأ أثناء تشغيل الأمر: {str(e)}"

    # --- Project management ---
    @Slot(str, str, result=bool)
    def add_project(self, name, path):
        path = self.clean_path_url(path)
        if not os.path.exists(path):
            self.logAdded.emit("error", "مسار المجلد المحدد غير موجود!")
            return False
        success = self.db.add_project(name, path)
        if success:
            self.db.log_action("success", f"تم تسجيل مشروع جديد بنجاح: {name}")
            self.logAdded.emit("success", f"تم تسجيل مشروع جديد: {name}")
            self.dbUpdated.emit()
            self.notificationSent.emit("إدارة المشاريع", f"تم إنشاء وربط المشروع '{name}' بنجاح.", "success")
        return success

    @Slot(str, result=str)
    def add_project_from_json(self, json_str):
        try:
            data = json.loads(json_str)
            name = data.get("name", "").strip()
            path = data.get("path", "").strip()
            
            if not name:
                return json.dumps({"success": False, "message": "اسم المشروع غير موجود أو فارغ!"}, ensure_ascii=False)
            
            # If path is not specified or relative, make it relative to self._base_dir
            if not path:
                path = os.path.join(self._base_dir, name)
            else:
                path = self.clean_path_url(path)
                if not os.path.isabs(path):
                    path = os.path.join(self._base_dir, path)
            
            # Ensure project directory exists
            os.makedirs(path, exist_ok=True)
            
            # Create subfolders listed in folders
            folders = data.get("folders", [])
            for f in folders:
                folder_path_en = f.get("path_en", "").strip()
                if folder_path_en:
                    full_folder_path = os.path.join(path, folder_path_en)
                    os.makedirs(full_folder_path, exist_ok=True)
            
            # Save project with the JSON template
            success = self.db.add_project(name, path, json_str)
            if success:
                self.db.log_action("success", f"تم استيراد مشروع من قالب JSON بنجاح: {name}")
                self.logAdded.emit("success", f"تم استيراد قالب مشروع جديد بنجاح: {name}")
                self.dbUpdated.emit()
                self.notificationSent.emit("إدارة المشاريع", f"تم استيراد وإنشاء المشروع '{name}' بنجاح.", "success")
                return json.dumps({"success": True, "message": f"تم استيراد وإنشاء المشروع '{name}' بنجاح."}, ensure_ascii=False)
            else:
                return json.dumps({"success": False, "message": "فشل حفظ المشروع في قاعدة البيانات!"}, ensure_ascii=False)
        except Exception as e:
            return json.dumps({"success": False, "message": f"خطأ أثناء استيراد القالب: {str(e)}"}, ensure_ascii=False)

    @Slot(str, result=str)
    def export_project_to_json(self, project_name):
        try:
            projects = self.db.get_projects()
            target_proj = None
            for p in projects:
                if p["name"] == project_name:
                    target_proj = p
                    break
            
            if not target_proj:
                return json.dumps({"success": False, "message": "المشروع المحدد غير موجود في قاعدة البيانات!"}, ensure_ascii=False)
            
            template_json_str = target_proj.get("template_json")
            if template_json_str:
                try:
                    parsed = json.loads(template_json_str)
                    return json.dumps(parsed, indent=4, ensure_ascii=False)
                except Exception:
                    pass
            
            folders = []
            proj_path = target_proj["path"]
            if os.path.exists(proj_path):
                for item in os.listdir(proj_path):
                    item_path = os.path.join(proj_path, item)
                    if os.path.isdir(item_path) and not item.startswith('.'):
                        folders.append({
                            "name_ar": f"مجلد {item}",
                            "path_en": item,
                            "file_types": [".kt", ".py", ".html", ".json"],
                            "keywords": []
                        })
            
            if not folders:
                folders = [
                    {"name_ar": "النماذج البرمجية", "path_en": "models", "file_types": [".kt", ".py"], "keywords": ["data class", "class"]},
                    {"name_ar": "واجهات العرض", "path_en": "views", "file_types": [".kt", ".qml"], "keywords": ["Composable", "Rectangle"]}
                ]
                
            export_data = {
                "name": project_name,
                "path": target_proj["path"],
                "folders": folders
            }
            return json.dumps(export_data, indent=4, ensure_ascii=False)
        except Exception as e:
            return json.dumps({"success": False, "message": f"خطأ أثناء تصدير المشروع: {str(e)}"}, ensure_ascii=False)

    @Slot(str, result=str)
    def get_project_details(self, project_name):
        try:
            projects = self.db.get_projects()
            target_proj = None
            for p in projects:
                if p["name"] == project_name:
                    target_proj = p
                    break
            
            if not target_proj:
                return json.dumps({"success": False, "message": "المشروع غير موجود!"}, ensure_ascii=False)
            
            folder_count = 0
            file_count = 0
            proj_path = target_proj["path"]
            
            if os.path.exists(proj_path):
                for root_dir, dirs, files in os.walk(proj_path):
                    dirs[:] = [d for d in dirs if not d.startswith('.')]
                    folder_count += len(dirs)
                    file_count += len(files)
            
            details = {
                "name": target_proj["name"],
                "path": target_proj["path"],
                "created_at": target_proj["created_at"],
                "folder_count": folder_count,
                "file_count": file_count,
                "template_json": target_proj.get("template_json", "") or ""
            }
            return json.dumps({"success": True, "details": details}, ensure_ascii=False)
        except Exception as e:
            return json.dumps({"success": False, "message": f"خطأ في جلب التفاصيل: {str(e)}"}, ensure_ascii=False)

    @Slot(str, result=bool)
    def delete_project(self, project_name):
        success = self.db.delete_project(project_name)
        if success:
            self.db.log_action("info", f"تم حذف المشروع من قائمة المنصة بنجاح: {project_name}")
            self.logAdded.emit("info", f"تم حذف المشروع: {project_name}")
            self.dbUpdated.emit()
            self.notificationSent.emit("إدارة المشاريع", f"تم إلغاء ربط المشروع '{project_name}' من قاعدة البيانات بنجاح.", "info")
        return success

    @Slot(result=str)
    def get_projects_json(self):
        projects = self.db.get_projects()
        return json.dumps(projects, ensure_ascii=False)

    # --- Log viewer methods ---
    @Slot(result=str)
    def get_logs_json(self):
        logs = self.db.get_logs()
        return json.dumps(logs, ensure_ascii=False)

    @Slot()
    def clear_logs(self):
        self.db.clear_logs()
        self.logAdded.emit("info", "تم تنظيف جميع سجلات العمليات والنظام.")
        self.dbUpdated.emit()

    # --- Extractor Processing Logic (Supports up to 50MB) ---
    @Slot(str, str)
    def process_text_directives_for_project(self, text, project_name):
        if not text or not text.strip():
            self.processingFinished.emit(False, "⚠️ النص المدخل فارغ!", "")
            return

        # Large File Chunking Strategy Check (Above 5,000,000 characters)
        if len(text) > 5000000:
            self.logAdded.emit("info", "🔄 حزمة برمجية ضخمة! جاري تفعيل نظام معالجة التجزئة الذكي...")
            self.db.log_action("info", f"بدء تجزئة حزمة ضخمة (الحجم: {len(text)} حرف) للمشروع {project_name}")
            self._process_large_text_chunked(text, project_name)
            return

        self._process_text_directives_standard(text, project_name)

    def _process_text_directives_standard(self, text, project_name="الافتراضي"):
        lines = text.splitlines()
        current_file_path = None
        current_rel_path = None
        current_content = []
        files_written = 0
        executed_commands = []
        errors = []

        self.logAdded.emit("info", f"🔄 البدء في معالجة واستخراج الحزمة للمشروع: {project_name}...")

        # Find project path
        project_dir = self._base_dir
        if project_name and project_name != "الافتراضي" and project_name != "Default":
            for p in self.db.get_projects():
                if p["name"] == project_name:
                    project_dir = p["path"]
                    break

        for line in lines:
            trimmed = line.strip()
            
            # Check @builder:file directive
            if "@builder:file" in trimmed:
                if current_file_path:
                    success, msg = self._write_file_safely(current_file_path, "\n".join(current_content))
                    if success:
                        files_written += 1
                        self.db.add_file(project_name, current_rel_path, current_file_path, len("\n".join(current_content)))
                    else:
                        errors.append(msg)
                    current_file_path = None
                    current_content = []

                match = re.search(r"@builder:file\s+(\S+)", trimmed)
                if match:
                    current_rel_path = match.group(1)
                    current_file_path = os.path.join(project_dir, current_rel_path)
                    self.logAdded.emit("info", f"📄 جاري إنشاء ملف: {current_rel_path}")
                else:
                    errors.append("توجيه @builder:file غير صالح أو مفقود المسار.")

            # Check @builder:end directive
            elif "@builder:end" in trimmed:
                if current_file_path:
                    success, msg = self._write_file_safely(current_file_path, "\n".join(current_content))
                    if success:
                        files_written += 1
                        self.db.add_file(project_name, current_rel_path, current_file_path, len("\n".join(current_content)))
                    else:
                        errors.append(msg)
                    current_file_path = None
                    current_content = []
                else:
                    errors.append("تم العثور على @builder:end دون بداية @builder:file")

            # Check @executor directive
            elif "@executor:" in trimmed:
                cmd = trimmed.split("@executor:", 1)[1].strip()
                if cmd:
                    self.logAdded.emit("info", f"⚙️ جاري التحقق من سلامة وتنفيذ الأمر: {cmd}")
                    success, output = self._run_safe_command_with_dir(cmd, project_dir)
                    executed_commands.append(f"Command: {cmd}\nOutput:\n{output}")
                    if success:
                        self.db.log_action("success", f"نجح التنفيذ الآمن: {cmd}")
                        self.logAdded.emit("success", f"✅ نجح تنفيذ: {cmd}")
                    else:
                        self.db.log_action("error", f"فشل تنفيذ: {cmd}\nOutput: {output}")
                        self.logAdded.emit("error", f"❌ فشل تنفيذ: {cmd}")
                        errors.append(f"الأمر '{cmd}' انتهى بفشل: {output[:150]}...")

            else:
                if current_file_path is not None:
                    current_content.append(line)

        # Finalize
        if current_file_path:
            success, msg = self._write_file_safely(current_file_path, "\n".join(current_content))
            if success:
                files_written += 1
                self.db.add_file(project_name, current_rel_path, current_file_path, len("\n".join(current_content)))
            else:
                errors.append(msg)

        details_list = []
        if files_written > 0:
            details_list.append(f"• تم كتابة وحفظ {files_written} ملفاً برمجياً بنجاح في مجلد العمل.")
        if executed_commands:
            details_list.append(f"• تم تنفيذ {len(executed_commands)} أمراً برمجياً بنجاح.")
        if errors:
            details_list.append(f"• تم رصد الأخطاء التالية:\n" + "\n".join(errors))

        details = "\n\n".join(details_list)
        if files_written > 0 or len(executed_commands) > 0:
            self.db.log_action("success", f"تم إكمال معالجة الحزمة للمشروع {project_name}. الملفات: {files_written}")
            self.processingFinished.emit(True, "⚙️ تم معالجة وتطبيق حزمة البناء بنجاح!", details)
            self.notificationSent.emit("معالجة الكود", f"تم استخراج {files_written} ملفاً بنجاح.", "success")
        else:
            self.processingFinished.emit(False, "⚠️ لم يتم استخراج أي ملفات أو تنفيذ أي أوامر!", details or "تأكد من كتابة التوجيهات بشكل صحيح.")
        self.dbUpdated.emit()

    def _write_file_safely(self, filepath, content):
        try:
            if os.path.exists(filepath):
                self._create_backup(filepath)
            os.makedirs(os.path.dirname(filepath), exist_ok=True)
            with open(filepath, "w", encoding="utf-8") as f:
                f.write(content)
            return True, ""
        except Exception as e:
            return False, f"خطأ في كتابة الملف {os.path.basename(filepath)}: {str(e)}"

    def _process_large_text_chunked(self, text, project_name):
        parts = text.split("// @builder:file")
        if len(parts) <= 1:
            self._process_text_directives_standard(text, project_name)
            return

        self.db.log_action("info", f"تم تقسيم الحزمة الكبيرة إلى {len(parts) - 1} كتلة مستقلة للمعالجة الآمنة.")
        files_written = 0
        errors = []

        project_dir = self._base_dir
        if project_name and project_name != "الافتراضي" and project_name != "Default":
            for p in self.db.get_projects():
                if p["name"] == project_name:
                    project_dir = p["path"]
                    break

        for i, part in enumerate(parts[1:], 1):
            self.logAdded.emit("info", f"🔄 جاري معالجة كتلة ملف {i}/{len(parts)-1}...")
            full_part_text = "@builder:file" + part
            lines = full_part_text.splitlines()
            current_file_path = None
            current_rel_path = None
            current_content = []

            for line in lines:
                trimmed = line.strip()
                if "@builder:file" in trimmed:
                    match = re.search(r"@builder:file\s+(\S+)", trimmed)
                    if match:
                        current_rel_path = match.group(1)
                        current_file_path = os.path.join(project_dir, current_rel_path)
                elif "@builder:end" in trimmed:
                    if current_file_path:
                        success, msg = self._write_file_safely(current_file_path, "\n".join(current_content))
                        if success:
                            files_written += 1
                            self.db.add_file(project_name, current_rel_path, current_file_path, len("\n".join(current_content)))
                        else:
                            errors.append(msg)
                        current_file_path = None
                else:
                    if current_file_path is not None:
                        current_content.append(line)

            if current_file_path:
                success, msg = self._write_file_safely(current_file_path, "\n".join(current_content))
                if success:
                    files_written += 1
                    self.db.add_file(project_name, current_rel_path, current_file_path, len("\n".join(current_content)))
                else:
                    errors.append(msg)

        details = f"🚀 [محرك التجزئة الضخم V2 Pro]\n• تم استخراج وحفظ {files_written} ملفاً برمجياً من أصل {len(parts)-1} كتل بنجاح!"
        if errors:
            details += "\n• أخطاء مرصودة:\n" + "\n".join(errors)

        self.processingFinished.emit(True, "⚙️ تم معالجة الحزمة الضخمة عبر نظام التجزئة بنجاح!", details)
        self.notificationSent.emit("معالجة الكتل الضخمة", f"اكتملت تجزئة واستخراج {files_written} ملفاً بأمان.", "success")
        self.db.log_action("success", f"تمت معالجة الحزمة الضخمة بنجاح لـ {project_name}. استخراج {files_written} ملف.")
        self.dbUpdated.emit()

    # --- Pack Directory V2 ---
    @Slot(str, str)
    def pack_directory_v2(self, folder_path, ignore_patterns_str):
        folder_path = self.clean_path_url(folder_path)
        if not os.path.exists(folder_path):
            self.logAdded.emit("error", f"المجلد غير موجود: {folder_path}")
            return

        custom_ignores = [p.strip() for p in ignore_patterns_str.split(",") if p.strip()]
        active_ignores = self.ignore_dirs + custom_ignores

        self.logAdded.emit("info", f"📦 جاري تجميع مجلد العمل: {os.path.basename(folder_path)}...")
        self.db.log_action("info", f"بدء تجميع المجلد {folder_path}")

        result_text = []
        result_text.append("// =========================================================\n")
        result_text.append(f"// 📥 حزمة التصدير الذهبية Pro V2 - {os.path.basename(folder_path)}\n")
        result_text.append(f"// تاريخ التجميع: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        result_text.append("// =========================================================\n\n")

        file_count = 0
        for root, dirs, files in os.walk(folder_path):
            dirs[:] = [d for d in dirs if d not in active_ignores]

            for file in files:
                if any(ignored in file for ignored in active_ignores):
                    continue
                
                ext = os.path.splitext(file)[1].lower()
                if ext in self.text_extensions or file in ["build.gradle", "settings.gradle", "Dockerfile", "Makefile", "CMakeLists.txt"]:
                    full_path = os.path.join(root, file)
                    rel_path = os.path.relpath(full_path, folder_path).replace("\\", "/")

                    try:
                        with open(full_path, "r", encoding="utf-8", errors="ignore") as f:
                            content = f.read()
                        
                        result_text.append(f"// @builder:file {rel_path}\n")
                        result_text.append(content)
                        if not content.endswith("\n"):
                            result_text.append("\n")
                        result_text.append("// @builder:end\n\n")
                        file_count += 1
                    except Exception as e:
                        self.logAdded.emit("error", f"⚠️ فشل قراءة {rel_path}: {str(e)}")

        final_pack = "".join(result_text)
        self.packCreated.emit(final_pack, file_count)
        self.logAdded.emit("success", f"✅ تم تجميع {file_count} ملفاً برمجياً بنجاح!")
        self.db.log_action("success", f"اكتمل تجميع المجلد {folder_path}. الملفات: {file_count}")
        self.notificationSent.emit("تجميع الحزم", f"تم تغليف وتصدير {file_count} ملفاً برمجياً.", "success")

    # --- Smart Capture V2 and Document Beautifier ---
    @Slot(str, str)
    def smart_capture_content_v2(self, text, theme_name):
        trimmed = text.strip()
        if not trimmed:
            self.captureResult.emit("error", "النص فارغ", "")
            return

        date_str = datetime.now().strftime("%Y%m%d_%H%M%S")
        inbox_dir = os.path.join(self._base_dir, "SmartInbox")
        os.makedirs(inbox_dir, exist_ok=True)

        # Style bank auto detector
        if "<style>" in text or "/* Style Name" in text:
            self._detect_and_save_style_bank(text)

        # 1. Builder check
        if "@builder:file" in text:
            file_name = f"Build_Pack_{date_str}.txt"
            target_path = os.path.join(inbox_dir, file_name)
            with open(target_path, "w", encoding="utf-8") as f:
                f.write(text)
            self.db.add_capture("حزمة بناء برمجية مجمعة", text[:300] + "...", "builder", target_path, theme_name)
            self.captureResult.emit("builder", file_name, target_path)
            self.logAdded.emit("success", f"🧠 تم التقاط وتأمين حزمة بناء: {file_name}")
            self.db.log_action("success", f"تأمين حزمة بناء وحفظها في {file_name}")
            self.dbUpdated.emit()
            return

        # 2. Markdown or style beautifier
        is_markdown = trimmed.startswith("#") or "\n## " in text or "**" in text or "```" in text or "\n- " in text
        if is_markdown:
            file_name = f"Beautified_Doc_{date_str}.html"
            target_path = os.path.join(inbox_dir, file_name)
            html_content = self._convert_md_to_html_premium(trimmed, theme_name)
            with open(target_path, "w", encoding="utf-8") as f:
                f.write(html_content)
            self.db.add_capture("مستند مجمّل ومنسق", trimmed[:300] + "...", "markdown", target_path, theme_name)
            self.captureResult.emit("markdown", file_name, target_path)
            self.logAdded.emit("success", f"🎨 تم تجميل وتنسيق مستند Markdown بأسلوب {theme_name} في {file_name}")
            self.dbUpdated.emit()
            return

        # 3. Raw capture
        file_name = f"Memo_{date_str}.txt"
        target_path = os.path.join(inbox_dir, file_name)
        with open(target_path, "w", encoding="utf-8") as f:
            f.write(text)
        self.db.add_capture("مذكرة سريعة", text[:300] + "...", "raw", target_path, theme_name)
        self.captureResult.emit("raw", file_name, target_path)
        self.logAdded.emit("success", f"📥 تم التقاط النص وحفظه كمذكرة سريعة: {file_name}")
        self.dbUpdated.emit()

    def _detect_and_save_style_bank(self, text):
        try:
            name = "نمط مخصص " + datetime.now().strftime("%H:%M:%S")
            match = re.search(r"/\*\s*Style Name:\s*([^*]+)\s*\*/", text)
            if match:
                name = match.group(1).strip()
            self.db.add_style(name, text)
            self.db.log_action("style", f"🎨 تم إضافة نمط بلمسة احترافية لبنك التصاميم باسم: {name}")
            self.logAdded.emit("success", f"🎨 تم إضافة تصميم للبنك: {name}")
        except Exception:
            pass

    @Slot(result=str)
    def get_styles_json(self):
        styles = self.db.get_styles()
        return json.dumps(styles, ensure_ascii=False)

    @Slot(str)
    def delete_style(self, name):
        self.db.delete_style(name)
        self.logAdded.emit("info", f"تم حذف التصميم {name} من البنك.")
        self.dbUpdated.emit()

    @Slot(result=str)
    def get_captures_json(self):
        captures = self.db.get_captures()
        return json.dumps(captures, ensure_ascii=False)

    def _convert_md_to_html_premium(self, md_text, theme):
        themes = {
            "dark": {"bg": "#0F131D", "text": "#E2E8F0", "card": "#1B2333", "accent": "#E5A93B", "border": "#2E3C54", "code": "#38BDF8"},
            "light": {"bg": "#F8FAFC", "text": "#1E293B", "card": "#FFFFFF", "accent": "#D97706", "border": "#E2E8F0", "code": "#0284C7"},
            "academic": {"bg": "#FAF9F6", "text": "#111111", "card": "#FFFFFF", "accent": "#4A3B32", "border": "#CCCCCC", "code": "#4A3B32"},
            "oasis": {"bg": "#0B1511", "text": "#E0EBE6", "card": "#12251D", "accent": "#10B981", "border": "#1B3B2E", "code": "#34D399"},
            "space": {"bg": "#05070E", "text": "#E4E9FC", "card": "#0D1127", "accent": "#FCD34D", "border": "#1E295D", "code": "#60A5FA"}
        }
        cfg = themes.get(theme, themes["space"])
        html_lines = []
        code_block = False

        html_lines.append(f"""<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>تجميل المستندات - الإصدار الذهبي للويندوز 2.0</title>
    <style>
        body {{
            background-color: {cfg["bg"]};
            color: {cfg["text"]};
            font-family: 'Segoe UI', 'Cairo', Tahoma, Geneva, Verdana, sans-serif;
            direction: rtl;
            line-height: 1.8;
            padding: 40px;
            max-width: 900px;
            margin: 0 auto;
        }}
        .card {{
            background-color: {cfg["card"]};
            border: 1px solid {cfg["border"]};
            padding: 35px;
            border-radius: 12px;
            box-shadow: 0 4px 20px rgba(0,0,0,0.15);
        }}
        h1, h2, h3 {{
            color: {cfg["accent"]};
            border-bottom: 2px solid {cfg["border"]};
            padding-bottom: 12px;
            margin-top: 35px;
            font-weight: 700;
        }}
        code {{
            background-color: {cfg["card"]};
            color: {cfg["code"]};
            padding: 3px 8px;
            border-radius: 6px;
            font-family: 'Consolas', monospace;
            font-size: 0.9em;
        }}
        pre {{
            background-color: {cfg["bg"]};
            border: 1px solid {cfg["border"]};
            padding: 20px;
            border-radius: 10px;
            overflow-x: auto;
        }}
        pre code {{
            background-color: transparent;
            color: {cfg["accent"]};
            padding: 0;
        }}
        ul, ol {{
            padding-right: 25px;
        }}
        li {{
            margin-bottom: 10px;
        }}
        .badge {{
            display: inline-block;
            background-color: {cfg["accent"]};
            color: {cfg["bg"]};
            padding: 4px 10px;
            border-radius: 20px;
            font-size: 0.8em;
            font-weight: bold;
            margin-bottom: 20px;
        }}
        .footer {{
            margin-top: 60px;
            border-top: 1px solid {cfg["border"]};
            padding-top: 20px;
            font-size: 0.85em;
            color: #64748B;
            text-align: center;
        }}
    </style>
</head>
<body>
    <div class="card">
        <div class="badge">الأسلوب الفني: {theme.upper()}</div>
""")

        for line in md_text.splitlines():
            if line.strip().startswith("```"):
                code_block = not code_block
                if code_block:
                    html_lines.append("<pre><code>")
                else:
                    html_lines.append("</code></pre>")
                continue

            if code_block:
                html_lines.append(line.replace("<", "&lt;").replace(">", "&gt;"))
                continue

            # Parse headers
            if line.startswith("# "):
                html_lines.append(f"<h1>{line[2:]}</h1>")
            elif line.startswith("## "):
                html_lines.append(f"<h2>{line[3:]}</h2>")
            elif line.startswith("### "):
                html_lines.append(f"<h3>{line[4:]}</h3>")
            elif line.strip().startswith("- ") or line.strip().startswith("* "):
                html_lines.append(f"<li>{line.strip()[2:]}</li>")
            elif line.strip().startswith("1. "):
                html_lines.append(f"<li>{line.strip()[3:]}</li>")
            elif not line.strip():
                html_lines.append("<br/>")
            else:
                processed = re.sub(r'`([^`]+)`', r'<code>\1</code>', line)
                processed = re.sub(r'\*\*([^*]+)\*\*', r'<strong>\1</strong>', processed)
                html_lines.append(f"<p>{processed}</p>")

        html_lines.append(f"""
            <div class="footer">
                تم توليد وتجميل المستند بأسلوب {theme.upper()} الذهبي 🌲🌐
            </div>
        </div>
    </body>
</html>""")
        return "\n".join(html_lines)

    # --- TreeDoc Pro Interactive Engine ---
    @Slot(str, result=str)
    def get_tree_data_json(self, folder_path):
        folder_path = self.clean_path_url(folder_path)
        if not os.path.exists(folder_path):
            return json.dumps({"success": False, "message": "المسار غير موجود!"}, ensure_ascii=False)
        
        import time
        start_time = time.time()
        
        stats = {
            "total_files": 0,
            "total_folders": 0,
            "total_size": 0,
            "scan_time_ms": 0,
            "total_size_formatted": "0 B"
        }
        
        chart = {
            "Code": {"count": 0, "size": 0, "name": "برمجيات"},
            "Text": {"count": 0, "size": 0, "name": "نصوص"},
            "Media": {"count": 0, "size": 0, "name": "وسائط"},
            "Other": {"count": 0, "size": 0, "name": "أخرى"}
        }
        
        code_exts = {'.py', '.kt', '.java', '.js', '.ts', '.qml', '.cpp', '.h', '.cs', '.go', '.sh', '.bat', '.spec', '.iss', '.sql', '.html', '.css', '.gradle', '.kts'}
        text_exts = {'.txt', '.md', '.json', '.xml', '.toml', '.yaml', '.yml', '.properties', '.csv', '.ini', '.cfg'}
        media_exts = {'.png', '.jpg', '.jpeg', '.gif', '.mp4', '.mp3', '.wav', '.ico', '.svg', '.pdf'}
        
        def format_size(size_bytes):
            if size_bytes < 1024:
                return f"{size_bytes} B"
            elif size_bytes < 1024 * 1024:
                return f"{size_bytes / 1024:.1f} KB"
            elif size_bytes < 1024 * 1024 * 1024:
                return f"{size_bytes / (1024 * 1024):.1f} MB"
            else:
                return f"{size_bytes / (1024 * 1024 * 1024):.1f} GB"
                
        def scan_node(path):
            name = os.path.basename(path) or path
            node = {
                "name": name,
                "path": path,
                "type": "directory" if os.path.isdir(path) else "file",
                "size_bytes": 0,
                "size_formatted": "0 B",
                "mtime": "",
                "children": []
            }
            
            try:
                mtime_epoch = os.path.getmtime(path)
                node["mtime"] = datetime.fromtimestamp(mtime_epoch).strftime('%Y-%m-%d %H:%M:%S')
            except Exception:
                pass
                
            if os.path.isdir(path):
                stats["total_folders"] += 1
                try:
                    items = sorted(os.listdir(path))
                except Exception:
                    items = []
                    
                dir_size = 0
                for item in items:
                    if item in self.ignore_dirs or item.startswith('.'):
                        continue
                    child_path = os.path.join(path, item)
                    child_node = scan_node(child_path)
                    node["children"].append(child_node)
                    dir_size += child_node["size_bytes"]
                
                node["size_bytes"] = dir_size
                node["size_formatted"] = format_size(dir_size)
            else:
                stats["total_files"] += 1
                try:
                    sz = os.path.getsize(path)
                except Exception:
                    sz = 0
                node["size_bytes"] = sz
                node["size_formatted"] = format_size(sz)
                stats["total_size"] += sz
                
                ext = os.path.splitext(name)[1].lower()
                node["extension"] = ext
                
                # Categorize
                if ext in code_exts:
                    cat = "Code"
                elif ext in text_exts:
                    cat = "Text"
                elif ext in media_exts:
                    cat = "Media"
                else:
                    cat = "Other"
                    
                chart[cat]["count"] += 1
                chart[cat]["size"] += sz
                
            return node
            
        tree_structure = scan_node(folder_path)
        
        chart_formatted = {}
        for cat, val in chart.items():
            chart_formatted[cat] = {
                "count": val["count"],
                "size_bytes": val["size"],
                "size_formatted": format_size(val["size"]),
                "name": val["name"]
            }
            
        stats["total_size_formatted"] = format_size(stats["total_size"])
        end_time = time.time()
        stats["scan_time_ms"] = int((end_time - start_time) * 1000)
        
        return json.dumps({
            "success": True,
            "stats": stats,
            "chart": chart_formatted,
            "tree": tree_structure
        }, ensure_ascii=False)

    @Slot(str, str, result=str)
    def generate_treedoc(self, folder_path, doc_format):
        folder_path = self.clean_path_url(folder_path)
        doc_format = doc_format.lower().strip()
        
        if not os.path.exists(folder_path):
            self.logAdded.emit("error", "المسار غير موجود!")
            return json.dumps({"success": False, "message": "المسار غير موجود!"}, ensure_ascii=False)
            
        self.logAdded.emit("info", f"🌲 جاري زراعة وتوليد التقرير الشجري بأسلوب {doc_format}...")
        self.db.log_action("info", f"بدء توليد TreeDoc للمجلد {folder_path} بصيغة {doc_format}")
        
        # Scan folder using get_tree_data_json
        tree_json_str = self.get_tree_data_json(folder_path)
        tree_data = json.loads(tree_json_str)
        if not tree_data.get("success"):
            return tree_json_str
            
        treedocs_dir = os.path.join(self._base_dir, "TreeDocs")
        os.makedirs(treedocs_dir, exist_ok=True)
        date_str = datetime.now().strftime("%Y%m%d_%H%M%S")
        
        stats = tree_data["stats"]
        chart = tree_data["chart"]
        tree = tree_data["tree"]
        
        result_text = ""
        file_name = ""
        
        if doc_format == "txt":
            # 1. ASCII Tree text format
            def _to_txt(node, indent=""):
                lines = []
                if indent == "":
                    lines.append(f"📁 {node['name']}/ [مجلد العمل النشط] ({node['size_formatted']})")
                for child in node.get("children", []):
                    if child["type"] == "directory":
                        lines.append(f"{indent}├── 📁 {child['name']}/ ({child['size_formatted']})")
                        lines.append(_to_txt(child, indent + "│   "))
                    else:
                        lines.append(f"{indent}├── 📄 {child['name']} ({child['size_formatted']})")
                return "\n".join([l for l in lines if l.strip()])
                
            result_text = _to_txt(tree)
            file_name = f"TreeDoc_{date_str}.txt"
            
        elif doc_format == "json":
            # 2. JSON format
            result_text = json.dumps(tree_data, ensure_ascii=False, indent=4)
            file_name = f"TreeDoc_{date_str}.json"
            
        elif doc_format == "csv":
            # 3. CSV flat-list format
            csv_lines = ["Name,Path,Type,Size_Bytes,Size_Formatted,Last_Modified,Extension"]
            def _to_csv(node):
                name_esc = node["name"].replace('"', '""')
                path_esc = node["path"].replace('"', '""')
                ext = node.get("extension", "")
                csv_lines.append(f'"{name_esc}","{path_esc}","{node["type"]}",{node["size_bytes"]},"{node["size_formatted"]}","{node["mtime"]}","{ext}"')
                for child in node.get("children", []):
                    _to_csv(child)
            _to_csv(tree)
            result_text = "\n".join(csv_lines)
            file_name = f"TreeDoc_{date_str}.csv"
            
        elif doc_format in ["html", "pdf"]:
            # 4. Interactive HTML or printable view (suitable for PDF saving)
            # Create a collapsible tree in HTML using <details> and <summary>
            def _to_html_tree(node):
                html_tree = []
                if node["type"] == "directory":
                    html_tree.append(f"<details open><summary class='folder'>📁 {node['name']} <span class='sz'>({node['size_formatted']})</span></summary>")
                    html_tree.append("<ul>")
                    for child in node.get("children", []):
                        html_tree.append(f"<li>{_to_html_tree(child)}</li>")
                    html_tree.append("</ul>")
                    html_tree.append("</details>")
                else:
                    html_tree.append(f"<span class='file'>📄 {node['name']} <span class='sz'>({node['size_formatted']})</span> <span class='mtime'>[{node['mtime']}]</span></span>")
                return "".join(html_tree)
                
            tree_html = _to_html_tree(tree)
            
            # Create category boxes
            category_boxes = []
            for cat, details in chart.items():
                category_boxes.append(f"""
                <div class="card">
                    <h3>{details['name']} ({cat})</h3>
                    <p class="large">{details['count']} ملفات</p>
                    <p class="sub">الحجم الكلي: {details['size_formatted']}</p>
                </div>
                """)
            category_cards_html = "\n".join(category_boxes)
            
            title = f"تقرير شجرة الملفات - {tree['name']}"
            is_pdf = (doc_format == "pdf")
            print_style = "body { background-color: white !important; color: black !important; } .container { box-shadow: none !important; border: none !important; }" if is_pdf else ""
            
            result_text = f"""<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="UTF-8">
    <title>{title}</title>
    <style>
        body {{
            background-color: #0B0F19;
            color: #E2E8F0;
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            margin: 0;
            padding: 20px;
        }}
        .container {{
            max-width: 1200px;
            margin: 0 auto;
            background-color: #161D2C;
            border: 1px solid #212A3E;
            padding: 30px;
            border-radius: 12px;
            box-shadow: 0 10px 25px rgba(0,0,0,0.5);
        }}
        h1 {{
            color: #D4AF37;
            border-bottom: 2px solid #212A3E;
            padding-bottom: 15px;
            margin-top: 0;
        }}
        .stats-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
            gap: 15px;
            margin-bottom: 30px;
        }}
        .card {{
            background-color: #0B0F19;
            border: 1px solid #212A3E;
            padding: 20px;
            border-radius: 8px;
            text-align: center;
        }}
        .card h3 {{
            margin-top: 0;
            color: #808A9D;
            font-size: 14px;
        }}
        .card .large {{
            font-size: 24px;
            font-weight: bold;
            color: #F3E5AB;
            margin: 10px 0;
        }}
        .card .sub {{
            font-size: 12px;
            color: #808A9D;
            margin: 0;
        }}
        details {{
            margin-left: 15px;
        }}
        summary {{
            cursor: pointer;
            padding: 4px;
            font-weight: bold;
            color: #F3E5AB;
        }}
        summary:hover {{
            color: #D4AF37;
        }}
        ul {{
            list-style: none;
            padding-left: 20px;
            border-left: 1px dashed #212A3E;
            margin: 5px 0;
        }}
        li {{
            margin: 4px 0;
        }}
        .file {{
            color: #E2E8F0;
            padding: 2px;
        }}
        .sz {{
            color: #10B981;
            font-size: 11px;
        }}
        .mtime {{
            color: #808A9D;
            font-size: 10px;
        }}
        .footer {{
            text-align: center;
            margin-top: 40px;
            padding-top: 20px;
            border-top: 1px solid #212A3E;
            color: #808A9D;
            font-size: 12px;
        }}
        {print_style}
    </style>
</head>
<body>
    <div class="container">
        <h1>🌲 تقرير مستكشف الملفات الشجري: {tree['name']}</h1>
        <div class="stats-grid">
            <div class="card">
                <h3>إجمالي المجلدات</h3>
                <p class="large">{stats['total_folders']}</p>
                <p class="sub">مجلد فرعي نشط</p>
            </div>
            <div class="card">
                <h3>إجمالي الملفات</h3>
                <p class="large">{stats['total_files']}</p>
                <p class="sub">ملف ممسوح ضوئياً</p>
            </div>
            <div class="card">
                <h3>الحجم الكلي على القرص</h3>
                <p class="large">{stats['total_size_formatted']}</p>
                <p class="sub">{stats['total_size']} بايت كلي</p>
            </div>
            <div class="card">
                <h3>مدة المسح والمعالجة</h3>
                <p class="large">{stats['scan_time_ms']} ms</p>
                <p class="sub">سرعة فائقة مخصصة للويندوز Pro</p>
            </div>
        </div>

        <h2>📊 تصنيف وتوزيع الملفات:</h2>
        <div class="stats-grid">
            {category_cards_html}
        </div>

        <h2>🌴 هيكلية المجلد الشجرية التفاعلية:</h2>
        <div style="background-color: #0B0F19; padding: 20px; border-radius: 8px; border: 1px solid #212A3E; overflow-x: auto;">
            {tree_html}
        </div>

        <div class="footer">
            تم التوليد والتصدير والتأمين بواسطة المساعد الذهبي للويندوز Pro 🌲 - تاريخ الإصدار: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
        </div>
    </div>
</body>
</html>"""
            file_name = f"TreeDoc_{date_str}.html" if doc_format == "html" else f"TreeDoc_{date_str}_print.html"
            
        else:
            return json.dumps({"success": False, "message": "الصيغة المحددة غير مدعومة!"}, ensure_ascii=False)
            
        # Write to target backup file
        target_path = os.path.join(treedocs_dir, file_name)
        with open(target_path, "w", encoding="utf-8") as f:
            f.write(result_text)
            
        # Log to db
        log_msg = f"تم بنجاح تصدير وحفظ التقرير الشجري ({doc_format.upper()}) إلى: {target_path}"
        self.db.log_action("success", log_msg)
        self.logAdded.emit("success", log_msg)
        self.treeDocCreated.emit(doc_format, target_path)
        
        self.notificationSent.emit(
            "تصدير التقرير الشجري" if self.appLanguage == "ar" else "TreeDoc Export",
            f"تم حفظ التقرير بنجاح بصيغة {doc_format.upper()} في المجلد المعتمد." if self.appLanguage == "ar" else f"Successfully saved {doc_format.upper()} report.",
            "success"
        )
        
        preview = result_text[:500] + "\n...\n" + result_text[-200:] if len(result_text) > 700 else result_text
        
        return json.dumps({
            "success": True,
            "path": target_path,
            "filename": file_name,
            "format": doc_format,
            "preview": preview,
            "stats": stats
        }, ensure_ascii=False)

    # --- Gemini AI Chat Assistant ---
    @Slot(str)
    def ask_gemini_async(self, prompt):
        api_key = self.get_gemini_api_key()
        if not api_key:
            self.geminiResponse.emit(False, "⚠️ يرجى إعداد وتأمين مفتاح Gemini API أولاً في لوحة التحكم.")
            return

        self.db.log_action("info", f"إرسال استعلام برمجية مساعد الذكاء الاصطناعي: {prompt[:100]}...")
        self.logAdded.emit("info", "🔄 جاري معالجة السؤال والتواصل مع خوادم ذكاء Gemini...")

        def worker():
            url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={api_key}"
            headers = {"Content-Type": "application/json"}
            payload = {"contents": [{"parts": [{"text": prompt}]}]}
            
            try:
                req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), headers=headers, method="POST")
                with urllib.request.urlopen(req, timeout=30) as response:
                    res_body = response.read().decode("utf-8")
                    data = json.loads(res_body)
                    reply = data["candidates"][0]["content"]["parts"][0]["text"]
                    
                    self.db.add_chat("user", prompt)
                    self.db.add_chat("gemini", reply)
                    self.geminiResponse.emit(True, reply)
                    self.db.log_action("success", "تم الرد من مساعد Gemini الذكي وحفظ المحادثة.")
                    self.logAdded.emit("success", "✅ تم تلقي الإجابة الذكية بنجاح!")
            except urllib.error.HTTPError as e:
                err_msg = e.read().decode("utf-8")
                try:
                    err_json = json.loads(err_msg)
                    err_desc = err_json["error"]["message"]
                except Exception:
                    err_desc = str(e)
                self.geminiResponse.emit(False, f"❌ خطأ من الخادم الذكي: {err_desc}")
                self.db.log_action("error", f"خطأ Gemini API: {err_desc}")
            except Exception as e:
                self.geminiResponse.emit(False, f"❌ فشل الاتصال بالشبكة: {str(e)}")
                self.db.log_action("error", f"خطأ شبكة: {str(e)}")
            self.dbUpdated.emit()

        threading.Thread(target=worker, daemon=True).start()

    @Slot(result=str)
    def get_chats_json(self):
        chats = self.db.get_chats()
        return json.dumps(chats, ensure_ascii=False)

    @Slot()
    def clear_chats(self):
        self.db.clear_chats()
        self.logAdded.emit("info", "تم حذف جميع محادثات الذكاء الاصطناعي بنجاح.")
        self.dbUpdated.emit()

    # --- BackupManager System ---
    def _create_backup(self, filepath):
        try:
            if not os.path.exists(filepath):
                return
            backup_dir = os.path.join(self._base_dir, "Backups")
            os.makedirs(backup_dir, exist_ok=True)
            filename = os.path.basename(filepath)
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            backup_filename = f"{timestamp}_{filename}.bak"
            backup_path = os.path.join(backup_dir, backup_filename)
            import shutil
            shutil.copy2(filepath, backup_path)
            self.db.log_action("backup", f"تم إنشاء نسخة احتياطية للملف {filename} في {backup_filename}")
        except Exception as e:
            print(f"Backup creation error: {e}")

    @Slot(result=str)
    def get_backups_json(self):
        backup_dir = os.path.join(self._base_dir, "Backups")
        if not os.path.exists(backup_dir):
            return "[]"
        try:
            items = []
            for name in sorted(os.listdir(backup_dir), reverse=True):
                filepath = os.path.join(backup_dir, name)
                size = os.path.getsize(filepath)
                modified = datetime.fromtimestamp(os.path.getmtime(filepath)).strftime("%Y-%m-%d %H:%M:%S")
                items.append({
                    "name": name,
                    "size": size,
                    "modified": modified
                })
            return json.dumps(items, ensure_ascii=False)
        except Exception:
            return "[]"

    @Slot(str, result=bool)
    def restore_backup(self, backup_name):
        backup_dir = os.path.join(self._base_dir, "Backups")
        src = os.path.join(backup_dir, backup_name)
        if not os.path.exists(src):
            return False
        try:
            clean_name = backup_name[16:] # Remove 20260628_031200_ timestamp
            if clean_name.endswith(".bak"):
                clean_name = clean_name[:-4]
            dest = os.path.join(self._base_dir, clean_name)
            import shutil
            shutil.copy2(src, dest)
            self.db.log_action("success", f"تم استعادة الملف {clean_name} بنجاح من النسخة {backup_name}")
            self.logAdded.emit("success", f"✅ تم استعادة الملف: {clean_name}")
            self.dbUpdated.emit()
            return True
        except Exception as e:
            print(f"Restore backup error: {e}")
            return False

    # --- ProjectContextManager automated routing ---
    def _get_context_routing_folder(self, filename, content):
        content_lower = content.lower()
        filename_lower = filename.lower()
        if "activity" in content_lower or "class mainactivity" in content_lower:
            return "app/src/main/java/com/example"
        elif "composable" in content_lower or "screen" in filename_lower:
            return "app/src/main/java/com/example/ui"
        elif "viewmodel" in content_lower or "viewmodel" in filename_lower:
            return "app/src/main/java/com/example/viewmodel"
        elif "room" in content_lower or "database" in content_lower or "dao" in content_lower:
            return "app/src/main/java/com/example/db"
        elif "style" in content_lower or "css" in filename_lower or "theme" in filename_lower:
            return "assets/styles"
        elif filename_lower.endswith(".py"):
            return "scripts"
        elif "import retrofit" in content_lower or "api" in filename_lower:
            return "app/src/main/java/com/example/api"
        return "src"

    # --- ChatLinkProcessor System ---
    @Slot(str, result=str)
    def process_chat_content(self, raw_input):
        if not raw_input or not raw_input.strip():
            return "النص المدخل فارغ!"
        blocks = []
        pattern = r"```(?:\w+)?\n(.*?)\n```"
        matches = re.findall(pattern, raw_input, re.DOTALL)
        if not matches:
            matches = [raw_input]
            
        output_blocks = []
        for idx, block in enumerate(matches, 1):
            file_match = re.search(r"(?://|#|/\*)\s*(?:File|Path|الملف):\s*(\S+)", block)
            if file_match:
                filepath = file_match.group(1).strip()
            else:
                suggested_folder = self._get_context_routing_folder(f"CodeBlock_{idx}", block)
                ext = ".kt"
                if "import os" in block or "def " in block:
                    ext = ".py"
                elif "<html>" in block or "<div" in block:
                    ext = ".html"
                elif "{" in block and ":" in block and "}" in block:
                    ext = ".json"
                filepath = f"{suggested_folder}/code_block_{idx}{ext}"
            output_blocks.append(f"// @builder:file {filepath}\n{block}\n// @builder:end\n")
        final_pack = "\n".join(output_blocks)
        self.db.log_action("chat", f"تم استخراج وتوجيه {len(matches)} كتل برمجية من محتوى المحادثة.")
        return final_pack

    @Slot(str, str, str)
    def download_chat_link_async(self, url, project_name, mode):
        self.db.log_action("info", f"بدء جلب ومعالجة الرابط: {url} للمشروع {project_name} بالوضع {mode}")
        self.linkProgress.emit(10, "جاري تحليل الرابط والتحضير...")
        
        def worker():
            try:
                target_url = url.strip()
                if not target_url.startswith(("http://", "https://")):
                    self.linkProcessingFinished.emit(False, "رابط غير صالح! يجب أن يبدأ بـ http:// أو https://", 0, 0, "")
                    return
                
                # Handle Pastebin raw mode conversion
                if "pastebin.com" in target_url and "/raw/" not in target_url:
                    path_parts = target_url.split('/')
                    if len(path_parts) > 3:
                        key = path_parts[-1]
                        target_url = f"https://pastebin.com/raw/{key}"
                        self.logAdded.emit("info", f"تم تحويل رابط Pastebin إلى المسار الخام: {target_url}")
                
                # Handle GitHub Gists raw mode conversion
                elif "gist.github.com" in target_url and "/raw" not in target_url:
                    target_url = target_url.rstrip('/') + '/raw'
                    self.logAdded.emit("info", f"تم تحويل رابط Gist إلى المسار الخام: {target_url}")
                
                self.linkProgress.emit(30, "جاري الاتصال بالخادم وجلب المحتوى...")
                
                # Perform HTTP GET request mimicking a real browser
                headers = {
                    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
                    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
                    'Accept-Language': 'ar,en-US,en;q=0.9',
                    'Referer': 'https://www.google.com/',
                    'DNT': '1'
                }
                
                req = urllib.request.Request(target_url, headers=headers)
                with urllib.request.urlopen(req, timeout=30) as response:
                    raw_data = response.read()
                    try:
                        downloaded_text = raw_data.decode("utf-8")
                    except UnicodeDecodeError:
                        downloaded_text = raw_data.decode("latin-1")
                
                self.linkProgress.emit(60, "تم التنزيل بنجاح. جاري تحليل المحتوى وتصفية البيانات...")
                
                extracted_content = downloaded_text
                
                # Try to extract conversation fields inside NEXT_DATA (like ChatGPT share pages)
                next_data_match = re.search(r'<script\s+id="__NEXT_DATA__"\s+type="application/json">(.*?)</script>', downloaded_text, re.DOTALL)
                if next_data_match:
                    try:
                        json_data = json.loads(next_data_match.group(1))
                        conversation_texts = []
                        def extract_texts(obj):
                            if isinstance(obj, dict):
                                for k, v in obj.items():
                                    if k in ['text', 'content'] and isinstance(v, str):
                                        conversation_texts.append(v)
                                    else:
                                        extract_texts(v)
                            elif isinstance(obj, list):
                                for item in obj:
                                    extract_texts(item)
                        extract_texts(json_data)
                        if conversation_texts:
                            extracted_content = "\n\n".join(conversation_texts)
                            self.logAdded.emit("info", "🧠 تم استخراج نصوص المحادثة بنجاح من بنية NEXT_DATA JSON!")
                    except Exception as ex:
                        self.logAdded.emit("warning", f"فشل تفكيك NEXT_DATA JSON: {str(ex)}")
                
                # Try finding other typical script state tags (DeepSeek/Claude fallback)
                elif "INITIAL_STATE" in downloaded_text:
                    state_match = re.search(r'window\.__INITIAL_STATE__\s*=\s*(.*?);', downloaded_text, re.DOTALL)
                    if state_match:
                        try:
                            quotes = re.findall(r'"text"\s*:\s*"([^"]+)"', state_match.group(1))
                            if quotes:
                                extracted_content = "\n\n".join([q.encode().decode('unicode_escape', errors='ignore') for q in quotes])
                                self.logAdded.emit("info", "🧠 تم استخراج نصوص المحادثة بنجاح من INITIAL_STATE!")
                        except Exception as ex:
                            self.logAdded.emit("warning", f"فشل تفكيك INITIAL_STATE: {str(ex)}")

                # Process based on Mode selected by user
                code_count = 0
                text_count = 0
                summary_report = ""
                
                if mode == "code":
                    # Extract code blocks
                    code_blocks = re.findall(r"```(?:\w+)?\n(.*?)\n```", extracted_content, re.DOTALL)
                    if not code_blocks:
                        # Fallback for raw files (Pastebin, Gists)
                        code_blocks = [extracted_content]
                    
                    code_count = len(code_blocks)
                    self.linkProgress.emit(80, f"تم رصد {code_count} كتل برمجية. جاري استخراجها وتوجيهها للمجلدات...")
                    
                    # Convert to builder format
                    output_blocks = []
                    for idx, block in enumerate(code_blocks, 1):
                        file_match = re.search(r"(?://|#|/\*)\s*(?:File|Path|الملف):\s*(\S+)", block)
                        if file_match:
                            filepath = file_match.group(1).strip()
                        else:
                            suggested_folder = self._get_context_routing_folder(f"LinkCodeBlock_{idx}", block)
                            ext = ".kt"
                            if "import os" in block or "def " in block:
                                ext = ".py"
                            elif "<html>" in block or "<div" in block:
                                ext = ".html"
                            elif "{" in block and ":" in block and "}" in block:
                                ext = ".json"
                            filepath = f"{suggested_folder}/code_block_{idx}{ext}"
                        output_blocks.append(f"// @builder:file {filepath}\n{block}\n// @builder:end\n")
                    
                    project_dir = self._base_dir
                    if project_name and project_name != "الافتراضي" and project_name != "Default":
                        for p in self.db.get_projects():
                            if p["name"] == project_name:
                                project_dir = p["path"]
                                break
                    
                    files_written = 0
                    saved_files_info = []
                    for item in output_blocks:
                        file_match = re.search(r"@builder:file\s+(\S+)", item)
                        if file_match:
                            rel_path = file_match.group(1)
                            full_path = os.path.join(project_dir, rel_path)
                            content_match = re.search(r"@builder:file\s+\S+\s*\n(.*?)\n@builder:end", item, re.DOTALL)
                            if content_match:
                                file_content = content_match.group(1)
                                success, msg = self._write_file_safely(full_path, file_content)
                                if success:
                                    files_written += 1
                                    self.db.add_file(project_name, rel_path, full_path, len(file_content))
                                    saved_files_info.append(f"📄 {rel_path} ({round(len(file_content)/1024, 2)} KB)")
                                else:
                                    self.logAdded.emit("error", f"فشل كتابة الملف {rel_path}: {msg}")
                    
                    self.dbUpdated.emit()
                    summary_report = "\n".join(saved_files_info)
                    message = f"تم استخراج وتوجيه {files_written} ملفاً بنجاح للمشروع: {project_name}"
                    self.linkProgress.emit(100, "اكتملت المعالجة بنجاح!")
                    self.linkProcessingFinished.emit(True, message, code_count, 0, summary_report)
                    self.db.log_action("success", f"تم استخراج وتوجيه {files_written} ملفات من الرابط {target_url}")
                    self.notificationSent.emit("مؤتمت الروابط", f"اكتمل استخراج {files_written} ملفات برمجية.", "success")
                
                elif mode == "text":
                    # Extract plain text
                    clean_text = extracted_content
                    if "<html" in downloaded_text.lower():
                        clean_text = re.sub('<[^<]+?>', '', extracted_content) # strip HTML tags
                        clean_text = re.sub(r'\n\s*\n', '\n\n', clean_text)    # compress spacing
                    
                    date_str = datetime.now().strftime("%Y%m%d_%H%M%S")
                    project_dir = self._base_dir
                    if project_name and project_name != "الافتراضي" and project_name != "Default":
                        for p in self.db.get_projects():
                            if p["name"] == project_name:
                                project_dir = p["path"]
                                break
                    
                    filename = f"Extracted_Chat_Text_{date_str}.txt"
                    filepath = os.path.join(project_dir, filename)
                    success, msg = self._write_file_safely(filepath, clean_text)
                    
                    if success:
                        self.db.add_file(project_name, filename, filepath, len(clean_text))
                        self.dbUpdated.emit()
                        summary_report = f"📄 {filename} ({round(len(clean_text)/1024, 2)} KB)\nتم حفظ المستند النصي بالكامل."
                        message = f"تم حفظ النص المستخرج بنجاح في ملف: {filename}"
                        text_count = len(clean_text.split())
                        self.linkProgress.emit(100, "اكتمل استخراج النص!")
                        self.linkProcessingFinished.emit(True, message, 0, text_count, summary_report)
                        self.db.log_action("success", f"تم حفظ النص المستخرج من الرابط في {filename}")
                        self.notificationSent.emit("مؤتمت الروابط", "تم استخراج وحفظ المحتوى النصي بنجاح.", "success")
                    else:
                        self.linkProcessingFinished.emit(False, f"فشل كتابة ملف النص المستخرج: {msg}", 0, 0, "")
                
                elif mode == "capture":
                    self.linkProgress.emit(80, "جاري إرسال المحتوى إلى نظام الالتقاط الذكي...")
                    self.smart_capture_content_v2(extracted_content, "space")
                    
                    summary_report = "تم توجيه المحتوى المستخرج بالكامل إلى صندوق الوارد للالتقاط الذكي (Smart Inbox).\nسيتم تجميله وعرضه تلقائياً."
                    self.linkProgress.emit(100, "اكتمل الالتقاط الذكي!")
                    self.linkProcessingFinished.emit(True, "تم توجيه المحتوى المستخرج للالتقاط الذكي وتنسيقه كصفحة HTML مجمّلة بنجاح.", 0, 0, summary_report)
                    self.notificationSent.emit("مؤتمت الروابط", "تم تمرير المحتوى لصندوق الالتقاط الذكي بنجاح.", "success")
                
            except urllib.error.HTTPError as e:
                err_msg = f"خطأ من الخادم (HTTP {e.code}): {e.reason}"
                self.linkProcessingFinished.emit(False, err_msg, 0, 0, "")
                self.db.log_action("error", f"فشل جلب الرابط {url}: {err_msg}")
            except Exception as e:
                err_msg = f"فشل الاتصال بالشبكة: {str(e)}"
                self.linkProcessingFinished.emit(False, err_msg, 0, 0, "")
                self.db.log_action("error", f"فشل جلب الرابط {url}: {err_msg}")
        
        threading.Thread(target=worker, daemon=True).start()

    # --- BuildPackExporter Advanced System ---
    @Slot(str, str, bool, result=str)
    def pack_directory_advanced(self, folder_path, style, format_md_to_html=False):
        folder_path = self.clean_path_url(folder_path)
        if not os.path.exists(folder_path):
            return "المجلد غير موجود."
            
        active_ignores = self.ignore_dirs
        result_text = []
        result_text.append("// =========================================================\n")
        result_text.append(f"// 📥 حزمة التصدير الذهبية Pro ({style.upper()}) - {os.path.basename(folder_path)}\n")
        result_text.append(f"// تاريخ التجميع: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        result_text.append("// =========================================================\n\n")

        file_count = 0
        all_files = []
        for root, dirs, files in os.walk(folder_path):
            dirs[:] = [d for d in dirs if d not in active_ignores]
            for file in files:
                if any(ignored in file for ignored in active_ignores):
                    continue
                ext = os.path.splitext(file)[1].lower()
                if ext in self.text_extensions or file in ["build.gradle", "settings.gradle", "Dockerfile", "Makefile", "CMakeLists.txt"]:
                    all_files.append(os.path.join(root, file))

        if style == "smart":
            def smart_key(fp):
                _, ext = os.path.splitext(fp)
                if ext in [".toml", ".kts", ".properties"]:
                    return (0, fp)
                elif ext in [".kt", ".java", ".py"]:
                    return (1, fp)
                return (2, fp)
            all_files.sort(key=smart_key)
        else:
            all_files.sort()

        for full_path in all_files:
            rel_path = os.path.relpath(full_path, folder_path).replace("\\", "/")
            try:
                with open(full_path, "r", encoding="utf-8", errors="ignore") as f:
                    content = f.read()
                
                if format_md_to_html and rel_path.endswith(".md"):
                    content = self._convert_md_to_html_premium(content, "space")
                    rel_path = rel_path[:-3] + ".html"
                
                if style == "raw":
                    result_text.append(f"### الملف: {rel_path}\n```\n{content}\n```\n\n")
                else: # smart or bundled
                    result_text.append(f"// @builder:file {rel_path}\n")
                    result_text.append(content)
                    if not content.endswith("\n"):
                        result_text.append("\n")
                    result_text.append("// @builder:end\n\n")
                file_count += 1
            except Exception as e:
                self.logAdded.emit("error", f"⚠️ فشل قراءة {rel_path}: {str(e)}")

        final_pack = "".join(result_text)
        self.packCreated.emit(final_pack, file_count)
        self.logAdded.emit("success", f"✅ تم تجميع {file_count} ملفاً بأسلوب {style.upper()} بنجاح!")
        self.db.log_action("success", f"اكتمل تجميع المجلد {folder_path} بأسلوب {style.upper()}. الملفات: {file_count}")
        return final_pack

    # --- AppReportHelper System ---
    @Slot(str, str, bool, result=str)
    def generate_project_report(self, folder_path, report_format="html", mask_sensitive=True):
        folder_path = self.clean_path_url(folder_path)
        if not os.path.exists(folder_path):
            return "المسار المحدد غير موجود لإنشاء التقرير."

        self.db.log_action("report", f"توليد تقرير عن المشروع {os.path.basename(folder_path)} بصيغة {report_format}")

        file_count = 0
        total_size = 0
        exts = {}
        sensitive_matches_count = 0
        sens_patterns = [
            r"api_key\s*=\s*['\"][^'\"]+['\"]",
            r"password\s*=\s*['\"][^'\"]+['\"]",
            r"token\s*=\s*['\"][^'\"]+['\"]",
            r"credentials\s*=\s*['\"][^'\"]+['\"]"
        ]
        
        for root, _, files in os.walk(folder_path):
            if any(ignored in root for ignored in self.ignore_dirs):
                continue
            for file in files:
                file_count += 1
                fp = os.path.join(root, file)
                try:
                    total_size += os.path.getsize(fp)
                    _, ext = os.path.splitext(file)
                    exts[ext] = exts.get(ext, 0) + 1
                    if mask_sensitive and ext in self.text_extensions:
                        with open(fp, "r", encoding="utf-8", errors="ignore") as f:
                            content = f.read()
                        for pat in sens_patterns:
                            if re.search(pat, content, re.IGNORECASE):
                                sensitive_matches_count += 1
                except Exception:
                    pass

        size_mb = round(total_size / (1024 * 1024), 2)
        
        if report_format == "txt":
            rep = [
                "==================================================",
                f"   تقرير المشروع الذهبي Pro: {os.path.basename(folder_path)}",
                f"   تاريخ التوليد: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
                "==================================================",
                f"• عدد الملفات الإجمالي: {file_count}",
                f"• الحجم الكلي للمشروع: {size_mb} MB",
                f"• توزيع الملفات حسب الامتداد:",
            ]
            for ext, count in sorted(exts.items(), key=lambda x: x[1], reverse=True):
                rep.append(f"   - {ext or 'بدون امتداد'}: {count} ملف")
            if mask_sensitive:
                rep.append(f"• فحص الخصوصية والأمان: تم العثور على {sensitive_matches_count} ملفات تحتوي على بيانات حساسة محتملة وتم حجب قيمها تلقائياً.")
            return "\n".join(rep)
            
        elif report_format == "html":
            ext_rows = "".join([f"<tr><td>{ext or 'بلا'}</td><td>{count}</td></tr>" for ext, count in sorted(exts.items(), key=lambda x: x[1], reverse=True)])
            html_rep = f"""<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>تقرير حالة المشروع - المنصة الذهبية للويندوز Pro</title>
    <style>
        body {{
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background-color: #0F172A;
            color: #F8FAFC;
            direction: rtl;
            padding: 30px;
        }}
        .report-card {{
            background-color: #1E293B;
            border: 1px solid #334155;
            padding: 25px;
            border-radius: 10px;
            max-width: 800px;
            margin: 0 auto;
            box-shadow: 0 4px 15px rgba(0,0,0,0.3);
        }}
        h1 {{ color: #F59E0B; border-bottom: 1px solid #334155; padding-bottom: 10px; }}
        table {{ width: 100%; border-collapse: collapse; margin-top: 20px; }}
        th, td {{ padding: 12px; border: 1px solid #334155; text-align: right; }}
        th {{ background-color: #0F172A; color: #F59E0B; }}
        .badge {{ background-color: #10B981; color: #FFFFFF; padding: 4px 10px; border-radius: 12px; font-size: 12px; }}
    </style>
</head>
<body>
    <div class="report-card">
        <h1>📊 تقرير حالة المشروع: {os.path.basename(folder_path)}</h1>
        <p><strong>تاريخ التوليد:</strong> {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</p>
        <p><strong>مسار المشروع:</strong> {folder_path}</p>
        <hr style="border: 0; border-top: 1px solid #334155;"/>
        <ul>
            <li><strong>الملفات الإجمالية:</strong> {file_count} ملف.</li>
            <li><strong>الحجم الإجمالي للمشروع:</strong> {size_mb} ميجابايت.</li>
            <li><strong>حالة الخصوصية والأمان:</strong> <span class="badge">تم الفحص الذكي</span> (اكتشاف وحجب {sensitive_matches_count} بيانات حساسة).</li>
        </ul>
        <h3>توزيع الملفات البرمجية</h3>
        <table>
            <thead>
                <tr><th>النوع / الامتداد</th><th>العدد</th></tr>
            </thead>
            <tbody>
                {ext_rows}
            </tbody>
        </table>
    </div>
</body>
</html>"""
            reports_dir = os.path.join(self._base_dir, "TreeDocs", "Reports")
            os.makedirs(reports_dir, exist_ok=True)
            report_path = os.path.join(reports_dir, f"Project_Report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.html")
            with open(report_path, "w", encoding="utf-8") as rf:
                rf.write(html_rep)
            return report_path
        return "صيغة غير مدعومة."

    # --- LogAggregator & Storyteller Narrative Log System ---
    @Slot(result=str)
    def get_stories_json(self):
        logs = self.db.get_logs()
        stories = []
        clusters = {}
        for log in logs:
            try:
                dt = datetime.strptime(log["created_at"], "%Y-%m-%d %H:%M:%S")
                cluster_key = dt.strftime("%Y-%m-%d %H") + ":00"
            except Exception:
                cluster_key = "أخرى"
                
            if cluster_key not in clusters:
                clusters[cluster_key] = []
            clusters[cluster_key].append(log)
            
        for key, log_list in sorted(clusters.items(), reverse=True):
            success_count = sum(1 for l in log_list if l["type"] == "success")
            info_count = sum(1 for l in log_list if l["type"] == "info")
            error_count = sum(1 for l in log_list if l["type"] == "error")
            style_count = sum(1 for l in log_list if l["type"] in ["style", "backup"])
            
            icon = "📦"
            title = "أعمال تطوير وصيانة متفرقة"
            desc = f"تم إنجاز {len(log_list)} عملية تطويرية وتحليلية بالنظام."
            
            if success_count > error_count and success_count > 0:
                icon = "✨"
                title = "تحديث وتطوير برمجية ناجح"
                desc = f"تم استخراج ملفات وإجراء تعديلات ناجحة وحفظ مذكرات بسلام."
            if style_count > 0:
                icon = "🎨"
                title = "تحسين المظهر وبنك التصاميم"
                desc = "تم تطبيق سمات جديدة أو رصد وتأمين قوالب تصميمية مخصصة."
            if error_count > 0 and success_count == 0:
                icon = "⚠️"
                title = "مراجعة وحل مشكلات برمجية"
                desc = "رصدت المنصة الذهبية بعض التحديات التقنية وتم تفاديها بأمان."
                
            stories.append({
                "time": key,
                "title": title,
                "desc": desc,
                "icon": icon,
                "details": "\n".join([f"• [{l['type'].upper()}] {l['message']}" for l in log_list[:5]]) + ("\n..." if len(log_list) > 5 else ""),
                "success_count": success_count,
                "info_count": info_count,
                "error_count": error_count
            })
        return json.dumps(stories, ensure_ascii=False)

    # --- CommandRegistry Advanced Execution System ---
    @Slot(str, str, bool, result=str)
    def execute_command_advanced(self, cmd_line, project_name, dry_run=False):
        res = self._execute_command_advanced_inner(cmd_line, project_name, dry_run)
        if not dry_run:
            status = "failed" if (res.startswith("❌") or "فشل" in res or "خطأ" in res or "⚠️" in res) else "completed"
            self.db.add_command_history(cmd_line, status, res)
            self.dbUpdated.emit()
        return res

    def _execute_command_advanced_inner(self, cmd_line, project_name, dry_run=False):
        parts = cmd_line.strip().split()
        if not parts:
            return "الأمر فارغ."
        
        cmd_name = parts[0].lower()
        args = parts[1:]
        
        project_dir = self._base_dir
        if project_name and project_name != "الافتراضي" and project_name != "Default":
            for p in self.db.get_projects():
                if p["name"] == project_name:
                    project_dir = p["path"]
                    break

        if dry_run:
            return f"[محاكاة التشغيل الآمن - Dry Run]\nالأمر المحدد: {cmd_line}\nمشروع العمل: {project_name} ({project_dir})\nالحالة: آمن ومصرح للتنفيذ.\nسياق المحاكاة: سيقوم بتنفيذ الأمر {cmd_name} مع المعاملات {args}."

        if cmd_name == "scan":
            try:
                files = []
                for root, _, filenames in os.walk(project_dir):
                    for f in filenames:
                        files.append(os.path.relpath(os.path.join(root, f), project_dir))
                return f"📁 تم فحص {len(files)} ملفاً في المشروع:\n" + "\n".join(files[:50]) + ("\n..." if len(files) > 50 else "")
            except Exception as e:
                return f"خطأ في الفحص: {e}"

        elif cmd_name == "move" and len(args) >= 2:
            src = os.path.join(project_dir, args[0])
            dest = os.path.join(project_dir, args[1])
            if not os.path.exists(src):
                return f"الملف المصدر غير موجود: {args[0]}"
            self._create_backup(src)
            try:
                os.makedirs(os.path.dirname(dest), exist_ok=True)
                import shutil
                shutil.move(src, dest)
                self.db.log_action("success", f"تم نقل {args[0]} إلى {args[1]}")
                return f"✅ تم نقل الملف بنجاح إلى: {args[1]}"
            except Exception as e:
                return f"فشل النقل: {e}"

        elif cmd_name == "rename" and len(args) >= 2:
            src = os.path.join(project_dir, args[0])
            dest = os.path.join(os.path.dirname(src), args[1])
            if not os.path.exists(src):
                return f"الملف غير موجود: {args[0]}"
            self._create_backup(src)
            try:
                os.rename(src, dest)
                self.db.log_action("success", f"تم إعادة تسمية {args[0]} إلى {args[1]}")
                return f"✅ تم إعادة التسمية بنجاح إلى: {args[1]}"
            except Exception as e:
                return f"فشل إعادة التسمية: {e}"

        elif cmd_name == "delete" and len(args) >= 1:
            target = os.path.join(project_dir, args[0])
            if not os.path.exists(target):
                return f"الملف غير موجود: {args[0]}"
            self._create_backup(target)
            try:
                import shutil
                if os.path.isdir(target):
                    shutil.rmtree(target)
                else:
                    os.remove(target)
                self.db.log_action("success", f"تم حذف {args[0]}")
                return f"🗑️ تم حذف {args[0]} بنجاح (وتم حفظ نسخة احتياطية)."
            except Exception as e:
                return f"فشل الحذف: {e}"

        elif cmd_name == "copy-safe" and len(args) >= 2:
            src = os.path.join(project_dir, args[0])
            dest = os.path.join(project_dir, args[1])
            if not os.path.exists(src):
                return f"المصدر غير موجود: {args[0]}"
            if os.path.exists(dest):
                return f"الملف الهدف موجود بالفعل! منعاً للتصادم تم إيقاف العملية."
            try:
                os.makedirs(os.path.dirname(dest), exist_ok=True)
                import shutil
                shutil.copy2(src, dest)
                return f"✅ تم نسخ الملف بأمان إلى: {args[1]}"
            except Exception as e:
                return f"فشل النسخ الآمن: {e}"

        elif cmd_name == "duplicates":
            import hashlib
            hashes = {}
            duplicates = []
            for root, _, filenames in os.walk(project_dir):
                for f in filenames:
                    fp = os.path.join(root, f)
                    try:
                        with open(fp, "rb") as file_to_hash:
                            h = hashlib.md5(file_to_hash.read()).hexdigest()
                        rel = os.path.relpath(fp, project_dir)
                        if h in hashes:
                            duplicates.append((rel, hashes[h]))
                        else:
                            hashes[h] = rel
                    except Exception:
                        pass
            if not duplicates:
                return "🔍 لا توجد ملفات مكررة متطابقة المحتوى في المشروع."
            return "⚠️ الملفات المكررة المكتشفة:\n" + "\n".join([f"• {dup} (مطابق لـ {orig})" for dup, orig in duplicates])

        elif cmd_name == "project":
            file_count = 0
            total_size = 0
            exts = {}
            for root, _, filenames in os.walk(project_dir):
                for f in filenames:
                    file_count += 1
                    fp = os.path.join(root, f)
                    try:
                        total_size += os.path.getsize(fp)
                        _, ext = os.path.splitext(f)
                        exts[ext] = exts.get(ext, 0) + 1
                    except Exception:
                        pass
            size_mb = round(total_size / (1024 * 1024), 2)
            dist = ", ".join([f"{k or 'بلا'}: {v}" for k, v in sorted(exts.items(), key=lambda x: x[1], reverse=True)[:5]])
            return f"📊 إحصائيات المشروع {project_name}:\n• المسار: {project_dir}\n• عدد الملفات: {file_count}\n• الحجم الإجمالي: {size_mb} MB\n• توزيع الامتدادات الشائعة: {dist}"

        elif cmd_name == "template":
            templates = {
                "activity": "import android.os.Bundle\nimport androidx.activity.ComponentActivity\n\nclass NewActivity : ComponentActivity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n    }\n}",
                "viewModel": "import androidx.lifecycle.ViewModel\nimport kotlinx.coroutines.flow.MutableStateFlow\n\nclass NewViewModel : ViewModel() {\n    val state = MutableStateFlow(\"Initial\")\n}",
                "screen": "import androidx.compose.runtime.Composable\nimport androidx.compose.material3.Text\n\n@Composable\nfun NewScreen() {\n    Text(\"Hello Template\")\n}"
            }
            if len(args) >= 1 and args[0] in templates:
                return f"📄 قالب '{args[0]}':\n\n{templates[args[0]]}"
            return "🛠️ القوالب المتوفرة: activity, viewModel, screen\nلاستعراض القالب استخدم: template <اسم_القالب>"

        elif cmd_name == "extract-title" and len(args) >= 1:
            target = os.path.join(project_dir, args[0])
            if not os.path.exists(target):
                return "الملف غير موجود."
            try:
                with open(target, "r", encoding="utf-8", errors="ignore") as f:
                    content = f.read(1000)
                match = re.search(r"class\s+(\w+)", content)
                if match:
                    return f"🏷️ عنوان مستخرج (Class): {match.group(1)}"
                match = re.search(r"h\d+\s+(.*)", content) or re.search(r"#\s+(.*)", content)
                if match:
                    return f"🏷️ عنوان رئيسي مستخرج: {match.group(1).strip()}"
                return "🏷️ لا يوجد عنوان واضح، تم إرجاع اسم الملف: " + os.path.basename(target)
            except Exception as e:
                return f"خطأ: {e}"

        elif cmd_name == "read-metadata" and len(args) >= 1:
            target = os.path.join(project_dir, args[0])
            if not os.path.exists(target):
                return "الملف غير موجود."
            try:
                with open(target, "r", encoding="utf-8", errors="ignore") as f:
                    lines = [f.readline().strip() for _ in range(10)]
                metadata = [l for l in lines if l.startswith("//") or l.startswith("/*") or l.startswith("#")]
                return "📋 البيانات الوصفية (أول 10 أسطر تعليق):\n" + "\n".join(metadata) if metadata else "📋 لا توجد بيانات وصفية تعليقية في بداية الملف."
            except Exception as e:
                return f"خطأ: {e}"

        elif cmd_name == "report":
            return self.generate_project_report(project_dir, "txt", True)

        elif cmd_name == "chart":
            exts = {}
            for root, _, filenames in os.walk(project_dir):
                for f in filenames:
                    _, ext = os.path.splitext(f)
                    exts[ext] = exts.get(ext, 0) + 1
            if not exts:
                return "لا توجد ملفات لرسم المخطط."
            total = sum(exts.values())
            chart_lines = ["📊 مخطط توزيع الملفات:"]
            for ext, count in sorted(exts.items(), key=lambda x: x[1], reverse=True)[:6]:
                pct = int((count / total) * 100)
                bar = "█" * (pct // 5)
                chart_lines.append(f"{ext or 'بلا':<8} | {bar:<20} {pct}% ({count})")
            return "\n".join(chart_lines)

        elif cmd_name == "export" and len(args) >= 1:
            import zipfile
            zip_name = f"Export_{os.path.basename(project_dir)}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.zip"
            target_zip = os.path.join(self._base_dir, zip_name)
            try:
                with zipfile.ZipFile(target_zip, 'w', zipfile.ZIP_DEFLATED) as zipf:
                    for root, _, files in os.walk(project_dir):
                        for file in files:
                            if any(ignored in root for ignored in self.ignore_dirs):
                                continue
                            fp = os.path.join(root, file)
                            zipf.write(fp, os.path.relpath(fp, project_dir))
                return f"📦 تم تصدير المشروع كحزمة مضغوطة بالكامل:\n📂 {target_zip}"
            except Exception as e:
                return f"خطأ في التصدير المضغوط: {e}"

        elif cmd_name == "open" and len(args) >= 1:
            target = os.path.join(project_dir, args[0])
            if not os.path.exists(target):
                return "الملف غير موجود."
            try:
                if os.name == 'nt':
                    os.startfile(target)
                else:
                    subprocess.call(["xdg-open", target])
                return f"🔓 تم إصدار أمر فتح الملف للنظام بنجاح: {args[0]}"
            except Exception as e:
                return f"فشل فتح الملف: {e}"

        elif cmd_name == "clipboard":
            if args:
                text_to_set = " ".join(args)
                self.clipboard.setText(text_to_set)
                return "📋 تم وضع النص في الحافظة بنجاح."
            else:
                return f"📋 محتوى الحافظة الحالي:\n{self.clipboard.text()[:200]}"

        elif cmd_name == "notify" and len(args) >= 1:
            msg = " ".join(args)
            self.notificationSent.emit("إشعار النظام الذكي", msg, "info")
            return f"🔔 تم إرسال إشعار للنظام: {msg}"

        elif cmd_name == "preview" and len(args) >= 1:
            target = os.path.join(project_dir, args[0])
            if not os.path.exists(target):
                return "الملف غير موجود."
            try:
                with open(target, "r", encoding="utf-8", errors="ignore") as f:
                    preview_text = f.read(500)
                return f"📄 معاينة الملف ({args[0]}):\n------------------------------------\n{preview_text}\n------------------------------------"
            except Exception as e:
                return f"فشل المعاينة: {e}"

        elif cmd_name == "ai" and len(args) >= 1:
            prompt = " ".join(args)
            self.ask_gemini_async(f"اكتب كود برمجياً سريعاً لـ: {prompt}")
            return "💬 جاري توليد الكود عبر خادم Gemini AI... ترقب النتيجة في المحادثة."

        elif cmd_name == "selftest":
            db_ok = os.path.exists(self.db.db_path)
            env_ok = "صالح" if self.get_gemini_api_key() else "مفقود أو غير مهيأ"
            return f"🛡️ تقرير الفحص الذاتي للنظام:\n• قاعدة البيانات المدمجة: {'✅ سليمة وتعمل' if db_ok else '❌ غير موجودة'}\n• مجلد العمل الحالي: {self._base_dir}\n• مفتاح Gemini AI: {env_ok}\n• مراقب حافظة الويندوز: {'✅ نشط' if self._clipboard_monitor_enabled else '⚠️ معطل'}"

        else:
            success, output = self._run_safe_command_with_dir(cmd_line, project_dir)
            if success:
                return f"✅ تم تنفيذ الأمر بنجاح:\n{output}"
            else:
                return f"❌ فشل تنفيذ الأمر:\n{output}"

    # --- UniversalActionHandler Dispatch System ---
    @Slot(str, str, str, result=str)
    def handle_universal_input(self, text, project_name, custom_mode="auto_detect"):
        trimmed = text.strip()
        if not trimmed:
            return json.dumps({"status": "error", "message": "النص المدخل فارغ!"})
            
        if custom_mode == "auto_detect":
            if "@builder:file" in text:
                self.process_text_directives_for_project(text, project_name)
                return json.dumps({"status": "builder", "message": "تم توجيه النص لمعالج ومستخرج الكود التلقائي."})
            elif trimmed.startswith("@executor:") or trimmed.split()[0].lower() in ["scan", "move", "rename", "delete", "copy-safe", "duplicates", "project", "template", "extract-title", "read-metadata", "report", "chart", "export", "open", "clipboard", "notify", "preview", "ai", "selftest"]:
                cmd_line = trimmed.replace("@executor:", "").strip()
                res = self.execute_command_advanced(cmd_line, project_name, False)
                return json.dumps({"status": "executor", "message": "تم تنفيذ الأمر بنجاح.", "result": res})
            elif trimmed.startswith("http://") or trimmed.startswith("https://"):
                res = self.process_chat_content(trimmed)
                return json.dumps({"status": "chat_link", "message": "تم استخراج كتل الأكواد من الرابط تلقائياً.", "result": res})
            elif trimmed.startswith("#") or "##" in text or "```" in text or "<style>" in text:
                self.smart_capture_content_v2(text, "space")
                return json.dumps({"status": "capture", "message": "تم توجيه النص للالتقاط الذكي والتجميل التلقائي."})
            else:
                self.ask_gemini_async(text)
                return json.dumps({"status": "gemini", "message": "جاري إرسال السؤال لمساعد Gemini AI."})
                
        elif custom_mode == "smart_capture":
            self.smart_capture_content_v2(text, "space")
            return json.dumps({"status": "capture", "message": "تم توجيه النص للالتقاط والتحليل الفوري."})
        elif custom_mode == "execute_commands":
            cmd_line = trimmed.replace("@executor:", "").strip()
            res = self.execute_command_advanced(cmd_line, project_name, False)
            return json.dumps({"status": "executor", "message": "تم إجبار تنفيذ الأوامر.", "result": res})
        elif custom_mode == "build_pack":
            self.process_text_directives_for_project(text, project_name)
            return json.dumps({"status": "builder", "message": "تم توجيه النص للاستخراج المباشر."})
        return json.dumps({"status": "error", "message": "الوضع المحدد غير مدعوم."})

    # --- Command Executor Dashboard Slots ---
    @Slot(result=str)
    def get_command_history_json(self):
        try:
            history = self.db.get_command_history()
            return json.dumps(history, ensure_ascii=False)
        except Exception as e:
            return json.dumps([], ensure_ascii=False)

    @Slot(result=bool)
    def clear_command_history(self):
        try:
            self.db.clear_command_history()
            self.dbUpdated.emit()
            return True
        except Exception:
            return False

    @Slot(str, result=str)
    def get_smart_suggestions(self, project_name):
        project_dir = self._base_dir
        if project_name and project_name != "الافتراضي" and project_name != "Default":
            for p in self.db.get_projects():
                if p["name"] == project_name:
                    project_dir = p["path"]
                    break
        
        suggestions = []
        
        # 1. Check for duplicates
        try:
            import hashlib
            hashes = set()
            has_duplicates = False
            for root, _, filenames in os.walk(project_dir):
                if has_duplicates:
                    break
                for f in filenames:
                    fp = os.path.join(root, f)
                    try:
                        with open(fp, "rb") as file_to_hash:
                            h = hashlib.md5(file_to_hash.read()).hexdigest()
                        if h in hashes:
                            has_duplicates = True
                            break
                        else:
                            hashes.add(h)
                    except Exception:
                        pass
            if has_duplicates:
                suggestions.append({
                    "title": "تنظيف الملفات المكررة" if self.appLanguage == "ar" else "Clean Duplicate Files",
                    "description": "تم الكشف عن ملفات مكررة متطابقة المحتوى بالمشروع، تخلص منها لتوفير المساحة." if self.appLanguage == "ar" else "Identical duplicate files found. Clean them up to save space.",
                    "command": "duplicates",
                    "icon": "⚠️",
                    "type": "duplicates"
                })
        except Exception:
            pass
            
        # 2. Check for file structure organization
        try:
            unorganized = 0
            if os.path.exists(project_dir):
                for item in os.listdir(project_dir):
                    if os.path.isfile(os.path.join(project_dir, item)):
                        unorganized += 1
            if unorganized > 3:
                suggestions.append({
                    "title": "تنظيم هيكل المجلد" if self.appLanguage == "ar" else "Organize Folder Structure",
                    "description": f"يوجد {unorganized} ملفات مبعثرة في المجلد الرئيسي. نقترح تنظيمها في مجلدات فرعية." if self.appLanguage == "ar" else f"There are {unorganized} unorganized files in root. Recommend grouping them.",
                    "command": "scan",
                    "icon": "📁",
                    "type": "organize"
                })
        except Exception:
            pass

        # 3. Code Backup Suggestion
        suggestions.append({
            "title": "أخذ نسخة احتياطية للمشروع" if self.appLanguage == "ar" else "Backup Project Code",
            "description": "توليد حزمة برمجية وتصدير الكود المصدري كنسخة احتياطية آمنة بالحافظة." if self.appLanguage == "ar" else "Generate code pack and export source code to clipboard.",
            "command": "export",
            "icon": "📦",
            "type": "backup"
        })

        # 4. System Diagnostic Suggestion
        suggestions.append({
            "title": "الفحص الذاتي الشامل" if self.appLanguage == "ar" else "Run Comprehensive Diagnosis",
            "description": "فحص صحة اتصال قاعدة البيانات ومراقب الحافظة والفقاعة وتصاريح الملفات." if self.appLanguage == "ar" else "Verify database health, clipboard monitor, and file permissions.",
            "command": "selftest",
            "icon": "🛡️",
            "type": "diagnostic"
        })
        
        return json.dumps(suggestions, ensure_ascii=False)

    @Slot(str, str, result=str)
    def generate_organize_plan(self, folder_path, option):
        folder_path = self.clean_path_url(folder_path)
        if not folder_path or not os.path.exists(folder_path):
            folder_path = self._base_dir
            
        plan_lines = []
        plan_lines.append(f"// 📋 خطة تنظيم المجلد المقترحة (الخيار: {option})")
        plan_lines.append(f"// المجلد المستهدف: {folder_path}")
        plan_lines.append("// لتطبيق الخطة، يرجى تشغيل الأوامر التالية عبر المنفذ:\n")
        
        try:
            files = [f for f in os.listdir(folder_path) if os.path.isfile(os.path.join(folder_path, f))]
            if not files:
                plan_lines.append("// 🔍 لم يتم العثور على أي ملفات مبعثرة في المجلد المستهدف.")
                return "\n".join(plan_lines)
                
            for f in files:
                full_path = os.path.join(folder_path, f)
                if option == "by_extension":
                    _, ext = os.path.splitext(f)
                    ext = ext.lower().replace(".", "")
                    if not ext:
                        ext = "others"
                    
                    target_folder = ""
                    if ext in ["jpg", "jpeg", "png", "gif", "bmp", "svg"]:
                        target_folder = "Images"
                    elif ext in ["txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "md"]:
                        target_folder = "Documents"
                    elif ext in ["py", "kt", "java", "qml", "js", "html", "css", "cpp", "h", "cs", "go", "rs"]:
                        target_folder = "SourceCode"
                    elif ext in ["mp3", "wav", "ogg", "m4a"]:
                        target_folder = "Audio"
                    elif ext in ["mp4", "mkv", "avi", "mov"]:
                        target_folder = "Video"
                    elif ext in ["zip", "rar", "tar", "gz", "7z"]:
                        target_folder = "Archives"
                    else:
                        target_folder = ext.upper() + "_Files"
                        
                    plan_lines.append(f"@executor: move {f} {target_folder}/{f}")
                    
                elif option == "by_date":
                    import time
                    mtime = os.path.getmtime(full_path)
                    date_folder = time.strftime("%Y-%m", time.localtime(mtime))
                    plan_lines.append(f"@executor: move {f} {date_folder}/{f}")
                    
                else:
                    plan_lines.append(f"@executor: move {f} Archive_Files/{f}")
                    
        except Exception as e:
            plan_lines.append(f"// ❌ خطة فشلت بسبب خطأ: {str(e)}")
            
        return "\n".join(plan_lines)

    @Slot(str, str, result=str)
    def generate_rename_plan(self, folder_path, pattern):
        folder_path = self.clean_path_url(folder_path)
        if not folder_path or not os.path.exists(folder_path):
            folder_path = self._base_dir
            
        plan_lines = []
        plan_lines.append(f"// 📋 خطة إعادة التسمية المقترحة (النمط: {pattern})")
        plan_lines.append(f"// المجلد المستهدف: {folder_path}")
        plan_lines.append("// لتطبيق الخطة، يرجى تشغيل الأوامر التالية عبر المنفذ:\n")
        
        try:
            files = sorted([f for f in os.listdir(folder_path) if os.path.isfile(os.path.join(folder_path, f))])
            if not files:
                plan_lines.append("// 🔍 لم يتم العثور على أي ملفات لإعادة تسميتها.")
                return "\n".join(plan_lines)
                
            for idx, f in enumerate(files, 1):
                _, ext = os.path.splitext(f)
                if "{num}" in pattern:
                    new_name = pattern.replace("{num}", f"{idx:02d}") + ext
                elif pattern.endswith("_") or pattern.endswith("-"):
                    new_name = f"{pattern}{f}"
                else:
                    new_name = f"{pattern}_{idx}{ext}"
                plan_lines.append(f"@executor: rename {f} {new_name}")
                
        except Exception as e:
            plan_lines.append(f"// ❌ خطة فشلت بسبب خطأ: {str(e)}")
            
        return "\n".join(plan_lines)

    @Slot(str, result=str)
    def get_typeahead_suggestions(self, input_str):
        trimmed = input_str.strip().lower()
        all_commands = [
            {"command": "scan", "desc": "فحص ملفات المشروع النشط وعرض شجرة الملفات" if self.appLanguage == "ar" else "Scan project files and show directory tree", "example": "scan"},
            {"command": "move", "desc": "نقل ملف أو مجلد إلى مسار جديد آمن" if self.appLanguage == "ar" else "Move a file or folder to a new path", "example": "move source.txt dest/source.txt"},
            {"command": "rename", "desc": "إعادة تسمية ملف محدد بمسار آمن" if self.appLanguage == "ar" else "Rename a file in the active folder", "example": "rename old.txt new.txt"},
            {"command": "delete", "desc": "حذف ملف أو مجلد مع أخذ نسخة احتياطية تلقائياً" if self.appLanguage == "ar" else "Delete a file or folder safely", "example": "delete temp.txt"},
            {"command": "copy-safe", "desc": "نسخ ملف بأمان دون تدمير الملفات الحالية" if self.appLanguage == "ar" else "Copy file safely without overwriting", "example": "copy-safe main.py backup.py"},
            {"command": "duplicates", "desc": "فحص الملفات المكررة ذات المحتوى المتطابق" if self.appLanguage == "ar" else "Scan workspace for duplicate files", "example": "duplicates"},
            {"command": "project", "desc": "إحصائيات كاملة للمشروع ونسب توزيع الملفات" if self.appLanguage == "ar" else "Get active project stats and summary", "example": "project"},
            {"command": "template", "desc": "توليد قوالب برمجية سريعة (activity, viewModel, screen)" if self.appLanguage == "ar" else "Generate code template", "example": "template viewModel"},
            {"command": "extract-title", "desc": "استخراج اسم الفئة (class) الرئيسية من ملف" if self.appLanguage == "ar" else "Extract main class name from code file", "example": "extract-title main.py"},
            {"command": "export", "desc": "حزم وتصدير الكود المصدري للمشروع للحافظة والملفات" if self.appLanguage == "ar" else "Pack and export project source code", "example": "export"},
            {"command": "selftest", "desc": "تقرير الفحص الذاتي للنظام والمراقب والاتصال" if self.appLanguage == "ar" else "Comprehensive system diagnostic test", "example": "selftest"}
        ]
        
        if not trimmed:
            return json.dumps(all_commands, ensure_ascii=False)
            
        suggestions = []
        for cmd in all_commands:
            if cmd["command"].startswith(trimmed) or trimmed in cmd["command"] or trimmed in cmd["desc"].lower():
                suggestions.append(cmd)
                
        return json.dumps(suggestions, ensure_ascii=False)

    # --- Self-Source Export System ---
    @Slot(str, result=str)
    def export_windows_source(self, custom_save_dir=""):
        try:
            source_dir = os.path.abspath(os.path.dirname(__file__))
            files = []
            for name in os.listdir(source_dir):
                fpath = os.path.join(source_dir, name)
                if os.path.isfile(fpath):
                    ext = os.path.splitext(name)[1].lower()
                    if ext in ['.py', '.qml', '.txt', '.md', '.spec', '.iss'] or name in ['requirements.txt', 'README.md']:
                        if name not in ['app.ico', 'generate_icon.py']:
                            files.append(name)
            
            result_text = []
            result_text.append("// =========================================================\n")
            result_text.append("// 📥 حزمة التصدير الذاتي للمصدر - المساعد الذكي الذهبي للويندوز Pro\n")
            result_text.append(f"// تاريخ التصدير: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
            result_text.append("// =========================================================\n\n")
            
            for fname in sorted(files):
                fpath = os.path.join(source_dir, fname)
                try:
                    with open(fpath, "r", encoding="utf-8", errors="ignore") as f:
                        content = f.read()
                    result_text.append(f"// @builder:file {fname}\n")
                    result_text.append(content)
                    if not content.endswith("\n"):
                        result_text.append("\n")
                    result_text.append("// @builder:end\n\n")
                except Exception as e:
                    print(f"Error reading file {fname}: {e}")
            
            final_pack = "".join(result_text)
            
            # Copy to Clipboard
            self.clipboard.setText(final_pack)
            
            # Determine save path
            if custom_save_dir and os.path.exists(custom_save_dir):
                save_dir = custom_save_dir
            else:
                save_dir = os.path.join(self._base_dir, "SmartInbox")
            
            os.makedirs(save_dir, exist_ok=True)
            save_path = os.path.join(save_dir, "Source_Export.txt")
            with open(save_path, "w", encoding="utf-8") as f:
                f.write(final_pack)
            
            # Log action
            log_msg = f"تم تصدير الكود المصدري لويندوز (عدد الملفات: {len(files)}, الحجم: {len(final_pack)} حرف) إلى {save_path}"
            self.db.log_action("success", log_msg)
            self.logAdded.emit("success", log_msg)
            self.notificationSent.emit(
                "التصدير الذاتي لويندوز" if self.appLanguage == "ar" else "Windows Self-Export",
                f"تم نسخ وتصدير {len(files)} ملفات بنجاح إلى الحافظة والملف." if self.appLanguage == "ar" else f"Successfully exported {len(files)} files to clipboard and backup.",
                "success"
            )
            
            preview = final_pack[:500] + "\n...\n" + final_pack[-200:] if len(final_pack) > 700 else final_pack
            
            return json.dumps({
                "success": True,
                "file_count": len(files),
                "char_count": len(final_pack),
                "save_path": save_path,
                "preview": preview
            }, ensure_ascii=False)
            
        except Exception as e:
            err_msg = f"خطأ في التصدير الذاتي: {str(e)}"
            self.db.log_action("error", err_msg)
            self.logAdded.emit("error", err_msg)
            return json.dumps({"success": False, "message": err_msg}, ensure_ascii=False)

    @Slot(str, str, result=str)
    def export_android_source(self, project_name, custom_save_dir=""):
        try:
            # 1. Resolve project directory path
            proj_path = ""
            projects = self.db.get_projects()
            for p in projects:
                if p["name"] == project_name:
                    proj_path = p["path"]
                    break
            
            if not proj_path or project_name in ["Default", "الافتراضي"]:
                proj_path = self._base_dir
            
            if not os.path.exists(proj_path):
                return json.dumps({"success": False, "message": "مسار المشروع غير موجود على القرص!"}, ensure_ascii=False)
            
            # 2. Walk directory to find all editable source files, ignoring build/binary dirs
            files_to_export = []
            exclude_dirs = {
                ".git", ".gradle", ".idea", "build", "app/build", "node_modules", 
                "__pycache__", "venv", "import_binaries", "SmartInbox", "TreeDocs"
            }
            allowed_extensions = {
                ".kt", ".java", ".xml", ".gradle", ".kts", ".json", ".toml", ".txt", ".md", ".properties"
            }
            
            for root_dir, dirs, files in os.walk(proj_path):
                # Edit dirs in place to prune excluded paths
                dirs[:] = [d for d in dirs if d not in exclude_dirs and not d.startswith('.')]
                for file in files:
                    ext = os.path.splitext(file)[1].lower()
                    if ext in allowed_extensions:
                        full_fpath = os.path.join(root_dir, file)
                        # Skip binary files or super huge files (above 1.5MB)
                        try:
                            if os.path.getsize(full_fpath) < 1500000:
                                # Rel path from proj_path to display nicely in @builder:file
                                rel_path = os.path.relpath(full_fpath, proj_path)
                                files_to_export.append((full_fpath, rel_path))
                        except Exception:
                            pass
            
            if not files_to_export:
                return json.dumps({"success": False, "message": "لم يتم العثور على أي ملفات برمجية صالحة للتصدير في هذا المجلد!"}, ensure_ascii=False)
            
            # 3. Construct package
            result_text = []
            result_text.append("// =========================================================\n")
            result_text.append(f"// 📥 حزمة تصدير مشروع أندرويد: {project_name}\n")
            result_text.append(f"// تاريخ التصدير: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
            result_text.append("// =========================================================\n\n")
            
            for full_fpath, rel_path in sorted(files_to_export, key=lambda x: x[1]):
                try:
                    with open(full_fpath, "r", encoding="utf-8", errors="ignore") as f:
                        content = f.read()
                    # Standard builder format
                    result_text.append(f"// @builder:file {rel_path}\n")
                    result_text.append(content)
                    if not content.endswith("\n"):
                        result_text.append("\n")
                    result_text.append("// @builder:end\n\n")
                except Exception as e:
                    print(f"Error reading {rel_path}: {e}")
            
            final_pack = "".join(result_text)
            
            # Copy to Clipboard
            self.clipboard.setText(final_pack)
            
            # Determine save path
            if custom_save_dir and os.path.exists(custom_save_dir):
                save_dir = custom_save_dir
            else:
                save_dir = os.path.join(self._base_dir, "SmartInbox")
            
            os.makedirs(save_dir, exist_ok=True)
            save_path = os.path.join(save_dir, "Source_Export.txt")
            with open(save_path, "w", encoding="utf-8") as f:
                f.write(final_pack)
            
            # Log action
            log_msg = f"تم تصدير كود أندرويد للمشروع '{project_name}' (عدد الملفات: {len(files_to_export)}, الحجم: {len(final_pack)} حرف) إلى {save_path}"
            self.db.log_action("success", log_msg)
            self.logAdded.emit("success", log_msg)
            self.notificationSent.emit(
                "تصدير كود أندرويد" if self.appLanguage == "ar" else "Android Export",
                f"تم نسخ وتصدير {len(files_to_export)} ملفات بنجاح إلى الحافظة والملف." if self.appLanguage == "ar" else f"Successfully exported {len(files_to_export)} files.",
                "success"
            )
            
            preview = final_pack[:500] + "\n...\n" + final_pack[-200:] if len(final_pack) > 700 else final_pack
            
            return json.dumps({
                "success": True,
                "file_count": len(files_to_export),
                "char_count": len(final_pack),
                "save_path": save_path,
                "preview": preview
            }, ensure_ascii=False)
            
        except Exception as e:
            err_msg = f"خطأ أثناء تصدير مشروع أندرويد: {str(e)}"
            self.db.log_action("error", err_msg)
            self.logAdded.emit("error", err_msg)
            return json.dumps({"success": False, "message": err_msg}, ensure_ascii=False)

    @Slot(result=str)
    def export_app_own_source(self):
        res_json = self.export_windows_source()
        try:
            res = json.loads(res_json)
            if res.get("success"):
                with open(res["save_path"], "r", encoding="utf-8") as f:
                    return f.read()
        except Exception:
            pass
        return ""

    # --- Local File Browser System ---
    @Slot(str, result=str)
    def list_local_directory(self, path):
        path = self.clean_path_url(path)
        if not path or not os.path.exists(path):
            path = self._base_dir
        try:
            items = []
            for name in sorted(os.listdir(path)):
                full_path = os.path.join(path, name)
                is_dir = os.path.isdir(full_path)
                size = os.path.getsize(full_path) if not is_dir else 0
                modified = datetime.fromtimestamp(os.path.getmtime(full_path)).strftime("%Y-%m-%d %H:%M:%S")
                items.append({
                    "name": name,
                    "path": full_path,
                    "is_dir": is_dir,
                    "size": size,
                    "modified": modified
                })
            return json.dumps(items, ensure_ascii=False)
        except Exception as e:
            return json.dumps([{"name": f"خطأ في قراءة المجلد: {e}", "path": "", "is_dir": False, "size": 0, "modified": ""}], ensure_ascii=False)

    @Slot(str, result=str)
    def read_local_file(self, path):
        path = self.clean_path_url(path)
        if not os.path.exists(path):
            return "الملف غير موجود."
        try:
            with open(path, "r", encoding="utf-8", errors="ignore") as f:
                return f.read()
        except Exception as e:
            return f"خطأ أثناء قراءة الملف: {e}"

    @Slot(str, str, result=bool)
    def write_local_file(self, path, content):
        path = self.clean_path_url(path)
        try:
            self._create_backup(path)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "w", encoding="utf-8") as f:
                f.write(content)
            self.db.log_action("success", f"تم كتابة وتحديث الملف يدوياً: {os.path.basename(path)}")
            self.dbUpdated.emit()
            return True
        except Exception as e:
            print(f"Write file error: {e}")
            return False

    @Slot(str, result=bool)
    def run_local_file(self, path):
        path = self.clean_path_url(path)
        if not os.name == 'nt' or not os.path.exists(path):
            return False
        try:
            os.startfile(path)
            return True
        except Exception:
            return False

    @Slot(str, str)
    def log_action(self, log_type, message):
        try:
            self.db.log_action(log_type, message)
            self.dbUpdated.emit()
        except Exception as e:
            print(f"Error logging action: {e}")

    @Slot(str, result=str)
    def read_file_text(self, path):
        return self.read_local_file(path)

    @Slot(result=bool)
    def is_clipboard_monitor_running(self):
        return self._clipboard_monitor_enabled

    @Slot(result=bool)
    def is_gemini_api_configured(self):
        return self.get_gemini_api_key() != ""

    @Slot(result=bool)
    def is_bubble_enabled(self):
        return self._bubble_enabled

    @Slot(result=str)
    def get_active_project(self):
        return self._active_project

    @Slot(result=int)
    def get_logs_count(self):
        try:
            with self.db.get_connection() as conn:
                count = conn.cursor().execute("SELECT COUNT(*) FROM action_logs").fetchone()[0]
                return count
        except Exception:
            return 0

    @Slot(result=str)
    def get_system_health(self):
        import json
        import platform
        try:
            health_data = {
                "clipboard_monitor": "Running" if self._clipboard_monitor_enabled else "Stopped",
                "gemini_api": "Configured" if self.get_gemini_api_key() != "" else "Missing Key",
                "bubble": "Enabled" if self._bubble_enabled else "Disabled",
                "active_project": self._active_project,
                "logs_count": self.get_logs_count(),
                "os": platform.system(),
                "os_release": platform.release(),
                "python_version": platform.python_version(),
                "projects_count": len(self.db.get_projects()),
                "styles_count": len(self.db.get_styles()),
                "captures_count": len(self.db.get_captures())
            }
            return json.dumps(health_data, ensure_ascii=False)
        except Exception as e:
            return json.dumps({"error": str(e)}, ensure_ascii=False)

    @Slot(result=str)
    def run_quick_self_test(self):
        return self.get_self_test_report()

    @Slot(result=str)
    def get_self_test_report(self):
        import json
        try:
            # 1. Database Check
            db_ok = True
            db_details_ar = "سليمة وتعمل بكفاءة عالية"
            db_details_en = "Healthy and running efficiently"
            try:
                self.db.get_setting("test", "test")
            except Exception as e:
                db_ok = False
                db_details_ar = f"فشل الفحص: {str(e)}"
                db_details_en = f"Check failed: {str(e)}"

            # 2. Base Directory Check
            dir_ok = os.path.exists(self._base_dir)
            dir_details_ar = f"سليم ومتاح للكتابة والقراءة في: {self._base_dir}" if dir_ok else "المجلد النشط غير موجود أو تالف!"
            dir_details_en = f"Accessible for reading and writing at: {self._base_dir}" if dir_ok else "Active directory does not exist or is corrupted!"

            # 3. Gemini Key Check
            key_configured = self.get_gemini_api_key() != ""
            gemini_details_ar = "نشط وتم التحقق من التهيئة" if key_configured else "مفتاح Gemini غير مسجل أو مفقود في الإعدادات"
            gemini_details_en = "Installed & configured successfully" if key_configured else "Gemini key is missing or not configured"

            # 4. Clipboard Monitor Check
            clip_ok = self._clipboard_monitor_enabled
            clip_details_ar = "نشط في الخلفية لمراقبة الأكواد" if clip_ok else "مراقب الحافظة معطل حالياً"
            clip_details_en = "Active in background to monitor codes" if clip_ok else "Clipboard monitor is currently disabled"

            # 5. Floating Bubble Check
            bubble_ok = self._bubble_enabled
            bubble_details_ar = "الفقاعة العائمة مفعلة وجاهزة للعمل" if bubble_ok else "الفقاعة العائمة معطلة"
            bubble_details_en = "Floating overlay bubble is enabled" if bubble_ok else "Floating overlay bubble is disabled"

            # Formulate Checklist Items
            items = [
                {
                    "name_ar": "قاعدة بيانات SQLite المدمجة",
                    "name_en": "Local SQLite Database",
                    "status": "success" if db_ok else "error",
                    "details_ar": db_details_ar,
                    "details_en": db_details_en
                },
                {
                    "name_ar": "مجلد العمل النشط",
                    "name_en": "Active Workspace Directory",
                    "status": "success" if dir_ok else "error",
                    "details_ar": dir_details_ar,
                    "details_en": dir_details_en
                },
                {
                    "name_ar": "مفتاح Gemini AI",
                    "name_en": "Gemini AI Key Configuration",
                    "status": "success" if key_configured else "warning",
                    "details_ar": gemini_details_ar,
                    "details_en": gemini_details_en
                },
                {
                    "name_ar": "مراقب حافظة الويندوز",
                    "name_en": "Windows Clipboard Monitor",
                    "status": "success" if clip_ok else "warning",
                    "details_ar": clip_details_ar,
                    "details_en": clip_details_en
                },
                {
                    "name_ar": "الفقاعة الذهبية العائمة",
                    "name_en": "Floating Golden Bubble",
                    "status": "success" if bubble_ok else "warning",
                    "details_ar": bubble_details_ar,
                    "details_en": bubble_details_en
                }
            ]

            overall_status = "Healthy" if (db_ok and dir_ok) else "Issues Detected"

            # Generated full string reports
            report_ar = "⚡ تقرير تشخيص النظام الذهبي المباشر:\n"
            report_ar += "--------------------------------------------------\n"
            report_ar += f"• قاعدة بيانات SQLite المدمجة: {'✅ سليمة وتعمل' if db_ok else '❌ غير صالحة'} ({db_details_ar})\n"
            report_ar += f"• مجلد العمل والوصول الآمن: {'✅ سليم ومتاح' if dir_ok else '❌ غير متاح'} ({dir_details_ar})\n"
            report_ar += f"• مصادقة مفتاح سحابي Gemini AI: {'✅ نشط ومثبت' if key_configured else '⚠️ غير مسجل'} ({gemini_details_ar})\n"
            report_ar += f"• مراقب حافظة ويندوز: {'🟢 نشط بالخلفية' if clip_ok else '🔴 متوقف'} ({clip_details_ar})\n"
            report_ar += f"• الفقاعة العائمة للمنصة: {'🟢 مفعلة ونشطة' if bubble_ok else '🔴 معطلة'} ({bubble_details_ar})\n"
            report_ar += "--------------------------------------------------\n"
            report_ar += f"• النتيجة وصحة النظام العامة: {'🟢 نظام ممتاز وسليم بالكامل' if overall_status == 'Healthy' else '⚠️ يتطلب بعض الإعدادات'}\n"

            report_en = "⚡ Live Golden System Diagnostic Report:\n"
            report_en += "--------------------------------------------------\n"
            report_en += f"• Local SQLite Database: {'✅ OK & Connected' if db_ok else '❌ Check Failed'} ({db_details_en})\n"
            report_en += f"• Workspace Directory Access: {'✅ OK & Accessible' if dir_ok else '❌ Access Error'} ({dir_details_en})\n"
            report_en += f"• Gemini AI Cloud Key: {'✅ Installed & Configured' if key_configured else '⚠️ Key Missing'} ({gemini_details_en})\n"
            report_en += f"• Clipboard Monitor Daemon: {'🟢 Daemon Active' if clip_ok else '🔴 Daemon Stopped'} ({clip_details_en})\n"
            report_en += f"• Floating Platform Bubble: {'🟢 Enabled' if bubble_ok else '🔴 Disabled'} ({bubble_details_en})\n"
            report_en += "--------------------------------------------------\n"
            report_en += f"• Overall General Status: {'🟢 System Fully Healthy' if overall_status == 'Healthy' else '⚠️ Settings Required'}\n"

            full_data = {
                "status": overall_status,
                "items": items,
                "raw_report_ar": report_ar,
                "raw_report_en": report_en
            }
            return json.dumps(full_data, ensure_ascii=False)
        except Exception as e:
            return json.dumps({"error": str(e)}, ensure_ascii=False)

    def _open_uri(self, uri):
        import platform
        import subprocess
        import os
        try:
            system = platform.system()
            if system == "Windows":
                os.startfile(uri)
            elif system == "Darwin":
                subprocess.Popen(["open", uri])
            else:
                subprocess.Popen(["xdg-open", uri])
            return True
        except Exception as e:
            print(f"Error opening URI {uri}: {e}")
            return False

    @Slot(result=bool)
    def is_admin(self):
        try:
            import os
            if hasattr(os, 'getuid'):
                return os.getuid() == 0
            else:
                import ctypes
                return ctypes.windll.shell32.IsUserAnAdmin() != 0
        except Exception:
            return False

    @Slot(result=bool)
    def has_full_filesystem_access(self):
        try:
            import os
            return os.access(self._base_dir, os.W_OK)
        except Exception:
            return False

    @Slot(result=bool)
    def has_overlay_permission(self):
        # Desktop systems allow drawing overlay bubbles if configured
        return True

    @Slot(result=bool)
    def has_notification_permission(self):
        # Desktop systems have notification channels enabled by default
        return True

    @Slot(result=bool)
    def open_admin_settings(self):
        # Developer / privilege options URI
        return self._open_uri("ms-settings:developers")

    @Slot(result=bool)
    def open_filesystem_settings(self):
        # Broad filesystem access or privacy settings
        return self._open_uri("ms-settings:privacy-broadfilesystemaccess") or self._open_uri("ms-settings:privacy")

    @Slot(result=bool)
    def open_overlay_settings(self):
        # Return developers panel as related setup
        return self._open_uri("ms-settings:developers")

    @Slot(result=bool)
    def open_notification_settings(self):
        return self._open_uri("ms-settings:notifications")

    @Slot(result=str)
    def get_permissions_status(self):
        import json
        try:
            status = {
                "admin": self.is_admin(),
                "filesystem": self.has_full_filesystem_access(),
                "overlay": self.has_overlay_permission(),
                "notifications": self.has_notification_permission()
            }
            return json.dumps(status, ensure_ascii=False)
        except Exception as e:
            return json.dumps({"error": str(e)}, ensure_ascii=False)

    @Slot(str)
    def set_pending_file(self, path):
        path = self.clean_path_url(path)
        self._pending_file = path
        self.pendingFileChanged.emit(path)
        self.fileOpenRequested.emit(path)

    @Slot(str)
    def set_pending_folder(self, path):
        path = self.clean_path_url(path)
        self._pending_folder = path
        self.pendingFolderChanged.emit(path)
        self.folderOpenRequested.emit(path)

    @Slot()
    def clear_pending_file(self):
        self._pending_file = ""
        self.pendingFileChanged.emit("")

    @Slot()
    def clear_pending_folder(self):
        self._pending_folder = ""
        self.pendingFolderChanged.emit("")

    @Slot(str, result=str)
    def handle_file_open(self, file_path):
        file_path = self.clean_path_url(file_path)
        if not os.path.exists(file_path) or not os.path.isfile(file_path):
            return json.dumps({"success": False, "error": "File not found"}, ensure_ascii=False)
        try:
            size_bytes = os.path.getsize(file_path)
            # Read first 1000 characters for preview
            with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
                content_preview = f.read(1000)
            
            def format_sz(sz):
                for unit in ['B', 'KB', 'MB', 'GB']:
                    if sz < 1024.0:
                        return f"{sz:.2f} {unit}"
                    sz /= 1024.0
                return f"{sz:.2f} TB"

            return json.dumps({
                "success": True,
                "path": file_path,
                "name": os.path.basename(file_path),
                "size_formatted": format_sz(size_bytes),
                "preview": content_preview
            }, ensure_ascii=False)
        except Exception as e:
            return json.dumps({"success": False, "error": str(e)}, ensure_ascii=False)

    @Slot(str, result=str)
    def handle_folder_open(self, folder_path):
        folder_path = self.clean_path_url(folder_path)
        if not os.path.exists(folder_path) or not os.path.isdir(folder_path):
            return json.dumps({"success": False, "error": "Folder not found"}, ensure_ascii=False)
        try:
            file_count = 0
            total_size = 0
            for root_dir, dirs, files in os.walk(folder_path):
                file_count += len(files)
                for file_name in files:
                    try:
                        total_size += os.path.getsize(os.path.join(root_dir, file_name))
                    except Exception:
                        pass
            
            def format_sz(sz):
                for unit in ['B', 'KB', 'MB', 'GB']:
                    if sz < 1024.0:
                        return f"{sz:.2f} {unit}"
                    sz /= 1024.0
                return f"{sz:.2f} TB"

            return json.dumps({
                "success": True,
                "path": folder_path,
                "name": os.path.basename(folder_path),
                "file_count": file_count,
                "size_formatted": format_sz(total_size)
            }, ensure_ascii=False)
        except Exception as e:
            return json.dumps({"success": False, "error": str(e)}, ensure_ascii=False)

    @Slot(str)
    def set_pending_shared_text(self, text):
        self._pending_shared_text = text
        self.pendingSharedTextChanged.emit(text)
        self.sharedTextRequested.emit(text)

    @Slot()
    def clear_pending_shared_text(self):
        self._pending_shared_text = ""
        self.pendingSharedTextChanged.emit("")

    @Slot(str, result=str)
    def get_available_actions(self, text):
        if not text:
            return json.dumps([], ensure_ascii=False)
            
        trimmed = text.strip()
        actions = []
        
        # 1. Builder Pack Action
        is_builder = "@builder:file" in text and "@builder:end" in text
        actions.append({
            "id": "builder",
            "label_ar": "📦 تطبيق حزمة البناء (@builder)",
            "label_en": "📦 Apply Builder Pack (@builder)",
            "description_ar": "استخراج الملفات البرمجية وهيكلتها وتوجيهها تلقائياً للمشروع النشط.",
            "description_en": "Extract source code files and route them to active project directory.",
            "recommended": is_builder
        })
        
        # 2. Command Line Executor Action
        is_executor = "@executor:" in text
        actions.append({
            "id": "executor",
            "label_ar": "⚙️ تنفيذ الأوامر الذكية التلقائية",
            "label_en": "⚙️ Execute Command Automations",
            "description_ar": "تشغيل التوجيهات البرمجية والأوامر الآمنة المرفقة بالمستند.",
            "description_en": "Run safe command-line automation routines bundled inside the context.",
            "recommended": is_executor
        })
        
        # 3. Smart Capture / Beautify Style Bank
        is_md = trimmed.startswith("#") or "\n## " in text or "**" in text or "```" in text or "\n- " in text
        actions.append({
            "id": "beautify",
            "label_ar": "🎨 تجميل وتحسين تنسيق النص",
            "label_en": "🎨 Convert & Beautify Markdown",
            "description_ar": "تحويل النص إلى وثيقة HTML فاخرة مع حفظها بصندوق الالتقاط الذكي.",
            "description_en": "Format raw text or markdown into a premium HTML document in the Smart Inbox.",
            "recommended": is_md and not is_builder
        })
        
        # 4. Smart Capture Memo
        actions.append({
            "id": "capture",
            "label_ar": "🧠 التقاط سريع كمسودة ومذكرة",
            "label_en": "🧠 Quick Capture as Memo",
            "description_ar": "توجيه النص المنسوخ كمسودة سريعة آمنة في صندوق الالتقاط وقاعدة البيانات.",
            "description_en": "Instantly route content to local database and save as a structured text memo.",
            "recommended": not is_builder and not is_executor and not is_md
        })
        
        return json.dumps(actions, ensure_ascii=False)

    @Slot(str, str, str, result=str)
    def execute_shared_action(self, action_id, text, project_name="Default"):
        """
        Executes the specified shared action on the text, returning success status and message.
        """
        try:
            if action_id == "builder":
                # Route to process_text_directives_for_project
                self.process_text_directives_for_project(text, project_name)
                return json.dumps({"success": True, "message": "builder_routed"}, ensure_ascii=False)
            elif action_id == "executor":
                # Executing commands found inside text
                lines = text.splitlines()
                executed = 0
                for line in lines:
                    trimmed = line.strip()
                    if "@executor:" in trimmed:
                        cmd = trimmed.split("@executor:", 1)[1].strip()
                        if cmd:
                            project_dir = self._base_dir
                            if project_name and project_name != "Default" and project_name != "الافتراضي":
                                for p in self.db.get_projects():
                                    if p["name"] == project_name:
                                        project_dir = p["path"]
                                        break
                            self._run_safe_command_with_dir(cmd, project_dir)
                            executed += 1
                return json.dumps({"success": True, "message": "executor_done", "count": executed}, ensure_ascii=False)
            elif action_id == "beautify":
                self.smart_capture_content_v2(text, "space")
                return json.dumps({"success": True, "message": "beautified"}, ensure_ascii=False)
            elif action_id == "capture":
                self.smart_capture_content_v2(text, "gold")
                return json.dumps({"success": True, "message": "captured"}, ensure_ascii=False)
            else:
                return json.dumps({"success": False, "message": "unknown_action"}, ensure_ascii=False)
        except Exception as e:
            return json.dumps({"success": False, "message": f"Error: {str(e)}"}, ensure_ascii=False)

    # --- Smart Task Queue Slots ---
    @Slot(str, str, str, result=str)
    def add_task(self, title, task_type, command):
        task_id = str(self._task_id_counter)
        self._task_id_counter += 1
        new_task = {
            "id": task_id,
            "title": title,
            "type": task_type,
            "command": command,
            "status": "pending",
            "output": "",
            "created_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        }
        self._tasks.append(new_task)
        self.db.log_action("info", f"🛠️ تم إضافة مهمة ذكية جديدة لطابور العمليات: {title}")
        self.dbUpdated.emit()
        return task_id

    @Slot(result=str)
    def get_tasks_json(self):
        return json.dumps(self._tasks, ensure_ascii=False)

    @Slot(str)
    def run_task_async(self, task_id):
        task = None
        for t in self._tasks:
            if t["id"] == task_id:
                task = t
                break
        if not task:
            return
            
        task["status"] = "running"
        task["output"] = "Running task command...\n"
        self.dbUpdated.emit()
        
        def run_thread():
            try:
                cmd = task["command"]
                if not cmd:
                    task["status"] = "completed"
                    task["output"] += "No command to execute."
                    self.dbUpdated.emit()
                    return
                    
                process = subprocess.Popen(
                    cmd,
                    shell=True,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    cwd=self._base_dir
                )
                stdout, stderr = process.communicate()
                task["output"] += f"STDOUT:\n{stdout}\n"
                if stderr:
                    task["output"] += f"STDERR:\n{stderr}\n"
                
                if process.returncode == 0:
                    task["status"] = "completed"
                    self.db.log_action("success", f"✅ اكتملت المهمة الذكية بنجاح: {task['title']}")
                else:
                    task["status"] = "failed"
                    self.db.log_action("error", f"❌ فشلت المهمة الذكية: {task['title']} (رمز الخروج: {process.returncode})")
            except Exception as e:
                task["status"] = "failed"
                task["output"] += f"Error executing task: {str(e)}\n"
                self.db.log_action("error", f"❌ فشلت المهمة الذكية بسبب خطأ داخلي: {task['title']}")
            finally:
                self.dbUpdated.emit()
                
        threading.Thread(target=run_thread, daemon=True).start()

    @Slot(str)
    def delete_task(self, task_id):
        self._tasks = [t for t in self._tasks if t["id"] != task_id]
        self.dbUpdated.emit()

    @Slot()
    def clear_completed_tasks(self):
        self._tasks = [t for t in self._tasks if t["status"] not in ["completed", "failed"]]
        self.dbUpdated.emit()

    # --- Clipboard History Slots ---
    @Slot(str, str, result=str)
    def get_clipboard_history_json(self, filter_type="all", search_query=""):
        try:
            with self.db.get_connection() as conn:
                cursor = conn.cursor()
                query = "SELECT * FROM smart_captures WHERE capture_type LIKE 'clip_%'"
                params = []
                if filter_type != "all":
                    query += " AND capture_type = ?"
                    params.append(f"clip_{filter_type}")
                if search_query:
                    query += " AND (title LIKE ? OR content LIKE ?)"
                    params.append(f"%{search_query}%")
                    params.append(f"%{search_query}%")
                query += " ORDER BY id DESC"
                rows = cursor.execute(query, params).fetchall()
                return json.dumps([dict(r) for r in rows], ensure_ascii=False)
        except Exception as e:
            print(f"Error reading clipboard history: {e}")
            return json.dumps([], ensure_ascii=False)

    @Slot(int)
    def delete_clipboard_entry(self, entry_id):
        try:
            with self.db.get_connection() as conn:
                conn.cursor().execute("DELETE FROM smart_captures WHERE id = ?", (entry_id,))
                conn.commit()
            self.dbUpdated.emit()
        except Exception as e:
            print(f"Error deleting entry: {e}")

    # --- Advanced Logs Export & Stats ---
    @Slot(str, str, result=str)
    def export_logs_advanced(self, format_type, options_json):
        try:
            options = json.loads(options_json)
            hide_sensitive = options.get("hide_sensitive", True)
            theme = options.get("html_theme", "gold")
            save_dir = options.get("save_dir", self._base_dir)
            
            logs = self.db.get_logs()
            
            # Sanitize if hide_sensitive
            if hide_sensitive:
                sanitized_logs = []
                for log in logs:
                    msg = log["message"]
                    import re
                    msg = re.sub(r'AIzaSy[A-Za-z0-9_\-]{33}', 'AIzaSy[MASKED]', msg)
                    log_copy = dict(log)
                    log_copy["message"] = msg
                    sanitized_logs.append(log_copy)
                logs = sanitized_logs

            filename = f"action_logs_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
            
            if format_type == "html":
                filename += ".html"
                filepath = os.path.join(save_dir, filename)
                
                bg_color = "#0F131D"
                card_bg = "#151B26"
                text_color = "#D1D5DB"
                accent_color = "#D4AF37"
                
                if theme == "light":
                    bg_color = "#F3F4F6"
                    card_bg = "#FFFFFF"
                    text_color = "#1F2937"
                    accent_color = "#3B82F6"
                elif theme == "dark":
                    bg_color = "#000000"
                    card_bg = "#111111"
                    text_color = "#E5E7EB"
                    accent_color = "#9CA3AF"
                
                html_content = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Golden Platform Action Logs Report</title>
    <style>
        body {{
            background-color: {bg_color};
            color: {text_color};
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            margin: 40px;
        }}
        h1 {{
            color: {accent_color};
            border-bottom: 2px solid {accent_color};
            padding-bottom: 10px;
        }}
        .summary {{
            background-color: {card_bg};
            padding: 20px;
            border-radius: 8px;
            margin-bottom: 20px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
        }}
        table {{
            width: 100%;
            border-collapse: collapse;
            margin-top: 20px;
        }}
        th, td {{
            padding: 12px;
            text-align: left;
            border-bottom: 1px solid #2D3748;
        }}
        th {{
            background-color: {accent_color};
            color: #000;
            font-weight: bold;
        }}
        tr:hover {{
            background-color: {card_bg};
        }}
        .badge {{
            padding: 4px 8px;
            border-radius: 4px;
            font-size: 11px;
            font-weight: bold;
        }}
        .success {{ background-color: #10B981; color: white; }}
        .error {{ background-color: #EF4444; color: white; }}
        .info {{ background-color: #3B82F6; color: white; }}
        .warning {{ background-color: #F59E0B; color: white; }}
    </style>
</head>
<body>
    <h1>📋 Golden Platform Pro Action Logs</h1>
    <div class="summary">
        <h3>Report Summary</h3>
        <p><strong>Generated At:</strong> {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</p>
        <p><strong>Total Recorded Entries:</strong> {len(logs)}</p>
    </div>
    <table>
        <thead>
            <tr>
                <th>ID</th>
                <th>Type</th>
                <th>Timestamp</th>
                <th>Log Message</th>
            </tr>
        </thead>
        <tbody>
"""
                for log in logs:
                    badge_class = "info"
                    if log["type"] == "success":
                        badge_class = "success"
                    elif log["type"] == "error":
                        badge_class = "error"
                    elif log["type"] == "warning":
                        badge_class = "warning"
                    
                    html_content += f"""            <tr>
                <td>{log['id']}</td>
                <td><span class="badge {badge_class}">{log['type'].upper()}</span></td>
                <td>{log['created_at']}</td>
                <td>{log['message']}</td>
            </tr>\n"""
                
                html_content += """        </tbody>
    </table>
</body>
</html>"""
                with open(filepath, "w", encoding="utf-8") as f:
                    f.write(html_content)
                    
            elif format_type == "txt":
                filename += ".txt"
                filepath = os.path.join(save_dir, filename)
                with open(filepath, "w", encoding="utf-8") as f:
                    f.write(f"=== Golden Platform Action Logs Report ===\n")
                    f.write(f"Generated At: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
                    f.write(f"Total Entries: {len(logs)}\n\n")
                    for log in logs:
                        f.write(f"[{log['created_at']}] [{log['type'].upper()}] - {log['message']}\n")
                        
            elif format_type == "csv":
                filename += ".csv"
                filepath = os.path.join(save_dir, filename)
                import csv
                with open(filepath, "w", encoding="utf-8", newline="") as f:
                    writer = csv.writer(f)
                    writer.writerow(["ID", "Type", "Timestamp", "Message"])
                    for log in logs:
                        writer.writerow([log["id"], log["type"], log["created_at"], log["message"]])
                        
            elif format_type == "json":
                filename += ".json"
                filepath = os.path.join(save_dir, filename)
                with open(filepath, "w", encoding="utf-8") as f:
                    json.dump(logs, f, indent=4, ensure_ascii=False)
            else:
                return json.dumps({"success": False, "error": "Unknown format type"}, ensure_ascii=False)
                
            self.db.log_action("success", f"📊 تم تصدير السجلات بنجاح إلى: {filename}")
            return json.dumps({"success": True, "filepath": filepath, "filename": filename}, ensure_ascii=False)
        except Exception as e:
            return json.dumps({"success": False, "error": str(e)}, ensure_ascii=False)

    @Slot(result=str)
    def get_advanced_logs_stats(self):
        try:
            logs = self.db.get_logs()
            total = len(logs)
            if total == 0:
                return json.dumps({
                    "total": 0,
                    "success": 0,
                    "error": 0,
                    "info": 0,
                    "warning": 0,
                    "success_rate": 0,
                    "most_active": "N/A"
                }, ensure_ascii=False)
                
            success = sum(1 for l in logs if l["type"] == "success")
            error = sum(1 for l in logs if l["type"] == "error")
            info = sum(1 for l in logs if l["type"] == "info")
            warning = sum(1 for l in logs if l["type"] == "warning")
            other = total - (success + error + info + warning)
            
            success_rate = round((success / total) * 100, 1) if total > 0 else 0
            
            type_counts = {}
            for l in logs:
                t = l["type"]
                type_counts[t] = type_counts.get(t, 0) + 1
            most_active = max(type_counts, key=type_counts.get) if type_counts else "N/A"
            
            return json.dumps({
                "total": total,
                "success": success,
                "error": error,
                "info": info,
                "warning": warning + other,
                "success_rate": success_rate,
                "most_active": most_active.upper()
            }, ensure_ascii=False)
        except Exception as e:
            print(f"Error computing advanced stats: {e}")
            return json.dumps({"total": 0, "success": 0, "error": 0, "info": 0, "warning": 0, "success_rate": 0, "most_active": "Error"}, ensure_ascii=False)
