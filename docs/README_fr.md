## Crédits

Ce projet est basé sur le travail original de 5ec1cff, qui a développé le code de base de **KsuWebUI Standalone**, avec des améliorations et des corrections supplémentaires apportées par KOWX712.

Un grand merci aux deux contributeurs pour leur travail, qui a servi de fondation à ce projet.

- [5ec1cff — KsuWebUIStandalone](https://github.com/5ec1cff/KsuWebUIStandalone)
- [KOWX712 — KsuWebUIStandalone](https://github.com/KOWX712/KsuWebUIStandalone)

> Ce projet est maintenu de manière indépendante et n'est plus un fork du dépôt d'origine.

# KsuWebUI-SN

[English](../README.md) | [简体中文](README_cn.md) | Français | [日本語](README_ja.md) | [العربية](README_ar.md) | [Polski](README_pl.md) | [Português (Brasil)](README_pt-BR.md)

Implémentation autonome de l'application [KernelSU WebUI](https://kernelsu.org/guide/module-webui.html)

Compatible avec Magisk, KernelSU et APatch

## Traduction

Tous les textes de l'interface se trouvent dans `app/src/main/res/values*/strings.xml`. L'anglais (`values/`) est la langue source ;
l'arabe (`values-ar`), le français (`values-fr`), le polonais (`values-pl`), le japonais (`values-ja`), le portugais brésilien (`values-pt-rBR`) et le chinois simplifié (`values-zh-rCN`) sont des traductions.

### Ajouter une langue

```
python3 scripts/language_extension.py
```

Affiche les langues pas encore ajoutées sous forme de liste numérotée par ordre alphabétique (nom et code). Saisissez un numéro puis **Entrée** pour créer `values-xx/strings.xml` avec toutes les chaînes copiées depuis l'anglais et marquées `<!-- TODO translate -->`, puis traduisez-les une par une.
Si votre langue n'est pas dans la liste, saisissez **`addnew`**, puis indiquez le nom et le code de la langue (par ex. `Esperanto`, `eo`). Une langue déjà ajoutée n'apparaît plus dans cette liste ; celle qui a encore des chaînes marquées apparaît sous **Unfinished** : choisissez-la là pour continuer (les chaînes ajoutées à l'anglais depuis sont ajoutées d'abord).
**Entrée** passe la chaîne (elle reste marquée), **`quit_translate`** enregistre et quitte, **`\n`** insère un retour à la ligne. Gardez les variables (`%s`, `%1$d`)
identiques ; une réponse avec des variables différentes est refusée. Chaque réponse est enregistrée aussitôt. La compilation prend la nouvelle langue en compte automatiquement.

### Modifier une langue

```
python3 scripts/language_extension.py --edit
```

Affiche les langues déjà ajoutées (ordre alphabétique, nom et code). Saisissez un numéro, puis indiquez un ou plusieurs noms de chaînes (par ex. `update reboot_now`) ou appuyez sur **Entrée** pour parcourir toutes les chaînes.
Affiche le texte anglais et le texte actuel de chaque chaîne, puis demande le nouveau ; **Entrée** conserve le texte actuel.

### Vérifier

```
python3 scripts/check_strings.py
```

Affiche les chaînes manquantes, les marqueurs `TODO translate` restants, les variables incohérentes et les
chaînes non définies. L'intégration continue l'exécute avec `--warn-only` : les problèmes apparaissent
sous forme d'avertissements et ne font jamais échouer la compilation.
