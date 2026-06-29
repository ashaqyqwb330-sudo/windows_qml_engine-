[Setup]
AppName=المنصة الذهبية Pro
AppVersion=2.0
AppPublisher=GoldenPlatform
AppPublisherURL=https://goldenplatform.org
DefaultDirName={autopf}\GoldenPlatformPro
DefaultGroupName=GoldenPlatformPro
UninstallDisplayIcon={app}\main.exe
Compression=lzma2
SolidCompression=yes
OutputDir=.
OutputBaseFilename=GoldenPlatformPro_Setup
SetupIconFile=app.ico


[Files]
Source: "dist\main.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "app.ico"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\المنصة الذهبية Pro"; Filename: "{app}\main.exe"; IconFilename: "{app}\app.ico"
Name: "{group}\إلغاء التثبيت"; Filename: "{uninstallexe}"
Name: "{commondesktop}\المنصة الذهبية Pro"; Filename: "{app}\main.exe"; IconFilename: "{app}\app.ico"

[Run]
Filename: "{app}\main.exe"; Description: "تشغيل التطبيق بعد التثبيت"; Flags: postinstall nowait skipifsilent

[UninstallDelete]
Type: files; Name: "{app}\app.ico"