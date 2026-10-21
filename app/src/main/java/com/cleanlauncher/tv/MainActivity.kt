package com.cleanlauncher.tv

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

data class AppEntry(
    val pkg: String,
    val label: String,
    val image: ImageBitmap,
    val isBanner: Boolean
)

/** One title from Stremio's public Cinemeta catalog. */
data class Meta(
    val id: String,
    val name: String,
    val poster: String,
    val type: String, // "movie" or "series"
    val background: String = "",
    val year: String = "",
    val rating: String = "",
    val description: String = ""
)

class Actions(
    val launch: (String) -> Unit,
    val info: (String) -> Unit,
    val uninstall: (String) -> Unit,
    val settings: () -> Unit,
    val openMeta: (Meta) -> Unit
)

/** Built-in backgrounds. Press the Background button to cycle through them. */
data class BgTheme(
    val name: String,
    val base: List<Color>,
    val glowA: Color,
    val glowAPos: Offset,
    val glowB: Color,
    val glowBPos: Offset
)

val Backgrounds = listOf(
    BgTheme(
        "Midnight",
        listOf(Color(0xFF0B0F1A), Color(0xFF05070C)),
        Color(0xFF3A5BFF), Offset(1600f, 150f),
        Color(0xFF6A2BFF), Offset(200f, 1000f)
    ),
    BgTheme(
        "Aurora",
        listOf(Color(0xFF07131A), Color(0xFF030A0F)),
        Color(0xFF16D9A0), Offset(1500f, 100f),
        Color(0xFF2B7BFF), Offset(300f, 950f)
    ),
    BgTheme(
        "Dusk",
        listOf(Color(0xFF160C1F), Color(0xFF07040B)),
        Color(0xFFFF5E8A), Offset(1650f, 200f),
        Color(0xFF7B3DFF), Offset(250f, 1000f)
    ),
    BgTheme(
        "Ember",
        listOf(Color(0xFF1A0D08), Color(0xFF0A0504)),
        Color(0xFFFF7A29), Offset(1550f, 120f),
        Color(0xFFFF3D4A), Offset(200f, 1050f)
    ),
    BgTheme(
        "Ocean",
        listOf(Color(0xFF071724), Color(0xFF030A12)),
        Color(0xFF1FA2FF), Offset(1400f, 200f),
        Color(0xFF12D8FA), Offset(300f, 1000f)
    ),
    BgTheme(
        "Forest",
        listOf(Color(0xFF08140D), Color(0xFF040A06)),
        Color(0xFF34D17A), Offset(1500f, 150f),
        Color(0xFF9BE15D), Offset(250f, 1000f)
    ),
    BgTheme(
        "Slate",
        listOf(Color(0xFF14171F), Color(0xFF090B10)),
        Color(0xFF8A94A8), Offset(1600f, 120f),
        Color(0xFF4A5568), Offset(250f, 1000f)
    ),
    BgTheme(
        "Sunrise",
        listOf(Color(0xFF1D1016), Color(0xFF0B0609)),
        Color(0xFFFFB347), Offset(1500f, 180f),
        Color(0xFFFF5E62), Offset(300f, 1000f)
    )
)

/** Names the home row is filled with the first time each one is found on the device. */
private val DefaultAppKeywords = listOf("stremio", "netflix", "hulu", "streamq", "youtube")

/** Persistent favorites, hidden apps, background and view settings. */
class Store(private val sp: SharedPreferences) {
    var pinned by mutableStateOf(
        (sp.getString("pinned", "") ?: "").split(",").filter { it.isNotBlank() }
    )
        private set

    var hidden by mutableStateOf(
        (sp.getStringSet("hidden", emptySet()) ?: emptySet()).toSet()
    )
        private set

    var bgIndex by mutableStateOf(Math.floorMod(sp.getInt("bg", 0), Backgrounds.size))
        private set

    var showAllApps by mutableStateOf(sp.getBoolean("showAll", false))
        private set

