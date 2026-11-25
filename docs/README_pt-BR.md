## Créditos

Este projeto é baseado no trabalho original de 5ec1cff, que desenvolveu o código base do **KsuWebUI Standalone**, com melhorias e correções adicionais de KOWX712.

Agradecimentos especiais aos dois colaboradores pelo trabalho, que serviu de base para este projeto.

- [5ec1cff — KsuWebUIStandalone](https://github.com/5ec1cff/KsuWebUIStandalone)
- [KOWX712 — KsuWebUIStandalone](https://github.com/KOWX712/KsuWebUIStandalone)

> Este projeto é mantido de forma independente e não é mais um fork do repositório original.

# KsuWebUI-SN

[English](../README.md) | [简体中文](README_cn.md) | [Français](README_fr.md) | [日本語](README_ja.md) | [العربية](README_ar.md) | [Polski](README_pl.md) | Português (Brasil)

Implementação independente do aplicativo [KernelSU WebUI](https://kernelsu.org/guide/module-webui.html)

Compatível com Magisk, KernelSU e APatch

## Tradução

Todo o texto da interface fica em `app/src/main/res/values*/strings.xml`. O inglês (`values/`) é o idioma de origem;
o árabe (`values-ar`), o francês (`values-fr`), o polonês (`values-pl`), o japonês (`values-ja`), o português do Brasil (`values-pt-rBR`) e o chinês simplificado (`values-zh-rCN`) são traduções.

### Adicionar um idioma

```
python3 scripts/language_extension.py
```

Mostra os idiomas ainda não adicionados em uma lista numerada em ordem alfabética (nome e código). Digite o número e pressione **Enter** para criar `values-xx/strings.xml` com todas as strings copiadas do inglês e marcadas com `<!-- TODO translate -->`, e depois traduza uma a uma.
Se o seu idioma não estiver na lista, digite **`addnew`** e envie o nome e o código do idioma (por ex. `Esperanto`, `eo`). Um idioma já adicionado deixa de aparecer nesta lista; um que ainda tem strings marcadas aparece em **Unfinished** — escolha-o ali para continuar (as strings adicionadas ao inglês desde então são adicionadas primeiro).
**Enter** pula a string (ela continua marcada), **`quit_translate`** salva e sai, **`\n`** é uma quebra de linha. Mantenha os marcadores (`%s`, `%1$d`)
idênticos; uma resposta com marcadores diferentes é recusada. Cada resposta é salva na hora. A compilação reconhece o novo idioma automaticamente.

### Editar um idioma

```
python3 scripts/language_extension.py --edit
```

Lista os idiomas já adicionados (ordem alfabética, nome e código). Digite o número e depois o nome de uma ou mais strings (por ex. `update reboot_now`) ou pressione **Enter** para percorrer todas as strings.
Mostra o texto em inglês e o texto atual de cada string e pede um novo; **Enter** mantém o texto atual.

### Verificar

```
python3 scripts/check_strings.py
```

Lista strings ausentes, marcadores `TODO translate` restantes, divergências de marcadores e strings não
definidas. A CI executa o script com `--warn-only`, então os problemas aparecem como avisos e nunca fazem
a compilação falhar.
