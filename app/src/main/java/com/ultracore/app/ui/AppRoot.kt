package com.ultracore.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ultracore.app.shizuku.ShizukuManager
import com.ultracore.app.ui.components.GlassBarHeight
import com.ultracore.app.ui.components.GlassBarItem
import com.ultracore.app.ui.components.GlassBottomBar
import com.ultracore.app.ui.components.LocalBottomBarInset
import com.ultracore.app.ui.components.SubTabs
import com.ultracore.app.ui.components.SystemStatusHeader
import com.ultracore.app.ui.screens.AppManagerScreen
import com.ultracore.app.ui.screens.BatteryScreen
import com.ultracore.app.ui.screens.CleanerScreen
import com.ultracore.app.ui.screens.HomeScreen
import com.ultracore.app.ui.screens.LauncherScreen
import com.ultracore.app.ui.screens.OptimizerScreen
import com.ultracore.app.ui.screens.SorterScreen
import com.ultracore.app.ui.screens.TerminalSection
import com.ultracore.app.ui.screens.TweaksScreen
import com.ultracore.app.ui.theme.AmoledBlack

private enum class Destination(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("home", "Главная", Icons.Outlined.Home, Icons.Rounded.Home),
    Files("files", "Файлы", Icons.Outlined.Folder, Icons.Rounded.Folder),
    Apps("apps", "Приложения", Icons.Outlined.Apps, Icons.Rounded.Apps),
    Terminal("terminal", "Терминал", Icons.Outlined.Terminal, Icons.Rounded.Terminal),
}

@Composable
fun UltraCoreRoot(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val navController = rememberNavController()

    val shizukuState by viewModel.shizukuState.collectAsStateWithLifecycle()
    val storageGranted by viewModel.storageGranted.collectAsStateWithLifecycle()
    val shellTest by viewModel.shellTest.collectAsStateWithLifecycle()

    // Re-check permissions every time the user returns from Settings or Shizuku.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose { }
    }

    // Everything above the bar is recorded into this layer; the glass bar draws it blurred.
    val contentLayer = rememberGraphicsLayer()
    var contentOrigin by remember { mutableStateOf(Offset.Zero) }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barBottomMargin = 10.dp
    // Scrolling screens leave this much room at the end so nothing hides under the bar.
    val bottomInset = GlassBarHeight + barBottomMargin + navBottom + 8.dp

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val selectedIndex = Destination.entries.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    CompositionLocalProvider(LocalBottomBarInset provides bottomInset) {
        Box(
            Modifier
                .fillMaxSize()
                .background(AmoledBlack),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { contentOrigin = it.positionInRoot() }
                    .drawWithContent {
                        contentLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(contentLayer)
                    }
                    .background(AmoledBlack)
                    .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))),
            ) {
                SystemStatusHeader(
                    shizukuState = shizukuState,
                    storageGranted = storageGranted,
                    shellTest = shellTest,
                    onOpenShizuku = { context.openShizukuApp() },
                    onRequestShizuku = viewModel::requestShizukuPermission,
                    onReconnectShizuku = viewModel::reconnectShizuku,
                    onGrantStorage = { context.openAllFilesAccessSettings() },
                    onRunShellTest = viewModel::runShellTest,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                )

                NavHost(
                    navController = navController,
                    startDestination = Destination.Home.route,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    // Springy Expressive transitions between sections.
                    enterTransition = {
                        fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 380f), initialScale = 0.94f)
                    },
                    exitTransition = { fadeOut(tween(120)) },
                    popEnterTransition = {
                        fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 380f), initialScale = 0.94f)
                    },
                    popExitTransition = { fadeOut(tween(120)) },
                ) {
                    composable(Destination.Home.route) {
                        SubTabs(listOf("Обзор", "Батарея", "Твики", "Оптимизация")) { tab ->
                            when (tab) {
                                0 -> HomeScreen()
                                1 -> BatteryScreen()
                                2 -> TweaksScreen()
                                else -> OptimizerScreen()
                            }
                        }
                    }
                    composable(Destination.Files.route) {
                        SubTabs(listOf("Очистка", "Сортировка")) { tab ->
                            if (tab == 0) CleanerScreen() else SorterScreen()
                        }
                    }
                    composable(Destination.Apps.route) {
                        SubTabs(listOf("Экраны", "Управление")) { tab ->
                            if (tab == 0) LauncherScreen() else AppManagerScreen()
                        }
                    }
                    composable(Destination.Terminal.route) { TerminalSection() }
                }
            }

            GlassBottomBar(
                items = Destination.entries.map { GlassBarItem(it.label, it.icon, it.selectedIcon) },
                selected = selectedIndex,
                onSelect = { index ->
                    val dest = Destination.entries[index]
                    if (dest.route != currentRoute) {
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                backdrop = contentLayer,
                backdropOrigin = contentOrigin,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = barBottomMargin),
            )
        }
    }
}

private fun Context.openAllFilesAccessSettings() {
    val appSpecific = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:$packageName"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(appSpecific)
    } catch (_: ActivityNotFoundException) {
        startActivity(
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun Context.openShizukuApp() {
    val launch = packageManager.getLaunchIntentForPackage(ShizukuManager.SHIZUKU_PACKAGE)
    if (launch != null) {
        startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return
    }
    Toast.makeText(this, "Shizuku не установлен — откроется страница загрузки", Toast.LENGTH_LONG).show()
    try {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: ActivityNotFoundException) {
        // No browser — nothing else to do.
    }
}