    var showHidden by mutableStateOf(false)
    var cinematicMode by mutableStateOf(sp.getBoolean("cinematic", true))
        private set
    fun toggleCinematic() {
        cinematicMode = !cinematicMode
        sp.edit().putBoolean("cinematic", cinematicMode).apply()
    }
    fun movePin(pkg: String, direction: Int) {
        val index = pinned.indexOf(pkg)
        val target = index + direction
        if (index < 0 || target !in pinned.indices) return
        val ordered = pinned.toMutableList()
        val item = ordered.removeAt(index)
        ordered.add(target, item)
        pinned = ordered
        sp.edit().putString("pinned", pinned.joinToString(",")).apply()
    }

    fun togglePin(pkg: String) {
        pinned = if (pkg in pinned) pinned - pkg else pinned + pkg
        sp.edit().putString("pinned", pinned.joinToString(",")).apply()
    }

    fun toggleHide(pkg: String) {
        hidden = if (pkg in hidden) hidden - pkg else hidden + pkg
        sp.edit().putStringSet("hidden", HashSet(hidden)).apply()
    }

    fun nextBackground() {
        bgIndex = (bgIndex + 1) % Backgrounds.size
        sp.edit().putInt("bg", bgIndex).apply()
    }

    fun toggleAllApps() = setAllApps(!showAllApps)

    fun setAllApps(enabled: Boolean) {
        showAllApps = enabled
        sp.edit().putBoolean("showAll", enabled).apply()
    }

    /**
     * Pins Stremio, Netflix, Hulu, StreamQ and YouTube the first time each one is found.
     * A keyword is only handled once, so if you remove an app from home it stays removed.
     */
    fun seedDefaults(apps: List<AppEntry>) {
        if (apps.isEmpty()) return
        // Seed a useful initial home even on TVs without the five suggested apps.
        if (!sp.getBoolean("firstRunSeeded", false)) {
            val preferred = DefaultAppKeywords.mapNotNull { keyword ->
                apps.firstOrNull { it.label.contains(keyword, ignoreCase = true) ||
                    it.pkg.contains(keyword, ignoreCase = true) }
            }
            // Only your five apps go on home. Fall back to other apps only if none are installed.
            val initial = if (preferred.isNotEmpty()) {
                preferred.distinctBy { it.pkg }.map { it.pkg }
            } else {
                apps.filter { it.pkg !in hidden }.take(6).map { it.pkg }
            }
            if (pinned.isEmpty()) pinned = initial
            sp.edit().putString("pinned", pinned.joinToString(","))
                .putBoolean("firstRunSeeded", true).apply()
        }
        val done = (sp.getStringSet("seededKw", emptySet()) ?: emptySet()).toMutableSet()
        var newPinned = pinned
        var changed = false
        for (kw in DefaultAppKeywords) {
            if (kw in done) continue
            val match = apps
                .filter { it.label.contains(kw, ignoreCase = true) || it.pkg.contains(kw, ignoreCase = true) }
                .minByOrNull { it.label.length } ?: continue
            if (match.pkg !in newPinned) newPinned = newPinned + match.pkg
            done.add(kw)
            changed = true
        }
        if (changed) {
            pinned = newPinned
            sp.edit()
                .putString("pinned", pinned.joinToString(","))
                .putStringSet("seededKw", HashSet(done))
                .apply()
        }
    }
}

fun loadApps(pm: PackageManager, selfPkg: String): List<AppEntry> {
    val leanback = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    val normal = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val infos = pm.queryIntentActivities(leanback, 0) + pm.queryIntentActivities(normal, 0)

    return infos
        .filter { it.activityInfo.packageName != selfPkg }
        .distinctBy { it.activityInfo.packageName }
        .mapNotNull { ri ->
            runCatching {
                val banner = ri.activityInfo.loadBanner(pm)
                val drawable = banner ?: ri.loadIcon(pm)
                val bmp = if (banner != null) drawable.toBitmap(320, 180) else drawable.toBitmap(160, 160)
                AppEntry(
                    pkg = ri.activityInfo.packageName,
                    label = ri.loadLabel(pm).toString(),
                    image = bmp.asImageBitmap(),
                    isBanner = banner != null
                )
            }.getOrNull()
        }
        .sortedBy { it.label.lowercase(Locale.getDefault()) }
}

