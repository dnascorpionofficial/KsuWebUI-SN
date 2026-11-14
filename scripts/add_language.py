#!/usr/bin/env python3
"""Create a new language file and translate

  python3 scripts/add_language.py de >> create values-de/strings.xml, then translate
  
  python3 scripts/add_language.py de >> (again) resume: only strings still marked TODO,
                                         new English strings are added to the file first
  
  python3 scripts/add_language.py de --edit reboot_now >> change that one translation (several names allowed)
  python3 scripts/add_language.py de --edit >> go through every string (Enter keeps it)
"""
import os, re, shutil, sys, unicodedata

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res")
USAGE = ("Usage: python3 scripts/add_language.py <code>   (e.g. de, fr, pt-BR, zh-TW)\n"
         "       python3 scripts/add_language.py <code> --edit [name ...]")
PH = re.compile(r"%(?:\d+\$)?[sdfx]")
STRING = re.compile(r"<string\s+name=\"([^\"]+)\"([^>]*)>(.*?)</string>", re.S)
TODO = re.compile(r"[ \t]*<!-- TODO translate -->[ \t]*\n[ \t]*<string name=\"([^\"]+)\"([^>]*)>(.*?)</string>", re.S)
SKIPPED = "Not Translated"
HINT = ("\nType quit_translate & press Enter to save and quit.\n"
        "Type \\n for a line break. Keep %s / %1$d placeholders texts")

def folder_for(code):
    m = re.fullmatch(r"([a-zA-Z]{2,3})(?:[-_]([a-zA-Z]{2}|\d{3}))?", code)
    if not m:
        sys.exit(f"Invalid language code '{code}'. Use e.g. de, fr, pt-BR, zh-TW.")
    lang, region = m.group(1).lower(), m.group(2)
    return f"values-{lang}" + (f"-r{region.upper()}" if region else "")


def width(s):
    return sum(0 if unicodedata.combining(c) else 2 if unicodedata.east_asian_width(c) in "WF" else 1 for c in s)

def wrap(s, size):
    lines, cur = [], ""
    for word in s.split(" "):
        while width(word) > size:
            if cur:
                lines.append(cur)
                cur = ""
            head, n = "", 0
            for c in word:
                if n + width(c) > size:
                    break
                head, n = head + c, n + width(c)
            lines.append(head)
            word = word[len(head):]
        if not cur:
            cur = word
        elif width(cur) + 1 + width(word) <= size:
            cur += " " + word
        else:
            lines.append(cur)
            cur = word
    return lines + [cur] if cur or not lines else lines

def pad(s, size):
    return s + " " * (size - width(s))

def readable(raw):
    s = re.sub(r"\s+", " ", raw).strip()
    for a, b in (("&lt;", "<"), ("&gt;", ">"), ("&quot;", '"'), ("&amp;", "&")):
        s = s.replace(a, b)
    return re.sub(r"\\([\'\"@?])", r"\1", s)

def to_xml(text):
    s = text.strip().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = re.sub(r"(?<!\\)'", r"\\'", s)
    s = re.sub(r'(?<!\\)"', r'\\"', s)
    return "\\" + s if s[:1] in ("@", "?") else s

def read_base():
    src = open(os.path.join(RES, "values", "strings.xml"), encoding="utf-8").read()
    return {m.group(1): m.group(3) for m in STRING.finditer(src) if 'translatable="false"' not in m.group(2)}

def entry(name, value):
    return f'    <!-- TODO translate -->\n    <string name="{name}">{value}</string>'

def sync(out, base):
    text = open(out, encoding="utf-8").read()
    have = {m.group(1) for m in STRING.finditer(text)}
    new = [k for k in base if k not in have]
    if not new:
        return
    text = text.replace("</resources>", "\n".join(entry(k, base[k]) for k in new) + "\n</resources>", 1)
    with open(out, "w", encoding="utf-8") as f:
        f.write(text)
    print(f"Added {len(new)} new string(s) from English: {', '.join(new)}")

def create(out, base):
    lines = [entry(k, v) for k, v in base.items()]
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + "\n".join(lines) + "\n</resources>\n")
    print(f"Created {os.path.relpath(out)} with {len(lines)} strings to translate.")

def apply(text, name, xml):
    marked = re.compile(r"[ \t]*<!-- TODO translate -->[ \t]*\n([ \t]*)<string name=\"" + re.escape(name) + r"\"([^>]*)>.*?</string>", re.S)
    new, n = marked.subn(lambda m: f'{m.group(1)}<string name="{name}"{m.group(2)}>{xml}</string>', text, count=1)
    if n:
        return new
    plain = re.compile(r"(<string name=\"" + re.escape(name) + r"\"[^>]*>).*?</string>", re.S)
    return plain.sub(lambda m: f"{m.group(1)}{xml}</string>", text, count=1)

