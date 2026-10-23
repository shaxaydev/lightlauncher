#!/usr/bin/env python3
"""Measure process-cold launch and 14 focus moves on a connected TV. Does not reboot the TV."""
import json
import os
from pathlib import Path
import re
import shutil
import statistics
import subprocess
import sys
import time

serial, directory = sys.argv[1:3]
adb = [os.environ.get('ADB') or shutil.which('adb'), '-s', serial]

def shell(*args):
    return subprocess.check_output(adb + ['shell', *args], timeout=30, text=True)

launches = []
for _ in range(3):
    shell('am', 'force-stop', 'uz.shaxay.tvhome')
    launches.append(shell('am', 'start', '-W', '-a', 'android.intent.action.MAIN',
                         '-c', 'android.intent.category.HOME', '-n', 'uz.shaxay.tvhome/.MainActivity'))
    time.sleep(.8)
shell('dumpsys', 'gfxinfo', 'uz.shaxay.tvhome', 'reset')
for key in ['KEYCODE_DPAD_RIGHT'] * 7 + ['KEYCODE_DPAD_LEFT'] * 7:
    shell('input', 'keyevent', key)
    time.sleep(.2)
time.sleep(.5)
gfx = shell('dumpsys', 'gfxinfo', 'uz.shaxay.tvhome')
memory = shell('dumpsys', 'meminfo', 'uz.shaxay.tvhome')
out = Path(directory)
out.mkdir(parents=True, exist_ok=True)
(out / 'launch.txt').write_text('\n'.join(launches))
(out / 'gfxinfo.txt').write_text(gfx)
(out / 'meminfo.txt').write_text(memory)
times = [int(re.search(r'TotalTime: (\d+)', value).group(1)) for value in launches]
summary = {'cold_launch_ms': times, 'median_cold_launch_ms': statistics.median(times)}
(out / 'summary.json').write_text(json.dumps(summary, indent=2))
print(json.dumps(summary, indent=2))
print('\n'.join(line for line in gfx.splitlines() if any(term in line for term in
      ['frames rendered', 'Janky frames', 'percentile', 'Texture:', 'Total GPU', 'bytes,'])))
print('\n'.join(line for line in memory.splitlines() if 'TOTAL:' in line or 'Activities:' in line))
