#!/usr/bin/env python3
"""Run the native TV integration check; restores favorites and removes the test APK afterward."""
import os
from pathlib import Path
import shutil
import subprocess
import sys

adb = os.environ.get('ADB') or shutil.which('adb')
if not adb:
    raise SystemExit('Install Android SDK Platform Tools or set ADB.')
serial = sys.argv[1]
base = Path(__file__).resolve().parent
main = base / 'app/build/outputs/apk/release/app-release.apk'
test = base / 'app/build/outputs/apk/androidTest/release/app-release-androidTest.apk'
if not main.exists() or not test.exists():
    raise SystemExit('First run: ./build.sh :app:assembleReleaseAndroidTest')

def run(*args):
    return subprocess.check_output([adb, '-s', serial, *map(str, args)], timeout=90, text=True)

print(run('install', '-r', main))
print(run('install', '-r', '-t', test))
try:
    run('shell', 'am', 'force-stop', 'uz.shaxay.tvhome')
    output = run('shell', 'am', 'instrument', '-w',
                 'uz.shaxay.tvhome.test/uz.shaxay.tvhome.TvSmokeInstrumentation')
    print(output)
    assert 'native TV integration checks' in output, 'TV integration check failed'
finally:
    print(run('uninstall', 'uz.shaxay.tvhome.test'))
    print(run('shell', 'am', 'start', '-W', '-n', 'uz.shaxay.tvhome/.MainActivity'))
