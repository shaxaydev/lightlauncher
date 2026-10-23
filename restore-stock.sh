#!/bin/sh
# Restore the original MiTV-MSSP1 home screen without deleting applications or data.
set -eu
serial="${1:?Usage: ./restore-stock.sh TV_IP:5555}"
adb -s "$serial" shell pm default-state --user 0 com.mitv.tvhome.atv
adb -s "$serial" shell pm enable --user 0 com.google.android.tvlauncher
adb -s "$serial" shell cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity
adb -s "$serial" shell input keyevent KEYCODE_HOME
