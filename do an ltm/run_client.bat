@echo off
chcp 65001 > nul
title CLIENT GUI - DO AN LAP TRINH MANG
echo =======================================================
echo     KHOI DONG CLIENT GUI FORM (NETBEANS / WINDOWS)
echo =======================================================

if not exist "target\classes\com\ltm\client\ClientGUI.class" (
    echo [THONG BAO] Dang bien dich ma nguon...
    call compile.bat
)

start javaw -cp "target\classes" com.ltm.client.ClientGUI
echo Client GUI da duoc mo trong cua so rieng!