/** Stremio's public Cinemeta catalogs. No account or login needed. */
object Cinemeta {
    private const val BASE = "https://v3-cinemeta.strem.io/catalog"

    fun fetchRaw(type: String): String? = runCatching {
        val conn = (URL("$BASE/$type/top.json").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    fun parse(raw: String, type: String): List<Meta> = runCatching {
        val arr = JSONObject(raw).getJSONArray("metas")
        (0 until minOf(arr.length(), 24)).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val poster = o.optString("poster").replace("http://", "https://")
            val id = o.optString("id")
            if (poster.isBlank() || id.isBlank()) null
            else Meta(
                id = id,
                name = o.optString("name"),
                poster = poster,
                type = type,
                background = o.optString("background").replace("http://", "https://"),
                year = o.optString("releaseInfo").ifBlank { o.optString("year") },
                rating = o.optString("imdbRating"),
                description = o.optString("description")
            )
        }
    }.getOrDefault(emptyList())
}

// ---------------------------------------------------------------------------
// Activity
// ---------------------------------------------------------------------------

class MainActivity : ComponentActivity() {

    private val apps = mutableStateOf<List<AppEntry>>(emptyList())
    private val movies = mutableStateOf<List<Meta>>(emptyList())
    private val series = mutableStateOf<List<Meta>>(emptyList())
    private lateinit var store: Store
    private lateinit var prefs: SharedPreferences
    @Volatile private var catalogRefreshInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("launcher", MODE_PRIVATE)
        store = Store(prefs)

        val actions = Actions(
            launch = ::launchApp,
            info = ::openAppInfo,
            uninstall = ::uninstallApp,
            settings = ::openSystemSettings,
            openMeta = ::openMeta
        )

        setContent {
            // Back exits the app drawer or closes the hidden-app view, never the home screen.
            BackHandler(enabled = true) {
                when {
                    store.showAllApps -> store.setAllApps(false)
                    store.showHidden -> store.showHidden = false
                }
            }
            LauncherScreen(
                apps = apps.value,
                movies = movies.value,
                series = series.value,
                store = store,
                actions = actions
            )
        }
    }

    override fun onResume() {
        super.onResume()
        reloadApps()
        loadCatalogs()
    }

    private fun reloadApps() {
        Thread {
            val list = loadApps(packageManager, packageName)
            runOnUiThread {
                // Only swap the list when the set of apps changed, so focus isn't reset.
                if (list.map { it.pkg } != apps.value.map { it.pkg }) {
                    apps.value = list
                }
                store.seedDefaults(list)
            }
        }.start()
    }

    /** Shows the saved catalog right away, then refreshes it if it is older than 6 hours. */
    private fun loadCatalogs() {
        // Resume can be called frequently when launching other TV applications.
        // Prevent duplicate refresh workers and duplicate network requests.
        if (catalogRefreshInProgress) return
        catalogRefreshInProgress = true
        Thread {
          try {
            for (type in listOf("movie", "series")) {
                val target = if (type == "movie") movies else series
                val cached = prefs.getString("cat_$type", null)
                val cachedAt = prefs.getLong("catT_$type", 0L)

                if (cached != null && target.value.isEmpty()) {
                    val parsed = Cinemeta.parse(cached, type)
                    runOnUiThread { target.value = parsed }
                }

                val stale = System.currentTimeMillis() - cachedAt > 6 * 60 * 60 * 1000L
                if (cached == null || stale) {
                    val raw = Cinemeta.fetchRaw(type)
                    if (raw != null) {
                        val parsed = Cinemeta.parse(raw, type)
                        if (parsed.isNotEmpty()) {
                            prefs.edit()
                                .putString("cat_$type", raw)
                                .putLong("catT_$type", System.currentTimeMillis())
                                .apply()
                            runOnUiThread { target.value = parsed }
                        }
                    }
                }
            }
          } finally {
            catalogRefreshInProgress = false
          }
        }.start()
    }

