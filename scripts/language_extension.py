#!/usr/bin/env python3
"""Add or edit a UI language (app/src/main/res/values-*/strings.xml)

  python3 scripts/language_extension.py          >> numbered list of languages that are not added yet
                                                    type a number + Enter to create it and translate
                                                    type addnew to add a language that is not in the list
                                                    languages with strings still marked TODO are listed
                                                    under 'Unfinished' so you can continue them

  python3 scripts/language_extension.py --edit   >> numbered list of the languages already added,
                                                    pick one, then edit all strings or only some
"""
import os, re, shutil, sys, unicodedata

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res")
USAGE = ("Usage: python3 scripts/language_extension.py          (add a language)\n"
         "       python3 scripts/language_extension.py --edit   (edit a language)")
PH = re.compile(r"%(?:\d+\$)?[sdfx]")
STRING = re.compile(r"<string\s+name=\"([^\"]+)\"([^>]*)>(.*?)</string>", re.S)
TODO = re.compile(r"[ \t]*<!-- TODO translate -->[ \t]*\n[ \t]*<string name=\"([^\"]+)\"([^>]*)>(.*?)</string>", re.S)
SKIPPED = "Not Translated"
HINT = ("\nType quit_translate & press Enter to save and quit.\n"
        "Type \\n for a line break. Keep %s / %1$d placeholders texts")

# code -> name. Android folder codes: Hebrew is "iw" and Indonesian is "in" (what Android Studio uses).
LANGS = {
    "af": "Afrikaans", "sq": "Albanian", "am": "Amharic", "ar": "Arabic", "hy": "Armenian",
    "az": "Azerbaijani", "eu": "Basque", "be": "Belarusian", "bn": "Bengali", "bs": "Bosnian",
    "bg": "Bulgarian", "my": "Burmese", "ca": "Catalan", "zh-CN": "Chinese (Simplified)",
    "zh-TW": "Chinese (Traditional)", "hr": "Croatian", "cs": "Czech", "da": "Danish",
    "nl": "Dutch", "et": "Estonian", "fil": "Filipino", "fi": "Finnish", "fr": "French",
    "gl": "Galician", "ka": "Georgian", "de": "German", "el": "Greek", "gu": "Gujarati",
    "iw": "Hebrew", "hi": "Hindi", "hu": "Hungarian", "is": "Icelandic", "in": "Indonesian",
    "it": "Italian", "ja": "Japanese", "kn": "Kannada", "kk": "Kazakh", "km": "Khmer",
    "ko": "Korean", "ky": "Kyrgyz", "lo": "Lao", "lv": "Latvian", "lt": "Lithuanian",
    "mk": "Macedonian", "ms": "Malay", "ml": "Malayalam", "mr": "Marathi", "mn": "Mongolian",
    "ne": "Nepali", "nb": "Norwegian (Bokm\u00e5l)", "fa": "Persian", "pl": "Polish",
    "pt": "Portuguese", "pt-BR": "Portuguese (Brazil)", "pa": "Punjabi", "ro": "Romanian",
    "ru": "Russian", "sr": "Serbian", "si": "Sinhala", "sk": "Slovak", "sl": "Slovenian",
    "es": "Spanish", "sw": "Swahili", "sv": "Swedish", "ta": "Tamil", "te": "Telugu",
    "th": "Thai", "tr": "Turkish", "uk": "Ukrainian", "ur": "Urdu", "uz": "Uzbek",
    "vi": "Vietnamese", "cy": "Welsh", "zu": "Zulu",
}

def folder_for(code):
    """'pt-BR' -> 'values-pt-rBR'; None if the code is not valid."""
    m = re.fullmatch(r"([a-zA-Z]{2,3})(?:[-_]([a-zA-Z]{2}|\d{3}))?", code)
    if not m:
        return None
    lang, region = m.group(1).lower(), m.group(2)
    if region and region.isdigit():
        return f"values-b+{lang}+{region}"
    return f"values-{lang}" + (f"-r{region.upper()}" if region else "")

def code_for(folder):
    """'values-pt-rBR' -> 'pt-BR'; None for folders that are not a language."""
    m = re.fullmatch(r"values-([a-z]{2,3})(?:-r([A-Z]{2}))?", folder)
    if m:
        return m.group(1) + (f"-{m.group(2)}" if m.group(2) else "")
    m = re.fullmatch(r"values-b\+([a-z]{2,3})\+(\d{3})", folder)
    return f"{m.group(1)}-{m.group(2)}" if m else None

def lang_name(code, path=None):
    if code in LANGS:
        return LANGS[code]
    if path and os.path.exists(path):
        m = re.search(r"<!--\s*Language:\s*(.+?)\s*-->", open(path, encoding="utf-8").read())
        if m:
            return m.group(1)
    return code

def installed():
    """{code: strings.xml path} for every translation that exists (English excluded)."""
    found = {}
    for folder in os.listdir(RES):
        path = os.path.join(RES, folder, "strings.xml")
        code = code_for(folder)
        if code and os.path.isfile(path):
            found[code] = path
    return found

def todo_left(path, base):
    return sum(1 for m in TODO.finditer(open(path, encoding="utf-8").read()) if m.group(1) in base)

