# Zoryvo distribution policy for all agents

This file is an operational contract for agents working on this repository.

## Core rule

**External app updates are distributed through Zoryvo.**

An external update means any change that requires replacing/installing an APK on the user's Android device, including:
- a new APK or application version;
- native Android code, manifest, permissions, SDK, packaged resources or bundled web assets that require a rebuild;
- any change that requires a `versionCode` bump.

The public distribution endpoint is:
- repository: `aseel90/zoryvo-app-hub`
- release: `apps-current`
- catalog: `catalog/apps.json`

## Internal updates stay inside the app

An internal update is a change that the **already-installed APK can consume without replacing the APK**, for example remote data, content, configuration, lists, server-side data, or an existing in-app content/patch mechanism.

Internal updates may continue to use the application's own mechanism.

**Do not classify an APK self-updater as an internal update.** If the app downloads and installs a new APK, that is an external update and Zoryvo is the distribution owner.

## External release contract

For every external release:

1. Keep the existing `applicationId/packageName`.
2. Keep the same signing certificate as the currently distributed APK.
3. Increment `versionCode`.
4. Build and test the APK in the source repository.
5. Verify package name, version and signer certificate.
6. Publish/send the verified APK to Zoryvo.
7. Zoryvo uploads it to `apps-current` and updates `catalog/apps.json`.
8. Verify that Zoryvo shows **Update** over the previous installed version.

Never silently replace a distributed APK with a differently signed APK.

## Repository visibility

Do **not** make the source repositories private unless the user explicitly asks for that change. The current plan keeps them public for now.

## Zoryvo itself

Zoryvo is the temporary public app-distribution hub until the applications are moved to Google Play. Zoryvo must remain usable on Android phones, tablets, Android TV, Google TV, TV boxes and TV sticks within its supported Android API range.

## Future automation

Source repositories may later trigger Zoryvo's publishing workflow using a dedicated fine-grained GitHub token/GitHub App. Until that credential is explicitly configured, do not add a workflow that assumes cross-repository write access exists.

## Current app mapping

- `selyro-tv` -> `com.selyro.tv` -> `Selyro-TV.apk`
- `bubble-safari-tv` -> `com.bubblesafari.tv` -> `Bubble-Safari-TV.apk`
- `feather-fury` -> `com.aseel.featherfury` -> `Feather-Fury.apk`

When adding future apps, add the mapping to `catalog/apps.json` and preserve it across releases.

## Cross-device UI / D-pad contract

Zoryvo targets Android 5.0+ phones, tablets and TV-class devices from one APK. Preserve these rules:
- Do not call `bringToFront()` as a focus effect on catalog cards; it changes child order and breaks deterministic navigation.
- Do not implement card navigation by overriding `focusSearch()`. Build explicit directional focus links after rendering instead.
- Keep physical card geometry deterministic for D-pad navigation, while text direction can follow its content.
- Preserve usable touch targets and a visible non-scaling focus state.
- Use window/configuration width rather than full-display pixels when choosing responsive layout behavior.
- When opening installed apps, fall back to a Leanback launch intent for TV-only packages.
- Returning from installers/settings must not force an unnecessary remote catalog fetch or reset the selected app.
- Any layout/navigation change must keep `LayoutPolicyTest` green and should extend its cases when rules change.

See `docs/CROSS-DEVICE-REVIEW.md` for the current rationale and compatibility matrix.