    private fun launchApp(pkg: String) {
        runCatching {
            val intent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
                ?: packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }
    }

    /** Tries to open the title inside Stremio; falls back to just opening Stremio. */
    private fun openMeta(m: Meta) {
        val link = if (m.type == "movie") {
            "stremio:///detail/movie/${m.id}/${m.id}"
        } else {
            "stremio:///detail/series/${m.id}"
        }
        val opened = runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)

        if (!opened) {
            val pkg = apps.value.firstOrNull {
                it.label.contains("stremio", ignoreCase = true) || it.pkg.contains("stremio", ignoreCase = true)
            }?.pkg
            if (pkg != null) launchApp(pkg)
        }
    }

    private fun openAppInfo(pkg: String) {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun uninstallApp(pkg: String) {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun openSystemSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

// ---------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------

private val Card = Color(0xCC161B28)
private val Accent = Color(0xFF5B8CFF)
private val TextDim = Color(0xFFB8C0D0)

@Composable
fun BackgroundLayer(theme: BgTheme) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(theme.base))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(theme.glowA.copy(alpha = 0.32f), Color.Transparent),
                        center = theme.glowAPos,
                        radius = 1200f
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(theme.glowB.copy(alpha = 0.22f), Color.Transparent),
                        center = theme.glowBPos,
                        radius = 1100f
                    )
                )
        )
    }
}

/** Alternates movies and series that have landscape art, for the big featured banner. */
private fun buildFeatured(movies: List<Meta>, series: List<Meta>): List<Meta> {
    val m = movies.filter { it.background.isNotBlank() }
    val s = series.filter { it.background.isNotBlank() }
    val out = ArrayList<Meta>()
    var i = 0
    while (out.size < 10 && (i < m.size || i < s.size)) {
        if (i < m.size) out.add(m[i])
        if (i < s.size && out.size < 10) out.add(s[i])
        i++
    }
    return out
}

