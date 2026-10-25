"""Black-box test of the exact patched APK. Never equate launch with playback."""
import json
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET
import uiautomator2 as u2

OUT = pathlib.Path('youtube-results')
OUT.mkdir(exist_ok=True)
PKG = 'app.revanced.android.youtube'
report = {'install': 'not_run', 'launch': 'not_run', 'video_progress': 'not_run',
          'shorts': 'not_verified', 'quality_above_720': 'not_verified'}

def adb(*args, check=True):
    return subprocess.run(['adb', *args], check=check, capture_output=True, text=True).stdout

def evidence(device, name):
    device.screenshot(str(OUT / (name + '.png')))
    xml = device.dump_hierarchy()
    (OUT / (name + '.xml')).write_text(xml)
    (OUT / (name + '-media.txt')).write_text(adb('shell', 'dumpsys', 'media_session'))
    return xml

def current_seconds(xml):
    # Only a displayed current-position field counts, not estimated session time.
    for node in ET.fromstring(xml).iter('node'):
        if node.get('resource-id', '').endswith(':id/time_bar_current_time'):
            text = node.get('text', '')
            if re.fullmatch(r'\d+:\d{2}(?::\d{2})?', text):
                value = 0
                for item in text.split(':'): value = value * 60 + int(item)
                return value
    return None

try:
    adb('install', '-r', 'gmscore.apk')
    adb('install', '-r', 'youtube.apk')
    report['install'] = 'passed'
    adb('logcat', '-c')
    d = u2.connect()
    d.app_start(PKG, stop=True)
    time.sleep(15)
    evidence(d, 'launch')
    assert d.app_current().get('package') == PKG, 'App not in foreground; inspect launch evidence'
    report['launch'] = 'passed'
    adb('shell', 'am', 'start', '-a', 'android.intent.action.VIEW', '-d',
        'https://www.youtube.com/watch?v=aqz-KE-bpKQ', '-p', PKG)
    time.sleep(20)
    positions = []
    for index in range(8):
        assert d.app_current().get('package') == PKG, 'App exited during playback'
        # Reveal player controls when hidden; retain XML to diagnose UI differences.
        if not d(resourceId=PKG + ':id/time_bar_current_time').exists:
            width, height = d.window_size()
            d.click(width // 2, height // 5)
            time.sleep(1)
        xml = evidence(d, 'video-' + str(index))
        positions.append(current_seconds(xml))
        time.sleep(20)
    report['position_samples_seconds'] = positions
    samples = [p for p in positions if p is not None]
    advancing = sum(b > a for a, b in zip(samples, samples[1:]))
    report['video_progress'] = 'passed' if len(samples) >= 6 and advancing >= 5 else 'failed_or_unobservable'
    # Collect Shorts UI evidence; this is explicitly not a verified playback test.
    d.app_start(PKG, stop=True)
    time.sleep(10)
    short = d(descriptionMatches='(?i).*shorts.*')
    if short.exists:
        short[0].click()
        time.sleep(20)
        evidence(d, 'shorts')
    assert report['video_progress'] == 'passed', 'Sustained playback not established; inspect evidence'
finally:
    (OUT / 'logcat.txt').write_text(adb('logcat', '-d', check=False))
    (OUT / 'report.json').write_text(json.dumps(report, indent=2))
    # An automated pass for all requested requirements is impossible until
    # Shorts progress and actual resolution are established from evidence.
    print(json.dumps(report, indent=2))
