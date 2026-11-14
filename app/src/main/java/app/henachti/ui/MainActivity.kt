package app.henachti.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.henachti.data.Prefs
import app.henachti.domain.DayPolicy
import app.henachti.service.BlockerService
import app.henachti.service.Guard
import app.henachti.BuildConfig
import app.henachti.ui.screens.AboutScreen
import app.henachti.ui.screens.DedicateScreen
import app.henachti.ui.screens.DedicationScreen
import app.henachti.ui.screens.HomeScreen
import app.henachti.ui.screens.OnboardingScreen
import app.henachti.ui.screens.PrayerReaderScreen
import app.henachti.ui.screens.PrayersScreen
import app.henachti.ui.screens.SettingsScreen
import kotlinx.coroutines.delay

private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_ABOUT = "about"

/** The app: permission switches on first run, then home, siddur, dedication and settings. */
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_REQUEST_ADMIN = "request_admin"
        private val CREAM_ARGB: Int = 0xFFF6F0E6.toInt()
    }

    private lateinit var prefs: Prefs
    private lateinit var policy: DayPolicy
    private val tick = mutableLongStateOf(System.currentTimeMillis())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        policy = DayPolicy(this)
        // Draw behind the system bars on every Android version (15+ does it anyway), with dark
        // icons on the cream screens; Home turns the status-bar icons light over its dark panel.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(CREAM_ARGB, CREAM_ARGB),
        )
        setContent {
            HenachtiTheme { AppRoot() }
        }
        handleAdminRequest(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAdminRequest(intent)
    }

    /** The lock screen cannot open the device-admin screen itself (see Guard.requestAdmin), so it asks us to. */
    private fun handleAdminRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_REQUEST_ADMIN, false) != true) return
        intent.removeExtra(EXTRA_REQUEST_ADMIN)
        BlockerService.startFixGrace()
        Guard.requestAdmin(this)
    }

    override fun onResume() {
        super.onResume()
        BlockerService.ensureRunning(this)
        BlockerService.instance?.refreshAllowlist()
        tick.longValue = System.currentTimeMillis()
        app.henachti.widget.StatusWidget.updateAll(this)
    }

    private fun refresh() {
        tick.longValue = System.currentTimeMillis()
    }

    private fun openVerify() {
        startActivity(Intent(this, VerifyActivity::class.java))
    }

    private fun NavHostController.goTab(route: String) {
        navigate(route) {
            launchSingleTop = true
            popUpTo(Tab.HOME.route) { saveState = true }
            restoreState = true
        }
    }

    @Composable
    private fun AppRoot() {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        val firstRun = remember { !prefs.setupDone }
        val start = remember { if (firstRun) ROUTE_ONBOARDING else Tab.HOME.route }

        LaunchedEffect(Unit) {
            while (true) {
                delay(30_000)
                tick.longValue = System.currentTimeMillis()
            }
        }

        val now = tick.longValue
        val state = remember(now) { policy.state(now) }
        val running = remember(now) { BlockerService.instance != null }
        val showNav = route == Tab.HOME.route || route == Tab.PRAYERS.route || route == Tab.DEDICATION.route

        Scaffold(
            containerColor = Palette.Cream,
            // Home paints its own panel under the status bar; every other screen keeps the bar insets
            contentWindowInsets = if (route == Tab.HOME.route) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
            bottomBar = {
                if (showNav) BottomNav(route) { tab -> nav.goTab(tab.route) }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = start,
                modifier = Modifier.padding(padding),
            ) {
                composable(ROUTE_ONBOARDING) {
                    OnboardingScreen(
                        prefs = prefs,
                        now = now,
                        firstRun = start == ROUTE_ONBOARDING && nav.previousBackStackEntry == null,
                        onRefresh = { refresh() },
                        onDone = {
                            nav.navigate(Tab.HOME.route) {
                                popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Tab.HOME.route) {
                    HomeScreen(
                        prefs = prefs,
                        policy = policy,
                        state = state,
                        now = now,
                        running = running,
                        onVerify = { openVerify() },
                        onPrayers = { nav.goTab(Tab.PRAYERS.route) },
                        onDedication = { nav.goTab(Tab.DEDICATION.route) },
                        onSettings = { nav.navigate(if (prefs.setupDone) ROUTE_SETTINGS else ROUTE_ONBOARDING) },
                        onFix = { TamperUi.launchFix(this@MainActivity, it) },
                    )
                }
                composable(Tab.PRAYERS.route) {
                    PrayersScreen(
                        onOpen = { nav.navigate("prayer/$it") },
                        onBack = { nav.goTab(Tab.HOME.route) },
                        onSettings = { nav.navigate(ROUTE_SETTINGS) },
                    )
                }
                composable("prayer/{id}") { backStack ->
                    PrayerReaderScreen(
                        id = backStack.arguments?.getString("id") ?: "",
                        prefs = prefs,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Tab.DEDICATION.route) {
                    DedicationScreen(
                        prefs = prefs,
                        now = now,
                        onOpenType = { nav.navigate("dedicate/$it") },
                        onBack = { nav.goTab(Tab.HOME.route) },
                        onSettings = { nav.navigate(ROUTE_SETTINGS) },
                    )
                }
                composable("dedicate/{type}") { backStack ->
                    DedicateScreen(
                        prefs = prefs,
                        typeId = backStack.arguments?.getString("type") ?: "",
                        onBack = { nav.popBackStack() },
                        onSaved = {
                            refresh()
                            nav.popBackStack()
                        },
                    )
                }
                composable(ROUTE_SETTINGS) {
                    SettingsScreen(
                        prefs = prefs,
                        policy = policy,
                        now = now,
                        onBack = { nav.popBackStack() },
                        onRefresh = { refresh() },
                        onVerify = { openVerify() },
                        onPermissions = { nav.navigate(ROUTE_ONBOARDING) },
                        onAbout = { nav.navigate(ROUTE_ABOUT) },
                    )
                }
                composable(ROUTE_ABOUT) {
                    AboutScreen(version = BuildConfig.VERSION_NAME, onBack = { nav.popBackStack() })
                }
            }
        }
    }
}
