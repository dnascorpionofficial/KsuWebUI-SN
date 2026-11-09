## Crédits

Ce projet est basé sur le travail original de 5ec1cff, qui a développé le code de base de **KsuWebUI Standalone**, avec des améliorations et des corrections supplémentaires apportées par KOWX712.

Un grand merci aux deux contributeurs pour leur travail, qui a servi de fondation à ce projet.

- [5ec1cff — KsuWebUIStandalone](https://github.com/5ec1cff/KsuWebUIStandalone)
- [KOWX712 — KsuWebUIStandalone](https://github.com/KOWX712/KsuWebUIStandalone)

> Ce projet est maintenu de manière indépendante et n'est plus un fork du dépôt d'origine.

# KsuWebUI-SN

[English](../README.md) | [简体中文](README_cn.md) | Français | [日本語](README_ja.md) | [العربية](README_ar.md) | [Polski](README_pl.md)

Implémentation autonome de l'application [KernelSU WebUI](https://kernelsu.org/guide/module-webui.html)

Compatible avec Magisk, KernelSU et APatch

## Traduction

Tous les textes de l'interface se trouvent dans `app/src/main/res/values*/strings.xml`. L'anglais (`values/`) est la langue source ;
l'arabe (`values-ar`), le français (`values-fr`), le polonais (`values-pl`), le japonais (`values-ja`) et le chinois simplifié (`values-zh-rCN`) sont des traductions.

### Ajouter une langue

```
python3 scripts/add_language.py de        # ou es, pt-BR, zh-TW, ...
```

Cette commande crée `values-de/strings.xml` avec toutes les chaînes copiées depuis l'anglais et marquées
`<!-- TODO translate -->`. Traduisez le texte, supprimez le marqueur, et c'est terminé. La compilation prend
automatiquement en compte la nouvelle langue (elle apparaît aussi dans les paramètres de langue par application d'Android).

### Ajouter ou modifier une chaîne

1. Ajoutez-la à `values/strings.xml` et utilisez `R.string.name` (code) ou `@string/name` (layouts).
2. Ajoutez la même clé à chaque fichier `values-*/strings.xml`. Gardez les variables (`%s`, `%1$d`) identiques.

### Vérifier

```
python3 scripts/check_strings.py
```

Affiche les chaînes manquantes, les marqueurs `TODO translate` restants, les variables incohérentes et les
chaînes non définies. L'intégration continue l'exécute avec `--warn-only` : les problèmes apparaissent
sous forme d'avertissements et ne font jamais échouer la compilation.
