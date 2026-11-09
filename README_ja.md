## クレジット

このプロジェクトは、**KsuWebUI Standalone** のベースコードを開発した 5ec1cff のオリジナルの成果を基にしており、KOWX712 によるさらなる改善と修正が加えられています。

このプロジェクトの基盤となった両貢献者の仕事に特に感謝します。

- [5ec1cff — KsuWebUIStandalone](https://github.com/5ec1cff/KsuWebUIStandalone)
- [KOWX712 — KsuWebUIStandalone](https://github.com/KOWX712/KsuWebUIStandalone)

> このプロジェクトは独立して管理されており、元のリポジトリのフォークではなくなりました。

# KsuWebUI-SN

[English](README.md) | [简体中文](README_cn.md) | [Français](README_fr.md) | 日本語 | [العربية](README_ar.md)

[KernelSU WebUI](https://kernelsu.org/guide/module-webui.html) アプリのスタンドアロン実装

Magisk、KernelSU、APatch に対応しています

## 翻訳

UI のテキストはすべて `app/src/main/res/values*/strings.xml` にあります。英語（`values/`）が基準言語で、
ポーランド語（`values-pl`）、日本語（`values-ja`）、簡体字中国語（`values-zh-rCN`）は翻訳です。

### 言語を追加する

```
python3 scripts/add_language.py de        # fr、es、pt-BR、zh-TW なども可
```

このコマンドは `values-de/strings.xml` を作成し、英語の文字列をすべてコピーして
`<!-- TODO translate -->` を付けます。テキストを翻訳し、マーカーを削除すれば完了です。ビルド時に新しい
言語が自動的に認識されます（Android のアプリ別言語設定にも表示されます）。

### 文字列を追加・変更する

1. `values/strings.xml` に追加し、コードでは `R.string.name`、レイアウトでは `@string/name` を使います。
2. すべての `values-*/strings.xml` に同じキーを追加します。プレースホルダー（`%s`、`%1$d`）は同一に保ってください。

### チェックする

```
python3 scripts/check_strings.py
```

不足している文字列、残っている `TODO translate` マーカー、プレースホルダーの不一致、未定義の文字列を
一覧表示します。CI は `--warn-only` で実行するため、問題は警告として表示され、ビルドが失敗することはありません。
