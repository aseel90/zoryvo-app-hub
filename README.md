# Zoryvo App Hub

**Zoryvo** هو الاسم المؤقت لمركز بسيط لتوزيع تطبيقات Android وAndroid TV قبل اكتمال نشرها على Google Play.

## تنزيل Zoryvo

أحدث APK ثابت للمركز:

https://github.com/aseel90/zoryvo-app-hub/releases/download/hub-latest/Zoryvo.apk

## الحالة الحالية

- ✅ تطبيق Android / Android TV قابل للبناء والتثبيت.
- ✅ الكتالوج منفصل في `catalog/apps.json`.
- ✅ Selyro TV موجود في Release المركزي.
- ✅ Bubble Safari TV موجود في Release المركزي.
- ✅ Feather Fury موجود في Release المركزي.
- ✅ Workflow يبني Zoryvo وينشر `Zoryvo.apk` تلقائيًا.
- ✅ ملفات التطبيقات العامة أصبحت في هذا المستودع، ولا يحتاج الكتالوج إلى قراءة مستودعات المصدر.

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

ملفات APK العامة موجودة في Release ثابت باسم:

`apps-current`

وبأسماء:

- `Selyro-TV.apk`
- `Bubble-Safari-TV.apk`
- `Feather-Fury.apk`

الكتالوج يشير إلى هذه الملفات في **Zoryvo App Hub نفسه**، وليس إلى مستودعات المصدر. لذلك يمكن لاحقًا جعل مستودعات التطوير خاصة مع بقاء النسخ الحالية قابلة للتنزيل.

## إضافة تطبيق جديد

1. ارفع APK إلى Release `apps-current`.
2. أضف عنصرًا جديدًا إلى `catalog/apps.json`:

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

بعد حفظ `apps.json` سيظهر التطبيق في Zoryvo عند المزامنة التالية دون تحديث APK الخاص بـ Zoryvo.

## تحديث تطبيق

1. ارفع APK أحدث إلى Release `apps-current` بنفس اسم الملف.
2. ارفع `versionCode` و`versionName` داخل `catalog/apps.json`.
3. يقرأ Zoryvo الكتالوج الجديد، ويعرض **تحديث** عندما يكون الإصدار المثبت أقدم.

> تحديث نفس التطبيق فوق النسخة المثبتة يخضع لقواعد Android المعتادة، ومنها تطابق package name والتوقيع.

## بناء Zoryvo

Workflow باسم **Build Zoryvo APK** يبني APK ويتحقق من هوية الحزمة ثم ينشر أحدث نسخة دائمًا في Release `hub-latest` باسم:

`Zoryvo.apk`

Package الخاص بالمركز:

`com.zoryvo.hub`

الإصدار الحالي:

`0.1.0 (versionCode 1)`

## ملاحظة

هذا المستودع مقصود كتوزيع مؤقت خارج Google Play. طريقة sideload وصلاحيات اكتشاف التطبيقات المستخدمة هنا مخصصة لهذا الاستخدام المؤقت وليست تصميم نسخة Google Play النهائية.
