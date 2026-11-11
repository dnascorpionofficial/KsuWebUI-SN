## Podziękowania

Ten projekt opiera się na oryginalnej pracy 5ec1cff, który stworzył bazowy kod **KsuWebUI Standalone**, z dalszymi ulepszeniami i poprawkami autorstwa KOWX712.

Szczególne podziękowania dla obu autorów za ich pracę, która stała się fundamentem tego projektu.

- [5ec1cff — KsuWebUIStandalone](https://github.com/5ec1cff/KsuWebUIStandalone)
- [KOWX712 — KsuWebUIStandalone](https://github.com/KOWX712/KsuWebUIStandalone)

> Ten projekt jest rozwijany niezależnie i nie jest już forkiem oryginalnego repozytorium.

# KsuWebUI-SN

[English](../README.md) | [简体中文](README_cn.md) | [Français](README_fr.md) | [日本語](README_ja.md) | [العربية](README_ar.md) | Polski

Samodzielna implementacja aplikacji [KernelSU WebUI](https://kernelsu.org/guide/module-webui.html)

Działa z Magisk, KernelSU i APatch

## Tłumaczenie

Cały tekst interfejsu znajduje się w `app/src/main/res/values*/strings.xml`. Angielski (`values/`) jest językiem źródłowym;
arabski (`values-ar`), francuski (`values-fr`), polski (`values-pl`), japoński (`values-ja`) i chiński uproszczony (`values-zh-rCN`) są tłumaczeniami.

### Dodawanie języka

```
python3 scripts/add_language.py de        # lub es, pt-BR, zh-TW, ...
```

To polecenie tworzy `values-de/strings.xml` ze wszystkimi tekstami skopiowanymi z angielskiego i oznaczonymi
`<!-- TODO translate -->`, a następnie prowadzi Cię przez nie po kolei: pokazuje angielski tekst, wpisujesz
tłumaczenie i naciskasz Enter (sam Enter pomija tekst, `quit_translate` zapisuje i kończy). Każda odpowiedź jest od
razu zapisywana w pliku i wyświetlana jako wiersz `String name | English name | Translated word`. Uruchom to samo
polecenie później, aby kontynuować z tekstami, które nadal są oznaczone. Użyj `--create-only`, aby tylko utworzyć plik
i edytować go ręcznie. Kompilacja sama uwzględnia nowy język (pojawia się też w ustawieniach języka dla poszczególnych
aplikacji w Androidzie).

### Dodawanie lub zmiana tekstu

1. Dodaj go do `values/strings.xml` i użyj `R.string.name` (kod) lub `@string/name` (layouty).
2. Dodaj ten sam klucz do każdego pliku `values-*/strings.xml`. Zachowaj identyczne symbole zastępcze (`%s`, `%1$d`).

### Sprawdzanie

```
python3 scripts/check_strings.py
```

Wyświetla brakujące teksty, pozostałe znaczniki `TODO translate`, niezgodne symbole zastępcze i niezdefiniowane
teksty. CI uruchamia go z `--warn-only`, więc problemy pojawiają się jako ostrzeżenia i nigdy nie przerywają kompilacji.
