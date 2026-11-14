#!/usr/bin/env bash
# Screenshot run for וקשרתם: drives the DEBUG build on an emulator through every page and state
# and writes PNGs to ./shots. Run by .github/workflows/screenshots.yml (manual trigger only).
# The emulator clock is set by hand per state (Israel time); debug builds skip the auto-time check.
set -u
PKG=app.henachti
OUT="${OUT:-shots}"
HERE="$(cd "$(dirname "$0")" && pwd)"
APK="${APK:-app/build/outputs/apk/direct/debug/app-direct-debug.apk}"
mkdir -p "$OUT"
log() { echo "== $*"; }

adb root >/dev/null 2>&1
sleep 5
adb wait-for-device
adb shell settings put global auto_time 0
adb shell settings put global auto_time_zone 0
adb shell setprop persist.sys.timezone Asia/Jerusalem
adb shell svc power stayon true
adb shell settings put system screen_off_timeout 1800000
adb shell input keyevent 82
adb shell cmd uimode night no
adb install -r "$APK" || { echo "install failed"; exit 1; }
APP_UID=$(adb shell stat -c %u /data/data/$PKG | tr -d '\r')
log "app uid $APP_UID, device zone $(adb shell getprop persist.sys.timezone | tr -d '\r')"

# setclock "2026-11-16 06:50"   (Israel time)
setclock() {
  local utc
  utc=$(python3 -c "import sys,datetime,zoneinfo; d=datetime.datetime.strptime(sys.argv[1],'%Y-%m-%d %H:%M').replace(tzinfo=zoneinfo.ZoneInfo('Asia/Jerusalem')).astimezone(datetime.timezone.utc); print(d.strftime('%m%d%H%M%Y.%S'))" "$1")
  adb shell date -u "$utc" >/dev/null
  log "clock $1 -> device: $(adb shell date | tr -d '\r')"
}

# writeprefs <prefs.py args>: the app is stopped, its data replaced, ownership restored
writeprefs() {
  python3 "$HERE/prefs.py" "$@" > /tmp/henachti.xml || exit 1
  adb shell am force-stop $PKG
  adb shell mkdir -p /data/data/$PKG/shared_prefs
  adb push /tmp/henachti.xml /data/data/$PKG/shared_prefs/henachti.xml >/dev/null
  adb shell chown -R "$APP_UID:$APP_UID" /data/data/$PKG/shared_prefs
  adb shell chmod 771 /data/data/$PKG/shared_prefs
  adb shell chmod 660 /data/data/$PKG/shared_prefs/henachti.xml
  adb shell restorecon -R /data/data/$PKG/shared_prefs >/dev/null 2>&1
}
clearprefs() {
  adb shell am force-stop $PKG
  adb shell rm -rf /data/data/$PKG/shared_prefs
}
shot() {
  sleep "${2:-4}"
  adb exec-out screencap -p > "$OUT/$1.png"
  log "shot $1 ($(stat -c %s "$OUT/$1.png") bytes)"
}
app() { adb shell am start -W -S -n $PKG/.ui.MainActivity "$@" >/dev/null; }
activity() { adb shell am start -W -n "$PKG/$1" >/dev/null; }
scroll() { adb shell input swipe 540 1900 540 650 400; }
back() { adb shell input keyevent 4; }

grant_all() {
  adb shell appops set $PKG GET_USAGE_STATS allow
  adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
  adb shell pm grant $PKG android.permission.CAMERA
  adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
  adb shell dpm set-active-admin --user 0 $PKG/.service.AdminReceiver
  adb shell dumpsys deviceidle whitelist +$PKG >/dev/null
}

# A Monday and a Shabbat in November 2026 (no festivals that month)
MON=2026-11-16
SAT=2026-11-21
BASE="--armed --streak 23 --milestone-seen 30 --dedication healing"