def prompt(text):
    try:
        return input(text).strip()
    except (EOFError, KeyboardInterrupt):
        print()
        sys.exit(0)

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

def create(out, base, name=None):
    lines = [entry(k, v) for k, v in base.items()]
    os.makedirs(os.path.dirname(out), exist_ok=True)
    label = f"<!-- Language: {name.replace('--', '-')} -->\n" if name else ""
    with open(out, "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n' + label + '<resources>\n' + "\n".join(lines) + "\n</resources>\n")
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
        print(f"Run `python3 scripts/language_extension.py` again and pick {lang_name(code, out)} under 'Unfinished' to continue with the remaining {left}.")
    else:
        print("All done. Check with: python3 scripts/check_strings.py")

def show(rows):
    """rows: list of ('header', text) or ('item', label). Prints items numbered 1..n."""
    n = 0
    for kind, text in rows:
        if kind == "header":
            print(f"\n{text}")
        else:
            n += 1
            print(f"{n:>3}. {text}")
    return n

def run_add(code, name):
    folder = folder_for(code)
    out = os.path.join(RES, folder, "strings.xml")
    base = read_base()
    if not os.path.exists(out):
        create(out, base, None if code in LANGS else name)
    else:
        print(f"{folder}/strings.xml already exists - continuing with the strings still marked 'TODO translate'.")
        sync(out, base)
    names = [m.group(1) for m in TODO.finditer(open(out, encoding="utf-8").read()) if m.group(1) in base]
    if not names:
        sys.exit(f"Nothing left to translate in {folder}/strings.xml.")
    translate(out, code, names, base)

def add_new(have):
    print("\nNew language (q = cancel)")
    name = prompt("  Language name (e.g. Esperanto): ")
    if not name or name.lower() == "q":
        return None
    while True:
        code = prompt("  Language code (e.g. eo, pt-BR, zh-TW): ")
        if not code or code.lower() == "q":
            return None
        if folder_for(code) is None:
            print("  ! Invalid code. Use 2-3 letters, optionally with a region: de, fr, pt-BR, zh-TW.")
            continue
        norm = code_for(folder_for(code))
        if norm in have:
            print(f"  ! {lang_name(norm, have[norm])} ({norm}) already exists - pick it from the list, or use --edit.")
            continue
        return norm, name

def add_mode():
    have, base = installed(), read_base()
    unfinished = sorted(((lang_name(c, p), c, todo_left(p, base)) for c, p in have.items() if todo_left(p, base)),
                        key=lambda t: t[0].lower())
    available = sorted(((n, c) for c, n in LANGS.items() if c not in have), key=lambda t: t[0].lower())
    rows, choices = [], []
    if unfinished:
        rows.append(("header", "Unfinished (continue translating):"))
        for n, c, left in unfinished:
            rows.append(("item", f"{n} ({c}) - {left} left"))
            choices.append((c, n))
    rows.append(("header", "Add a language:"))
    for n, c in available:
        rows.append(("item", f"{n} ({c})"))
        choices.append((c, n))
    total = show(rows)
    print("\nType the number & Enter to translate it.")
    print("Type addnew & Enter for a language that is not in the list.  q = quit.")
    while True:
        ans = prompt("\n> ").lower()
        if ans in ("q", "quit", "exit"):
            return
        if ans == "addnew":
            picked = add_new(have)
            if picked:
                run_add(*picked)
                return
            continue
        if ans.isdigit() and 1 <= int(ans) <= total:
            run_add(*choices[int(ans) - 1])
            return
        print(f"  ! Type a number from 1 to {total}, addnew or q.")

def edit_mode():
    have, base = installed(), read_base()
    if not have:
        sys.exit("No translations to edit yet. Run: python3 scripts/language_extension.py")
    langs = sorted(((lang_name(c, p), c) for c, p in have.items()), key=lambda t: t[0].lower())
    rows = [("header", "Edit a language:")]
    for n, c in langs:
        left = todo_left(have[c], base)
        rows.append(("item", f"{n} ({c})" + (f" - {left} not translated" if left else "")))
    total = show(rows)
    print("\nType the number & Enter.  q = quit.")
    while True:
        ans = prompt("\n> ").lower()
        if ans in ("q", "quit", "exit"):
            return
        if ans.isdigit() and 1 <= int(ans) <= total:
            break
        print(f"  ! Type a number from 1 to {total} or q.")
    name, code = langs[int(ans) - 1]
    out = have[code]
    current = {m.group(1): m.group(3) for m in STRING.finditer(open(out, encoding="utf-8").read())}
    known = [k for k in base if k in current]
    while True:
        raw = prompt(f"\n{name} ({code}): string names to edit, separated by spaces (Enter = every string): ")
        names = raw.split() or known
        unknown = [k for k in names if k not in known]
        if not unknown:
            break
        print(f"  ! Unknown string name(s): {', '.join(unknown)}")
    translate(out, code, names, base, current)

def main():
    args = sys.argv[1:]
    if not args:
        add_mode()
    elif args == ["--edit"]:
        edit_mode()
    else:
        sys.exit(USAGE)

if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    main()
