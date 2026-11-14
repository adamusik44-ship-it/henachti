"""Writes the app's SharedPreferences (henachti.xml) for one screenshot state.

Used only by the screenshot run in CI (.github/screenshots/shots.sh) on an emulator, with the
debug build. All times are Israel time. Example:
  python3 prefs.py --now "2026-11-16 12:15" --armed --streak 23 --verified-at "2026-11-16 07:12"
"""
import argparse
import datetime as dt
import sys
import zoneinfo
from xml.sax.saxutils import escape

IL = zoneinfo.ZoneInfo("Asia/Jerusalem")
SAT = 5

p = argparse.ArgumentParser()
p.add_argument("--now", required=True)
p.add_argument("--armed", action="store_true")
p.add_argument("--streak", type=int, default=0, help="verified tefillin days before today")
p.add_argument("--verified-at", help="today was verified at this time")
p.add_argument("--milestone-seen", type=int, default=0)
p.add_argument("--snooze-used", type=int, default=0)
p.add_argument("--snooze-until")
p.add_argument("--lock-start", type=int, default=-1)
p.add_argument("--strict", type=int, default=1)
p.add_argument("--dedication", help="dedication type id, e.g. healing")
a = p.parse_args()


def t(s):
    return dt.datetime.strptime(s, "%Y-%m-%d %H:%M").replace(tzinfo=IL)


def ms(d):
    return int(d.timestamp() * 1000)


now = t(a.now)
today = now.date()  # every moment in the run is after misheyakir, so the tefillin day is the calendar day

# the streak: consecutive tefillin days back from yesterday (Shabbat skipped), plus today if verified
days = [today] if a.verified_at else []
d = today - dt.timedelta(days=1)
while len(days) < a.streak + (1 if a.verified_at else 0):
    if d.weekday() != SAT:
        days.append(d)
    d -= dt.timedelta(days=1)
# one missed day before the streak, and three verified days before that, then the day the lock was armed
missed = 0
while missed < 1:
    if d.weekday() != SAT:
        missed += 1
    d -= dt.timedelta(days=1)
older = []
while len(older) < 3:
    if d.weekday() != SAT:
        older.append(d)
    d -= dt.timedelta(days=1)
armed_day = d
verified = days + older

out = ["<?xml version='1.0' encoding='utf-8' standalone='yes' ?>", "<map>"]


def b(k, v):
    out.append(f'    <boolean name="{k}" value="{"true" if v else "false"}" />')


def i(k, v):
    out.append(f'    <int name="{k}" value="{v}" />')


def lng(k, v):
    out.append(f'    <long name="{k}" value="{v}" />')


def s(k, v):
    out.append(f'    <string name="{k}">{escape(v)}</string>')


b("armed", a.armed)
if a.armed:
    lng("armed_at", ms(dt.datetime.combine(armed_day, dt.time(9, 0), IL)))
    out.append('    <set name="verified_days">')
    for v in sorted(verified):
        out.append(f"        <string>{v.isoformat()}</string>")
    out.append("    </set>")
    i("verified_count", len(verified) + 2)
    if a.verified_at:
        s("verified_day", today.isoformat())
        lng("verified_at", ms(t(a.verified_at)))
    i("snoozes_per_day", 3)
    if a.snooze_used:
        s("snooze_day", today.isoformat())
        i("snooze_count", a.snooze_used)
        lng("snooze_until", ms(t(a.snooze_until)))
    i("lock_start_minutes", a.lock_start)
    b("strict_after_sunset", bool(a.strict))
    i("milestone_seen", a.milestone_seen)
    if a.dedication:
        s("dedication_type", a.dedication)
        s("dedication_name", "פלוני בן פלונית")
i("city_index", 0)
out.append("</map>")

sys.stdout.reconfigure(encoding="utf-8")
print("\n".join(out))
