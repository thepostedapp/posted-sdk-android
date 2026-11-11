# Changelog

All notable changes to `com.postedapp:posted-sdk` are documented here.

## [4.0.2]

### Added

- The install report includes `installSource`: `store` when Google Play
  installed the app, `development` for debuggable builds and for any other
  installer (sideloads, `adb install`, other app stores). The field is omitted
  if the installer can't be read. No API changes.

## [4.0.1] — Initial public release

Initial public release of `com.postedapp:posted-sdk` for Android.

### Public API

- **`Posted.reportInstall(context, config)`** — reports the install once per
  install, so the backend can match it to the ad click and report the
  conversion. Call it on every launch: it retries on later launches until the
  backend accepts it, never throws, never blocks the caller, and never reads
  the clipboard. Installs include the Play install referrer `click_id` when
  present.
- **`PostedConfig(apiKey, appId, storage?)`** — the only configuration. `storage`
  replaces the default `SharedPreferences` store with any `PostedStorage`.
- **Backend API base**: `attr.postedapp.com`.
- Kotlin and Java callers, minSdk 21.
