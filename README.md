# Zoryvo App Hub

**Zoryvo** هو اسم مؤقت لمركز بسيط لتوزيع تطبيقات Android وAndroid TV قبل اكتمال نشرها على Google Play.

## كيف يعمل؟

التطبيق يقرأ الكتالوج مباشرة من:

`catalog/apps.json`

لذلك إضافة تطبيق جديد أو تغيير إصدار أو رابط APK لا تحتاج إصدار نسخة جديدة من تطبيق Zoryvo نفسه.

الحالات التي يعرضها المركز لكل تطبيق:

- **تثبيت**: التطبيق غير موجود على الجهاز.
- **تحديث**: `versionCode` الموجود في الكتالوج أكبر من الإصدار المثبت.
- **فتح**: الإصدار المثبت مساوي أو أحدث من الموجود في الكتالوج.

## التطبيقات الحالية

- Selyro TV — `com.selyro.tv`
- Bubble Safari TV — `com.bubblesafari.tv`
- Feather Fury — `com.aseel.featherfury`

## توزيع APKs

ملفات APK العامة التي يقرأها الكتالوج توضع في Release ثابت داخل هذا المستودع باسم:

`apps-current`

وبأسماء:

- `Selyro-TV.apk`
- `Bubble-Safari-TV.apk`
- `Feather-Fury.apk`

بهذا يمكن أن تبقى مستودعات المصدر خاصة لاحقًا، بينما يظل مستودع Zoryvo العام هو واجهة التوزيع فقط.

## إضافة تطبيق جديد

أضف عنصرًا جديدًا إلى `catalog/apps.json`:

```json
{
  "id": "my-app",
  "name": "My App",
  "packageName": "com.example.myapp",
  "versionCode": 12,
  "versionName": "1.2.0",
  "apkUrl": "https://github.com/aseel90/zoryvo-app-hub/releases/download/apps-current/My-App.apk",
  "notes": "وصف قصير",
  "enabled": true
}
```

ثم ارفع APK بالاسم نفسه إلى Release `apps-current`.

## تحديث تطبيق

1. انشر APK أحدث بنفس `packageName` والتوقيع المستخدم سابقًا لذلك التطبيق.
2. استبدل ملف APK في Release `apps-current`.
3. ارفع `versionCode` و`versionName` داخل `catalog/apps.json`.

في المرة التالية التي يفتح فيها المستخدم Zoryvo سيظهر زر **تحديث** تلقائيًا.

## بناء Zoryvo

Workflow باسم **Build Zoryvo APK** يبني APK ويضع أحدث نسخة دائمًا في Release `hub-latest` باسم:

`Zoryvo.apk`

## ملاحظة

هذا المستودع مقصود كتوزيع مؤقت خارج Google Play. صلاحية `QUERY_ALL_PACKAGES` وطريقة sideload هنا ليست تصميمًا لنسخة Play Store النهائية.
