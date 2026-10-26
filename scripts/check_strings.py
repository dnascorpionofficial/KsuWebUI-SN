#!/usr/bin/env python3
import re, sys, glob, os
import xml.etree.ElementTree as ET

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main")
RES = os.path.join(ROOT, "res")
PH = re.compile(r"%(?:\d+\$)?[sdfx]")
LIBRARY = {"appbar_scrolling_view_behavior"}

def load(path):
    out = {}
    for e in ET.parse(path).getroot().iter("string"):
        out[e.get("name")] = ("".join(e.itertext()), e.get("translatable") != "false")
    return out

base = load(os.path.join(RES, "values", "strings.xml"))
translatable = {k: v for k, (v, t) in base.items() if t}
problems = 0
WARN_ONLY = "--warn-only" in sys.argv
CI = os.environ.get("GITHUB_ACTIONS") == "true"

def report(msg):
    global problems
    problems += 1
    print(f"::warning title=Translation::{msg}" if CI and WARN_ONLY else msg)

for f in sorted(glob.glob(os.path.join(RES, "values-*", "strings.xml"))):
    loc = os.path.basename(os.path.dirname(f))
    if loc != loc.lower() and "-r" not in loc:
        report(f"[{loc}] folder name should be lowercase")
    todo = open(f, encoding="utf-8").read().count("TODO translate")
    if todo:
        report(f"[{loc}] {todo} string(s) still marked 'TODO translate'")
    cur = load(f)
    for k in translatable:
        if k not in cur:
            report(f"[{loc}] missing: {k}")
    for k, (v, _) in cur.items():
        if k not in base:
            report(f"[{loc}] no default for: {k}")
        elif k in translatable and sorted(PH.findall(v)) != sorted(PH.findall(translatable[k])):
            report(f"[{loc}] placeholder mismatch: {k}")

used = set()
for dp, _, fs in os.walk(ROOT):
    for fn in fs:
        if fn.endswith((".kt", ".java", ".xml")) and "strings.xml" not in fn:
            txt = open(os.path.join(dp, fn), encoding="utf-8", errors="ignore").read()
            used |= set(re.findall(r"(?<![\w.])R\.string\.(\w+)", txt)) | set(re.findall(r"@string/(\w+)", txt))
for k in sorted(used - set(base) - LIBRARY):
    report(f"[code] undefined string resource: {k}")

print("OK" if not problems else f"{problems} problem(s)")
sys.exit(1 if problems and not WARN_ONLY else 0)
