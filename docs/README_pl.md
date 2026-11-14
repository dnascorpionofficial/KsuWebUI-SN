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
python3 scripts/add_language.py de        # niemiecki
python3 scripts/add_language.py pt-BR     # portugalski (Brazylia)
python3 scripts/add_language.py zh-TW     # chiński tradycyjny
```

Tworzy `values-de/strings.xml` ze wszystkimi tekstami skopiowanymi z angielskiego i oznaczonymi `<!-- TODO translate -->`, a następnie pyta o tłumaczenie każdego z nich po kolei.
**Enter** pomija tekst (pozostaje oznaczony), **`quit_translate`** zapisuje i kończy, **`\n`** wstawia podział wiersza. Zachowaj identyczne symbole zastępcze (`%s`, `%1$d`);
odpowiedź z innymi symbolami jest odrzucana. Każda odpowiedź jest od razu zapisywana. Uruchom to samo polecenie ponownie, aby kontynuować z tekstami, które nadal są oznaczone
(teksty dodane od tego czasu do angielskiego są najpierw dopisywane). Kompilacja sama uwzględnia nowy język.

### Edycja języka

```
python3 scripts/add_language.py de --edit                     # pokazuje każdy tekst do edycji
python3 scripts/add_language.py de --edit reboot_now          # tylko tekst reboot_now
python3 scripts/add_language.py de --edit update reboot_now   # teksty update i reboot_now - można dodać kolejne
```

Pokazuje angielski i aktualny tekst każdej pozycji, a następnie prosi o nowy; **Enter** zachowuje aktualny tekst.
Podaj jedną lub kilka nazw albo żadnej, aby przejść przez wszystkie teksty.

### Sprawdzanie

```
python3 scripts/check_strings.py
```

Wyświetla brakujące teksty, pozostałe znaczniki `TODO translate`, niezgodne symbole zastępcze i niezdefiniowane
teksty. CI uruchamia go z `--warn-only`, więc problemy pojawiają się jako ostrzeżenia i nigdy nie przerywają kompilacji.
