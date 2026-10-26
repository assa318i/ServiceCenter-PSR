; ============================================================================
;  «Сервис центр от PSR» — установщик для Inno Setup 6 (бесплатный, Windows).
;
;  Сборка:      installer\make-installer.bat   → ..\build\dist\PSR-ServiceCenter-Setup-*.exe
;  Проверка на другом ПК: запустить этот exe — админ-права не обязательны
;  (установка в папку текущего пользователя, программе нужна запись в data\).
;
;  Файл лежит в UTF-8 с BOM: без BOM Inno читает скрипт как ANSI, и русские
;  надписи в помощнике превращаются в нечитаемые значки.
;
;  Компонент «Свой Java» появится, если в ..\build\dist\jre\ есть bin\javaw.exe
;  (make-installer.bat собирает его из JRE_DIR или через jlink). Для Windows 7
;  кладите в build\dist\jre именно Java 8 x64 — Java 17 на Win7 не работает.
; ============================================================================

#define MyAppName "Сервис центр от PSR"
; Версия: по умолчанию 1.0.16, можно передать /DMyAppVersion=1.2.0 (make-installer.bat берёт её из PSR_VERSION).
#ifndef MyAppVersion
  #define MyAppVersion "1.0.20"
#endif
#define MyAppPublisher "PSR"
#define Dist "..\build\dist"
#define HaveJre FileExists(SourcePath + Dist + "\jre\bin\javaw.exe")
#define HaveIcon FileExists(SourcePath + "icon.ico")

