package com.kash.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kash.app.KashApp
import com.kash.app.core.UserSettings
import com.kash.app.ui.activity.ActivityScreen
import com.kash.app.ui.activity.ActivityViewModel
import com.kash.app.ui.components.KashBackground
import com.kash.app.ui.components.pressable
import com.kash.app.ui.home.HomeScreen
import com.kash.app.ui.home.HomeViewModel
import com.kash.app.ui.icons.Lucide
import com.kash.app.OpenRequest
import com.kash.app.ui.report.ReportScreen
import com.kash.app.ui.report.ReportViewModel
import com.kash.app.ui.savings.SavingsScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.kash.app.ui.settings.SettingsScreen
import com.kash.app.ui.theme.Kash
import com.kash.app.ui.theme.Space
import kotlinx.coroutines.launch

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Lucide.House),
    ACTIVITY("activity", "Activity", Lucide.Receipt),
    REPORT("report", "Report", Lucide.ChartNoAxesColumn),
    SAVINGS("savings", "Savings", Lucide.PiggyBank),
    SETTINGS("settings", "Settings", Lucide.Settings),
}

/**
 * Tabs switch with a 120ms cross-fade: fast enough to feel instant, smooth enough not to flash.
 * Each tab keeps its scroll position and search text (saveState/restoreState).
 */
@Composable
fun KashNav(
    app: KashApp,
    settings: UserSettings,
    onRequestSms: () -> Unit,
    openRequest: OpenRequest? = null,
    onOpenHandled: () -> Unit = {},
) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route

    fun go(tab: Tab) = nav.navigate(tab.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    // Shared so the notification can point it at a month (and start a PDF) before the tab opens.
    val reportVm: ReportViewModel = viewModel { ReportViewModel(app.repository) }
    val context = LocalContext.current
    LaunchedEffect(openRequest) {
        val req = openRequest ?: return@LaunchedEffect
        reportVm.show(req.month)
        go(Tab.REPORT)
        if (req.pdf) reportVm.exportPdf(context, req.month)
        onOpenHandled()
    }

    KashBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0), // insets handled explicitly; avoids double padding
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { BottomBar(current) { go(it) } },
            modifier = Modifier.statusBarsPadding(),
        ) { scaffoldPadding ->
            // Screens end exactly where the bottom bar starts, so nothing ever scrolls underneath it.
            val padding = PaddingValues(top = scaffoldPadding.calculateTopPadding())
            NavHost(
                navController = nav,
                startDestination = Tab.HOME.route,
                modifier = Modifier.padding(bottom = scaffoldPadding.calculateBottomPadding()),
                enterTransition = { fadeIn(tween(120)) },
                exitTransition = { fadeOut(tween(90)) },
                popEnterTransition = { fadeIn(tween(120)) },
                popExitTransition = { fadeOut(tween(90)) },
            ) {
                composable(Tab.HOME.route) {
                    val vm: HomeViewModel = viewModel { HomeViewModel(app.repository) }
                    HomeScreen(
                        vm = vm,
                        hidden = settings.hideAmounts,
                        onToggleHidden = { scope.launch { app.settings.setHideAmounts(!settings.hideAmounts) } },
                        onSeeAll = { go(Tab.ACTIVITY) },
                        onRequestSms = onRequestSms,
                        snackbar = snackbar,
                        contentPadding = padding,
                    )
                }
                composable(Tab.ACTIVITY.route) {
                    val vm: ActivityViewModel = viewModel { ActivityViewModel(app.repository) }
                    ActivityScreen(vm, settings.hideAmounts, padding)
                }
                composable(Tab.REPORT.route) { ReportScreen(reportVm, settings.hideAmounts, snackbar, padding) }
                composable(Tab.SAVINGS.route) { SavingsScreen(app.repository, settings.hideAmounts, padding) }
                composable(Tab.SETTINGS.route) {
                    val repoState by app.repository.state.collectAsStateWithLifecycle()
                    SettingsScreen(
                        settings = settings,
                        online = app.connectivity.isOnline,
                        seenProviders = repoState.seenProviders,
                        ignoredProviders = repoState.ignoredProviders,
                        onTracked = { p, on -> scope.launch { app.repository.setTracked(p, on) } },
                        onTheme = { scope.launch { app.settings.setTheme(it) } },
                        onHideAmounts = { scope.launch { app.settings.setHideAmounts(it) } },
                        contentPadding = padding,
                    )
                }
            }
        }
    }
}

/** Solid bottom bar, always visible. Each item is a full 48dp+ target; the selected one gets the accent. */
@Composable
private fun BottomBar(current: String?, onSelect: (Tab) -> Unit) {
    val c = Kash.colors
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.background, shape) // opaque base: content never shows through
            .background(c.glassStrong, shape)
            .border(1.dp, c.border, shape)
            .navigationBarsPadding()
            .padding(horizontal = Space.s, vertical = Space.xs),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        Tab.entries.forEach { tab ->
            val isSelected = current == tab.route
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .pressable({ onSelect(tab) }, RoundedCornerShape(14.dp), role = Role.Tab)
                    .semantics { selected = isSelected }
                    .padding(vertical = Space.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(tab.icon, null, Modifier.size(22.dp), tint = if (isSelected) c.accent else c.textMuted)
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) c.text else c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
