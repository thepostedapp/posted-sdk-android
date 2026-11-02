# posted-sdk for Android

`com.postedapp:posted-sdk` reports app installs to Posted. The backend matches each install to the ad click that led to it and reports it back to the ad network as a conversion.

The SDK has one function, `Posted.reportInstall()`. Call it on every app launch. It sends the report once per install.

- [Requirements](#requirements)
- [Get your app ID and API key](#get-your-app-id-and-api-key)
- [1. Install](#1-install)
- [2. Report the install](#2-report-the-install)
- [3. Verify the integration](#3-verify-the-integration)
- [Configuration](#configuration)
- [Data collected](#data-collected)
- [Troubleshooting](#troubleshooting)
- [Support](#support)

## Requirements

- Android 5.0+ (API 21)
- Kotlin or Java. The SDK uses Kotlin coroutines internally; your app doesn't need them.

## Get your app ID and API key

Both come from the Posted dashboard at [app.postedapp.com/dashboard/settings](https://app.postedapp.com/dashboard/settings), under **Posted SDK**.

1. Under **Apps**, click **Add app**, fill in the form and submit it. The dashboard generates the **app ID** (`app_…`) for the new app.
2. Under **API keys**, click **Create key**. Copy the key when it is shown; it is not displayed again.

Pass these as `appId` and `apiKey` in `PostedConfig`. One API key works for all apps in your account.

## 1. Install

The SDK is on Maven Central. In your app module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.postedapp:posted-sdk:1.0.0")
}
```

Or with a version catalog, in `gradle/libs.versions.toml`:

```toml
[libraries]
posted-sdk = { module = "com.postedapp:posted-sdk", version = "1.0.0" }
```

The SDK declares the `INTERNET` permission itself. No other setup is needed.

## 2. Report the install

Call `reportInstall()` once per launch, as early as possible: in `Application.onCreate()`.

```kotlin
import com.postedapp.posted.Posted
import com.postedapp.posted.PostedConfig

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Posted.reportInstall(
            this,
            PostedConfig(
                apiKey = "<YOUR_API_KEY>",
                appId = "<YOUR_APP_ID>",
            ),
        )
    }
}
```

From Java:

```java
Posted.reportInstall(this, new PostedConfig.Builder("<YOUR_API_KEY>", "<YOUR_APP_ID>").build());
```

It returns immediately and runs in the background. It never throws, and it doesn't block app startup. If you need to know when it has finished, for example in a test, use the suspending `Posted.reportInstallAndAwait(context, config)` instead.

## 3. Verify the integration

1. Run the app with a **fresh install**. Uninstalling clears the SDK's storage, so a reinstall reports again.
2. Watch Logcat, filtered by the `PostedApiClient` tag. On success you'll see `Install reported`. On failure, a line starting with `[Posted:INSTALL_ERROR]` (see [Troubleshooting](#troubleshooting)).

## Configuration

```kotlin
Posted.reportInstall(context: Context, config: PostedConfig)
```

| Field     | Type            | Required | Description                                                              |
| --------- | --------------- | -------- | ------------------------------------------------------------------------ |
| `apiKey`  | `String`        | Yes      | API key from the dashboard, **Posted SDK → API keys**.                   |
| `appId`   | `String`        | Yes      | App ID from the dashboard, **Posted SDK → Apps**.                        |
| `storage` | `PostedStorage` | No       | Persistent key-value storage. Defaults to `SharedPreferences`.           |

### Custom storage

Pass any storage that persists across launches, for example MMKV, DataStore or EncryptedSharedPreferences.

```kotlin
interface PostedStorage {
    suspend fun getItem(key: String): String?
    suspend fun setItem(key: String, value: String)
}
```

The SDK stores two keys, both prefixed with `Posted_`: the "install reported" flag and a device ID. If storage is cleared, the install is reported again.

## Data collected

The install report is one `POST` to `attr.postedapp.com/api/install`, sent once per install. The backend uses the fields below to match the install to an earlier ad click on the same device.

| Field                                  | Source                                                  |
| -------------------------------------- | ------------------------------------------------------- |
| `platform`                             | Always `android`                                        |
| `model`, `manufacturer`, `systemVersion` | `android.os.Build`                                    |
| `screenWidth`, `screenHeight`, `scale` | Window metrics in dp and the display density            |
| `locale`                               | `Locale.getDefault()`                                   |
| `timezone`                             | `TimeZone.getDefault().id`                              |
| `userAgent`                            | The `http.agent` system property                        |
| `timestamp`                            | Current time in milliseconds                            |
| `device_id`                            | A random UUID generated once per install                |
| `clickId`                              | `click_id` from the Play install referrer, when present |

The SDK never reads the clipboard and never collects the advertising ID.

## Troubleshooting

All SDK logs use the `Posted` and `PostedApiClient` tags in Logcat.

| Log line                                                   | Meaning                                                                                              |
| ---------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| `Install reported`                                         | Success. Nothing more is sent for this install.                                                      |
| `[Posted:INSTALL_ERROR] Failed to report install: 401`     | Wrong `apiKey` or `appId`. Check the credentials in the dashboard.                                   |
| `[Posted:INSTALL_ERROR] Failed to report install: 4xx/5xx` | The backend rejected the report. The SDK retries on the next launch.                                 |
| `[Posted:INSTALL_ERROR] Error reporting install`           | No network. The SDK retries on the next launch.                                                      |
| `[Posted:INSTALL_ERROR] apiKey or appId is missing`        | `PostedConfig` has a blank credential. Nothing is sent.                                              |
| Nothing logged                                             | The install was already reported on an earlier launch. Uninstall and reinstall to report again.      |

## Example app

See [examples/example](examples/example/README.md) for a minimal project that calls `reportInstall` on launch.

## Support

Found a bug or have a question? [Open an issue](https://github.com/thepostedapp/posted-sdk-android/issues).
