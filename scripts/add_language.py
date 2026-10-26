#!/usr/bin/env python3
import os, re, sys

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res")
USAGE = "Usage: python3 scripts/add_language.py <code>   (e.g. de, fr, pt-BR, zh-TW)"

def folder_for(code):
    m = re.fullmatch(r"([a-zA-Z]{2,3})(?:[-_]([a-zA-Z]{2}|\d{3}))?", code)
    if not m:
        sys.exit(f"Invalid language code '{code}'. Use e.g. de, fr, pt-BR, zh-TW.")
    lang, region = m.group(1).lower(), m.group(2)
    return f"values-{lang}" + (f"-r{region.upper()}" if region else "")

def main():
    if len(sys.argv) != 2:
        sys.exit(USAGE)
    folder = folder_for(sys.argv[1])
    out_dir = os.path.join(RES, folder)
    out = os.path.join(out_dir, "strings.xml")
    if os.path.exists(out):
        sys.exit(f"{folder}/strings.xml already exists - edit it instead.")
    src = open(os.path.join(RES, "values", "strings.xml"), encoding="utf-8").read()
    lines = []
    for m in re.finditer(r"<string\s+name=\"([^\"]+)\"([^>]*)>(.*?)</string>", src, re.S):
        if 'translatable="false"' in m.group(2):
            continue
        lines.append(f'    <!-- TODO translate -->\n    <string name="{m.group(1)}">{m.group(3)}</string>')
    os.makedirs(out_dir, exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + "\n".join(lines) + "\n</resources>\n")
    print(f"Created {os.path.relpath(out)} with {len(lines)} strings to translate.")

main()
