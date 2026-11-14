"""Google Play compliance checks for the Play build of וקשרתם (app.ukshartem).

Checks the FINAL artifacts (what Google receives), not the source:
  A. the merged manifest of the release APK (aapt2): target SDK, permissions, debuggable, backup,
     cleartext, foreground-service declaration, exported components, no device admin / accessibility
  B. native code and 16 KB page alignment, AAB signature
  C. store assets in store/: icon, feature graphic, phone screenshots (sizes, ratio, alpha)
  D. the listing text in store/google-play.md: lengths and metadata-policy patterns
  E. the privacy policy URL: reachable over HTTPS and covering camera, usage access, contact
  F. lint results (errors by id)
Every check prints PASS / WARN / FAIL with the evidence. Writes compliance-report.md.
Parts whose inputs are not given are skipped, so C to E can run on a laptop without a build.

  python3 check_play.py --apk ... --aab ... --lint ... --store store --listing store/google-play.md --privacy-url https://...
"""
import argparse
import glob
import os
import re
import shutil
import struct
import subprocess
import sys
import urllib.request
import zipfile

REQUIRED_TARGET_SDK = 35          # Play floor for new apps and updates since 31 Aug 2025 (raised yearly)
PACKAGE = "app.ukshartem"
FORBIDDEN_PERMISSIONS = {
    "android.permission.INTERNET": "the privacy policy and Data safety say the app has no internet access",
    "android.permission.QUERY_ALL_PACKAGES": "restricted; needs a declaration",
    "android.permission.REQUEST_INSTALL_PACKAGES": "restricted",
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS": "restricted; the Play flavor must not request it",
    "android.permission.MANAGE_EXTERNAL_STORAGE": "restricted",
    "android.permission.READ_EXTERNAL_STORAGE": "not needed",
    "android.permission.WRITE_EXTERNAL_STORAGE": "not needed",
    "android.permission.READ_MEDIA_IMAGES": "photo and video permissions policy",
    "android.permission.READ_MEDIA_VIDEO": "photo and video permissions policy",
    "android.permission.ACCESS_FINE_LOCATION": "not needed; city is chosen by hand",
    "android.permission.ACCESS_COARSE_LOCATION": "not needed",
    "android.permission.ACCESS_BACKGROUND_LOCATION": "restricted",
    "android.permission.READ_CONTACTS": "not needed",
    "android.permission.READ_SMS": "restricted (SMS and call log policy)",
    "android.permission.READ_CALL_LOG": "restricted (SMS and call log policy)",
    "android.permission.READ_PHONE_STATE": "not needed",
    "android.permission.RECORD_AUDIO": "not needed",
    "android.permission.SCHEDULE_EXACT_ALARM": "restricted on Android 14+",
    "android.permission.USE_EXACT_ALARM": "restricted to alarm/calendar apps",
    "android.permission.BIND_ACCESSIBILITY_SERVICE": "accessibility API policy",
    "com.google.android.gms.permission.AD_ID": "the app declares no advertising ID",
}

results = []


def rec(status, area, check, evidence):
    results.append((status, area, check, evidence))
    print(f"[{status}] {area}: {check} -- {evidence}")


def find_tool(name):
    home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""
    cands = sorted(glob.glob(os.path.join(home, "build-tools", "*", name)))
    if cands:
        return cands[-1]
    return shutil.which(name)


def attr_bool(text, name):
    """True/False for a boolean manifest attribute in aapt2 xmltree output, None when absent."""
    m = re.search(name + r"\(0x[0-9a-f]+\)=(?:\(type 0x12\))?\"?(0x[0-9a-f]+|true|false)\"?", text)
    if not m:
        return None
    v = m.group(1)
    return v == "true" or (v.startswith("0x") and int(v, 16) != 0)


def run(cmd):
    p = subprocess.run(cmd, capture_output=True, text=True)
    return p.returncode, p.stdout + p.stderr