[Setup]
; AppId менять нельзя — по нему следующая версия ставится поверх, а не рядом.
AppId={{B7E14C93-4C2A-4B6D-9E51-2A7F0D3C8A11}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultGroupName={#MyAppName}
DefaultDirName={autopf}\PSR Service Center
DisableProgramGroupPage=yes
AllowNoIcons=yes
; Без прав администратора {autopf} = LocalAppData\Programs — туда можно писать,
; а программа хранит базу в data\ рядом с собой. Установка «для всех» тоже
; работает: установщик выдаёт права на data\ обычным пользователям.
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
OutputDir={#Dist}
OutputBaseFilename=PSR-ServiceCenter-Setup-{#MyAppVersion}
#if HaveIcon
SetupIconFile=icon.ico
#endif
UninstallDisplayName={#MyAppName}
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
SetupLogging=yes
UninstallDisplayIcon={app}\icon.ico
CloseApplications=no
RestartIfNeededByRun=no

[Languages]
Name: "ru"; MessagesFile: "compiler:Languages\Russian.isl"

[Tasks]
Name: "desktopicon"; Description: "Ярлык на рабочем столе"; GroupDescription: "Дополнительно:"
Name: "datadirs"; Description: "Создать папки data\ (база, копии, экспорт, сканы) и modules\"; \
    GroupDescription: "Дополнительно:"

[Components]
; Types обязателен: компонент без Types не выбран ни одним типом установки,
; и его файлы молча не копируются (так и вышло: папка без jar и docs).
Name: "app"; Description: "Программа (jar и библиотеки)"; \
    Types: full compact custom; Flags: fixed
Name: "docs"; Description: "Документация и чек-лист в HTML"; \
    Types: full compact custom
#if HaveJre
Name: "jre"; Description: "Свой Java (нужен, если на ПК нет Java 8+)"; \
    Types: full compact custom
#endif

[Files]
Source: "{#Dist}\psr-service-center.jar"; DestDir: "{app}"; Components: app; Flags: ignoreversion
Source: "{#Dist}\lib\*"; DestDir: "{app}\lib"; Components: app; Flags: ignoreversion
Source: "{#Dist}\run.bat"; DestDir: "{app}"; Components: app; Flags: ignoreversion
; Свой значок: .ico берут установщик, ярлыки и список «Программы и компоненты»,
; .png — окно программы (Java 8 не читает .ico).
Source: "{#Dist}\icon.ico"; DestDir: "{app}"; Components: app; \
    Flags: ignoreversion skipifsourcedoesntexist
Source: "{#Dist}\icon.png"; DestDir: "{app}"; Components: app; \
    Flags: ignoreversion skipifsourcedoesntexist
Source: "{#Dist}\docs\*"; DestDir: "{app}\docs"; Components: docs; Flags: ignoreversion
#if HaveJre
Source: "{#Dist}\jre\*"; DestDir: "{app}\jre"; Components: jre; \
    Flags: ignoreversion recursesubdirs createallsubdirs
#endif

[Dirs]
Name: "{app}\data"; Tasks: datadirs
Name: "{app}\data\backups"; Tasks: datadirs
Name: "{app}\data\export"; Tasks: datadirs
Name: "{app}\data\attaches"; Tasks: datadirs
Name: "{app}\modules"; Tasks: datadirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\run.bat"; WorkingDir: "{app}"; \
    IconFilename: "{app}\icon.ico"
Name: "{group}\Чек-лист знакомства (браузер)"; Filename: "{app}\docs\chek-list.html"; \
    Components: docs
Name: "{group}\Документация целиком (браузер)"; Filename: "{app}\docs\doc.html"; Components: docs
Name: "{group}\Импорт и экспорт списков (браузер)"; Filename: "{app}\docs\import.html"; Components: docs
Name: "{group}\История версий (браузер)"; Filename: "{app}\docs\versions.html"; Components: docs
Name: "{group}\Папка с базой данных"; Filename: "{app}\data"
Name: "{group}\Удалить {#MyAppName}"; Filename: "{uninstallexe}"
Name: "{userdesktop}\{#MyAppName}"; Filename: "{app}\run.bat"; WorkingDir: "{app}"; \
    IconFilename: "{app}\icon.ico"; Tasks: desktopicon

[Run]
; Общая установка в Program Files: дать обычным пользователям права на data\.
Filename: "{sys}\icacls.exe"; Parameters: """{app}\data"" /grant ""*S-1-5-32-545:(OI)(CI)(M)"" /t /q"; \
    Flags: runhidden; Check: GrantDataRights
; Страница «Готово»: знакомство с документацией и запуск программы.
Filename: "{app}\docs\chek-list.html"; Description: "Открыть ЧЕК-ЛИСТ знакомства с программой (в браузере)"; \
    Components: docs; Flags: postinstall shellexec skipifsilent
Filename: "{app}\docs\doc.html"; Description: "Открыть ДОКУМЕНТАЦИЮ целиком (в браузере)"; \
    Components: docs; Flags: postinstall shellexec skipifsilent
Filename: "{app}\docs\import.html"; Description: "Открыть инструкцию по импорту и экспорту (в браузере)"; \
    Components: docs; Flags: postinstall shellexec skipifsilent
Filename: "{app}\run.bat"; WorkingDir: "{app}"; Description: "Запустить {#MyAppName}"; \
    Flags: postinstall skipifsilent runhidden

[Code]
var
  CopiesPage: TInputQueryWizardPage;
  Suggested: array of String;

const
  MaxCopyRows = 6;
  DriveLetters = 'DEFGHIJKLMNOPQRSTUV';

procedure AddSuggestion(Path: String);
begin
  if (Path = '') or (GetArrayLength(Suggested) >= MaxCopyRows) then Exit;
  SetArrayLength(Suggested, GetArrayLength(Suggested) + 1);
  Suggested[GetArrayLength(Suggested) - 1] := Path;
end;

{ Куда логично складывать копии: диски, кроме системного, и папки облачных
  клиентов, которые видны в проводнике. Яндекс.Диск намеренно не предлагается. }
procedure BuildSuggestions;
var
  I: Integer;
  Root, Profile: String;
begin
  SetArrayLength(Suggested, 0);
  Profile := GetEnv('USERPROFILE');
  for I := 1 to Length(DriveLetters) do
  begin
    if GetArrayLength(Suggested) >= MaxCopyRows - 2 then Break;
    Root := Copy(DriveLetters, I, 1) + ':\';
    if DirExists(Root) then AddSuggestion(Root + 'PSR-копии');
  end;
  if DirExists(Profile + '\OneDrive') then AddSuggestion(Profile + '\OneDrive\PSR-копии');
  if DirExists(Profile + '\Dropbox') then AddSuggestion(Profile + '\Dropbox\PSR-копии');
  if DirExists(Profile + '\Google Drive') then
    AddSuggestion(Profile + '\Google Drive\PSR-копии');
  if DirExists('G:\My Drive') then AddSuggestion('G:\My Drive\PSR-копии');
  if GetArrayLength(Suggested) = 0 then
  begin
    AddSuggestion('D:\PSR-копии');
    AddSuggestion('\\server\psr');
  end;
end;

procedure CreateCopiesPage;
var
  I: Integer;
begin
  BuildSuggestions;
  CopiesPage := CreateInputQueryPage(wpSelectDir,
    'Резервные копии базы',
    'Куда, кроме data\backups, класть копии psr.db',
    'Можно несколько папок: второй диск или флешка (D:\PSR-копии), сетевая папка'
    + ' (\\server\psr), папка облачного клиента, синхронизируемого с этим компьютером'
    + ' (OneDrive, Google Drive, Dropbox, Mail.ru Облако, Selectel, Коммунарка и т.п.).'
    + #13#10#13#10
    + 'Папка должна уже существовать — установщик новых папок не создаёт, а программа'
    + ' позже напишет «Папки недоступны», если её нет. Чужие строки очистите, свои пути'
    + ' допишите. Программа примет этот список при первом запуске; правится он позже в'
    + ' «Настройки» → «Папки для дополнительных копий» (несколько — через «;»).'
    + #13#10#13#10
    + 'В облако лучше класть копии с датой (psr_20261022_0915.db): живой psr.db перезаписывается'
    + ' на каждом сохранении, и синхронизатор может подменить его своей старой версией.'
    + ' Общий доступ на двоих через облако не делается — база должна быть на одном компьютере.');
  for I := 0 to GetArrayLength(Suggested) - 1 do
    CopiesPage.Add('Копия ' + IntToStr(I + 1), False);
  for I := 0 to GetArrayLength(Suggested) - 1 do
    CopiesPage.Edits[I].Text := Suggested[I];
end;

{ Оставленные строки → data\backup-dirs.txt (по одной папке в строке). Берутся
  только существующие папки: на чужих дисках и в облаках установщик ничего не
  создаёт. Файл пишется в системной кодировке (ANSI) — программа подбирает
  кодировку при чтении, так что файл можно править и в Блокноте как UTF-8. }
procedure SaveCopiesProposal;
var
  I, N: Integer;
  P, Text: String;
begin
  Text := '# Папки для дополнительных копий базы (по одной в строке).' + #13#10
    + '# Правится в программе: Настройки -> «Папки для дополнительных копий».' + #13#10;
  N := 0;
  for I := 0 to GetArrayLength(Suggested) - 1 do
  begin
    P := Trim(CopiesPage.Edits[I].Text);
    if (P = '') or (not DirExists(P)) then Continue;
    Text := Text + P + #13#10;
    N := N + 1;
  end;
  if N > 0 then
  begin
    ForceDirectories(ExpandConstant('{app}\data'));
    SaveStringToFile(ExpandConstant('{app}\data\backup-dirs.txt'), Text, False);
  end
  else
    DeleteFile(ExpandConstant('{app}\data\backup-dirs.txt'));
end;

function RegJavaHome(Base: Integer; Key: String): String;
var
  V: String;
begin
  Result := '';
  if RegQueryStringValue(Base, Key, 'CurrentVersion', V) then
    RegQueryStringValue(Base, Key + '\' + V, 'JavaHome', Result);
end;

{ Java ищется так, как её ищет run.bat: свой jre\, затем javaw.exe в PATH,
  затем записи реестра. OpenJDK без записи в реестре — обычный случай, и
  пугать пользователя ошибкой из-за него нельзя. }
function JavaOnPath: Boolean;
var
  Rest, Dir: String;
  P: Integer;
begin
  Result := False;
  Rest := GetEnv('PATH');
  while Rest <> '' do
  begin
    P := Pos(';', Rest);
    if P = 0 then
    begin
      Dir := Rest;
      Rest := '';
    end
    else
    begin
      Dir := Copy(Rest, 1, P - 1);
      Rest := Copy(Rest, P + 1, Length(Rest) - P);
    end;
    Dir := RemoveQuotes(Trim(Dir));
    if Dir = '' then Continue;
    if FileExists(AddBackslash(Dir) + 'javaw.exe') or
       FileExists(AddBackslash(Dir) + 'java.exe') then
    begin
      Result := True;
      Exit;
    end;
  end;
end;

function IsJavaPresent: Boolean;
var
  Home: String;
begin
  if FileExists(ExpandConstant('{app}\jre\bin\javaw.exe')) then
  begin
    Result := True;
    Exit;
  end;
  if JavaOnPath then
  begin
    Result := True;
    Exit;
  end;
  Home := RegJavaHome(HKLM, 'SOFTWARE\JavaSoft\JDK');
  if Home = '' then Home := RegJavaHome(HKLM, 'SOFTWARE\JavaSoft\Java Runtime Environment');
  if Home = '' then Home := RegJavaHome(HKCU, 'SOFTWARE\JavaSoft\Java Runtime Environment');
  Result := (Home <> '') and FileExists(Home + '\bin\javaw.exe');
end;

function GrantDataRights: Boolean;
begin
  Result := IsAdminInstallMode and WizardIsTaskSelected('datadirs');
end;

procedure InitializeWizard;
begin
  CreateCopiesPage;
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep <> ssPostInstall then Exit;
  SaveCopiesProposal;
  if not IsJavaPresent then
    MsgBox('Java на этом компьютере не найдена — программа не запустится.' + #13#10#13#10
      + 'Поставьте Java 8 (64-bit) с сайта java.com / Adoptium, либо переустановите приложение'
      + ' с компонентом «Свой Java».', mbError, MB_OK);
end;

{ База и копии при удалении НЕ трогаются — только предупреждение, где они лежат. }
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if (CurUninstallStep = usPostUninstall) and DirExists(ExpandConstant('{app}\data')) then
    MsgBox('Программа снята, а папка data\ с базой, копиями и сканами осталась: '
      + ExpandConstant('{app}\data') + #13#10#13#10
      + 'Перед её удалением проверьте, что копии базы есть в data\backups и во внешних папках.',
      mbInformation, MB_OK);
end;