@Composable
fun LauncherScreen(
    apps: List<AppEntry>,
    movies: List<Meta>,
    series: List<Meta>,
    store: Store,
    actions: Actions
) {
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    var showOptions by remember { mutableStateOf(false) }
    var activeFeature by remember { mutableStateOf(0) }
    val scroll = rememberScrollState()
    val firstApp = remember { FocusRequester() }

    val visible = apps.filter { store.showHidden || it.pkg !in store.hidden }
    val favorites = store.pinned.mapNotNull { p -> visible.find { it.pkg == p } }
    val theme = Backgrounds[store.bgIndex % Backgrounds.size]

    val featured = remember(movies, series) { buildFeatured(movies, series) }
    val hero = if (store.cinematicMode && featured.isNotEmpty()) {
        featured[activeFeature % featured.size]
    } else null

    // The featured banner rotates by itself every 12 seconds; pressing Next restarts the timer.
    LaunchedEffect(featured.size, activeFeature, store.cinematicMode) {
        if (store.cinematicMode && featured.size > 1) {
            delay(12_000)
            activeFeature = (activeFeature + 1) % featured.size
        }
    }

    // Start with focus on the first app so the remote works immediately.
    LaunchedEffect(favorites.isNotEmpty(), store.showAllApps) {
        if (favorites.isNotEmpty() && !store.showAllApps) {
            delay(200)
            runCatching { firstApp.requestFocus() }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenH = maxHeight

        Crossfade(
            targetState = store.bgIndex % Backgrounds.size,
            animationSpec = tween(700),
            label = "background"
        ) { i -> BackgroundLayer(Backgrounds[i]) }

        // Full-width cinematic backdrop that fades out as you scroll down.
        if (hero != null && !store.showAllApps) {
            val scrim = theme.base.last()
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(0.74f)
                    .height(screenH * 0.8f)
                    .alpha((1f - scroll.value / 450f).coerceIn(0f, 1f))
            ) {
                Crossfade(
                    targetState = hero,
                    animationSpec = tween(900),
                    label = "backdrop"
                ) { m ->
                    AsyncImage(
                        model = m.background,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to scrim,
                                0.5f to scrim.copy(alpha = 0.45f),
                                1f to Color.Transparent
                            )
                        )
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.5f to Color.Transparent,
                                1f to scrim
                            )
                        )
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 28.dp)
        ) {
            // Top bar: clock on the left, two buttons on the right
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val now by produceState(Date()) {
                        while (true) {
                            value = Date()
                            delay(10_000)
                        }
                    }
                    Text(
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(now),
                        color = Color.White,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Light,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now),
                        color = TextDim,
                        fontSize = 15.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Pill(if (store.showAllApps) "Back to home" else "All apps") { store.toggleAllApps() }
                Spacer(Modifier.width(10.dp))
                Pill("Options") { showOptions = true }
            }

            Spacer(Modifier.height(16.dp))

            if (store.showAllApps) {
                SectionLabel("All apps  (hold OK on an app to add it to home)")
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 170.dp),
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(visible, key = { it.pkg }) { app ->
                        AppTile(
                            app = app,
                            dim = app.pkg in store.hidden,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { actions.launch(app.pkg) },
                            onMenu = { menuFor = app }
                        )
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scroll)
                ) {
                    // Featured title sits above your apps so both fit on the first screen.
                    if (hero != null) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(maxOf(screenH - 300.dp, 220.dp))
                        ) {
                            HeroText(
                                meta = hero,
                                modifier = Modifier.align(Alignment.BottomStart),
                                onOpen = { actions.openMeta(hero) },
                                onNext = { activeFeature = (activeFeature + 1) % featured.size }
                            )
                        }
                    }

                    SectionLabel("YOUR APPS")
                    if (favorites.isEmpty()) {
                        Text(
                            "Nothing here yet. Open All apps and hold OK on an app to add it to home.",
                            color = TextDim,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 16.dp)
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
                        ) {
                            itemsIndexed(favorites, key = { _, a -> a.pkg }) { index, app ->
                                AppTile(
                                    app = app,
                                    dim = app.pkg in store.hidden,
                                    modifier = Modifier
                                        .width(156.dp)
                                        .then(
                                            if (index == 0) Modifier.focusRequester(firstApp)
                                            else Modifier
                                        ),
                                    onClick = { actions.launch(app.pkg) },
                                    onMenu = { menuFor = app }
                                )
                            }
                        }
                    }

                    if (movies.isEmpty() && series.isEmpty()) {
                        Text(
                            "Stremio discovery will appear here when the public catalog is available. Your apps work offline.",
                            color = TextDim,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 14.dp)
                        )
                    }
                    if (movies.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        PosterRow("Popular movies on Stremio", movies, actions.openMeta)
                    }
                    if (series.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        PosterRow("Popular series on Stremio", series, actions.openMeta)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showOptions) {
        OptionsMenu(
            store = store,
            onSettings = { actions.settings() },
            onDismiss = { showOptions = false }
        )
    }

    menuFor?.let { app ->
        AppMenu(
            app = app,
            pinned = app.pkg in store.pinned,
            hidden = app.pkg in store.hidden,
            onPin = { store.togglePin(app.pkg) },
            onMoveLeft = { store.movePin(app.pkg, -1) },
            onMoveRight = { store.movePin(app.pkg, 1) },
            onHide = { store.toggleHide(app.pkg) },
            onInfo = { actions.info(app.pkg) },
            onUninstall = { actions.uninstall(app.pkg) },
            onDismiss = { menuFor = null }
        )
    }
}

