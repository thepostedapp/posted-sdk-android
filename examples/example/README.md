# posted-sdk for Android — Full Example

This example demonstrates a full integration of `com.postedapp:posted-sdk` using `PostedDelegate` and `LinkProcessingMode.ALL`.

## Scenario represented

- `MainActivity`: uses `PostedDelegate` with `LinkProcessingMode.ALL` to handle Universal Links, custom scheme links (`posted-example-app://`), and deferred deep links automatically. Shows custom encrypted storage via `EncryptedStorageProvider`.
- `ProductActivity`: navigated to from a `/products/{id}` route. Logs a `ViewItem` analytics event.
- `PromoActivity`: navigated to from a `/promo` route. Logs a `Purchase` event and a `subscription_renewed` retention event.

## What this example covers

- SDK initialization and `PostedDelegate` lifecycle wiring (`onCreate` / `onNewIntent`).
- All three `LinkProcessingMode` options (inline comments in `MainActivity` config).
- Universal Links (`https://`) and custom scheme links (`posted-example-app://`) handled side by side.
- Custom storage implementation (`EncryptedSharedPreferences`).
- `LinkResult` handling: `Success` (with `route`, `pathname`, `params`, `type`), `NotFirstLaunch`, `NoLink`, `Error`.
- Route-based navigation from link results.
- Analytics:
  - `MainActivity` — `PostedAnalytics.logEvent(ReEngage)` on every link-driven open, with `source` (`deferred` / `universal` / `scheme` from `LinkType.name`) and `route` properties.
  - `ProductActivity` — `PostedAnalytics.logEvent(ViewItem)` with the product ID.
  - `PromoActivity` — `PostedAnalytics.logEvent(Purchase)` and `PostedAnalytics.logRetention("subscription_renewed")`.

## Test flow

1) Start the app on a device or emulator.
2) You land on `MainActivity` — status shows "No deep link detected".
3) Trigger the App Link by opening the link on the device — Android will open the app directly if App Links are configured. Alternatively, use following command:
   ```shell
   adb shell am start -a android.intent.action.VIEW \
     -d "https://<your-link-domain>/products/42" \
     com.postedapp.posted.example
   ```
   - Status updates and app navigates to `ProductActivity`. Type shows `VERIFIED`.
   - A `ReEngage` event with `source=verified` is logged. A `ViewItem` event is logged in `ProductActivity`.
4) Trigger a custom scheme link:
   ```shell
   adb shell am start -a android.intent.action.VIEW \
     -d "posted-example-app://products/42" \
     com.postedapp.posted.example
   ```
   - Same navigation result but type shows `SCHEME`. A `ReEngage` event with `source=scheme` is logged.
5) For deferred link testing: uninstall the app, copy a posted-sdk link from the Dashboard, then install and launch — the deferred link should resolve on first open and a `ReEngage` event with `source=deferred` is logged.

## Custom scheme links

The `posted-example-app://` scheme is registered in `AndroidManifest.xml`:

```xml
<intent-filter>
  <action android:name="android.intent.action.VIEW" />
  <category android:name="android.intent.category.DEFAULT" />
  <category android:name="android.intent.category.BROWSABLE" />
  <data android:scheme="posted-example-app" />
</intent-filter>
```

The SDK parses the URI as `/<host><path>` — so `posted-example-app://products/42` becomes route `/products/42`, identical to what a Universal Link would produce. Custom scheme links are only processed when `linkProcessingMode = ALL`.

> Replace `posted-example-app` with your app's actual URI scheme. Custom scheme links do not require domain verification, making them useful for testing or internal tools.

## Quick start

1. Configure this app in the Posted Dashboard. You'll need two values:
   - **Package name:** `com.postedapp.posted.example` (from `applicationId` in `build.gradle.kts`)
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

3. Update the Universal Link `intent-filter` in `AndroidManifest.xml` with your app's verified link domain and path prefix from the Dashboard.

4. Build and run:
   ```shell
   ./gradlew :examples:example:installDebug
   ```

5. Trigger test links: **deferred** — copy the link from the Posted Dashboard before a fresh install, then install and launch. **Universal Link** — open the link while the app is running or from cold start. **Custom scheme** — use the ADB command from the test flow above.
