!macro customInstall
  ${if} ${FileExists} "$INSTDIR\resources\hamrochurch-icon.ico"
    CopyFiles /SILENT "$INSTDIR\resources\hamrochurch-icon.ico" "$INSTDIR\resources\hamrochurch-${VERSION}.ico"
  ${endIf}

  ${if} ${FileExists} "$INSTDIR\resources\hamrochurch-${VERSION}.ico"
    !ifndef DO_NOT_CREATE_START_MENU_SHORTCUT
      !insertmacro createMenuDirectory
      Delete "$newStartMenuLink"
      CreateShortCut "$newStartMenuLink" "$appExe" "" "$INSTDIR\resources\hamrochurch-${VERSION}.ico" 0 "" "" "${APP_DESCRIPTION}"
      ClearErrors
      WinShell::SetLnkAUMI "$newStartMenuLink" "${APP_ID}"
    !endif

    !ifndef DO_NOT_CREATE_DESKTOP_SHORTCUT
      ${ifNot} ${isNoDesktopShortcut}
        Delete "$newDesktopLink"
        CreateShortCut "$newDesktopLink" "$appExe" "" "$INSTDIR\resources\hamrochurch-${VERSION}.ico" 0 "" "" "${APP_DESCRIPTION}"
        ClearErrors
        WinShell::SetLnkAUMI "$newDesktopLink" "${APP_ID}"
      ${endIf}
    !endif

    ReadEnvStr $R9 "USERPROFILE"
    ${if} $R9 != ""
      ${if} ${FileExists} "$R9\Desktop\${SHORTCUT_NAME}.lnk"
      ${andIf} "$R9\Desktop\${SHORTCUT_NAME}.lnk" != "$newDesktopLink"
        Delete "$R9\Desktop\${SHORTCUT_NAME}.lnk"
        CreateShortCut "$R9\Desktop\${SHORTCUT_NAME}.lnk" "$appExe" "" "$INSTDIR\resources\hamrochurch-${VERSION}.ico" 0 "" "" "${APP_DESCRIPTION}"
        ClearErrors
        WinShell::SetLnkAUMI "$R9\Desktop\${SHORTCUT_NAME}.lnk" "${APP_ID}"
      ${endIf}
      ${if} ${FileExists} "$R9\OneDrive\Desktop\${SHORTCUT_NAME}.lnk"
      ${andIf} "$R9\OneDrive\Desktop\${SHORTCUT_NAME}.lnk" != "$newDesktopLink"
        Delete "$R9\OneDrive\Desktop\${SHORTCUT_NAME}.lnk"
        CreateShortCut "$R9\OneDrive\Desktop\${SHORTCUT_NAME}.lnk" "$appExe" "" "$INSTDIR\resources\hamrochurch-${VERSION}.ico" 0 "" "" "${APP_DESCRIPTION}"
        ClearErrors
        WinShell::SetLnkAUMI "$R9\OneDrive\Desktop\${SHORTCUT_NAME}.lnk" "${APP_ID}"
      ${endIf}
    ${endIf}

    WriteRegStr SHELL_CONTEXT "${UNINSTALL_REGISTRY_KEY}" "DisplayIcon" "$INSTDIR\resources\hamrochurch-${VERSION}.ico"
  ${endIf}

  System::Call 'Shell32::SHChangeNotify(i 0x8000000, i 0, i 0, i 0)'
  ${if} ${FileExists} "$SYSDIR\ie4uinit.exe"
    ExecWait '"$SYSDIR\ie4uinit.exe" -show'
  ${endIf}
!macroend
