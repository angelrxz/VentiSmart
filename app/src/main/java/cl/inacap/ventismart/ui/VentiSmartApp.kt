package cl.inacap.ventismart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cl.inacap.ventismart.ui.screens.*
import cl.inacap.ventismart.ui.theme.V
import cl.inacap.ventismart.vm.AppViewModel

object R {
    const val HOME = "inicio"
    const val SCAN = "agregar"
    const val PAIR = "vincular"
    const val PAIRED = "vinculado"
    const val CONTROL = "control"
    const val ALERTS = "alertas"
    const val SETTINGS = "ajustes"
    const val CLOUD = "nube"
}

private data class Tab(val route: String, val label: String, val on: ImageVector, val off: ImageVector)
private val tabs = listOf(
    Tab(R.HOME, "Inicio", Icons.Filled.Home, Icons.Outlined.Home),
    Tab(R.CLOUD, "Nube", Icons.Filled.Cloud, Icons.Outlined.Cloud),
    Tab(R.ALERTS, "Alertas", Icons.Filled.Notifications, Icons.Outlined.Notifications),
    Tab(R.SETTINGS, "Ajustes", Icons.Filled.Tune, Icons.Outlined.Tune),
)

@Composable
fun VentiSmartApp(vm: AppViewModel) {
    val user by vm.user.collectAsState()
    if (user == null) {
        Box(Modifier.fillMaxSize().background(V.Bg).systemBarsPadding()) { LoginScreen(vm) }
        return
    }
    val nav = rememberNavController()
    val current = nav.currentBackStackEntryAsState().value?.destination?.route
    val activeTab = when (current) { R.CLOUD -> R.CLOUD; R.ALERTS -> R.ALERTS; R.SETTINGS -> R.SETTINGS; else -> R.HOME }

    Scaffold(
        containerColor = V.Bg,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = activeTab == t.route,
                        onClick = { nav.goTab(t.route) },
                        icon = { Icon(if (activeTab == t.route) t.on else t.off, t.label) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = V.Teal, selectedTextColor = V.Teal, indicatorColor = V.Mint,
                            unselectedIconColor = V.Muted, unselectedTextColor = V.Muted,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = R.HOME, modifier = Modifier.padding(pad).statusBarsPadding()) {
            composable(R.HOME) {
                HomeScreen(vm, onAdd = { nav.navigate(R.SCAN) }, onControl = { nav.navigate(R.CONTROL) })
            }
            composable(R.SCAN) {
                ScanScreen(vm, onBack = { nav.popBackStack() }, onChosen = { nav.navigate(R.PAIR) })
            }
            composable(R.PAIR) {
                PairScreen(vm, onBack = { nav.popBackStack() }, onDone = {
                    nav.navigate(R.PAIRED) { popUpTo(R.HOME) }
                })
            }
            composable(R.PAIRED) {
                PairedScreen(
                    vm, onBack = { nav.popBackStack() },
                    onControl = { nav.navigate(R.CONTROL) { popUpTo(R.HOME) } },
                    onHome = { nav.goTab(R.HOME) },
                )
            }
            composable(R.CONTROL) {
                ControlRoute(vm, onBack = { nav.popBackStack() }, onAlerts = { nav.goTab(R.ALERTS) })
            }
            composable(R.ALERTS) {
                AlertsScreen(vm, onBack = { nav.goTab(R.HOME) }, onControl = { nav.navigate(R.CONTROL) })
            }
            composable(R.CLOUD) {
                CloudScreen(vm, onBack = { nav.goTab(R.HOME) })
            }
            composable(R.SETTINGS) {
                SettingsScreen(vm, onBack = { nav.goTab(R.HOME) })
            }
        }
    }
}

private fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(R.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
