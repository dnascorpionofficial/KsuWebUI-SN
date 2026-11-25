## Credits

This project is based on the original work by 5ec1cff , who developed the base code for **KsuWebUI Standalone**, with further improvements and fixes by KOWX712.

Special thanks to both contributors for their work, which served as the foundation for this project.

- [5ec1cff — KsuWebUIStandalone](https://github.com/5ec1cff/KsuWebUIStandalone)
- [KOWX712 — KsuWebUIStandalone](https://github.com/KOWX712/KsuWebUIStandalone)

> This project is maintained independently and is no longer a fork of the original repository.

# KsuWebUI-SN

English | [简体中文](docs/README_cn.md) | [Français](docs/README_fr.md) | [日本語](docs/README_ja.md) | [العربية](docs/README_ar.md) | [Polski](docs/README_pl.md) | [Português (Brasil)](docs/README_pt-BR.md)

Standalone implementation of the [KernelSU WebUI](https://kernelsu.org/guide/module-webui.html) application

Suitable for Magisk, KernelSU, and APatch

## Translating

All UI text lives in `app/src/main/res/values*/strings.xml`. English (`values/`) is the source;
Arabic (`values-ar`), French (`values-fr`), Polish (`values-pl`), Japanese (`values-ja`), Brazilian Portuguese (`values-pt-rBR`) and Simplified Chinese (`values-zh-rCN`) are translations.

### Add a language

```
python3 scripts/language_extension.py
```

Shows the languages that are not added yet as a numbered list in alphabetical order (name and code). Type a number and press **Enter** to create `values-xx/strings.xml` with every string copied from English marked `<!-- TODO translate -->`, then translate them one by one.
If your language is not in the list, type **`addnew`**, then send the language name and code (e.g. `Esperanto`, `eo`). A language that has been added no longer shows in this list; one that still has strings marked appears under **Unfinished**, pick it there to continue (strings added to English since are added first).
**Enter** skips a string (it stays marked), **`quit_translate`** saves and quits, **`\n`** is a line break. Keep placeholders (`%s`, `%1$d`)
identical; an answer with different ones is rejected. Every answer is saved at once. The build picks the new language up by itself.

### Edit a language

```
python3 scripts/language_extension.py --edit
```

Lists the languages already added (alphabetical order, name and code). Type a number, then give one or several string names (e.g. `update reboot_now`) or press **Enter** to go through every string.
Shows the English and the current text of each string and asks for a new one; **Enter** keeps the current text.

### Check

```
python3 scripts/check_strings.py
```

Lists missing strings, leftover `TODO translate` markers, placeholder mismatches and undefined
strings. CI runs it with `--warn-only`, so problems show as warnings and never fail the build.
