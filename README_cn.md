# KsuWebUI-SN

[English](README.md) | 简体中文 | [Français](README_fr.md)

[KernelSU WebUI](https://kernelsu.org/guide/module-webui.html) 应用的独立实现版本

适用于 Magisk、KernelSU 和 APatch

## 翻译

所有界面文本都位于 `app/src/main/res/values*/strings.xml`。英语（`values/`）是源语言；
波兰语（`values-pl`）、日语（`values-ja`）和简体中文（`values-zh-rCN`）为翻译版本。

### 添加语言

```
python3 scripts/add_language.py de        # 也可以是 fr、es、pt-BR、zh-TW 等
```

该命令会创建 `values-de/strings.xml`，其中包含从英语复制的所有字符串，并标有
`<!-- TODO translate -->`。翻译文本后删除该标记即可。构建时会自动识别新语言
（它也会出现在 Android 的应用语言设置中）。

### 添加或修改字符串

1. 将其添加到 `values/strings.xml`，并在代码中使用 `R.string.name`，在布局中使用 `@string/name`。
2. 在每个 `values-*/strings.xml` 中添加相同的键。请保持占位符（`%s`、`%1$d`）完全一致。

### 检查

```
python3 scripts/check_strings.py
```

该命令会列出缺失的字符串、遗留的 `TODO translate` 标记、占位符不一致以及未定义的字符串。
CI 使用 `--warn-only` 运行它，因此问题只会显示为警告，不会导致构建失败。
