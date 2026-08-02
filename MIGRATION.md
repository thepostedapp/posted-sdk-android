# Migration guide

## Detour → posted-sdk 3.0.0

posted-sdk 3.0.0 is the first public release under the **Posted** brand, and it
repositions the SDK as **attribution-first**: the public surface is now
`Posted.initialize(...)` to configure the SDK and `Posted.resolveAppLaunch(...)` as
the single call that resolves cold-start attribution (App Link, custom scheme, or
deferred install-referrer match), plus optional `PostedAnalytics.logEvent`/
`logRetention` for engagement tracking. The one-shot match-link behavior itself —
clickId/fingerprint → `{link}` — is unchanged; there is no contract or backend
change in this release.

If you previously integrated the legacy **Detour** Android SDK, follow the steps
below. Most changes are source-compatible for one cycle via deprecated aliases and
shims, so you can upgrade first and rename/replace incrementally.

### 1. Update the dependency

```kotlin
// before
implementation("com.detour:detour-sdk:<old-version>")

// after
implementation("com.postedapp:posted-sdk:3.0.0")
```

### 2. Update imports and type names

The package moved from `com.detour.*` to `com.postedapp.posted.*` and every
`Detour*` type was renamed to `Posted*`:

| Legacy (Detour)     | New (Posted)      |
| ------------------- | ----------------- |
| `Detour`            | `Posted`          |
| `DetourConfig`      | `PostedConfig`    |
| `DetourDelegate`    | `PostedDelegate`  |
| `DetourStorage`     | `PostedStorage`   |
| `DetourAnalytics`   | `PostedAnalytics` |
| `DetourEventNames`  | `PostedEventNames`|

Deprecated `typealias` shims for the old names ship in 3.0.0 with `ReplaceWith`
hints, so existing code keeps compiling. They will be **removed in the next major
version** — migrate to the `Posted*` names when convenient (Android Studio's
"Replace with" quick-fix does this automatically).

### 3. Stored data migrates automatically

`SharedPreferences` keys written under the old `Detour_` prefix are migrated to the
new `Posted_` prefix on first launch after upgrade. This is a one-time, automatic
operation — no action required, and first-launch/deferred-attribution state is
preserved across the upgrade.

### 4. Replace `resolveInitialLink(...)` with `resolveAppLaunch(...)`

3.0.0 consolidates every launch-time attribution call into one method,
`Posted.resolveAppLaunch(...)`. `resolveInitialLink(...)` (both the `Intent` and
no-arg overloads) is deprecated in favor of it:

```kotlin
// before
lifecycleScope.launch {
    val result = Posted.resolveInitialLink(intent)
}

// after
lifecycleScope.launch {
    val result = Posted.resolveAppLaunch(intent)
}
```

The unified `launchContext` parameter is, on Android, the standard `Intent` you
already have — pass the Activity's `intent` (from `onCreate`/`onNewIntent`) for App
Link/scheme handling, or `null`/omit it for a deferred-only cold start:

```kotlin
// link-carrying launch
Posted.resolveAppLaunch(intent)

// deferred-only / background cold start
Posted.resolveAppLaunch(null) // or Posted.resolveAppLaunch()
```

`resolveInitialLink` shims are retained for one migration cycle (this release) and
will be **removed in the next major (4.0)** — migrate to `resolveAppLaunch` when
convenient.

### 5. Other deprecated entry points

`processLink(intent)`, `getDeferredLink()`, and `resolveShortLink(url)` are also
deprecated in 3.0.0 in favor of `resolveAppLaunch(...)`:

| Legacy call             | Replacement                                                                             |
| ------------------------ | ---------------------------------------------------------------------------------------- |
| `processLink(intent)`    | `resolveAppLaunch(intent)`                                                              |
| `getDeferredLink()`      | `resolveAppLaunch()`                                                                     |
| `resolveShortLink(url)`  | `resolveAppLaunch(intent)` (short-link resolution now happens internally as part of App Link handling) |

Like `resolveInitialLink`, these are retained as deprecated shims for one migration
cycle and will be **removed in the next major (4.0)**.

### 6. Fire-once semantics

`resolveAppLaunch(...)` resolves attribution **exactly once per install**: the
first call makes the network request and caches the `LinkResult`; every later call
(same session, later app opens, or after an app **update**) returns the cached
result with no network call. The cache re-arms — so the next call resolves fresh
again — only when:

- the app is **reinstalled** (the OS clears app storage), or
- you explicitly call `Posted.resetSession()` / `Posted.resetSessionAndAwait()`.

### 7. (Optional) Register the `posted://` scheme

3.0.0 introduces the canonical `posted://` custom URL scheme. To open your app from
`posted://…` links, add an intent-filter to your link-handling activity:

```xml
<intent-filter>
  <action android:name="android.intent.action.VIEW" />
  <category android:name="android.intent.category.DEFAULT" />
  <category android:name="android.intent.category.BROWSABLE" />
  <data android:scheme="posted" />
</intent-filter>
```

This is optional and independent of deferred-link attribution, which uses the
install referrer and requires no intent-filter.
