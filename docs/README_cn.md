## 致谢

本项目基于 5ec1cff 的原始工作，他开发了 **KsuWebUI Standalone** 的基础代码，KOWX712 随后进行了进一步的改进和修复。

特别感谢两位贡献者的工作，它们为本项目奠定了基础。

- [5ec1cff — KsuWebUIStandalone](https://github.com/5ec1cff/KsuWebUIStandalone)
- [KOWX712 — KsuWebUIStandalone](https://github.com/KOWX712/KsuWebUIStandalone)

> 本项目独立维护，不再是原始仓库的分叉（fork）。

# KsuWebUI-SN

[English](../README.md) | 简体中文 | [Français](README_fr.md) | [日本語](README_ja.md) | [العربية](README_ar.md) | [Polski](README_pl.md) | [Português (Brasil)](README_pt-BR.md)

[KernelSU WebUI](https://kernelsu.org/guide/module-webui.html) 应用的独立实现版本

适用于 Magisk、KernelSU 和 APatch

## 翻译

所有界面文本都位于 `app/src/main/res/values*/strings.xml`。英语（`values/`）是源语言；
阿拉伯语（`values-ar`）、法语（`values-fr`）、波兰语（`values-pl`）、日语（`values-ja`）、巴西葡萄牙语（`values-pt-rBR`）和简体中文（`values-zh-rCN`）为翻译版本。

### 添加语言

```
python3 scripts/language_extension.py
```

以按字母顺序编号的列表显示尚未添加的语言（名称和代码）。输入编号并按**回车**，即可创建 `values-xx/strings.xml`，其中包含从英语复制的所有字符串并标有 `<!-- TODO translate -->`，然后逐条翻译。
如果列表中没有你的语言，输入 **`addnew`**，再发送语言名称和代码（例如 `Esperanto`、`eo`）。已添加的语言不会再出现在此列表中；仍有带标记字符串的语言会显示在 **Unfinished** 下，在那里选择它即可继续（此后新增到英语中的字符串会先被补上）。
**回车**跳过（仍保留标记），**`quit_translate`** 保存并退出，**`\n`** 表示换行。请保持占位符（`%s`、`%1$d`）一致，不一致的输入会被拒绝。
每个答案会立即保存。构建时会自动识别新语言。

### 编辑语言

```
python3 scripts/language_extension.py --edit
```

按字母顺序列出已添加的语言（名称和代码）。输入编号，然后输入一个或多个字符串名称（例如 `update reboot_now`），或直接按**回车**遍历所有字符串。
显示每条字符串的英文原文和当前译文，并请你输入新译文；直接按**回车**则保留当前译文。

### 检查

```
python3 scripts/check_strings.py
```

该命令会列出缺失的字符串、遗留的 `TODO translate` 标记、占位符不一致以及未定义的字符串。
CI 使用 `--warn-only` 运行它，因此问题只会显示为警告，不会导致构建失败。
