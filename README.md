# posted-sdk for Android

posted-sdk is an Android SDK for handling deferred deep links. A deferred link works like a regular deep link, but survives the Play Store install — a user who clicks a link before having the app installed is redirected to the right screen on first launch. posted-sdk also handles App Links and custom scheme links in a single unified API.

## Quick links

- **Getting-Started guide** (cross-platform golden path — create an app, install, verify): [GETTING-STARTED.md](https://github.com/thepostedapp/posted-sdk-admin/blob/main/GETTING-STARTED.md). This README is the full Android reference.
- **API host**: `attr.postedapp.com` (the SDK targets this by default — you do not configure it). Deep links resolve via `l.postedapp.com`.
- **Troubleshooting**: [TROUBLESHOOTING.md](https://github.com/thepostedapp/posted-sdk-admin/blob/main/TROUBLESHOOTING.md)
- **Backend contract redoc**: not yet live <!-- TODO(POS-589): link once the contract redoc is hosted — Firebase Hosting activation is gated on the POS-312 D1/D2 DNS decision -->
- **Support**: see [Support](#support) below

## Create account

You need a posted-sdk account to generate app credentials (`app_id` + API key) and configure your links. Follow [**Create an app & find your credentials**](https://github.com/thepostedapp/posted-sdk-admin/blob/main/README.md#create-an-app--find-your-credentials) in the dashboard README — it covers the full create-app flow and where each credential appears afterward.

## Install

Add the SDK to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.postedapp:posted-sdk:4.0.0")
}
```

### Requirements

- Minimum SDK: Android 5.0 (API 21)
- Target SDK: Android 14 (API 34)
- Kotlin 2.0+

### Permissions

The following permissions are included automatically:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## Quickstart

The fastest way to get attribution working: initialize the SDK and call `resolveAppLaunch()`. For automatic intent/lifecycle wiring (recommended for App Links and custom scheme links), use `PostedDelegate` instead — see [Deep-link handling](#deep-link-handling) for the full example.

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = PostedConfig(
            appId = "<YOUR_APP_ID>",
            apiKey = "<YOUR_API_KEY>"
        )
        Posted.initialize(this, config)

        lifecycleScope.launch {
            val result = Posted.resolveAppLaunch(intent)
            // handle result — see resolveAppLaunch below
        }
    }
}
```

See [resolveAppLaunch](#resolveapplaunch) below for fire-once semantics and `resetSession()`.

## Configure

Beyond `appId` and `apiKey`, `PostedConfig` accepts a few optional knobs:

- **`linkProcessingMode`** — controls which link sources the SDK processes (default `ALL`). See [Deep-link handling](#controlling-which-links-posted-sdk-processes) for the full behavior table.
- **`storage`** — override the default persistent storage (`SharedPreferences`) with your own `PostedStorage` implementation.

See [Types → PostedConfig](#postedconfig) for the full type definition.

### Custom storage

The SDK uses `SharedPreferences` by default. You can provide a custom storage implementation via `PostedConfig`:

<details>
<summary>Custom storage example</summary>

```kotlin
import com.postedapp.posted.storage.PostedStorage

class EncryptedStorageProvider(context: Context) : PostedStorage {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "PostedSecure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override suspend fun getItem(key: String): String? =
        withContext(Dispatchers.IO) { prefs.getString(key, null) }

    override suspend fun setItem(key: String, value: String) {
        withContext(Dispatchers.IO) { prefs.edit().putString(key, value).apply() }
    }
}

val config = PostedConfig(
    appId = "<YOUR_APP_ID>",
    apiKey = "<YOUR_API_KEY>",
    storage = EncryptedStorageProvider(this)
)
```

</details>

## resolveAppLaunch

`Posted.resolveAppLaunch()` is **fire-once per install**: the first call makes one network request and caches the `LinkResult`. Every later call — including across app restarts, process death, and app updates — returns the cached result with no network call. This matches the fire-once contract of the other Posted SDKs.

The cache re-arms (a fresh resolve happens again) in two cases:

- **Reinstall** — the OS clears app storage, so there's nothing cached to return.
- **Explicit `resetSession()`** — call this when you want the next `resolveAppLaunch()` to re-resolve, e.g. in test harnesses, QA re-installs, or a user-facing "log out / switch account" flow.

```kotlin
Posted.resetSession()
```

`resetSession()` is synchronous, non-blocking, and safe to call from the main/UI thread — it only flips a volatile flag and never touches storage or `runBlocking` on the calling thread. The actual cache clear happens lazily, inside the next `resolveAppLaunch()` call, under the same lock that guards resolution — so there's no race between a reset and an in-flight resolve. Concretely: after `resetSession()` returns, the *next* call to `resolveAppLaunch()` clears the stale cached result and performs a fresh network resolution; the on-disk record is inert until that next call, since every read path checks the pending-reset flag first.

If you need the cache to be physically cleared before your code proceeds (rather than just re-armed for the next call), use the suspend variant instead:

```kotlin
lifecycleScope.launch {
    Posted.resetSessionAndAwait() // clears the cache before returning
    val result = Posted.resolveAppLaunch(intent) // always re-resolves
}
```

## Deep-link handling

Initialize the SDK and use `PostedDelegate` in your Activity for automatic intent/lifecycle wiring:

<details>
<summary>Usage example</summary>

```kotlin
import android.util.Log
import com.postedapp.posted.Posted
import com.postedapp.posted.PostedConfig
import com.postedapp.posted.PostedDelegate
import com.postedapp.posted.models.LinkResult
import com.postedapp.posted.models.LinkType

class MainActivity : AppCompatActivity() {

    private val postedDelegate = PostedDelegate(
        lifecycleOwner = this,
        config = PostedConfig(
            appId = "<YOUR_APP_ID>",
            apiKey = "<YOUR_API_KEY>"
        ),
        onLinkResult = { result -> handleLinkResult(result) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Posted.initialize(this, postedDelegate.config)
        postedDelegate.onCreate(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        postedDelegate.onNewIntent(intent)
    }

    private fun handleLinkResult(result: LinkResult) {
        when (result) {
            is LinkResult.Success -> {
                val url = result.url            // Full matched URL
                val route = result.route        // Extracted route for navigation
                val pathname = result.pathname  // Route path without query string
                val params = result.params      // Parsed query parameters
                val type = result.type          // DEFERRED, VERIFIED, or SCHEME

                // navigate to route
            }
            is LinkResult.NotFirstLaunch -> { /* Already processed on a previous launch */ }
            is LinkResult.NoLink -> { /* Normal app launch without any deep link */ }
            is LinkResult.Error -> {
                Log.e("MainActivity", "Error processing link", result.exception)
            }
        }
    }
}
```

</details>

### Manual control without PostedDelegate

<details>
<summary>Manual control example</summary>

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = PostedConfig(
            appId = "<YOUR_APP_ID>",
            apiKey = "<YOUR_API_KEY>"
        )
        Posted.initialize(this, config)

        lifecycleScope.launch {
            val result = Posted.resolveAppLaunch(intent)
            // handle result
        }
    }
}
```

</details>

### Controlling which links posted-sdk processes

Use `linkProcessingMode` to control which link sources the SDK handles:

| Value           | App Links | Deferred links | Custom scheme links |
| --------------- | --------- | -------------- | ------------------- |
| `ALL` (default) | ✅        | ✅             | ✅                  |
| `WEB_ONLY`      | ✅        | ✅             | ❌                  |
| `DEFERRED_ONLY` | ❌        | ✅             | ❌                  |

<details>
<summary>linkProcessingMode config example</summary>

```kotlin
val config = PostedConfig(
    appId = "<YOUR_APP_ID>",
    apiKey = "<YOUR_API_KEY>",
    linkProcessingMode = LinkProcessingMode.WEB_ONLY
)
```

</details>

Use `DEFERRED_ONLY` when your navigation framework already handles App Links and scheme links — this prevents double-processing.

### The `posted://` scheme

`posted://` is the SDK's canonical custom URL scheme. Register it on the activity that hosts your `PostedDelegate` so that `posted://…` links open your app and are resolved by the SDK, alongside the deterministic install-referrer flow used for deferred links:

```xml
<intent-filter>
  <action android:name="android.intent.action.VIEW" />
  <category android:name="android.intent.category.DEFAULT" />
  <category android:name="android.intent.category.BROWSABLE" />
  <data android:scheme="posted" />
</intent-filter>
```

`posted://` links are processed like any other custom scheme link (`LinkType.SCHEME`) and require `linkProcessingMode = ALL`. You can register your own app-specific scheme in the same manifest in addition to `posted://`. Registering `posted://` does **not** affect deferred-link attribution, which relies on the install referrer and works with no intent-filter at all.

### Continuous deep-link stream (cross-platform surface)

The **continuous deep-link stream** delivers every link the app receives while it is running (warm links). It is a **separate feature from fire-once install attribution**: `resolveAppLaunch` resolves the single deferred/deep link responsible for the install exactly once (cached thereafter), whereas the continuous stream is unrelated to attribution and emits on every warm link for the life of the process. Use `resolveAppLaunch` for attribution; use the stream (or the native warm-link path below) for ongoing in-session navigation.

The stream is an idiomatic convenience exposed **only on the JS/Dart wrappers**, which already sit on top of a framework-level URL event bus (React Native `Linking`, Flutter `EventChannel`). Native iOS/Android apps deliver warm links through platform lifecycle hooks instead — this divergence is **intentional, not a gap**.

| Platform | Continuous stream API | Status | Warm-link path |
|----------|----------------------|--------|----------------|
| Flutter (`PostedSdk`) | `Stream<PostedResult> get linkStream` | ✅ present, non-deprecated (EventChannel-backed) | via `linkStream` |
| React Native (`Posted`) | `addEventListener("url", listener) → PostedUrlSubscription` | ✅ present, non-deprecated (mirrors RN core `Linking`) | via `addEventListener` |
| Android (`Posted`) | — none (intentional) — | ➖ not offered | `PostedDelegate.onNewIntent(intent)` → `resolveAppLaunch()` |
| iOS (`Posted`) | — none (intentional) — | ➖ not offered | Scene/App URL handling (`scene(_:willConnectTo:)`, `openURL`, `continueUserActivity`) → `resolveAppLaunch()` |

**Fire-once vs continuous** — `resolveAppLaunch` fires once per install and returns the attribution result; the continuous stream never participates in attribution and fires on every warm link.

## Analytics

The SDK includes a built-in analytics module. `Posted.initialize()` automatically tracks app opens for retention. You can also log custom events using the predefined `PostedEventNames` enum:

<details>
<summary>Analytics example</summary>

```kotlin
import com.postedapp.posted.analytics.PostedAnalytics
import com.postedapp.posted.analytics.PostedEventNames

PostedAnalytics.logEvent(PostedEventNames.Purchase)
PostedAnalytics.logRetention("week_1")
```

</details>

## Types

### PostedConfig

<details>
<summary>PostedConfig</summary>

```kotlin
data class PostedConfig(
    /** Your API key from the Posted dashboard. */
    val apiKey: String,

    /** Your application ID from the Posted dashboard. */
    val appId: String,

    /**
     * Controls which link sources are handled by the SDK (default: ALL).
     * - ALL: deferred + App Links + custom scheme links
     * - WEB_ONLY: deferred + App Links, no custom scheme links
     * - DEFERRED_ONLY: only deferred links (no intent processing)
     */
    val linkProcessingMode: LinkProcessingMode = LinkProcessingMode.ALL,

    /** Custom storage implementation (defaults to SharedPreferences). */
    val storage: PostedStorage? = null
)
```

</details>

### LinkResult

<details>
<summary>LinkResult</summary>

```kotlin
sealed class LinkResult {
    data class Success(
        val url: String,                    // Full URL that was matched
        val route: String,                  // Extracted route for navigation
        val pathname: String,               // Route path without query string
        val type: LinkType,                 // DEFERRED, VERIFIED, or SCHEME
        val params: Map<String, String>     // Parsed query parameters
    ) : LinkResult()

    data object NotFirstLaunch : LinkResult()               // Not first launch
    data object NoLink : LinkResult()                       // Normal app launch
    data class Error(val exception: Exception) : LinkResult()
}
```

</details>

### LinkType

<details>
<summary>LinkType</summary>

```kotlin
enum class LinkType {
    DEFERRED,   // User clicked link before app was installed
    VERIFIED,   // App Link — http/https link with verified domain ownership
    SCHEME      // Custom scheme deep link (e.g. myapp://...)
}
```

</details>

### LinkProcessingMode

<details>
<summary>LinkProcessingMode</summary>

```kotlin
enum class LinkProcessingMode {
    ALL,            // Deferred links + App Links + custom scheme links (default)
    WEB_ONLY,       // Deferred links + App Links, no custom scheme links
    DEFERRED_ONLY   // Deferred links only — no intent processing
}
```

</details>

### PostedStorage

<details>
<summary>PostedStorage</summary>

```kotlin
interface PostedStorage {
    suspend fun getItem(key: String): String?
    suspend fun setItem(key: String, value: String)
    suspend fun removeItem(key: String) { /* optional */ }
}
```

</details>

## API Reference

Full KDoc reference is auto-generated from source via [Dokka](https://github.com/Kotlin/dokka) and cannot drift from the code. CI runs `./gradlew dokkaHtml` on every PR to `main` and fails the build if generation errors. Generate it locally with:

```bash
./gradlew :posted-sdk:dokkaHtml
# output: posted-sdk/build/dokka/html/index.html
```

Hosting the generated reference publicly is tracked separately (POS-590); this repo builds and links it, it does not publish it.

### Posted

<details>
<summary>Posted singleton</summary>

```kotlin
object Posted {
    /** Initialize SDK — call once in Application.onCreate() or Activity.onCreate(). */
    fun initialize(context: Context, config: PostedConfig)

    /**
     * Resolve attribution for this app launch (App Link, scheme, or deferred).
     * Fire-once per install — see "resolveAppLaunch" above.
     */
    suspend fun resolveAppLaunch(intent: Intent? = null): LinkResult

    /** Re-arm the fire-once gate so the next resolveAppLaunch() re-resolves. Main-thread safe. */
    fun resetSession()

    /** Suspend variant of resetSession() that awaits the cache clear before returning. */
    suspend fun resetSessionAndAwait()

    /** Resolve a short link URL to its full data. */
    suspend fun resolveShortLink(url: String): ShortLinkResponse?
}
```

`processLink(intent)`, `getDeferredLink()`, `resolveInitialLink(...)` are deprecated aliases for `resolveAppLaunch(...)` — see [Migration](#migration) below.

</details>

### PostedDelegate

<details>
<summary>PostedDelegate</summary>

```kotlin
class PostedDelegate(
    private val lifecycleOwner: LifecycleOwner,
    val config: PostedConfig,
    private val onLinkResult: (LinkResult) -> Unit
) {
    fun onCreate(intent: Intent)
    fun onNewIntent(intent: Intent)
}
```

</details>

### PostedAnalytics

<details>
<summary>PostedAnalytics</summary>

```kotlin
object PostedAnalytics {
    /** Log a predefined analytics event with optional data. */
    fun logEvent(eventName: PostedEventNames, data: Any? = null)

    /** Log a retention event. */
    fun logRetention(eventName: String)
}
```

</details>

## Errors/Troubleshooting

See the canonical [Troubleshooting & error-code reference](https://github.com/thepostedapp/posted-sdk-admin/blob/main/TROUBLESHOOTING.md) — 401s, no-match, deferred deep links not firing, iOS clipboard / Android install-referrer edge cases, and key rotation, each with cause → how to detect → fix.

## Migration

The imperative link-resolution methods are consolidated into a single attribution-first call. `processLink(intent)`, `getDeferredLink()`, and `resolveInitialLink(...)` are `@Deprecated` aliases that forward to `resolveAppLaunch(...)`, each carrying a `ReplaceWith` for IDE-assisted migration. `resolveShortLink(url)` is deprecated for external use — it is now internal-only; use `resolveAppLaunch(intent)` for link resolution.

| Deprecated method | Use instead |
| --- | --- |
| `resolveInitialLink(intent)` / `resolveInitialLink()` | `resolveAppLaunch(intent)` / `resolveAppLaunch()` |
| `processLink(intent)` | `resolveAppLaunch(intent)` |
| `getDeferredLink()` | `resolveAppLaunch()` |
| `resolveShortLink(url)` | `resolveAppLaunch(intent)` (internal use only now) |

All deprecated aliases are retained for one migration cycle and will be **removed in the next major**. See [CHANGELOG.md](./CHANGELOG.md) for the full version history.

## Versioning/compatibility

- **SDK version**: `4.0.0` (aligned across iOS/Android/React Native/Flutter)
- **Backend contract**: v1.1.0, served from `attr.postedapp.com`
- **Min Android**: minSdk 21 (Android 5.0) · compileSdk 34 (Android 14) · Kotlin 2.0+
- **Cross-platform compatibility matrix**: [COMPATIBILITY.md](https://github.com/thepostedapp/posted-sdk-admin/blob/main/COMPATIBILITY.md)

## Example

Two example apps with the SDK integrated live in `examples/`:

| Example | Description |
| --- | --- |
| [`examples/example`](./examples/example) | Full integration with `PostedDelegate` and `LinkProcessingMode.ALL` — App Links, custom scheme links, deferred deep links, custom encrypted storage, and analytics. |
| [`examples/example-deferred-only`](./examples/example-deferred-only) | `LinkProcessingMode.DEFERRED_ONLY` with a splash-screen pattern for seamless deferred deep link UX — for apps where a navigation framework already handles App Links and scheme links. |

Open the repo root in Android Studio and run either `:examples:example` or `:examples:example-deferred-only` from the run configuration dropdown, or from the command line:

```shell
./gradlew :examples:example:installDebug
./gradlew :examples:example-deferred-only:installDebug
```

## Support

- Found a bug or have a question specific to this SDK? [Open an issue](https://github.com/thepostedapp/posted-sdk-android/issues) on this repo.

### Other posted-sdk SDKs

posted-sdk is also available for other app stacks:

- iOS SDK: [https://github.com/thepostedapp/posted-sdk-ios](https://github.com/thepostedapp/posted-sdk-ios)
- React Native SDK: [https://github.com/thepostedapp/posted-sdk-react-native](https://github.com/thepostedapp/posted-sdk-react-native)
- Flutter SDK: [https://github.com/thepostedapp/posted-sdk-flutter](https://github.com/thepostedapp/posted-sdk-flutter)
