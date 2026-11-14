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
python3 scripts/add_language.py de        # ドイツ語
python3 scripts/add_language.py pt-BR     # ポルトガル語（ブラジル）
python3 scripts/add_language.py zh-TW     # 繁体字中国語
```

`values-de/strings.xml` を作成し、英語の文字列をすべて `<!-- TODO translate -->` 付きでコピーしてから、1 つずつ翻訳を尋ねます。
**Enter** でスキップ（マーカーは残ります）、**`quit_translate`** で保存して終了、**`\n`** は改行です。プレースホルダー（`%s`、`%1$d`）は同一に保ってください。
異なる入力は拒否されます。入力はすぐに保存されます。同じコマンドをもう一度実行すると、マーカーが残っている文字列から続けられます
（その後に英語へ追加された文字列は先に追加されます）。ビルド時に新しい言語が自動的に認識されます。

### 言語を編集する

```
python3 scripts/add_language.py de --edit                     # 各文字列を順に表示して編集
python3 scripts/add_language.py de --edit reboot_now          # reboot_now の文字列のみ
python3 scripts/add_language.py de --edit update reboot_now   # update と reboot_now の文字列（さらに追加可能）
```

各文字列の英語のテキストと現在の訳を表示し、新しい訳の入力を求めます。**Enter** だけで現在の訳を保持します。
名前は 1 つでも複数でも指定でき、省略するとすべての文字列を順に確認します。

### チェックする

```
python3 scripts/check_strings.py
```

不足している文字列、残っている `TODO translate` マーカー、プレースホルダーの不一致、未定義の文字列を
一覧表示します。CI は `--warn-only` で実行するため、問題は警告として表示され、ビルドが失敗することはありません。
