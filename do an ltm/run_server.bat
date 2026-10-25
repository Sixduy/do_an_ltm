@echo off
chcp 65001 > nul
title SERVER GUI - DO AN LAP TRINH MANG
echo =======================================================
echo     KHOI DONG SERVER GUI FORM (NETBEANS / WINDOWS)
echo =======================================================

if not exist "target\classes\com\ltm\server\ServerGUI.class" (
    echo [THONG BAO] Dang bien dich ma nguon...
    call compile.bat
)

start javaw -cp "target\classes" com.ltm.server.ServerGUI
echo Server GUI da duoc mo trong cua so rieng!