# ---------------------------------------------------------------- A. manifest
def check_manifest(apk):
    aapt2 = find_tool("aapt2")
    if not aapt2:
        rec("WARN", "manifest", "aapt2 available", "not found; manifest checks skipped")
        return
    _, badging = run([aapt2, "dump", "badging", apk])
    _, perms_out = run([aapt2, "dump", "permissions", apk])
    _, tree = run([aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", apk])

    m = re.search(r"package: name='([^']+)'.*?versionCode='(\d+)'.*?versionName='([^']*)'", badging)
    if m:
        rec("PASS" if m.group(1) == PACKAGE else "FAIL", "manifest", "package name",
            f"{m.group(1)} versionCode {m.group(2)} versionName {m.group(3)}")
    t = re.search(r"targetSdkVersion:'(\d+)'", badging)
    mn = re.search(r"(?:minSdkVersion|sdkVersion):'(\d+)'", badging)
    if t:
        tv = int(t.group(1))
        rec("PASS" if tv >= REQUIRED_TARGET_SDK else "FAIL", "manifest", f"targetSdk >= {REQUIRED_TARGET_SDK}",
            f"targetSdk {tv}, minSdk {mn.group(1) if mn else '?'}")

    perms = sorted(set(re.findall(r"(?:uses-permission|permission): name='([^']+)'", perms_out)))
    perms = [p for p in perms if not p.endswith("DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")]
    rec("PASS", "manifest", "declared permissions (for the Data safety form)", ", ".join(perms))
    bad = [p for p in perms if p in FORBIDDEN_PERMISSIONS]
    if bad:
        for p in bad:
            rec("FAIL", "manifest", f"no {p.split('.')[-1]}", FORBIDDEN_PERMISSIONS[p])
    else:
        rec("PASS", "manifest", "no restricted or contradicting permissions",
            "INTERNET, QUERY_ALL_PACKAGES, battery exemption, storage, location, SMS, exact alarm, AD_ID all absent")

    rec("FAIL" if "application-debuggable" in badging else "PASS", "manifest", "release is not debuggable",
        "application-debuggable present" if "application-debuggable" in badging else "no debuggable flag")
    ab = attr_bool(tree, "allowBackup")
    rec("PASS" if ab is False else "FAIL", "manifest", "allowBackup=false (privacy policy: no cloud backup)", f"allowBackup={ab}")
    der = "dataExtractionRules" in tree
    rec("PASS" if der else "FAIL", "manifest", "dataExtractionRules set (no backup or device transfer on Android 12+)",
        "present" if der else "missing")
    ct = attr_bool(tree, "usesCleartextTraffic")
    rec("FAIL" if ct else "PASS", "manifest", "no cleartext traffic", f"usesCleartextTraffic={ct}")

    # foreground service types and their permissions
    fgs = re.findall(r"foregroundServiceType\(0x[0-9a-f]+\)=\(type 0x11\)0x([0-9a-f]+)", tree)
    types = {0x40000000: "specialUse", 0x1: "dataSync", 0x2: "mediaPlayback", 0x40: "camera", 0x8: "location"}
    for v in fgs:
        val = int(v, 16)
        names = [n for bit, n in types.items() if val & bit] or [hex(val)]
        for n in names:
            need = "android.permission.FOREGROUND_SERVICE_" + re.sub(r"(?<!^)(?=[A-Z])", "_", n).upper()
            rec("PASS" if need in perms else "FAIL", "manifest", f"FGS type {n} has its permission", need)
    if any(int(v, 16) & 0x40000000 for v in fgs):
        has_prop = "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" in tree
        rec("PASS" if has_prop else "FAIL", "manifest", "specialUse FGS carries PROPERTY_SPECIAL_USE_FGS_SUBTYPE",
            "present" if has_prop else "missing")
        rec("WARN", "console", "Play Console foreground-service declaration (specialUse)",
            "must be filled by hand with the justification and a video; text ready in store/google-play.md")

    # components
    has_admin = "BIND_DEVICE_ADMIN" in tree or "android.app.device_admin" in tree
    rec("FAIL" if has_admin else "PASS", "manifest", "no device admin in the Play build", "device admin receiver present" if has_admin else "absent")
    has_a11y = "android.accessibilityservice" in tree
    rec("FAIL" if has_a11y else "PASS", "manifest", "no accessibility service", "present" if has_a11y else "absent")
    exported = []
    blocks = re.split(r"\n\s*E: (?=activity|receiver|service|provider)", tree)
    for b in blocks[1:]:
        kind = b.split(" ", 1)[0]
        name = re.search(r'A: android:name\(0x[0-9a-f]+\)="([^"]+)"', b)
        if attr_bool(b, "exported"):
            exported.append(f"{kind} {name.group(1) if name else '?'}")
    expected = {"activity app.henachti.ui.MainActivity", "receiver app.henachti.service.BootReceiver"}
    extra = [e for e in exported if e not in expected]
    rec("FAIL" if extra or not exported else "PASS", "manifest", "exported components are only the launcher and the boot receiver",
        "; ".join(exported) or "none found (parse problem)")


# ---------------------------------------------------------------- B. native code, 16 KB, signature
def elf_min_align(data):
    if data[:4] != b"\x7fELF" or data[4] != 2:  # 64-bit only
        return None
    e_phoff = struct.unpack_from("<Q", data, 32)[0]
    e_phentsize, e_phnum = struct.unpack_from("<HH", data, 54)
    aligns = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type = struct.unpack_from("<I", data, off)[0]
        if p_type == 1:  # PT_LOAD
            aligns.append(struct.unpack_from("<Q", data, off + 48)[0])
    return min(aligns) if aligns else None


def check_native_and_signing(apk, aab):
    if apk:
        z = zipfile.ZipFile(apk)
        libs = [n for n in z.namelist() if n.startswith("lib/") and n.endswith(".so")]
        if not libs:
            rec("PASS", "native", "16 KB page size", "no native libraries in the APK, so the requirement does not apply")
        else:
            abis = sorted({n.split("/")[1] for n in libs})
            rec("PASS" if "arm64-v8a" in abis else "FAIL", "native", "64-bit (arm64-v8a) present", ", ".join(abis))
            small = []
            for n in libs:
                if "/arm64-v8a/" in n or "/x86_64/" in n:
                    a = elf_min_align(z.read(n))
                    if a is not None and a < 0x4000:
                        small.append(f"{n} align {hex(a)}")
            rec("FAIL" if small else "PASS", "native", "LOAD segments aligned to 16 KB", "; ".join(small) or f"{len(libs)} libs ok")
            za = find_tool("zipalign")
            if za:
                code, out = run([za, "-c", "-P", "16", "-v", "4", apk])
                rec("PASS" if code == 0 else "FAIL", "native", "zip alignment of .so for 16 KB", "zipalign -c -P 16 ok" if code == 0 else out[-300:])
    if aab:
        size = os.path.getsize(aab)
        rec("PASS" if size < 150 * 1024 * 1024 else "WARN", "bundle", "bundle size", f"{size/1024/1024:.1f} MB")
        js = shutil.which("jarsigner")
        if js:
            code, out = run([js, "-verify", aab])
            ok = "jar verified" in out
            rec("PASS" if ok else "FAIL", "bundle", "AAB is signed", "jar verified" if ok else out[-300:])
            _, certs = run(["keytool", "-printcert", "-jarfile", aab])
            debug = "CN=Android Debug" in certs
            rec("FAIL" if debug else "PASS", "bundle", "signed with the upload key, not the debug key",
                "debug certificate" if debug else (re.search(r"Owner: ([^\n]+)", certs).group(1) if "Owner:" in certs else "?"))


# ---------------------------------------------------------------- C. store assets
def img_info(path):
    """Return (w, h, has_alpha, fmt) for PNG/JPEG without third-party libraries."""
    with open(path, "rb") as f:
        head = f.read(64)
        if head[:8] == b"\x89PNG\r\n\x1a\n":
            w, h = struct.unpack(">II", head[16:24])
            color_type = head[25]
            return w, h, color_type in (4, 6), "png"
        if head[:2] == b"\xff\xd8":
            f.seek(2)
            while True:
                marker = f.read(2)
                if len(marker) < 2:
                    break
                ln = struct.unpack(">H", f.read(2))[0]
                if marker[1] in (0xC0, 0xC1, 0xC2):
                    f.read(1)
                    h, w = struct.unpack(">HH", f.read(4))
                    return w, h, False, "jpeg"
                f.seek(ln - 2, 1)
    return None


def check_assets(store):
    icon = os.path.join(store, "icon-512.png")
    if os.path.exists(icon):
        w, h, alpha, fmt = img_info(icon)
        size = os.path.getsize(icon)
        ok = (w, h) == (512, 512) and fmt == "png" and size <= 1024 * 1024
        rec("PASS" if ok else "FAIL", "assets", "app icon 512x512 PNG <= 1 MB", f"{w}x{h} {fmt} {size//1024} KB alpha={alpha}")
    else:
        rec("FAIL", "assets", "app icon", "store/icon-512.png missing")
    fg = os.path.join(store, "feature-graphic.png")
    if os.path.exists(fg):
        w, h, alpha, fmt = img_info(fg)
        ok = (w, h) == (1024, 500) and not alpha
        rec("PASS" if ok else "FAIL", "assets", "feature graphic 1024x500, no alpha", f"{w}x{h} {fmt} alpha={alpha}")
    else:
        rec("FAIL", "assets", "feature graphic", "missing")
    shots = sorted(glob.glob(os.path.join(store, "screenshots", "*.jpg")) + glob.glob(os.path.join(store, "screenshots", "*.png")))
    rec("PASS" if 2 <= len(shots) <= 8 else "FAIL", "assets", "2 to 8 phone screenshots", f"{len(shots)} files")
    for s in shots:
        w, h, alpha, fmt = img_info(s)
        lo, hi = min(w, h), max(w, h)
        ok = 320 <= lo and hi <= 3840 and hi <= 2 * lo and not alpha
        rec("PASS" if ok else "FAIL", "assets", f"screenshot {os.path.basename(s)}", f"{w}x{h} ratio {hi/lo:.2f} alpha={alpha}")


# ---------------------------------------------------------------- D. listing text
def code_block_after(text, label):
    i = text.find(label)
    if i < 0:
        return None
    m = re.search(r"```\n(.*?)\n```", text[i:], re.S)
    return m.group(1) if m else None


EMOJI = re.compile("[\U0001F300-\U0001FAFF☀-➿]")


def check_listing(path):
    t = open(path, encoding="utf-8").read()
    name = re.search(r"App name: `([^`]+)`", t)
    if name:
        n = name.group(1)
        issues = []
        if len(n) > 30:
            issues.append(f"{len(n)} chars > 30")
        if EMOJI.search(n):
            issues.append("emoji")
        if re.search(r"(?i)\b(best|top|#1|free|new)\b|הכי טוב|מספר 1|חינם|חדש", n):
            issues.append("ranking or price words")
        rec("FAIL" if issues else "PASS", "listing", "app title (<= 30 chars, no emoji, no ranking/price words)", f"'{n}' {len(n)} chars {'; '.join(issues)}")
    short = code_block_after(t, "Short description")
    if short:
        issues = [f"{len(short)} > 80"] if len(short) > 80 else []
        if EMOJI.search(short):
            issues.append("emoji")
        rec("FAIL" if issues else "PASS", "listing", "short description <= 80", f"{len(short)} chars {'; '.join(issues)}")
    full = code_block_after(t, "Full description")
    if full:
        issues = []
        if len(full) > 4000:
            issues.append(f"{len(full)} > 4000")
        if EMOJI.search(full):
            issues.append("emoji")
        if re.search(r"(?i)\b(best|#1|top rated)\b|הכי טוב|מספר 1 ", full):
            issues.append("ranking claims")
        if re.search(r"(?i)download now|הורד עכשיו|!!", full):
            issues.append("calls to action or !!")
        if "—" in full:
            issues.append("em dash")
        rec("FAIL" if issues else "PASS", "listing", "full description (<= 4000, no emoji, no ranking claims)", f"{len(full)} chars {'; '.join(issues)}")
        mentions = {"widget": "ווידג'ט" in full, "no internet": "אינטרנט" in full, "camera": "מצלמה" in full or "המצלמה" in full}
        rec("PASS" if all(mentions.values()) else "WARN", "listing", "description matches the app (camera, no internet, widget)",
            ", ".join(f"{k}={'yes' if v else 'no'}" for k, v in mentions.items()))


# ---------------------------------------------------------------- E. privacy policy
def check_privacy(url):
    if not url.startswith("https://"):
        rec("FAIL", "privacy", "privacy policy over HTTPS", url)
        return
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "compliance-check"})
        with urllib.request.urlopen(req, timeout=30) as r:
            code = r.status
            body = r.read().decode("utf-8", "replace")
    except Exception as e:  # noqa: BLE001
        rec("FAIL", "privacy", "privacy policy reachable", str(e)[:200])
        return
    rec("PASS" if code == 200 else "FAIL", "privacy", "privacy policy reachable", f"HTTP {code}, {len(body)} bytes")
    text = re.sub(r"<[^>]+>", " ", body)
    need = {
        "names the app": ("Ukshartem" in text or "וקשרתם" in text),
        "camera use": ("camera" in text.lower() or "מצלמה" in text),
        "usage access": ("usage" in text.lower() or "נתוני שימוש" in text),
        "no data leaves the device": ("not collect" in text.lower() or "לא אוספת" in text),
        "retention and deletion": ("Uninstall" in text or "הסרת האפליקציה" in text),
        "contact route": bool(re.search(r"mailto:|contact|יצירת קשר|issues", body, re.I)),
    }
    for k, v in need.items():
        rec("PASS" if v else "FAIL", "privacy", f"policy covers: {k}", "yes" if v else "missing")
    mail = re.search(r"mailto:([^\"'>]+)", body)
    rec("PASS" if mail else "WARN", "privacy", "a direct privacy contact email",
        mail.group(1) if mail else "only a contact form link; Play accepts it, an email is clearer")