def ask(code, base_raw):
    while True:
        try:
            ans = input(f"  {code}> ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            return None
        if ans == "quit_translate":
            return None
        if not ans:
            return ""
        if sorted(PH.findall(to_xml(ans))) != sorted(PH.findall(base_raw)):
            print(f"  ! Keep the placeholders exactly as in English: {', '.join(PH.findall(base_raw)) or '(none)'}")
            continue
        return ans

def translate(out, code, names, base, current=None):
    edit = current is not None
    text = open(out, encoding="utf-8").read()
    total = len(names)
    cols = shutil.get_terminal_size((100, 20)).columns
    box = min(max(len("Translated word"), max(len(n) for n in names)), (cols - 6) // 3)
    def row(a, b, c):
        cells = [wrap(x, box) for x in (a, b, c)]
        lines = []
        for i in range(max(len(cell) for cell in cells)):
            p = [cell[i] if i < len(cell) else "" for cell in cells]
            lines.append(f"{pad(p[0], box)} | {pad(p[1], box)} | {p[2]}".rstrip())
        return "\n".join(lines)
    head = row("String name", "English name", "Translated word")
    rule = "-" * (box + 1) + "+" + "-" * (box + 2) + "+" + "-" * (box + 1)
    print(f"\nTotal strings to {'edit' if edit else 'translate'}: {total}\n")
    print("Type the new translation & Enter. Enter alone = keep the current one\n" if edit else
          "Type the translation & Enter. Enter alone = skip (kept as 'TODO translate')\n")
    print(HINT)
    done, skipped, rows = 0, 0, []
    for i, name in enumerate(names, 1):
        english = readable(base[name])
        print(f"\n[{i}/{total}] {name}\n  English: {english}")
        if edit:
            print(f"  Current: {readable(current[name])}")
        ans = ask(code, base[name])
        if ans is None:
            break
        if ans:
            text = apply(text, name, to_xml(ans))
            with open(out, "w", encoding="utf-8") as f:
                f.write(text)
            done += 1
        else:
            skipped += 1
        rows.append(row(name, english, ans or ("(unchanged)" if edit else SKIPPED)))
        print(f"\n{head}\n{rule}\n{rows[-1]}")
        print(f"\nLeft to {'edit' if edit else 'translate'}: {total - i}   ({'changed' if edit else 'translated'} {done}, {'kept' if edit else 'skipped'} {skipped})")
        print(HINT)
    print(f"\n{'=' * 8} Summary {'=' * 8}")
    if rows:
        print(f"{head}\n{rule}")
        print(("\n" + rule + "\n" if any("\n" in r for r in rows) else "\n").join(rows))
    left = total - done
    if edit:
        print(f"\nChanged: {done}   Unchanged: {left}   File: {os.path.relpath(out)}")
        return
    print(f"\nTranslated: {done}   Not translated: {left}   File: {os.path.relpath(out)}")
    if left:
        print(f"Run `python3 scripts/add_language.py {code}` again to continue with the remaining {left}.")
    else:
        print("All done. Check with: python3 scripts/check_strings.py")

def main():
    flags = [a for a in sys.argv[1:] if a.startswith("--")]
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    edit = "--edit" in flags
    if not args or set(flags) - {"--edit"} or (not edit and len(args) != 1):
        sys.exit(USAGE)
    code, keys = args[0], args[1:]
    folder = folder_for(code)
    out = os.path.join(RES, folder, "strings.xml")
    base = read_base()
    if edit:
        if not os.path.exists(out):
            sys.exit(f"{folder}/strings.xml does not exist - create it first: python3 scripts/add_language.py {code}")
        current = {m.group(1): m.group(3) for m in STRING.finditer(open(out, encoding="utf-8").read())}
        names = keys or [k for k in base if k in current]
        unknown = [k for k in names if k not in base or k not in current]
        if unknown:
            sys.exit(f"Unknown string name(s) in {folder}: {', '.join(unknown)}")
        translate(out, code, names, base, current)
        return
    if not os.path.exists(out):
        create(out, base)
    else:
        print(f"{folder}/strings.xml already exists - continuing with the strings still marked 'TODO translate'.")
        sync(out, base)
    names = [m.group(1) for m in TODO.finditer(open(out, encoding="utf-8").read()) if m.group(1) in base]
    if not names:
        sys.exit(f"Nothing left to translate in {folder}/strings.xml.")
    translate(out, code, names, base)

if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    main()
