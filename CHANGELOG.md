# Changelog

All notable changes to posted-sdk for Android are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [4.0.0] — released

First public release under the **Posted** brand. Published to Maven Central as
`com.postedapp:posted-sdk:4.0.0`.

### Added
- Canonical **`posted://`** custom URL scheme. Register it on your link-handling
  activity to open the app from `posted://…` links; processed as `LinkType.SCHEME`
  when `linkProcessingMode = ALL`. Works alongside the existing deterministic
  install-referrer flow used for deferred-link attribution.
- `Posted.resolveAppLaunch(intent: Intent? = null)`: single unified cold-start
  attribution entry point (replaces `resolveInitialLink`). The platform
  `launchContext` parameter is the standard Android `Intent` — pass the launching
  `Intent` for App Link/scheme handling, or `null`/omit it for a deferred-only cold
  start. Fire-once per install (survives app updates; re-arms on reinstall or
  `resetSession`/`resetSessionAndAwait`). Result type is unchanged (`LinkResult`).

### Changed
- **Attribution-first positioning.** The primary public surface is now
  `Posted.initialize(...)` + `Posted.resolveAppLaunch(...)` (plus optional
  `PostedAnalytics.logEvent`/`logRetention`). The underlying one-shot match-link
  behavior is unchanged — clickId/fingerprint → `{link}` — with no contract or
  backend change.
- **Rebrand to Posted.** Public types are now `Posted`, `PostedConfig`,
  `PostedDelegate`, `PostedStorage`, `PostedAnalytics`, `PostedEventNames` in the
  `com.postedapp.posted` package. Maven coordinate is `com.postedapp:posted-sdk`.
- Publishing metadata (POM `url`/`scm`, developer contact) updated to the
  `posted-sdk-android` repository and `yuriy@postedapp.com`.
- Public API surface tightened — internal helpers are no longer exported.
- Single-segment universal-link probes are now restricted to the known link domain.

### Deprecated
- `resolveInitialLink(intent)` / `resolveInitialLink()` → use
  `resolveAppLaunch(intent)` / `resolveAppLaunch()`.
- `processLink(intent)` → use `resolveAppLaunch(intent)`.
- `getDeferredLink()` → use `resolveAppLaunch()`.
- `resolveShortLink(url)` → internal use only now; use `resolveAppLaunch(intent)`
  for link resolution.
- All of the above are retained for one migration cycle and will be **removed in
  the next major (4.0)**. Each carries a `ReplaceWith` for IDE-assisted migration.

### Removed
- Nothing removed from the public surface in 3.0.0. `processLink`,
  `getDeferredLink`, `resolveShortLink`, and `resolveInitialLink` remain callable
  as deprecated shims (see **Deprecated**) rather than being dropped outright —
  hard removal is deferred to 4.0.

[4.0.0]: https://github.com/thepostedapp/posted-sdk-android/releases/tag/v4.0.0