log "1. first run, nothing granted yet"
setclock "$MON 09:00"
clearprefs
for s in 0 1 2 3 4 5 6 7 8; do app --ei step $s; shot "01_first_run_step$s" 3; done
grant_all
app --ei step 8; shot 02_first_run_ready_to_arm 3

log "2. Monday 06:50, locked"
writeprefs --now "$MON 06:50" $BASE
setclock "$MON 06:50"
app; shot 10_home_locked
scroll; shot 11_home_locked_scrolled 2
activity .ui.LockActivity; shot 12_lock_screen
activity .ui.VerifyActivity; shot 13_verify_camera 5; shot 14_verify_after_10s 9
back

log "3. Monday 07:05, snoozed"
writeprefs --now "$MON 07:05" $BASE --snooze-used 1 --snooze-until "$MON 07:30"
setclock "$MON 07:05"
app; shot 20_home_snoozed
activity .ui.LockActivity; shot 21_lock_snoozed

log "4. Monday 12:15, verified at 07:12"
writeprefs --now "$MON 12:15" --armed --streak 23 --verified-at "$MON 07:12" --milestone-seen 0 --dedication healing
setclock "$MON 12:15"
app; shot 30_home_verified_milestone_card
writeprefs --now "$MON 12:15" --armed --streak 23 --verified-at "$MON 07:12" --milestone-seen 7 --dedication healing
app; shot 31_home_verified
scroll; shot 32_home_verified_scrolled 2

log "5. every page"
app --es route prayers; shot 40_siddur_list
app --es route prayer/shacharit; shot 41_siddur_shacharit
app --es route prayer/tefillin; shot 42_siddur_tefillin_order
app --es route dedication; shot 43_dedication
app --es route dedicate/memory; shot 44_dedicate_memory
app --es route settings; shot 45_settings
scroll; shot 46_settings_2 2
scroll; shot 47_settings_3 2
app --es route about; shot 48_about
app --es route onboarding; shot 49_permissions_from_settings

log "6. the notification"
app
adb shell cmd statusbar expand-notifications
shot 50_notification_shade 3
adb shell cmd statusbar collapse

log "7. evening and early"
writeprefs --now "$MON 18:30" --armed --streak 23 --milestone-seen 30 --strict 0
setclock "$MON 18:30"
app; shot 60_home_after_sunset
writeprefs --now "$MON 18:30" --armed --streak 23 --milestone-seen 30 --strict 1
app; shot 61_home_after_sunset_strict_still_locked
writeprefs --now "$MON 06:30" --armed --streak 23 --milestone-seen 30 --lock-start 480
setclock "$MON 06:30"
app; shot 62_home_before_lock_time

log "8. Shabbat"
writeprefs --now "$SAT 12:00" --armed --streak 27 --milestone-seen 30
setclock "$SAT 12:00"
app; shot 70_home_shabbat
setclock "$SAT 18:30"
app; shot 71_home_motzaei_shabbat

log "9. a permission switched off (tamper state)"
writeprefs --now "$MON 06:50" $BASE
setclock "$MON 06:50"
adb shell appops set $PKG SYSTEM_ALERT_WINDOW deny
app; shot 80_home_tampered
activity .ui.LockActivity; shot 81_lock_tampered
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow

log "10. large system font (130%)"
writeprefs --now "$MON 06:50" $BASE
adb shell settings put system font_scale 1.3
app; shot 90_home_locked_font130
activity .ui.LockActivity; shot 91_lock_font130
app --es route settings; shot 92_settings_font130
adb shell settings put system font_scale 1.0

adb logcat -d -b crash > "$OUT/crash-log.txt" 2>/dev/null
log "crashes: $(grep -c 'FATAL EXCEPTION' "$OUT/crash-log.txt" || true)"
grep -A12 'FATAL EXCEPTION' "$OUT/crash-log.txt" | head -60
log "done: $(ls "$OUT"/*.png | wc -l) screenshots"
