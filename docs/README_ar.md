## شكر وتقدير

يعتمد هذا المشروع على العمل الأصلي للمطور 5ec1cff، صاحب الكود البرمجي الأساسي لتطبيق **KsuWebUI Standalone**، مع تحسينات وإصلاحات إضافية بواسطة KOWX712.

نتقدم بجزيل الشكر لكلا المساهمين على جهودهما التي كانت بمثابة الأساس الذي بُني عليه هذا المشروع.

- [5ec1cff — KsuWebUIStandalone](https://github.com/5ec1cff/KsuWebUIStandalone)
- [KOWX712 — KsuWebUIStandalone](https://github.com/KOWX712/KsuWebUIStandalone)

> يتم تطوير هذا المشروع وصيانته بشكل مستقل، ولم يعد تفريعاً (fork) للمستودع الأصلي.

# KsuWebUI-SN

[English](../README.md) | [简体中文](README_cn.md) | [Français](README_fr.md) | [日本語](README_ja.md) | العربية | [Polski](README_pl.md)

تطبيق مستقل لتشغيل واجهة [KernelSU WebUI](https://kernelsu.org/guide/module-webui.html)

متوافق مع Magisk و KernelSU و APatch

## الترجمة

توجد كافة نصوص واجهة المستخدم في `app/src/main/res/values*/strings.xml`. الإنجليزية (`values/`) هي لغة المصدر؛
بينما تمثل العربية (`values-ar`) والفرنسية (`values-fr`) والبولندية (`values-pl`) واليابانية (`values-ja`) والصينية المبسطة (`values-zh-rCN`) ترجمات للتطبيق.

### إضافة لغة جديدة

```
python3 scripts/add_language.py de        # الألمانية
python3 scripts/add_language.py pt-BR     # البرتغالية (البرازيل)
python3 scripts/add_language.py zh-TW     # الصينية التقليدية
```

يُنشئ الملف `values-de/strings.xml` بنسخ كافة النصوص من الإنجليزية وتحديدها بعلامة `<!-- TODO translate -->`، ثم يطلب ترجمة كل نص على حدة.
**Enter** يتخطى النص (وتبقى العلامة عليه)، و**`quit_translate`** يحفظ ويخرج، و**`\n`** سطر جديد. أبقِ علامات المتغيرات (`%s` و`%1$d`) متطابقة؛
فالإجابة التي تحتوي على متغيرات مختلفة تُرفض. تُحفظ كل إجابة فوراً. شغّل الأمر نفسه مرة أخرى لمتابعة النصوص التي ما زالت تحمل العلامة
(وتُضاف أولاً النصوص التي أُضيفت إلى الإنجليزية منذ ذلك الحين). يتعرف نظام البناء تلقائياً على اللغة الجديدة.

### تعديل لغة

```
python3 scripts/add_language.py de --edit                     # عرض كل نص للتعديل
python3 scripts/add_language.py de --edit reboot_now          # نص reboot_now فقط
python3 scripts/add_language.py de --edit update reboot_now   # النصان update و reboot_now - ويمكن إضافة المزيد
```

يعرض الأمر النص الإنجليزي والنص الحالي لكل نص، ثم يطلب النص الجديد؛ والضغط على **Enter** وحده يُبقي النص الحالي.
اكتب اسماً واحداً أو عدة أسماء، أو لا تكتب أي اسم للمرور على كل النصوص.

### الفحص والتحقق

```
python3 scripts/check_strings.py
```

يعرض هذا الأمر النصوص المفقودة، وعلامات `TODO translate` المتبقية، وعدم تطابق المتغيرات، والنصوص غير المعرّفة.
يقوم الـ CI بتشغيل هذا الفحص مع خيار `--warn-only`، لذا ستظهر الملاحظات على شكل تحذيرات فقط ولن تتسبب في فشل البناء.
