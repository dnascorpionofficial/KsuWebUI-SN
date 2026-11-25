## クレジット

このプロジェクトは、**KsuWebUI Standalone** のベースコードを開発した 5ec1cff のオリジナルの成果を基にしており、KOWX712 によるさらなる改善と修正が加えられています。

このプロジェクトの基盤となった両貢献者の仕事に特に感謝します。

- [5ec1cff — KsuWebUIStandalone](https://github.com/5ec1cff/KsuWebUIStandalone)
- [KOWX712 — KsuWebUIStandalone](https://github.com/KOWX712/KsuWebUIStandalone)

> このプロジェクトは独立して管理されており、元のリポジトリのフォークではなくなりました。

# KsuWebUI-SN

[English](../README.md) | [简体中文](README_cn.md) | [Français](README_fr.md) | 日本語 | [العربية](README_ar.md) | [Polski](README_pl.md) | [Português (Brasil)](README_pt-BR.md)

[KernelSU WebUI](https://kernelsu.org/guide/module-webui.html) アプリのスタンドアロン実装

Magisk、KernelSU、APatch に対応しています

## 翻訳

UI のテキストはすべて `app/src/main/res/values*/strings.xml` にあります。英語（`values/`）が基準言語で、
アラビア語（`values-ar`）、フランス語（`values-fr`）、ポーランド語（`values-pl`）、日本語（`values-ja`）、ポルトガル語（ブラジル）（`values-pt-rBR`）、簡体字中国語（`values-zh-rCN`）は翻訳です。

### 言語を追加する

```
python3 scripts/language_extension.py
```

まだ追加されていない言語を、アルファベット順の番号付きリスト（名前とコード）で表示します。番号を入力して **Enter** を押すと、英語の文字列をすべて `<!-- TODO translate -->` 付きでコピーした `values-xx/strings.xml` を作成し、1 つずつ翻訳を尋ねます。
リストにない言語は **`addnew`** と入力し、言語名とコード（例：`Esperanto`、`eo`）を送ります。追加済みの言語はこのリストに表示されなくなります。マーカーが残っている言語は **Unfinished** の下に表示されるので、そこから選んで続けられます（その後に英語へ追加された文字列は先に追加されます）。
**Enter** でスキップ（マーカーは残ります）、**`quit_translate`** で保存して終了、**`\n`** は改行です。プレースホルダー（`%s`、`%1$d`）は同一に保ってください。
異なる入力は拒否されます。入力はすぐに保存されます。ビルド時に新しい言語が自動的に認識されます。

### 言語を編集する

```
python3 scripts/language_extension.py --edit
```

追加済みの言語をアルファベット順（名前とコード）で表示します。番号を入力し、続けて文字列名を 1 つまたは複数（例：`update reboot_now`）入力するか、**Enter** だけで全文字列を順に確認します。
各文字列の英語のテキストと現在の訳を表示し、新しい訳の入力を求めます。**Enter** だけで現在の訳を保持します。

### チェックする

```
python3 scripts/check_strings.py
```

不足している文字列、残っている `TODO translate` マーカー、プレースホルダーの不一致、未定義の文字列を
一覧表示します。CI は `--warn-only` で実行するため、問題は警告として表示され、ビルドが失敗することはありません。
