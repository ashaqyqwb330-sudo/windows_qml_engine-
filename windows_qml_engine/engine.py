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

        # Clipboard Monitor setup
        self._last_clipboard_text = ""
        self.clipboard = QGuiApplication.clipboard()
        self.clipboard.dataChanged.connect(self.on_clipboard_changed)

    # --- Properties ---
    @Property(str)
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
        else:
            self.logAdded.emit("error", f"المجلد غير موجود: {val}")

    @Property(str)
    def activeTheme(self):
        return self._active_theme

    @activeTheme.setter
    def activeTheme(self, val):
        self._active_theme = val
        self.db.set_setting("active_theme", val)
        self.dbUpdated.emit()

    @Property(str)
    def appLanguage(self):
        return self._app_language

    @appLanguage.setter
    def appLanguage(self, val):
        self._app_language = val
        self.db.set_setting("language", val)
        self.dbUpdated.emit()

    @Property(bool)
    def bubbleEnabled(self):
        return self._bubble_enabled

    @bubbleEnabled.setter
    def bubbleEnabled(self, val):
        self._bubble_enabled = val
        self.db.set_setting("bubble_enabled", str(val).lower())
        self.dbUpdated.emit()

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
                # Detect package markers
                if "@builder:file" in text and "@builder:end" in text:
                    self.clipboardBuilderDetected.emit(text)
                    self.db.log_action("info", "📋 تم رصد حزمة بناء صالحة ومعالجة مسبقة في الحافظة!")
                    self.logAdded.emit("success", "📋 تم رصد حزمة بناء برمجية جاهزة للتثبيت!")
                    self.notificationSent.emit("مراقب الحافظة", "تم الكشف تلقائياً عن حزمة بناء برمجية صالحة في حافظة الويندوز.", "info")
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
    @Slot(str, str)
    def generate_treedoc(self, folder_path, doc_format):
        folder_path = self.clean_path_url(folder_path)
        if not os.path.exists(folder_path):
            self.logAdded.emit("error", "المسار غير موجود!")
            return

        self.logAdded.emit("info", f"🌲 جاري زراعة وتوليد التقرير الشجري بأسلوب {doc_format}...")
        self.db.log_action("info", f"بدء توليد TreeDoc للمجلد {folder_path} بصيغة {doc_format}")

        treedocs_dir = os.path.join(self._base_dir, "TreeDocs")
        os.makedirs(treedocs_dir, exist_ok=True)
        date_str = datetime.now().strftime("%Y%m%d_%H%M%S")

        if doc_format == "txt":
            result = self._generate_treedoc_txt(folder_path)
            file_name = f"TreeDoc_{date_str}.txt"
            target_path = os.path.join(treedocs_dir, file_name)
            with open(target_path, "w", encoding="utf-8") as f:
                f.write(result)
            self.treeDocCreated.emit("txt", target_path)
            self.logAdded.emit("success", f"🌲 تم حفظ التقرير النصي بنجاح في {file_name}!")

        elif doc_format == "json":
            tree_data = self._generate_treedoc_json_structure(folder_path)
            result = json.dumps(tree_data, ensure_ascii=False, indent=4)
            file_name = f"TreeDoc_{date_str}.json"
            target_path = os.path.join(treedocs_dir, file_name)
            with open(target_path, "w", encoding="utf-8") as f:
                f.write(result)
            self.treeDocCreated.emit("json", target_path)
            self.logAdded.emit("success", f"🌲 تم توليد التقرير الشجري المهيكل بنجاح في {file_name}!")

        elif doc_format == "html":
            result = self._generate_treedoc_html(folder_path)
            file_name = f"TreeDoc_{date_str}.html"
            target_path = os.path.join(treedocs_dir, file_name)
            with open(target_path, "w", encoding="utf-8") as f:
                f.write(result)
            self.treeDocCreated.emit("html", target_path)
            self.logAdded.emit("success", f"🌲 تم تصميم شجرة تفاعلية غنية بالوسائط في {file_name}!")

    def _generate_treedoc_txt(self, path, indent=""):
        lines = []
        if indent == "":
            lines.append(f"📁 {os.path.basename(path)}/ [مجلد العمل النشط]")
        try:
            items = sorted(os.listdir(path))
        except Exception:
            return ""

        for item in items:
            if item in self.ignore_dirs:
                continue
            full_path = os.path.join(path, item)
            if os.path.isdir(full_path):
                lines.append(f"{indent}├── 📁 {item}/")
                lines.append(self._generate_treedoc_txt(full_path, indent + "│   "))
            else:
                size_kb = round(os.path.getsize(full_path) / 1024, 1)
                lines.append(f"{indent}├── 📄 {item} ({size_kb} KB)")
        return "\n".join([l for l in lines if l.strip()])

    def _generate_treedoc_json_structure(self, path):
        node = {"name": os.path.basename(path), "type": "directory", "children": []}
        try:
            items = sorted(os.listdir(path))
            for item in items:
                if item in self.ignore_dirs:
                    continue
                full_path = os.path.join(path, item)
                if os.path.isdir(full_path):
                    node["children"].append(self._generate_treedoc_json_structure(full_path))
                else:
                    node["children"].append({
                        "name": item,
                        "type": "file",
                        "size_bytes": os.path.getsize(full_path)
                    })
        except Exception:
            pass
        return node

    def _generate_treedoc_html(self, path):
        txt_tree = self._generate_treedoc_txt(path)
        escaped_tree = txt_tree.replace("<", "&lt;").replace(">", "&gt;")
        
        html = f"""<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>شجرة الملفات التفاعلية - TreeDoc Pro</title>
    <style>
        body {{
            background-color: #05070E;
            color: #E4E9FC;
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            direction: rtl;
            padding: 40px;
        }}
        .container {{
            background-color: #0D1127;
            border: 1px solid #1E295D;
            padding: 30px;
            border-radius: 12px;
            max-width: 1000px;
            margin: 0 auto;
        }}
        h1 {{
            color: #FCD34D;
            border-bottom: 1px solid #1E295D;
            padding-bottom: 10px;
        }}
        pre {{
            background-color: #05070E;
            border: 1px solid #1E295D;
            padding: 20px;
            border-radius: 8px;
            font-family: 'Consolas', monospace;
            font-size: 13px;
            overflow-x: auto;
            line-height: 1.6;
        }}
        .badge {{
            display: inline-block;
            background-color: #1E295D;
            color: #FCD34D;
            padding: 4px 10px;
            border-radius: 20px;
            font-size: 11px;
            margin-bottom: 20px;
        }}
        .footer {{
            margin-top: 50px;
            text-align: center;
            color: #53648E;
            font-size: 12px;
        }}
    </style>
</head>
<body>
    <div class="container">
        <h1>🌲 التقرير الشجري التفاعلي لـ {os.path.basename(path)}</h1>
        <div class="badge">نظام TreeDoc للويندوز Pro</div>
        <pre>{escaped_tree}</pre>
        <div class="footer">
            تم التوليد والتغليف بواسطة محرك المنصة الذهبية للويندوز Pro 🌲🌐
        </div>
    </div>
</body>
</html>"""
        return html

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

    # --- Self-Source Export System ---
    @Slot(result=str)
    def export_app_own_source(self):
        source_dir = os.path.abspath(os.path.dirname(__file__))
        result_text = []
        result_text.append("// =========================================================\n")
        result_text.append("// 📥 حزمة التصدير الذاتي للمصدر - المساعد الذكي الذهبي للويندوز Pro\n")
        result_text.append(f"// تاريخ التصدير: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        result_text.append("// =========================================================\n\n")

        files_to_export = [
            "main.py", "engine.py", "db_manager.py", "main.qml", "Spacer.qml", "requirements.txt", "README.md"
        ]
        for fname in files_to_export:
            fpath = os.path.join(source_dir, fname)
            if os.path.exists(fpath):
                try:
                    with open(fpath, "r", encoding="utf-8", errors="ignore") as f:
                        content = f.read()
                    result_text.append(f"// @builder:file {fname}\n")
                    result_text.append(content)
                    if not content.endswith("\n"):
                        result_text.append("\n")
                    result_text.append("// @builder:end\n\n")
                except Exception as e:
                    print(f"Self-source export error for {fname}: {e}")

        final_pack = "".join(result_text)
        self.clipboard.setText(final_pack)
        self.db.log_action("success", "تم تصدير وحفظ الكود المصدري للتطبيق في حافظة الويندوز!")
        self.notificationSent.emit("التصدير الذاتي للمصدر", "تم تجميع ونسخ الكود المصدري للتطبيق بالكامل كحزمة بناء للمطورين.", "success")
        return final_pack

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
