; Przy deinstalacji usuwa wpis autostartu z rejestru użytkownika (jeśli użytkownik go włączył).
; Zamykanie działającej aplikacji obsługuje standardowy instalator Tauri (pyta użytkownika).
!macro NSIS_HOOK_PREUNINSTALL
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "OpenRoutine"
!macroend
