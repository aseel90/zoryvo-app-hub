# Zoryvo cross-device UI and navigation review

This review covers Zoryvo as a single Android app for Android 5.0+ phones, tablets, Android TV, Google TV, TV boxes/sticks, keyboard/D-pad devices, large screens, rotation and multi-window.

## Root cause of the 0.5.1 list-navigation failure

`AppCardView` called `bringToFront()` when a card gained focus and also calculated list navigation from `parent.indexOfChild(this)`. In a `LinearLayout`, moving the focused child to the front changes child ordering. The focused card could therefore become the last child, so DOWN returned the same card and list navigation appeared stuck.

The replacement design removes both `bringToFront()` and the `focusSearch()` override. Navigation is now an explicit focus graph created after the catalog is rendered. Every card receives a stable runtime view ID and explicit up/down/left/right neighbors.

## Cross-device contracts

- Physical D-pad directions follow physical layout directions. Card geometry is LTR for deterministic navigation; text uses first-strong direction so Arabic and Latin labels remain readable.
- Touch users can tap the card, primary action and management icon. Remote/keyboard users focus the card as one predictable target; MENU/INFO or long press opens management.
- The active focus target is shown with a 3dp cyan border and a distinct background. Focus does not scale or reorder views.
- Returning from another app or the Android package installer refreshes installed states from the cached catalog without refetching the network catalog or resetting the selected app.
- Android TV-only apps are opened with `getLeanbackLaunchIntentForPackage()` when no regular launcher activity exists.
- Auto layout uses list on phones and grid on tablets/TV. Explicit list/grid selection still works. Forced grid uses one column on very narrow windows and up to five on large displays.
- Grid columns use weighted `GridLayout` cells so width follows the actual app window instead of full-display pixel metrics. This is safer for multi-window and foldables.
- Narrow phone list rows hide the redundant inline action button below 420dp; tapping the card remains the primary action and the management button remains available.
- Top controls and dialogs use at least 48dp focus/touch targets. Card management targets are 44dp while the whole card remains a larger touch target.
- Dialogs are scrollable, which keeps settings and management usable in landscape phones and with larger font scales.
- System bars and display cutouts are handled through WindowInsets so Android 15/16 edge-to-edge behavior does not cover content.
- Rotation destroys the activity normally. An active custom download is cancelled safely, the partial file remains, and the next activity can resume it rather than letting the old Activity keep updating stale UI.

## Download and network review

- Range resume validates the server's `Content-Range` start before appending. A mismatched range restarts cleanly instead of corrupting the APK.
- Download progress UI/prefs writes are throttled instead of updating on every 32KB buffer.
- The download dialog can be cancelled with Back or the explicit stop-and-save button; the partial file is retained.
- Catalog network results are cached under internal app storage. If network/cache is unavailable, a bundled fallback catalog keeps the installed hub usable.
- Remote icon downloads are capped and downsampled to avoid decoding arbitrarily large images into memory.
- Completed cached APKs are retained temporarily and cleaned only after they are old, avoiding races with the Android package installer.

## Compatibility matrix used for the layout policy

- 320dp phone: auto=list; forced grid=1 column.
- 360dp phone: auto=list; forced grid=2 columns.
- 600dp tablet: auto=grid; 2 columns.
- 760dp tablet/large phone window: grid; 3 columns.
- 960dp TV/large tablet: grid; 4 columns.
- 1280dp+ large TV/tablet: grid; capped at 5 columns.

The layout policy and neighbor calculations have JVM unit tests. CI also rejects regressions that reintroduce `bringToFront()` or a custom `focusSearch()` override in the app card.

## Remaining temporary-distribution constraints

Zoryvo still uses `QUERY_ALL_PACKAGES` because the current sideload catalog needs to detect dynamically added packages. This should be revisited for Google Play. Zoryvo is still signed with the temporary stable AOSP test key; changing that signer later requires migration planning for already installed Zoryvo copies.