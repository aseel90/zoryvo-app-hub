# Zoryvo App Hub

**Zoryvo** هو مركز مؤقت لتوزيع تطبيقات Android وAndroid TV قبل اكتمال نشرها على Google Play.

## تنزيل Zoryvo

أحدث APK ثابت:

https://github.com/aseel90/zoryvo-app-hub/releases/download/hub-latest/Zoryvo.apk

الإصدار الحالي:

- package: `com.zoryvo.hub`
- version: `0.2.0`
- versionCode: `2`
- min Android: **5.0 / API 21**
- target SDK: **36**

## الأجهزة المدعومة

Zoryvo نفسه مصمم ليعمل على:

- هواتف Android
- أجهزة Android اللوحية
- Android TV
- Google TV
- TV boxes / TV sticks

التطبيق لا يفرض وضع landscape على الهاتف، ويستخدم تخطيطًا مضغوطًا على الشاشات الضيقة، مع دعم Launcher العادي وLeanback Launcher.

## العلامة البصرية

تمت إضافة:

- launcher icon خاص بـ Zoryvo
- Android TV banner
- هوية Z باللون الأزرق/البنفسجي

## قاعدة التحديثات

**التحديث الخارجي** = أي تحديث يتطلب APK جديدًا أو استبدال APK المثبت. هذا النوع يمر عبر Zoryvo.

**التحديث الداخلي** = بيانات/محتوى/إعدادات يستطيع التطبيق المثبت استهلاكها بدون استبدال APK. هذا النوع يبقى داخل التطبيق نفسه.

التعليمات الكاملة للـAgents موجودة في:

`AGENTS.md`

ومراجعة المستودعات وخطة العمل موجودة في:

`docs/REPOSITORY-REVIEW.md`

## التطبيقات الحالية

- Selyro TV — `com.selyro.tv`
- Bubble Safari TV — `com.bubblesafari.tv`
- Feather Fury — `com.aseel.featherfury`

## توزيع APKs

ملفات APK العامة موجودة في Release ثابت:

`apps-current`

الكتالوج:

`catalog/apps.json`

لكل تطبيق نسجل package name وversionCode وversionName واسم asset وSHA-256 ومصدره.

## نشر تحديث خارجي

Workflow باسم **Publish External App Update** يستقبل APK المرشح ثم يتحقق من:

1. package name
2. أن versionCode أعلى من النسخة الحالية
3. أن توقيع APK يطابق توقيع النسخة الموزعة حاليًا
4. SHA-256

إذا اختلف التوقيع، يفشل النشر بدل إرسال تحديث لا يستطيع Android تثبيته فوق النسخة الحالية.

بعد نجاح الفحص، يتم استبدال APK في `apps-current` وتحديث `catalog/apps.json` واحتساب SHA-256 الجديد. بعدها يظهر زر **تحديث** في Zoryvo للمستخدم الذي لديه إصدار أقدم.

## ملاحظة عن مستودعات المصدر

المستودعات الأصلية **تبقى Public حاليًا**. لا يتم تحويلها إلى Private إلا بطلب صريح لاحقًا.

قبل جعلها Private مستقبلًا سنضيف Fine-grained token أو GitHub App يسمح لـ Zoryvo بقراءة artifacts/releases الخاصة أو استقبال dispatch مصادق عليه.

## تنبيه توقيع مهم

- Selyro لديه مسار QA بتوقيع ثابت حاليًا.
- Bubble Safari يحتاج تثبيت signing identity دائم قبل أول تحديث خارجي جديد عبر Zoryvo.
- Feather Fury يحتاج تحويل signing من cache-based debug keystore إلى signing identity دائم قبل الاعتماد على تحديثات Zoryvo المتتابعة.

هذا يمنع Android من طلب حذف التطبيق القديم عند محاولة التحديث.

## بناء Zoryvo

Workflow باسم **Build Zoryvo APK** يبني التطبيق ويتحقق من package/version وmin SDK 21 وLauncher للهاتف/اللوحي وLeanback Launcher للتلفاز، ثم ينشر `Zoryvo.apk` إلى Release `hub-latest`.

## ملاحظة Google Play

هذا المستودع مخصص حاليًا للتوزيع المؤقت خارج Google Play. صلاحية `QUERY_ALL_PACKAGES` وsideloading هنا ليست بالضرورة تصميم نسخة Play Store النهائية.