@Composable
fun HeroText(meta: Meta, modifier: Modifier, onOpen: () -> Unit, onNext: () -> Unit) {
    val details = listOfNotNull(
        meta.year.takeIf { it.isNotBlank() },
        meta.rating.takeIf { it.isNotBlank() }?.let { "★ $it" }
    ).joinToString("   ·   ")

    Column(
        modifier
            .fillMaxWidth(0.58f)
            .padding(start = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            if (meta.type == "movie") "FEATURED MOVIE" else "FEATURED SERIES",
            color = Color(0xFF94B6FF),
            fontSize = 12.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            meta.name,
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (details.isNotEmpty()) {
            Text(details, color = TextDim, fontSize = 14.sp)
        }
        if (meta.description.isNotBlank()) {
            Text(
                meta.description,
                color = Color(0xFFE0E6F5),
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pill("Explore in Stremio") { onOpen() }
            Pill("Next  ›") { onNext() }
        }
    }
}

@Composable
fun OptionsMenu(store: Store, onSettings: () -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { first.requestFocus() }
    }
    val theme = Backgrounds[store.bgIndex % Backgrounds.size]

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(380.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1B2133))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Options",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            MenuItem(
                if (store.cinematicMode) "View: Cinematic" else "View: Compact",
                Modifier.focusRequester(first)
            ) { store.toggleCinematic() }
            MenuItem("Theme: ${theme.name}") { store.nextBackground() }
            MenuItem(if (store.showHidden) "Hide hidden apps" else "Show hidden apps") {
                store.showHidden = !store.showHidden
            }
            MenuItem("System settings") { onSettings(); onDismiss() }
            MenuItem("Close") { onDismiss() }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = TextDim,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 8.dp)
    )
}

@Composable
fun PosterRow(title: String, items: List<Meta>, onOpen: (Meta) -> Unit) {
    SectionLabel(title)
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
    ) {
        items(items, key = { it.type + it.id }) { meta ->
            PosterCard(meta) { onOpen(meta) }
        }
    }
}

@Composable
fun PosterCard(meta: Meta, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "posterScale")
    val shape = RoundedCornerShape(10.dp)

    Column(
        Modifier
            .width(120.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = meta.poster,
            contentDescription = meta.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(Card)
                .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier)
        )
        Spacer(Modifier.height(5.dp))
        Text(
            meta.name,
            color = if (focused) Color.White else TextDim,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: AppEntry,
    dim: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onMenu: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "tileScale")
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier
            .scale(scale)
            .alpha(if (dim) 0.5f else 1f)
            // Remote's Menu button (or hold OK) opens the options for the app
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp && e.key == Key.Menu) {
                    onMenu()
                    true
                } else false
            }
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onClick, onLongClick = onMenu),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(Card)
                .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (app.isBanner) {
                Image(
                    bitmap = app.image,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    bitmap = app.image,
                    contentDescription = app.label,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            app.label,
            color = if (focused) Color.White else TextDim,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun Pill(text: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(if (focused) Color.White else Color(0x33FFFFFF))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text,
            color = if (focused) Color.Black else Color.White,
            fontSize = 14.sp
        )
    }
}

@Composable
fun AppMenu(
    app: AppEntry,
    pinned: Boolean,
    hidden: Boolean,
    onPin: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onHide: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { first.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(380.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1B2133))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                app.label,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            MenuItem(
                if (pinned) "Remove from home" else "Add to home",
                Modifier.focusRequester(first)
            ) { onPin(); onDismiss() }
            if (pinned) {
                MenuItem("Move left") { onMoveLeft(); onDismiss() }
                MenuItem("Move right") { onMoveRight(); onDismiss() }
            }
            MenuItem(if (hidden) "Unhide app" else "Hide app") { onHide(); onDismiss() }
            MenuItem("App info") { onInfo(); onDismiss() }
            MenuItem("Uninstall") { onUninstall(); onDismiss() }
            MenuItem("Cancel") { onDismiss() }
        }
    }
}

@Composable
fun MenuItem(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) Accent else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text, color = Color.White, fontSize = 16.sp)
    }
}