# ---------------------------------------------------------------- F. lint
def check_lint(path):
    if not os.path.exists(path):
        rec("WARN", "lint", "lint results", f"{path} not found")
        return
    x = open(path, encoding="utf-8").read()
    issues = re.findall(r'<issue\s+id="([^"]+)"\s+severity="([^"]+)"', x)
    by = {}
    for i, sev in issues:
        by.setdefault(sev, {}).setdefault(i, 0)
        by[sev][i] += 1
    for sev in ("Fatal", "Error"):
        if by.get(sev):
            rec("FAIL", "lint", f"no lint {sev.lower()}s", ", ".join(f"{k} x{v}" for k, v in sorted(by[sev].items())))
        else:
            rec("PASS", "lint", f"no lint {sev.lower()}s", "none")
    w = by.get("Warning", {})
    policy_ids = {"ForegroundServiceType", "QueryAllPackagesPermission", "ExportedReceiver", "ExportedService", "ProtectedPermissions",
                  "SystemPermissionTypo", "OldTargetApi", "ScheduleExactAlarm", "UnspecifiedRegisterReceiverFlag", "DataExtractionRules"}
    hits = {k: v for k, v in w.items() if k in policy_ids}
    rec("WARN" if hits else "PASS", "lint", "policy-related lint warnings", ", ".join(f"{k} x{v}" for k, v in hits.items()) or "none")
    rec("PASS", "lint", "all warnings (for reference)", f"{sum(w.values())} warnings in {len(w)} kinds")


