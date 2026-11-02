# posted-sdk for Android — Example

A minimal app that calls `Posted.reportInstall()` in `Application.onCreate()` and shows when the report for this launch has finished.

## Setup

1. Create an app and an API key at [app.postedapp.com/dashboard/settings](https://app.postedapp.com/dashboard/settings) under **Posted SDK** (see the main README's [Get your app ID and API key](../../README.md#get-your-app-id-and-api-key)).
2. Copy `secrets.properties.example` to `secrets.properties` in this folder and fill in the two values:

   | Key              | Where to find it                                          |
   | ---------------- | --------------------------------------------------------- |
   | `POSTED_API_KEY` | Dashboard → Settings → Posted SDK → API keys → Create key |
   | `POSTED_APP_ID`  | Dashboard → Settings → Posted SDK → Apps → Add app        |

3. Open the repository root in Android Studio, select the `examples.example` run configuration, and run it on a device or emulator.

## What to expect

On the first launch the SDK posts the install report. Filter Logcat by the `PostedApiClient` tag and look for `Install reported`. Later launches send nothing. Uninstall and run again to report a fresh install.
