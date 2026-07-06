# posted-sdk for Android

posted-sdk is an Android SDK for handling deferred deep links. A deferred link works like a regular deep link, but survives the Play Store install — a user who clicks a link before having the app installed is redirected to the right screen on first launch. posted-sdk also handles App Links and custom scheme links in a single unified API.

## Installation

Add the SDK to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.postedapp:posted-sdk:3.0.0")
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

## Usage

Initialize the SDK and use `PostedDelegate` in your Activity:

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
            appId = "<REPLACE_WITH_APP_ID_FROM_PLATFORM>",
            apiKey = "<REPLACE_WITH_YOUR_API_KEY>"
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
            appId = "<REPLACE_WITH_APP_ID_FROM_PLATFORM>",
            apiKey = "<REPLACE_WITH_YOUR_API_KEY>"
        )
        Posted.initialize(this, config)

        lifecycleScope.launch {
            val result = Posted.processLink(intent)
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
    appId = "<REPLACE_WITH_APP_ID_FROM_PLATFORM>",
    apiKey = "<REPLACE_WITH_YOUR_API_KEY>",
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
    appId = "<REPLACE_WITH_APP_ID_FROM_PLATFORM>",
    apiKey = "<REPLACE_WITH_YOUR_API_KEY>",
    storage = EncryptedStorageProvider(this)
)
```

</details>

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

### Posted

<details>
<summary>Posted singleton</summary>

```kotlin
object Posted {
    /** Initialize SDK — call once in Application.onCreate() or Activity.onCreate(). */
    fun initialize(context: Context, config: PostedConfig)

    /** Process intent and extract deep link (App Link, scheme, or deferred). */
    suspend fun processLink(intent: Intent): LinkResult

    /** Get deferred link only — ignores App Links and scheme links. */
    suspend fun getDeferredLink(): LinkResult

    /** Resolve a short link URL to its full data. */
    suspend fun resolveShortLink(url: String): ShortLinkResponse?
}
```

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

---

## License

This library is licensed under [The MIT License](./LICENSE).
