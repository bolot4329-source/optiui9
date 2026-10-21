package com.ultracore.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ultracore.app.shizuku.ShizukuManager
import com.ultracore.app.ui.components.SubTabs
import com.ultracore.app.ui.components.SystemStatusHeader
import com.ultracore.app.ui.screens.BatteryScreen
import com.ultracore.app.ui.screens.CleanerScreen
import com.ultracore.app.ui.screens.HomeScreen
import com.ultracore.app.ui.screens.OptimizerScreen
import com.ultracore.app.ui.screens.PhasePlaceholder
import com.ultracore.app.ui.screens.SorterScreen
import com.ultracore.app.ui.screens.TweaksScreen
import com.ultracore.app.ui.theme.AmoledBlack
import com.ultracore.app.ui.theme.BarBlack
import com.ultracore.app.ui.theme.NeonCyan
import com.ultracore.app.ui.theme.NeonCyanDim
import com.ultracore.app.ui.theme.TextSecondary

private enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Главная", Icons.Outlined.Home),
    Files("files", "Файлы", Icons.Outlined.FolderOpen),
    Apps("apps", "Приложения", Icons.Outlined.Apps),
    Terminal("terminal", "Терминал", Icons.Outlined.Terminal),
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

    Scaffold(
        containerColor = AmoledBlack,
        bottomBar = { UltraBottomBar(navController) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
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
                    PhasePlaceholder(
                        icon = Icons.Outlined.Apps,
                        title = "Приложения",
                        phase = "Фазы 4–5",
                        description = "Управление приложениями и запуск их экранов.",
                        plannedFeatures = listOf(
                            "Заморозка и разморозка приложений",
                            "Удаление системных приложений для пользователя с восстановлением",
                            "Бэкап APK и установка .apk / .apks",
                            "Песочница: автозаморозка после выключения экрана",
                            "Запуск экранов приложений и ярлыки на рабочий стол",
                        ),
                    )
                }
                composable(Destination.Terminal.route) {
                    PhasePlaceholder(
                        icon = Icons.Outlined.Terminal,
                        title = "Терминал",
                        phase = "Фаза 3",
                        description = "Команды через Shizuku с живым выводом.",
                        plannedFeatures = listOf(
                            "Ввод команд с историей и автодополнением",
                            "Каталог полезных команд с описаниями",
                            "Макросы: несколько команд одной кнопкой",
                            "Плитка в шторке для любимого макроса",
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun UltraBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar(containerColor = BarBlack, tonalElevation = 0.dp) {
        Destination.entries.forEach { dest ->
            NavigationBarItem(
                selected = currentRoute == dest.route,
                onClick = {
                    navController.navigate(dest.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(dest.icon, contentDescription = dest.label) },
                label = { Text(dest.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NeonCyan,
                    selectedTextColor = NeonCyan,
                    indicatorColor = NeonCyanDim,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                ),
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
