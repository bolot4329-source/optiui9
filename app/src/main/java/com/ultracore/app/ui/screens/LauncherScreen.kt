package com.ultracore.app.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ultracore.app.launcher.ActivityEntry
import com.ultracore.app.launcher.AppEntry
import com.ultracore.app.launcher.FavoriteActivity
import com.ultracore.app.launcher.LaunchMode
import com.ultracore.app.launcher.LauncherFilter
import com.ultracore.app.launcher.LauncherViewModel
import com.ultracore.app.ui.components.InfoBanner
import com.ultracore.app.ui.components.UltraCard
import com.ultracore.app.ui.theme.AmoledBlack
import com.ultracore.app.ui.theme.CardBlack
import com.ultracore.app.ui.theme.CardBorder
import com.ultracore.app.ui.theme.NeonAmber
import com.ultracore.app.ui.theme.NeonCyan
import com.ultracore.app.ui.theme.NeonGreen
import com.ultracore.app.ui.theme.NeonRed
import com.ultracore.app.ui.theme.TextPrimary
import com.ultracore.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** «Приложения → Экраны»: every app's screens, search, launch, favourites, home-screen shortcuts. */
@Composable
fun LauncherScreen(viewModel: LauncherViewModel = viewModel()) {
    val apps by viewModel.visibleApps.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val expanded by viewModel.expanded.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteItems.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val rootMode by viewModel.rootMode.collectAsStateWithLifecycle()
    val favoriteKeys = favorites.map { "${it.packageName}/${it.className}" }.toSet()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = TextSecondary) },
                        placeholder = { Text("Приложение, пакет или экран") },
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CardBorder,
                            cursorColor = NeonCyan,
                        ),
                    )
                    if (progress == null) {
                        IconButton(onClick = viewModel::rescan) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Обновить список", tint = NeonCyan)
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LauncherFilter.entries.forEach { f ->
                        val selected = f == filter
                        OutlinedButton(
                            onClick = { viewModel.setFilter(f) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (selected) NeonCyan else CardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) NeonCyan.copy(alpha = 0.15f) else AmoledBlack,
                                contentColor = if (selected) NeonCyan else TextPrimary,
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) { Text(f.title, fontSize = 13.sp) }
                    }
                }
            }
            if (!rootMode) {
                item {
                    InfoBanner(
                        "Без Root открываются только экраны, доступные снаружи. Закрытые помечены 🔒.",
                        color = TextSecondary,
                    )
                }
            }

            progress?.let { p ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = NeonCyan)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (p.total > 0) "Читаю приложения: ${p.done} из ${p.total}" else "Читаю приложения…",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (favorites.isNotEmpty() && query.isBlank()) {
                item {
                    Text("Избранное", style = MaterialTheme.typography.titleSmall, color = NeonAmber, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                }
                items(favorites, key = { "fav/${it.packageName}/${it.className}" }) { fav ->
                    FavoriteRow(
                        fav = fav,
                        onLaunch = { viewModel.launch(fav.packageName, fav.className) },
                        onShortcut = { viewModel.pinShortcut(fav.packageName, fav.className, fav.label) },
                    )
                }
            }

            if (progress == null && apps.isEmpty()) {
                item { InfoBanner("Ничего не найдено", color = TextSecondary) }
            }

            items(apps, key = { it.packageName }) { app ->
                AppCard(
                    app = app,
                    expanded = app.packageName in expanded || query.length >= 3,
                    rootMode = rootMode,
                    favoriteKeys = favoriteKeys,
                    viewModel = viewModel,
                )
            }
        }

        message?.let { text ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                containerColor = CardBlack,
                contentColor = TextPrimary,
                action = { TextButton(onClick = viewModel::clearMessage) { Text("OK", color = NeonCyan) } },
            ) { Text(text) }
        }
    }
}

@Composable
private fun AppCard(
    app: AppEntry,
    expanded: Boolean,
    rootMode: Boolean,
    favoriteKeys: Set<String>,
    viewModel: LauncherViewModel,
) {
    UltraCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleExpanded(app.packageName) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.packageName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (app.enabled) TextPrimary else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${app.packageName} · экранов: ${app.activities.size}" + if (!app.enabled) " · заморожено" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = TextSecondary)
        }
        if (expanded) {
            app.activities.forEach { act ->
                HorizontalDivider(color = CardBorder, modifier = Modifier.padding(vertical = 4.dp))
                ActivityRow(act, app.label, rootMode, "${act.packageName}/${act.className}" in favoriteKeys, viewModel)
            }
        }
    }
}

@Composable
private fun ActivityRow(
    act: ActivityEntry,
    appLabel: String,
    rootMode: Boolean,
    favorite: Boolean,
    viewModel: LauncherViewModel,
) {
    val context = LocalContext.current
    val label by produceState<String?>(null, act.packageName, act.className) {
        value = withContext(Dispatchers.IO) { viewModel.activityLabel(act) }
    }
    val locked = act.mode == LaunchMode.ROOT_ONLY && !rootMode
    val canLaunch = !locked && act.enabled

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .clickable(enabled = canLaunch) { viewModel.launch(act.packageName, act.className) },
        ) {
            Text(
                label ?: act.shortName.substringAfterLast('.'),
                style = MaterialTheme.typography.bodyMedium,
                color = if (canLaunch) TextPrimary else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                act.shortName,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    !act.enabled -> "отключён"
                    locked -> "🔒 закрытый экран — нужен Root"
                    act.mode == LaunchMode.SHELL -> "защищён разрешением — через Shizuku"
                    act.mode == LaunchMode.ROOT_ONLY -> "закрытый экран — через Root"
                    else -> "открывается напрямую"
                },
                fontSize = 11.sp,
                color = when {
                    locked || !act.enabled -> NeonRed
                    act.mode == LaunchMode.DIRECT -> NeonGreen
                    else -> NeonAmber
                },
            )
        }
        if (locked) {
            Icon(Icons.Outlined.Lock, contentDescription = "Нужен Root", tint = NeonRed, modifier = Modifier.padding(8.dp))
        } else {
            IconButton(onClick = { viewModel.toggleFavorite(act, appLabel) }) {
                Icon(
                    if (favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                    contentDescription = "В избранное",
                    tint = if (favorite) NeonAmber else TextSecondary,
                )
            }
            IconButton(onClick = { viewModel.pinShortcut(act.packageName, act.className, label ?: shortLabel(context, act)) }) {
                Icon(Icons.Outlined.AddToHomeScreen, contentDescription = "Ярлык на рабочий стол", tint = TextSecondary)
            }
            IconButton(onClick = { viewModel.launch(act.packageName, act.className) }, enabled = canLaunch) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = "Открыть", tint = if (canLaunch) NeonGreen else CardBorder)
            }
        }
    }
}

@Composable
private fun FavoriteRow(fav: FavoriteActivity, onLaunch: () -> Unit, onShortcut: () -> Unit) {
    UltraCard(Modifier.clickable(onClick = onLaunch)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(fav.packageName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(fav.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(fav.appLabel, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
            }
            IconButton(onClick = onShortcut) {
                Icon(Icons.Outlined.AddToHomeScreen, contentDescription = "Ярлык на рабочий стол", tint = TextSecondary)
            }
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = NeonGreen)
        }
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }
    Box(Modifier.size(36.dp)) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

private fun shortLabel(context: Context, act: ActivityEntry): String =
    act.shortName.substringAfterLast('.').ifBlank { context.packageName }
