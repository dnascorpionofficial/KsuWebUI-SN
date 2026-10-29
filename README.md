"original credits goes to [KOWX712](https://github.com/KOWX712/KsuWebUIStandalone) and [5ec1cff](https://github.com/5ec1cff/KsuWebUIStandalone) for base code of Ksuwebui Standalone"

# KsuWebUI-SN

English | [简体中文](README_cn.md) | [Français](README_fr.md) | [日本語](README_ja.md)

Standalone implementation of the [KernelSU WebUI](https://kernelsu.org/guide/module-webui.html) application

Suitable for Magisk, KernelSU, and APatch

## Translating

All UI text lives in `app/src/main/res/values*/strings.xml`. English (`values/`) is the source;
Polish (`values-pl`), Japanese (`values-ja`) and Simplified Chinese (`values-zh-rCN`) are translations.

### Add a language

```
python3 scripts/add_language.py de        # or fr, es, pt-BR, zh-TW, ...
```

This creates `values-de/strings.xml` with every string copied from English and marked
`<!-- TODO translate -->`. Translate the text, delete the marker, done. The build picks the new
language up by itself (it also appears in Android's per-app language settings).

### Add or change a string

1. Add it to `values/strings.xml` and use `R.string.name` (code) or `@string/name` (layouts).
2. Add the same key to every `values-*/strings.xml`. Keep placeholders (`%s`, `%1$d`) identical.

### Check

```
python3 scripts/check_strings.py
```

Lists missing strings, leftover `TODO translate` markers, placeholder mismatches and undefined
strings. CI runs it with `--warn-only`, so problems show as warnings and never fail the build.
