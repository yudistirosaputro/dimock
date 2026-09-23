package com.yudistirosaputro.dimock.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yudistirosaputro.dimock.okhttp.Dimock
import com.yudistirosaputro.dimock.okhttp.InspectorTheme
import com.yudistirosaputro.dimock.ui.notification.DimockNotification
import com.yudistirosaputro.dimock.ui.screens.AgentScreen
import com.yudistirosaputro.dimock.ui.screens.CurlSheet
import com.yudistirosaputro.dimock.ui.screens.DetailScreen
import com.yudistirosaputro.dimock.ui.screens.ForceStateSheet
import com.yudistirosaputro.dimock.ui.screens.MocksScreen
import com.yudistirosaputro.dimock.ui.screens.TrafficScreen
import com.yudistirosaputro.dimock.ui.screens.controlShape
import com.yudistirosaputro.dimock.ui.theme.DimockDimens
import com.yudistirosaputro.dimock.ui.theme.DimockTheme
import com.yudistirosaputro.dimock.ui.theme.DimockType

/** The inspector. Opened from the notification, `Dimock.launch`, or a shake. Compose only; no XML layouts. */
class DimockInspectorActivity : ComponentActivity() {

    private val viewModel: InspectorViewModel by viewModels { InspectorViewModel.Factory(Dimock.engine(), Dimock.port) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Dimock.isInitialized) {
            finish()
            return
        }
        val startTab = intent.getStringExtra(EXTRA_TAB) ?: Routes.TRAFFIC
        setContent {
            // Resolved inside the composition: `System` reads uiMode, so a night-mode change recomposes the whole panel.
            DimockTheme(darkTheme = isDarkTheme(Dimock.config?.inspectorTheme)) {
                InspectorApp(viewModel, startTab, ::copyToClipboard, ::share)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // POST_NOTIFICATIONS may have been granted since init; make the notification appear now.
        if (Dimock.config?.showNotification != false) DimockNotification.refresh(this)
    }

    private fun copyToClipboard(text: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("dimock", text))
        viewModel.showSnackbar("Copied to clipboard")
    }

    private fun share(text: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share"))
    }

    companion object {
        const val EXTRA_TAB = "com.yudistirosaputro.dimock.ui.TAB"
        const val TAB_TRAFFIC = "traffic"
        const val TAB_MOCKS = "mocks"
        const val TAB_AGENT = "agent"

        fun intent(context: Context, tab: String? = null): Intent =
            Intent(context, DimockInspectorActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { tab?.let { putExtra(EXTRA_TAB, it) } }
    }
}

/** `Dimock.Config.inspectorTheme` → the flag `DimockTheme` takes. No config yet (or `System`) follows the device. */
@Composable
private fun isDarkTheme(theme: InspectorTheme?): Boolean = when (theme ?: InspectorTheme.System) {
    InspectorTheme.System -> isSystemInDarkTheme()
    InspectorTheme.Dark -> true
    InspectorTheme.Light -> false
}

private object Routes {
    const val TRAFFIC = "traffic"
    const val MOCKS = "mocks"
    const val AGENT = "agent"
    const val DETAIL = "detail/{id}"
    fun detail(id: String) = "detail/$id"
}

private enum class Sheet { NONE, FORCE_STATE, CURL }

@Composable
private fun InspectorApp(viewModel: InspectorViewModel, startTab: String, onCopy: (String) -> Unit, onShare: (String) -> Unit) {
    val state by viewModel.state.collectAsState()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    var highlightRule by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.snackbar) {
        val text = state.snackbar ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.snackbarShown()
    }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val barShown = route != Routes.DETAIL

