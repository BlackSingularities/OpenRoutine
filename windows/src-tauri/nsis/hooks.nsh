; Przed instalacją i deinstalacją zamyka działający w tle OpenRoutine (ikona w zasobniku),
; a przy deinstalacji usuwa wpis autostartu z rejestru użytkownika.
!macro NSIS_HOOK_PREINSTALL
  nsExec::Exec 'taskkill /F /IM "OpenRoutine.exe"'
!macroend

!macro NSIS_HOOK_PREUNINSTALL
  nsExec::Exec 'taskkill /F /IM "OpenRoutine.exe"'
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "OpenRoutine"
!macroend
