## 致谢

本项目基于 5ec1cff 的原始工作，他开发了 **KsuWebUI Standalone** 的基础代码，KOWX712 随后进行了进一步的改进和修复。

特别感谢两位贡献者的工作，它们为本项目奠定了基础。

- [5ec1cff — KsuWebUIStandalone](https://github.com/5ec1cff/KsuWebUIStandalone)
- [KOWX712 — KsuWebUIStandalone](https://github.com/KOWX712/KsuWebUIStandalone)

> 本项目独立维护，不再是原始仓库的分叉（fork）。

# KsuWebUI-SN

[English](../README.md) | 简体中文 | [Français](README_fr.md) | [日本語](README_ja.md) | [العربية](README_ar.md) | [Polski](README_pl.md)

[KernelSU WebUI](https://kernelsu.org/guide/module-webui.html) 应用的独立实现版本

适用于 Magisk、KernelSU 和 APatch

## 翻译

所有界面文本都位于 `app/src/main/res/values*/strings.xml`。英语（`values/`）是源语言；
阿拉伯语（`values-ar`）、法语（`values-fr`）、波兰语（`values-pl`）、日语（`values-ja`）和简体中文（`values-zh-rCN`）为翻译版本。

### 添加语言

```
python3 scripts/add_language.py de        # 也可以是 es、pt-BR、zh-TW 等
```

该命令会创建 `values-de/strings.xml`，其中包含从英语复制的所有字符串，并标有
`<!-- TODO translate -->`，然后逐条引导你翻译：显示英文原文，输入译文后按回车（直接按回车跳过，
输入 `quit_translate` 保存并退出）。每个答案会立即写入文件，并以 `String name | English name | Translated word`
的行显示。之后再次运行同一命令，即可继续翻译仍带标记的字符串。使用 `--create-only` 则只创建文件，手动编辑。
构建时会自动识别新语言（它也会出现在 Android 的应用语言设置中）。

### 添加或修改字符串

1. 将其添加到 `values/strings.xml`，并在代码中使用 `R.string.name`，在布局中使用 `@string/name`。
2. 在每个 `values-*/strings.xml` 中添加相同的键。请保持占位符（`%s`、`%1$d`）完全一致。

### 检查

```
python3 scripts/check_strings.py
```

该命令会列出缺失的字符串、遗留的 `TODO translate` 标记、占位符不一致以及未定义的字符串。
CI 使用 `--warn-only` 运行它，因此问题只会显示为警告，不会导致构建失败。
