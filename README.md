## Credits

This project is based on the original work by 5ec1cff , who developed the base code for **KsuWebUI Standalone**, with further improvements and fixes by KOWX712.

Special thanks to both contributors for their work, which served as the foundation for this project.

- [5ec1cff — KsuWebUIStandalone](https://github.com/5ec1cff/KsuWebUIStandalone)
- [KOWX712 — KsuWebUIStandalone](https://github.com/KOWX712/KsuWebUIStandalone)
This project is maintained independently and is no longer a fork of the original repository.

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
