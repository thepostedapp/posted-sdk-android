# Changelog

All notable changes to posted-sdk for Android are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.0.0] — Unreleased (staged)

First public release under the **Posted** brand. This version supersedes the
legacy **Detour** SDK; see [MIGRATION.md](./MIGRATION.md) for the upgrade path.

> **Staged release.** This entry is prepared ahead of publishing. The artifact is
> **not** yet published to Maven Central and no git tag has been pushed — publishing
> is gated on a separate board approval.

### Added
- Canonical **`posted://`** custom URL scheme. Register it on your link-handling
  activity to open the app from `posted://…` links; processed as `LinkType.SCHEME`
  when `linkProcessingMode = ALL`. Works alongside the existing deterministic
  install-referrer flow used for deferred-link attribution.
- `Posted.resolveAppLaunch(...)` facade as the single entry point for launch-time
  attribution, backed by a fire-once attribution cache so a resolved install is
  reported exactly once.

### Changed
- **Rebrand to Posted.** Public types are now `Posted`, `PostedConfig`,
  `PostedDelegate`, `PostedStorage`, `PostedAnalytics`, `PostedEventNames` in the
  `com.postedapp.posted` package. Maven coordinate is `com.postedapp:posted-sdk`.
- Publishing metadata (POM `url`/`scm`, developer contact) updated to the
  `posted-sdk-android` repository and `yuriy@postedapp.com`.
- Public API surface tightened — internal helpers are no longer exported.
- Single-segment universal-link probes are now restricted to the known link domain.

### Deprecated
- Legacy `Detour*` type aliases (`Detour`, `DetourConfig`, `DetourDelegate`,
  `DetourStorage`, `DetourAnalytics`, `DetourEventNames`) and pre-`resolveAppLaunch`
  method shims are retained for one migration cycle and will be removed in the next
  major version. Each carries a `ReplaceWith` for IDE-assisted migration.

### Migration
- `SharedPreferences` keys are migrated automatically from the `Detour_` prefix to
  the `Posted_` prefix on first launch — no consumer action required.

[3.0.0]: https://github.com/thepostedapp/posted-sdk-android/releases/tag/v3.0.0
