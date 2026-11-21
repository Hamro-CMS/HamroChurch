!macro customInstall
  ${if} ${FileExists} "$INSTDIR\resources\aradhanastudio-icon.ico"
    CopyFiles /SILENT "$INSTDIR\resources\aradhanastudio-icon.ico" "$INSTDIR\resources\aradhanastudio-${VERSION}.ico"
  ${endIf}

  ${if} ${FileExists} "$INSTDIR\resources\aradhanastudio-${VERSION}.ico"
    !ifndef DO_NOT_CREATE_START_MENU_SHORTCUT
      !insertmacro createMenuDirectory
      Delete "$newStartMenuLink"
      CreateShortCut "$newStartMenuLink" "$appExe" "" "$INSTDIR\resources\aradhanastudio-${VERSION}.ico" 0 "" "" "${APP_DESCRIPTION}"
      ClearErrors
      WinShell::SetLnkAUMI "$newStartMenuLink" "${APP_ID}"
    !endif

    !ifndef DO_NOT_CREATE_DESKTOP_SHORTCUT
      ${ifNot} ${isNoDesktopShortcut}
        Delete "$DESKTOP\Hamro Church.lnk"
        Delete "$newDesktopLink"
        CreateShortCut "$newDesktopLink" "$appExe" "" "$INSTDIR\resources\aradhanastudio-${VERSION}.ico" 0 "" "" "${APP_DESCRIPTION}"
        ClearErrors
        WinShell::SetLnkAUMI "$newDesktopLink" "${APP_ID}"
      ${endIf}
    !endif

    ReadEnvStr $R9 "USERPROFILE"
    ${if} $R9 != ""
      Delete "$R9\Desktop\Hamro Church.lnk"
      Delete "$R9\OneDrive\Desktop\Hamro Church.lnk"
      ${if} ${FileExists} "$R9\Desktop\${SHORTCUT_NAME}.lnk"
      ${andIf} "$R9\Desktop\${SHORTCUT_NAME}.lnk" != "$newDesktopLink"
        Delete "$R9\Desktop\${SHORTCUT_NAME}.lnk"
        CreateShortCut "$R9\Desktop\${SHORTCUT_NAME}.lnk" "$appExe" "" "$INSTDIR\resources\aradhanastudio-${VERSION}.ico" 0 "" "" "${APP_DESCRIPTION}"
        ClearErrors
        WinShell::SetLnkAUMI "$R9\Desktop\${SHORTCUT_NAME}.lnk" "${APP_ID}"
      ${endIf}
      ${if} ${FileExists} "$R9\OneDrive\Desktop\${SHORTCUT_NAME}.lnk"
      ${andIf} "$R9\OneDrive\Desktop\${SHORTCUT_NAME}.lnk" != "$newDesktopLink"
        Delete "$R9\OneDrive\Desktop\${SHORTCUT_NAME}.lnk"
        CreateShortCut "$R9\OneDrive\Desktop\${SHORTCUT_NAME}.lnk" "$appExe" "" "$INSTDIR\resources\aradhanastudio-${VERSION}.ico" 0 "" "" "${APP_DESCRIPTION}"
        ClearErrors
        WinShell::SetLnkAUMI "$R9\OneDrive\Desktop\${SHORTCUT_NAME}.lnk" "${APP_ID}"
      ${endIf}
    ${endIf}

    WriteRegStr SHELL_CONTEXT "${UNINSTALL_REGISTRY_KEY}" "DisplayIcon" "$INSTDIR\resources\aradhanastudio-${VERSION}.ico"
  ${endIf}

  ${if} ${FileExists} "$SYSDIR\netsh.exe"
    nsExec::Exec `"$SYSDIR\netsh.exe" advfirewall firewall delete rule name="Aradhana Studio Mobile Remote (TCP)"`
    Pop $0
    nsExec::Exec `"$SYSDIR\netsh.exe" advfirewall firewall delete rule name="Aradhana Studio Mobile Remote (UDP)"`
    Pop $0
    nsExec::Exec `"$SYSDIR\netsh.exe" advfirewall firewall add rule name="Aradhana Studio Mobile Remote (TCP)" dir=in action=allow program="$appExe" protocol=TCP profile=any enable=yes`
    Pop $0
    nsExec::Exec `"$SYSDIR\netsh.exe" advfirewall firewall add rule name="Aradhana Studio Mobile Remote (UDP)" dir=in action=allow program="$appExe" protocol=UDP profile=any enable=yes`
    Pop $0
  ${endIf}

  System::Call 'Shell32::SHChangeNotify(i 0x8000000, i 0, i 0, i 0)'
  ${if} ${FileExists} "$SYSDIR\ie4uinit.exe"
    ExecWait '"$SYSDIR\ie4uinit.exe" -show'
  ${endIf}
!macroend
