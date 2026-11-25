## Podziękowania

Ten projekt opiera się na oryginalnej pracy 5ec1cff, który stworzył bazowy kod **KsuWebUI Standalone**, z dalszymi ulepszeniami i poprawkami autorstwa KOWX712.

Szczególne podziękowania dla obu autorów za ich pracę, która stała się fundamentem tego projektu.

- [5ec1cff — KsuWebUIStandalone](https://github.com/5ec1cff/KsuWebUIStandalone)
- [KOWX712 — KsuWebUIStandalone](https://github.com/KOWX712/KsuWebUIStandalone)

> Ten projekt jest rozwijany niezależnie i nie jest już forkiem oryginalnego repozytorium.

# KsuWebUI-SN

[English](../README.md) | [简体中文](README_cn.md) | [Français](README_fr.md) | [日本語](README_ja.md) | [العربية](README_ar.md) | Polski | [Português (Brasil)](README_pt-BR.md)

Samodzielna implementacja aplikacji [KernelSU WebUI](https://kernelsu.org/guide/module-webui.html)

Działa z Magisk, KernelSU i APatch

## Tłumaczenie

Cały tekst interfejsu znajduje się w `app/src/main/res/values*/strings.xml`. Angielski (`values/`) jest językiem źródłowym;
arabski (`values-ar`), francuski (`values-fr`), polski (`values-pl`), japoński (`values-ja`), portugalski brazylijski (`values-pt-rBR`) i chiński uproszczony (`values-zh-rCN`) są tłumaczeniami.

### Dodawanie języka

```
python3 scripts/language_extension.py
```

Wyświetla jeszcze niedodane języki w postaci numerowanej listy w kolejności alfabetycznej (nazwa i kod). Wpisz numer i naciśnij **Enter**, aby utworzyć `values-xx/strings.xml` ze wszystkimi tekstami skopiowanymi z angielskiego i oznaczonymi `<!-- TODO translate -->`, a następnie tłumacz je po kolei.
Jeśli Twojego języka nie ma na liście, wpisz **`addnew`**, a potem podaj nazwę i kod języka (np. `Esperanto`, `eo`). Dodany język znika z tej listy; język, w którym nadal są oznaczone teksty, pojawia się w sekcji **Unfinished** – wybierz go tam, aby kontynuować (teksty dodane od tego czasu do angielskiego zostaną dodane najpierw).
**Enter** pomija tekst (pozostaje oznaczony), **`quit_translate`** zapisuje i kończy, **`\n`** oznacza łamanie wiersza. Zachowaj identyczne symbole zastępcze (`%s`, `%1$d`);
odpowiedź z innymi zostanie odrzucona. Każda odpowiedź jest zapisywana od razu. Kompilacja sama uwzględnia nowy język.

### Edycja języka

```
python3 scripts/language_extension.py --edit
```

Wyświetla już dodane języki (kolejność alfabetyczna, nazwa i kod). Wpisz numer, a następnie podaj nazwę jednego lub kilku tekstów (np. `update reboot_now`) albo naciśnij **Enter**, aby przejść przez wszystkie teksty.
Dla każdego tekstu pokazuje angielski oryginał i obecne tłumaczenie oraz prosi o nowe; **Enter** zachowuje obecne.

### Sprawdzanie

```
python3 scripts/check_strings.py
```

Wyświetla brakujące teksty, pozostałe znaczniki `TODO translate`, niezgodne symbole zastępcze i niezdefiniowane
teksty. CI uruchamia go z `--warn-only`, więc problemy pojawiają się jako ostrzeżenia i nigdy nie przerywają kompilacji.