def write_report(path):
    fails = [r for r in results if r[0] == "FAIL"]
    warns = [r for r in results if r[0] == "WARN"]
    lines = ["# Google Play compliance report (וקשרתם, app.ukshartem)", "",
             f"**{len(results)} checks: {len(results) - len(fails) - len(warns)} PASS, {len(warns)} WARN, {len(fails)} FAIL**", "",
             "| status | area | check | evidence |", "|---|---|---|---|"]
    for s, a, c, e in results:
        lines.append(f"| {s} | {a} | {c} | {e.replace('|', '/')} |")
    open(path, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print(f"\n{len(fails)} FAIL, {len(warns)} WARN -> {path}")
    return len(fails)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk")
    ap.add_argument("--aab")
    ap.add_argument("--lint")
    ap.add_argument("--store")
    ap.add_argument("--listing")
    ap.add_argument("--privacy-url")
    ap.add_argument("--report", default="compliance-report.md")
    a = ap.parse_args()
    if a.apk:
        check_manifest(a.apk)
    if a.apk or a.aab:
        check_native_and_signing(a.apk, a.aab)
    if a.store:
        check_assets(a.store)
    if a.listing:
        check_listing(a.listing)
    if a.privacy_url:
        check_privacy(a.privacy_url)
    if a.lint:
        check_lint(a.lint)
    sys.exit(1 if write_report(a.report) else 0)
