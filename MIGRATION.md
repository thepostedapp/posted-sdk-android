# Migration guide

## Detour → posted-sdk 3.0.0

posted-sdk 3.0.0 is the first public release under the **Posted** brand. If you
previously integrated the legacy **Detour** Android SDK, follow the steps below.
Most changes are source-compatible for one cycle via deprecated aliases, so you can
upgrade first and rename incrementally.

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

### 4. (Optional) Register the `posted://` scheme

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

### 5. Prefer `resolveAppLaunch(...)`

New integrations should use `Posted.resolveAppLaunch(...)` as the single launch-time
attribution entry point. Older per-source calls are retained as deprecated shims for
one cycle; see their `ReplaceWith` hints.