    // The tab bar floats over the screens, so the Scaffold reserves nothing for it; each screen leaves
    // BAR_CLEARANCE_DP under itself instead.
    Scaffold(
        containerColor = DimockTheme.colors.surface,
        snackbarHost = {
            // Lifted clear of the floating bar so a "Copied" never lands on the tabs.
            val lift = if (barShown) (DimockDimens.BAR_HEIGHT_DP + DimockDimens.BAR_BOTTOM_DP).dp else 0.dp
            SnackbarHost(snackbar, Modifier.padding(bottom = lift)) { data ->
                Snackbar(containerColor = DimockTheme.colors.snackbarSurface, contentColor = DimockTheme.colors.snackbarText, shape = controlShape) {
                    Text(data.visuals.message, style = DimockType.Body)
                }
            }
        },
    ) { padding ->
        // The Scaffold padding already carries both system-bar insets; consuming them here keeps the screens'
        // own `navigationBarsPadding()` calls, and the bar's, from adding a second inset.
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
            NavHost(nav, startDestination = startTab.takeIf { it == Routes.MOCKS || it == Routes.AGENT } ?: Routes.TRAFFIC) {
                composable(Routes.TRAFFIC) {
                    TrafficScreen(
                        state = state,
                        onRecording = viewModel::setRecording,
                        onClear = viewModel::clearCaptures,
                        onSearch = viewModel::setSearch,
                        onFilters = viewModel::setFilters,
                        onOpen = { nav.navigate(Routes.detail(it.id)) },
                    )
                }
                composable(Routes.MOCKS) {
                    MocksScreen(
                        state = state,
                        highlightId = highlightRule,
                        onToggle = viewModel::setRuleEnabled,
                        onReset = viewModel::resetRule,
                        onRemove = viewModel::removeRule,
                        onSetAll = viewModel::setAllEnabled,
                        onClearAll = viewModel::clearRules,
                    )
                }
                composable(Routes.AGENT) {
                    AgentScreen(state, onAgentWrite = viewModel::setAgentWriteEnabled, onClearActivity = viewModel::clearActivity, onCopy = onCopy)
                }
                composable(Routes.DETAIL) { entry ->
                    val id = entry.arguments?.getString("id") ?: return@composable
                    val ui = remember(id, state.rules) { viewModel.detail(id) }
                    if (ui == null) {
                        LaunchedEffect(id) { nav.popBackStack() }
                        return@composable
                    }
                    var sheet by remember { mutableStateOf(Sheet.NONE) }
                    DetailScreen(
                        ui = ui,
                        onBack = { nav.popBackStack() },
                        onOpenRule = { ruleId -> highlightRule = ruleId; nav.switchTo(Routes.MOCKS) },
                        onCopy = onCopy,
                        onCurl = { sheet = Sheet.CURL },
                        onMockThis = { sheet = Sheet.FORCE_STATE },
                    )
                    when (sheet) {
                        Sheet.FORCE_STATE -> ForceStateSheet(
                            ui = ui,
                            onApply = { f -> viewModel.forceState(ui.tx, f.preset, f.status, f.delayMs, f.body); sheet = Sheet.NONE },
                            onDismiss = { sheet = Sheet.NONE },
                        )
                        Sheet.CURL -> CurlSheet(ui, onCopy = { onCopy(it); sheet = Sheet.NONE }, onShare = { onShare(it); sheet = Sheet.NONE }, onDismiss = { sheet = Sheet.NONE })
                        Sheet.NONE -> Unit
                    }
                }
            }
            if (barShown) {
                BottomBar(
                    nav,
                    route,
                    state.enabledMocks,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = DimockDimens.INSET_DP.dp, end = DimockDimens.INSET_DP.dp, bottom = DimockDimens.BAR_BOTTOM_DP.dp),
                )
            }
        }
    }
}

/** The floating tab bar: a raised, bordered, shadowed slab inset from the screen edge. Hidden on Detail by the caller. */
@Composable
private fun BottomBar(nav: NavHostController, route: String?, enabledMocks: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(DimockDimens.RADIUS_BAR_DP.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(DimockDimens.BAR_HEIGHT_DP.dp)
            .shadow(8.dp, shape)
            .background(DimockTheme.colors.surfaceRaised, shape)
            .border(1.dp, DimockTheme.colors.hairline, shape),
    ) {
        Tab("Traffic", route == Routes.TRAFFIC, Modifier.weight(1f)) { nav.switchTo(Routes.TRAFFIC) }
        Tab("Mocks", route == Routes.MOCKS, Modifier.weight(1f), badge = enabledMocks.takeIf { it > 0 }?.toString()) { nav.switchTo(Routes.MOCKS) }
        Tab("Agent", route == Routes.AGENT, Modifier.weight(1f)) { nav.switchTo(Routes.AGENT) }
    }
}

private fun NavHostController.switchTo(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun Tab(label: String, selected: Boolean, modifier: Modifier, badge: String? = null, onClick: () -> Unit) {
    val colors = DimockTheme.colors
    Box(modifier.fillMaxSize().clickable(onClick = onClick)) {
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                label,
                style = DimockType.Tab.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                color = if (selected) colors.text else colors.textMuted,
            )
            if (badge != null) Text(badge, style = DimockType.SectionLabel, color = colors.accentText)
        }
        // The active mark is a 16×3 dp pill under the label, not an edge stripe.
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .size(width = 16.dp, height = 3.dp)
                    .background(colors.accent, RoundedCornerShape(DimockDimens.RADIUS_PILL_DP.dp)),
            )
        }
    }
}
