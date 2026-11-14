## クレジット

このプロジェクトは、**KsuWebUI Standalone** のベースコードを開発した 5ec1cff のオリジナルの成果を基にしており、KOWX712 によるさらなる改善と修正が加えられています。

このプロジェクトの基盤となった両貢献者の仕事に特に感謝します。

- [5ec1cff — KsuWebUIStandalone](https://github.com/5ec1cff/KsuWebUIStandalone)
- [KOWX712 — KsuWebUIStandalone](https://github.com/KOWX712/KsuWebUIStandalone)

> このプロジェクトは独立して管理されており、元のリポジトリのフォークではなくなりました。

# KsuWebUI-SN

[English](../README.md) | [简体中文](README_cn.md) | [Français](README_fr.md) | 日本語 | [العربية](README_ar.md) | [Polski](README_pl.md)

[KernelSU WebUI](https://kernelsu.org/guide/module-webui.html) アプリのスタンドアロン実装

Magisk、KernelSU、APatch に対応しています

## 翻訳

UI のテキストはすべて `app/src/main/res/values*/strings.xml` にあります。英語（`values/`）が基準言語で、
アラビア語（`values-ar`）、フランス語（`values-fr`）、ポーランド語（`values-pl`）、日本語（`values-ja`）、簡体字中国語（`values-zh-rCN`）は翻訳です。

### 言語を追加する

```
python3 scripts/add_language.py de        # es、pt-BR、zh-TW なども可
```

このコマンドは `values-de/strings.xml` を作成し、英語の文字列をすべてコピーして
`<!-- TODO translate -->` を付けたあと、1 つずつ対話形式で翻訳を進めます。英語のテキストが表示されるので、
翻訳を入力して Enter を押してください（Enter だけでスキップ、`quit_translate` で保存して終了）。入力した内容は
すぐにファイルへ書き込まれ、`String name | English name | Translated word` の行として表示されます。後で同じ
コマンドをもう一度実行すると、マーカーが残っている文字列から続けられます。その後に英語へ追加された文字列も自動で追加されます。`--create-only` を付けるとファイルの
作成だけを行い、手作業で編集できます。ビルド時に新しい言語が自動的に認識されます（Android のアプリ別言語設定にも表示されます）。

### 文字列を追加・変更する

1. `values/strings.xml` に追加し、コードでは `R.string.name`、レイアウトでは `@string/name` を使います。
2. すべての `values-*/strings.xml` に同じキーを追加します（または各言語に対して `python3 scripts/add_language.py <code>` を実行すると、`TODO translate` マーカー付きでコピーされます）。プレースホルダー（`%s`、`%1$d`）は同一に保ってください。

### チェックする

```
python3 scripts/check_strings.py
```

不足している文字列、残っている `TODO translate` マーカー、プレースホルダーの不一致、未定義の文字列を
一覧表示します。CI は `--warn-only` で実行するため、問題は警告として表示され、ビルドが失敗することはありません。
