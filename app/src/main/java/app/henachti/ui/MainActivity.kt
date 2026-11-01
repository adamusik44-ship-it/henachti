package app.henachti.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
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
import app.henachti.ui.screens.DedicationScreen
import app.henachti.ui.screens.HomeScreen
import app.henachti.ui.screens.PrayerReaderScreen
import app.henachti.ui.screens.PrayersScreen
import app.henachti.ui.screens.SettingsScreen
import kotlinx.coroutines.delay

/** The app: home, prayers, dedication, settings. One activity, Compose navigation. */
class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var policy: DayPolicy
    private val tick = mutableLongStateOf(System.currentTimeMillis())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        policy = DayPolicy(this)
        setContent {
            HenachtiTheme { AppRoot() }
        }
    }

    override fun onResume() {
        super.onResume()
        BlockerService.ensureRunning(this)
        BlockerService.instance?.refreshAllowlist()
        tick.longValue = System.currentTimeMillis()
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
            popUpTo("home") { saveState = true }
            restoreState = true
        }
    }

    @Composable
    private fun AppRoot() {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route

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
            bottomBar = {
                if (showNav) BottomNav(route) { tab -> nav.goTab(tab.route) }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Tab.HOME.route,
                modifier = Modifier.padding(padding),
            ) {
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
                        onSettings = { nav.navigate("settings") },
                        onFix = { TamperUi.launchFix(this@MainActivity, it) },
                    )
                }
                composable(Tab.PRAYERS.route) {
                    PrayersScreen(
                        onOpen = { nav.navigate("prayer/$it") },
                        onBack = { nav.goTab(Tab.HOME.route) },
                        onSettings = { nav.navigate("settings") },
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
                        onBack = { nav.goTab(Tab.HOME.route) },
                        onSettings = { nav.navigate("settings") },
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        prefs = prefs,
                        policy = policy,
                        now = now,
                        onBack = { nav.popBackStack() },
                        onRefresh = { refresh() },
                        onVerify = { openVerify() },
                    )
                }
            }
        }
    }
}
