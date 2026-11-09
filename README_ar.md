## شكر وتقدير

يعتمد هذا المشروع على العمل الأصلي للمطور 5ec1cff، صاحب الكود البرمجي الأساسي لتطبيق **KsuWebUI Standalone**، مع تحسينات وإصلاحات إضافية بواسطة KOWX712.

نتقدم بجزيل الشكر لكلا المساهمين على جهودهما التي كانت بمثابة الأساس الذي بُني عليه هذا المشروع.

- [5ec1cff — KsuWebUIStandalone](https://github.com/5ec1cff/KsuWebUIStandalone)
- [KOWX712 — KsuWebUIStandalone](https://github.com/KOWX712/KsuWebUIStandalone)

> يتم تطوير هذا المشروع وصيانته بشكل مستقل، ولم يعد تفريعاً (fork) للمستودع الأصلي.

# KsuWebUI-SN

[English](README.md) | [简体中文](README_cn.md) | [Français](README_fr.md) | [日本語](README_ja.md) | العربية

تطبيق مستقل لتشغيل واجهة [KernelSU WebUI](https://kernelsu.org/guide/module-webui.html)

متوافق مع Magisk و KernelSU و APatch

## الترجمة

توجد كافة نصوص واجهة المستخدم في `app/src/main/res/values*/strings.xml`. الإنجليزية (`values/`) هي لغة المصدر؛
بينما تمثل العربية (`values-ar`) والبولندية (`values-pl`) واليابانية (`values-ja`) والصينية المبسطة (`values-zh-rCN`) ترجمات للتطبيق.

### إضافة لغة جديدة

```
python3 scripts/add_language.py de        # أو fr، es، pt-BR، zh-TW ...
```

يُنشئ هذا الأمر الملف `values-de/strings.xml` بنسخ كافة النصوص من الإنجليزية وتحديدها بعلامة
`<!-- TODO translate -->`. كل ما عليك هو ترجمة النص وحذف هذه العلامة. يتعرف نظام البناء تلقائياً على اللغة الجديدة
(كما تظهر أيضاً ضمن إعدادات لغات التطبيق في Android).

### إضافة نص أو تعديله

1. أضف النص إلى `values/strings.xml` واستخدم `R.string.name` في الكود البرمجي أو `@string/name` في ملفات الواجهة (layouts).
2. أضف نفس المفتاح في كافة ملفات `values-*/strings.xml`. واحرص على إبقاء علامات المتغيرات (`%s` و `%1$d`) متطابقة تماماً.

### الفحص والتحقق

```
python3 scripts/check_strings.py
```

يعرض هذا الأمر النصوص المفقودة، وعلامات `TODO translate` المتبقية، وعدم تطابق المتغيرات، والنصوص غير المعرّفة.
يقوم الـ CI بتشغيل هذا الفحص مع خيار `--warn-only`، لذا ستظهر الملاحظات على شكل تحذيرات فقط ولن تتسبب في فشل البناء.
