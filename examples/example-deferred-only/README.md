# posted-sdk for Android — Deferred-Only Example

This example demonstrates `LinkProcessingMode.DEFERRED_ONLY` — the integration pattern for apps where a navigation framework (Jetpack Navigation, etc.) already handles Universal Links and custom scheme links, and posted-sdk is only needed for deferred deep link resolution on first install.

## Scenario represented

- `MainActivity`: initializes posted-sdk with `DEFERRED_ONLY` mode and calls `Posted.getDeferredLink()` directly on launch. No `PostedDelegate`, no intent processing.
- `DeepLinkDestinationActivity`: simulated target screen the user lands on when a deferred link is matched.

## What this example covers

- `LinkProcessingMode.DEFERRED_ONLY` configuration.
- Manual `Posted.getDeferredLink()` call without `PostedDelegate`.
- All four `LinkResult` states: `Success`, `NotFirstLaunch`, `NoLink`, `Error`.
- Splash screen pattern for seamless deferred deep link UX.

## Splash screen pattern

The example uses the `androidx.core:core-splashscreen` API to hide the initialization process entirely from the user:

1. App launches → splash screen is shown immediately by the system (before any code runs).
2. `getDeferredLink()` is called in the background while the splash stays on screen via `setKeepOnScreenCondition`.
3. **Link found** → `MainActivity` starts `DeepLinkDestinationActivity` and finishes itself. The user lands directly on the destination screen as if they had opened a deep link — `MainActivity` is never visible.
4. **No link / not first launch / error** → splash exits with its standard animation and the home screen is shown normally.

This pattern is the recommended approach for production apps: the deferred link resolution is invisible to the user, and on first install after clicking a link they appear to land directly on the target content.

## Test flow

1) Start the app — splash shows briefly while `getDeferredLink()` runs.
2) **Not first launch:** splash exits, status shows "Not first launch — deferred link already consumed".
3) **No deferred link:** uninstall, reinstall, and launch with no prior posted-sdk link — splash exits, status shows "No deferred link matched".
4) **With a deferred link:** uninstall, visit a posted-sdk link, install the app and launch it — splash stays until the link resolves, then the app navigates directly to `DeepLinkDestinationActivity`.

## Quick start

1. Configure this app in the Posted Dashboard. You'll need two values:
   - **Package name:** `com.postedapp.posted.example.deferred` (from `AndroidManifest.xml`)
   - **SHA256 certificate fingerprint** — run this to get it from your debug keystore:
     ```shell
     keytool -list -v \
       -keystore ~/.android/debug.keystore \
       -alias androiddebugkey \
       -storepass android \
       -keypass android \
       | grep "SHA256"
     ```
     > The debug certificate is machine-specific — each developer must register their own fingerprint. For a release build, use your release keystore and its alias instead.

2. Add your credentials from the Dashboard — either via a secrets file:
   ```shell
   cp secrets.properties.example secrets.properties
   # then edit secrets.properties with your POSTED_API_KEY and POSTED_APP_ID
   ```
   or by hardcoding them directly in `MainActivity.kt`.

3. Build and run:
   ```shell
   ./gradlew :examples:example-deferred-only:installDebug
   ```

4. Test deferred links: uninstall, copy a posted-sdk link from the Dashboard, install and launch — the deferred link resolves on first open.
