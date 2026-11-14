## Credits

This project is based on the original work by 5ec1cff , who developed the base code for **KsuWebUI Standalone**, with further improvements and fixes by KOWX712.

Special thanks to both contributors for their work, which served as the foundation for this project.

- [5ec1cff — KsuWebUIStandalone](https://github.com/5ec1cff/KsuWebUIStandalone)
- [KOWX712 — KsuWebUIStandalone](https://github.com/KOWX712/KsuWebUIStandalone)

> This project is maintained independently and is no longer a fork of the original repository.

# KsuWebUI-SN

English | [简体中文](docs/README_cn.md) | [Français](docs/README_fr.md) | [日本語](docs/README_ja.md) | [العربية](docs/README_ar.md) | [Polski](docs/README_pl.md)

Standalone implementation of the [KernelSU WebUI](https://kernelsu.org/guide/module-webui.html) application

Suitable for Magisk, KernelSU, and APatch

## Translating

All UI text lives in `app/src/main/res/values*/strings.xml`. English (`values/`) is the source;
Arabic (`values-ar`), French (`values-fr`), Polish (`values-pl`), Japanese (`values-ja`) and Simplified Chinese (`values-zh-rCN`) are translations.

### Add a language

```
python3 scripts/add_language.py de        # or es, pt-BR, zh-TW, ...
```

This creates `values-de/strings.xml` with every string copied from English and marked
`<!-- TODO translate -->`, then walks you through them one by one: it shows the English text,
you type the translation and press Enter (Enter alone skips it, `quit_translate` saves and quits). Each
answer is written straight to the file and shown as a `String name | English name | Translated word`
row. Run the same command again later to continue with the strings still marked; it also adds any
strings that were added to English since. Use
`--create-only` to just create the file and edit it by hand. The build picks the new
language up by itself (it also appears in Android's per-app language settings).

### Add or change a string

1. Add it to `values/strings.xml` and use `R.string.name` (code) or `@string/name` (layouts).
2. Add the same key to every `values-*/strings.xml` (or run `python3 scripts/add_language.py <code>` for each
   language, which copies it in marked `TODO translate`). Keep placeholders (`%s`, `%1$d`) identical.

### Check

```
python3 scripts/check_strings.py
```

Lists missing strings, leftover `TODO translate` markers, placeholder mismatches and undefined
strings. CI runs it with `--warn-only`, so problems show as warnings and never fail the build.
