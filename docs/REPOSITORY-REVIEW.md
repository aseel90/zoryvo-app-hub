# Zoryvo repository review — 2026-09-20

This review defines how external Android releases should move into Zoryvo without interfering with legitimate in-app/internal update systems.

## Executive decision

Zoryvo owns **binary/APK distribution**.

Each source repository continues to own development, testing, internal data/content updates, and its own build pipeline. A source build only becomes a Zoryvo update after its APK passes identity, version, checksum and signing-continuity checks.

Source repositories remain public for now. No visibility changes are part of this work.

## Repository findings

### Zoryvo App Hub

Current role: public Android/Android TV distribution catalog.

Changes made during this review:
- min SDK lowered from 26 to **21 (Android 5.0)**;
- target/compile SDK remain 36;
- phone/tablet launcher and Leanback launcher are both declared;
- Leanback and touchscreen are optional device features;
- forced landscape orientation was removed;
- layout now switches to a compact vertical mode on narrow screens;
- launcher icon and TV banner resources were added;
- downloaded APK package/version/SHA-256 are verified before installer handoff;
- catalog now records stable asset names, SHA-256 and source repository metadata.

This gives the hub one APK that is installable across phones, tablets, Android TV, Google TV, TV boxes and sticks running Android 5.0+.

### Selyro TV — aseel90/iptv

Android identity:
- package: `com.selyro.tv`
- current reviewed version: versionCode 18 / 0.3.8 navigation line
- min SDK: 23
- target SDK: 36
- TV-oriented and landscape
- current export workflow re-signs with a stable AOSP QA test certificate

Important update finding:
- `update/latest.json` is an APK-download/install channel. Under the new terminology that is an **external update**, not an internal content update.
- New binary releases should therefore go to Zoryvo instead of advancing a second APK feed, unless the user explicitly requests two binary channels.

Good fit for Zoryvo:
- signer is already deterministic in the reviewed export workflow;
- package/version checks already exist.

### Bubble Safari TV — aseel90/bubble-safari-tv

Android identity:
- package: `com.bubblesafari.tv`
- versionCode 5 / 0.9.2-tv5
- min SDK: 26
- target SDK: 36
- Leanback is required; app is intentionally TV-only today.

Build architecture:
- web/game files are copied into the APK at build time.
- therefore changes to those bundled files require a new APK unless a future runtime-content mechanism is deliberately introduced.

Critical release risk:
- the current release workflow builds `assembleDebug` without a durable repository signing contract.
- GitHub runner debug keys are not a safe long-term update identity.
- before the next Zoryvo update, Bubble Safari must adopt a stable signing certificate. Otherwise Android will reject the new APK as an update over the installed version.

### Feather Fury — aseel90/FeatherFury-LaB

Android identity:
- package: `com.aseel.featherfury`
- Capacitor-based Android build
- compile/target SDK 36
- current Android RC pipeline builds a debug APK and unsigned release AAB.

Strengths:
- Android release-readiness gates are documented;
- runtime is packaged self-contained;
- approved icon contract exists;
- package identity is verified in CI.

Release risks:
- current debug APK signing relies on a cached `~/.android/debug.keystore`.
- cache persistence is not a durable release-signing guarantee.
- the separate `publish-shareable-apk.yml` also contains a fixed historical run-id while the RC workflow itself already publishes a shareable prerelease. This is redundant and can become stale.

Before the next Zoryvo update, Feather Fury should use a durable stable signing key for its distributed APK.

## External vs internal update rule

### External update — goes through Zoryvo

Anything that requires replacing the installed APK:
- native Android code;
- manifest/permission changes;
- SDK/dependency changes;
- packaged resources;
- bundled HTML/JS/CSS/assets;
- package version bump;
- any self-updater that downloads another APK.

### Internal update — remains in the source app

Only changes the already-installed APK can consume without replacing itself:
- remote lists/data/configuration;
- server-side content;
- an established data/content patch system that does not replace the APK.

## Publishing model

1. Develop and test in the source repository.
2. For an external release, increment `versionCode`.
3. Build with the same signing certificate as the currently distributed APK.
4. Publish a source APK or provide an accessible artifact URL.
5. Run **Publish External App Update** in Zoryvo.
6. Zoryvo verifies package name, version and signer continuity against the currently distributed APK.
7. Zoryvo uploads the asset to `apps-current`, calculates SHA-256 and updates `catalog/apps.json`.
8. Installed Zoryvo clients discover the new version and show **Update**.

## Before repositories become private

Nothing needs to change yet.

When privacy is requested later, configure a fine-grained token or GitHub App that can:
- read release/artifact bytes from the private source repositories; and/or
- dispatch an authenticated publish event into `zoryvo-app-hub`.

Do not make repositories private as part of ordinary app-release work.

## Next source-repository actions

- Selyro: keep the existing stable signer and make Zoryvo the single APK distribution channel.
- Bubble Safari: establish a stable APK signing identity before the next external release.
- Feather Fury: replace cache-based debug signing with a durable APK signing identity before the next external release.
