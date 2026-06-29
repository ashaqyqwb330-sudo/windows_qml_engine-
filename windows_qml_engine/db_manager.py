import os
import sqlite3
from datetime import datetime

class DatabaseManager:
    def __init__(self, db_dir):
        os.makedirs(db_dir, exist_ok=True)
        self.db_path = os.path.join(db_dir, "golden_platform_pro.db")
        self.init_db()

    def get_connection(self):
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def init_db(self):
        with self.get_connection() as conn:
            cursor = conn.cursor()
            
            # Projects Table
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS projects (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    path TEXT NOT NULL,
                    template_json TEXT,
                    created_at TEXT NOT NULL
                )
            """)
            try:
                cursor.execute("ALTER TABLE projects ADD COLUMN template_json TEXT")
            except sqlite3.OperationalError:
                pass
            
            # Extracted Files Table
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS extracted_files (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    project_name TEXT NOT NULL,
                    filename TEXT NOT NULL,
                    filepath TEXT NOT NULL,
                    size INTEGER,
                    created_at TEXT NOT NULL
                )
            """)
            
            # Action Logs Table
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS action_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    type TEXT NOT NULL,
                    message TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
            """)
            
            # Smart Capture Inbox Table
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS smart_captures (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    content TEXT,
                    capture_type TEXT NOT NULL,
                    file_path TEXT,
                    theme TEXT,
                    created_at TEXT NOT NULL
                )
            """)
            
            # Gemini Chat Table
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS gemini_chats (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    role TEXT NOT NULL,
                    message TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
            """)

            # Settings Table (Key-Value Keyring fallback)
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
            """)

            # Style Bank Table for holding design snippets
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS style_bank (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    css_code TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
            """)

            # Command History Table for holding execution history
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS command_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    command TEXT NOT NULL,
                    status TEXT NOT NULL,
                    output TEXT,
                    created_at TEXT NOT NULL
                )
            """)
            
            conn.commit()

    # --- Projects Methods ---
    def add_project(self, name, path, template_json=None):
        try:
            with self.get_connection() as conn:
                conn.cursor().execute(
                    "INSERT OR REPLACE INTO projects (name, path, template_json, created_at) VALUES (?, ?, ?, ?)",
                    (name, path, template_json, datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
                )
                conn.commit()
            return True
        except sqlite3.OperationalError:
            try:
                with self.get_connection() as conn:
                    conn.cursor().execute(
                        "INSERT OR REPLACE INTO projects (name, path, created_at) VALUES (?, ?, ?)",
                        (name, path, datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
                    )
                    conn.commit()
                return True
            except Exception:
                return False
        except Exception:
            return False

    def delete_project(self, name):
        try:
            with self.get_connection() as conn:
                conn.cursor().execute("DELETE FROM projects WHERE name = ?", (name,))
                conn.commit()
            return True
        except Exception:
            return False

    def get_projects(self):
        with self.get_connection() as conn:
            rows = conn.cursor().execute("SELECT * FROM projects ORDER BY id DESC").fetchall()
            return [dict(r) for r in rows]

    # --- Extracted Files Methods ---
    def add_file(self, project_name, filename, filepath, size):
        with self.get_connection() as conn:
            conn.cursor().execute(
                "INSERT INTO extracted_files (project_name, filename, filepath, size, created_at) VALUES (?, ?, ?, ?, ?)",
                (project_name, filename, filepath, size, datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
            )
            conn.commit()

    def get_extracted_files(self):
        with self.get_connection() as conn:
            rows = conn.cursor().execute("SELECT * FROM extracted_files ORDER BY id DESC LIMIT 100").fetchall()
            return [dict(r) for r in rows]

    # --- Action Logs Methods ---
    def log_action(self, log_type, message):
        with self.get_connection() as conn:
            conn.cursor().execute(
                "INSERT INTO action_logs (type, message, created_at) VALUES (?, ?, ?)",
                (log_type, message, datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
            )
            conn.commit()

    def get_logs(self):
        with self.get_connection() as conn:
            rows = conn.cursor().execute("SELECT * FROM action_logs ORDER BY id DESC LIMIT 100").fetchall()
            return [dict(r) for r in rows]

    def clear_logs(self):
        with self.get_connection() as conn:
            conn.cursor().execute("DELETE FROM action_logs")
            conn.commit()

    # --- Smart Capture Methods ---
    def add_capture(self, title, content, capture_type, file_path, theme="gold"):
        with self.get_connection() as conn:
            conn.cursor().execute(
                "INSERT INTO smart_captures (title, content, capture_type, file_path, theme, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                (title, content, capture_type, file_path, theme, datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
            )
            conn.commit()

    def get_captures(self):
        with self.get_connection() as conn:
            rows = conn.cursor().execute("SELECT * FROM smart_captures ORDER BY id DESC").fetchall()
            return [dict(r) for r in rows]

    # --- Gemini Chat Methods ---
    def add_chat(self, role, message):
        with self.get_connection() as conn:
            conn.cursor().execute(
                "INSERT INTO gemini_chats (role, message, created_at) VALUES (?, ?, ?)",
                (role, message, datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
            )
            conn.commit()

    def get_chats(self):
        with self.get_connection() as conn:
            rows = conn.cursor().execute("SELECT * FROM gemini_chats ORDER BY id ASC LIMIT 100").fetchall()
            return [dict(r) for r in rows]

    def clear_chats(self):
        with self.get_connection() as conn:
            conn.cursor().execute("DELETE FROM gemini_chats")
            conn.commit()

    # --- Settings Key-Value Methods ---
    def set_setting(self, key, value):
        with self.get_connection() as conn:
            conn.cursor().execute(
                "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)", (key, str(value))
            )
            conn.commit()

    def get_setting(self, key, default=""):
        with self.get_connection() as conn:
            row = conn.cursor().execute("SELECT value FROM settings WHERE key = ?", (key,)).fetchone()
            return row["value"] if row else default

    # --- Style Bank Methods ---
    def add_style(self, name, css_code):
        try:
            with self.get_connection() as conn:
                conn.cursor().execute(
                    "INSERT OR REPLACE INTO style_bank (name, css_code, created_at) VALUES (?, ?, ?)",
                    (name, css_code, datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
                )
                conn.commit()
            return True
        except Exception:
            return False

    def get_styles(self):
        with self.get_connection() as conn:
            rows = conn.cursor().execute("SELECT * FROM style_bank ORDER BY name ASC").fetchall()
            return [dict(r) for r in rows]

    def delete_style(self, style_name):
        with self.get_connection() as conn:
            conn.cursor().execute("DELETE FROM style_bank WHERE name = ?", (style_name,))
            conn.commit()

    # --- Command History Methods ---
    def add_command_history(self, command, status, output):
        try:
            with self.get_connection() as conn:
                conn.cursor().execute(
                    "INSERT INTO command_history (command, status, output, created_at) VALUES (?, ?, ?, ?)",
                    (command, status, output, datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
                )
                conn.commit()
            return True
        except Exception as e:
            print(f"Error adding command history: {e}")
            return False

    def get_command_history(self):
        try:
            with self.get_connection() as conn:
                rows = conn.cursor().execute("SELECT * FROM command_history ORDER BY id DESC LIMIT 50").fetchall()
                return [dict(r) for r in rows]
        except Exception as e:
            print(f"Error getting command history: {e}")
            return []

    def clear_command_history(self):
        try:
            with self.get_connection() as conn:
                conn.cursor().execute("DELETE FROM command_history")
                conn.commit()
            return True
        except Exception as e:
            print(f"Error clearing command history: {e}")
            return False
