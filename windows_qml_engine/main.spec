# -*- mode: python ; coding: utf-8 -*-

a = Analysis(
    ['main.py'],
    pathex=[],
    binaries=[],
    datas=[
        ('main.qml', '.'),
        ('Spacer.qml', '.'),
        ('golden_platform_pro.db', '.'),
        ('app.ico', '.'),
    ],
    hiddenimports=['PySide6.QtQml', 'PySide6.QtQuick', 'PySide6.QtCore', 'PySide6.QtGui', 'sqlite3'],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
)

pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='main',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,  # إذا أردت رؤية الأخطاء شغّل console=True
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon='app.ico'
)