package com.example.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.formatDuration
import com.example.core.formatTonnage
import com.example.core.kg
import com.example.core.trimNum
import com.example.data.PrEntity
import com.example.ui.components.ChoiceChip
import com.example.ui.components.FitCard
import com.example.ui.components.LabeledSwitch
import com.example.ui.components.LocalMenuAction
import com.example.ui.components.OverlineText
import com.example.ui.components.PillTabs
import com.example.ui.components.ProgressRing
import com.example.ui.components.RoundIconButton
import com.example.ui.components.ThinProgress
import com.example.ui.screens.ActiveWorkoutScreen
import com.example.ui.screens.BodyScreen
import com.example.ui.screens.DayEditorScreen
import com.example.ui.screens.ExerciseDetailScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NotesScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RoutinesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.screens.WorkoutDetailScreen
import com.example.ui.screens.shareText
import com.example.ui.theme.Palette
import com.example.ui.theme.fit
import com.example.ui.theme.mono
import com.example.ui.theme.overline
import com.example.ui.theme.parseHex
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val ROUTINES = "routines"
    const val STATS = "stats"
    const val LIBRARY = "library"
    const val PROFILE = "profile"
    const val DAY = "day"
    const val WORKOUT = "workout"
    const val WORKOUT_DETAIL = "workoutDetail"
    const val EXERCISE = "exercise"
    const val SETTINGS = "settings"
    const val TOOLS = "tools"
    const val HISTORY = "history"
    const val BODY = "body"
    const val NOTES = "notes"
}

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem(Routes.HOME, "Bugün", Icons.Outlined.Home),
    NavItem(Routes.ROUTINES, "Program", Icons.Outlined.CalendarMonth),
    NavItem(Routes.STATS, "İlerleme", Icons.Outlined.BarChart),
    NavItem(Routes.LIBRARY, "Hareket", Icons.Outlined.FitnessCenter)
    // Profil ve ayarlar alt menüden çıktı: ana ekrandaki ⚙ ikonundan açılır.
)

@Composable
fun FitFlowApp(vm: AppViewModel) {
    val lockEnabled by vm.settings.lockEnabled.collectAsStateWithLifecycle()
    val passcode by vm.settings.passcode.collectAsStateWithLifecycle()
    var unlocked by remember { mutableStateOf(false) }

    if (lockEnabled && passcode.isNotBlank() && !unlocked) {
        LockScreen(correct = passcode, onUnlock = { unlocked = true })
        return
    }
    AppScaffold(vm)
}

@Composable
private fun AppScaffold(vm: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: Routes.HOME
    val showBars = navItems.any { current == it.route }

    val rest by vm.restTimer.collectAsStateWithLifecycle()
    val durTimer by vm.durationTimer.collectAsStateWithLifecycle()
    val celebration by vm.celebration.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }

    val navigateToTab: (String) -> Unit = { route ->
        if (route != current) {
            nav.navigate(route) {
                popUpTo(Routes.HOME) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Yan menü (drawer) devre dışı: her ekrana alt menü ve ⚙ üzerinden ulaşılıyor.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            AppDrawer(
                vm = vm,
                onNavigate = { route ->
                    closeDrawer()
                    navigateToTab(route)
                },
                onStartFreeWorkout = {
                    closeDrawer()
                    vm.startWorkout(null) { id -> nav.navigate("${Routes.WORKOUT}/$id") }
                },
                onClose = closeDrawer
            )
        }
    ) {
        CompositionLocalProvider(LocalMenuAction provides null) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    AnimatedVisibility(
                        visible = showBars,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        BottomBar(current) { route ->
                            navigateToTab(route)
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize()) {
                    NavHost(
                        navController = nav,
                        startDestination = Routes.HOME,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = if (showBars) padding.calculateBottomPadding() else 0.dp)
                    ) {
                        composable(Routes.HOME) { HomeScreen(vm, nav) }
                        composable(Routes.ROUTINES) { RoutinesScreen(vm, nav) }
                        composable(Routes.STATS) { StatsScreen(vm, nav) }
                        composable(Routes.LIBRARY) { LibraryScreen(vm, nav) }
                        composable(Routes.PROFILE) { ProfileScreen(vm, nav) }
                        composable(Routes.SETTINGS) { SettingsScreen(vm, nav) }
                        composable(Routes.TOOLS) { ToolsScreen(vm, nav) }
                        composable(Routes.HISTORY) { HistoryScreen(vm, nav) }
                        composable(Routes.BODY) { BodyScreen(vm, nav) }
                        composable(Routes.NOTES) { NotesScreen(vm, nav) }
                        composable(
                            "${Routes.DAY}/{dayId}",
                            arguments = listOf(navArgument("dayId") { type = NavType.LongType })
                        ) { entry ->
                            DayEditorScreen(vm, nav, entry.arguments?.getLong("dayId") ?: 0L)
                        }
                        composable("${Routes.WORKOUT}/{id}") { ActiveWorkoutScreen(vm, nav) }
                        composable(
                            "${Routes.WORKOUT_DETAIL}/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.LongType })
                        ) { entry ->
                            WorkoutDetailScreen(vm, nav, entry.arguments?.getLong("id") ?: 0L)
                        }
                        composable(
                            "${Routes.EXERCISE}/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.LongType })
                        ) { entry ->
                            ExerciseDetailScreen(vm, nav, entry.arguments?.getLong("id") ?: 0L)
                        }
                    }

                    // Uygulama genelinde görünen hareket süresi sayacı (süreli hareketler için)
                    AnimatedVisibility(
                        visible = durTimer?.running == true,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (showBars) padding.calculateBottomPadding() + 8.dp else 12.dp),
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        durTimer?.let { state ->
                            DurationTimerBar(
                                state = state,
                                onEarlyFinish = vm::finishDurationTimerEarly,
                                onCancel = vm::cancelDurationTimer
                            )
                        }
                    }

                    // Uygulama genelinde görünen dinlenme sayacı
                    AnimatedVisibility(
                        visible = rest.active && durTimer?.running != true,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (showBars) padding.calculateBottomPadding() + 8.dp else 12.dp),
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        RestTimerBar(
                            state = rest,
                            onAdjust = vm::adjustRest,
                            onToggle = vm::pauseResumeRest,
                            onStop = vm::stopRest,
                            onClick = {
                                val wId = vm.activeWorkout.value?.id
                                if (wId != null) {
                                    if (current != "${Routes.WORKOUT}/$wId") {
                                        nav.navigate("${Routes.WORKOUT}/$wId")
                                    }
                                    rest.exerciseId?.let { id -> vm.scrollToExercise(id) }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    celebration?.let { c ->
        PrDialog(c) { vm.dismissCelebration() }
    }
}

/* ================================ Yan menü ================================== */

@Composable
private fun AppDrawer(
    vm: AppViewModel,
    onNavigate: (String) -> Unit,
    onStartFreeWorkout: () -> Unit,
    onClose: () -> Unit
) {
    val s = vm.settings
    val context = LocalContext.current

    val name by s.userName.collectAsStateWithLifecycle()
    val goal by s.weeklyGoal.collectAsStateWithLifecycle()
    val themeMode by s.themeMode.collectAsStateWithLifecycle()
    val accent by s.accent.collectAsStateWithLifecycle()
    val amoled by s.amoled.collectAsStateWithLifecycle()
    val autoRest by s.autoRest.collectAsStateWithLifecycle()
    val defaultRest by s.defaultRest.collectAsStateWithLifecycle()
    val sound by s.sound.collectAsStateWithLifecycle()
    val vibrate by s.vibrate.collectAsStateWithLifecycle()
    val beep by s.countdownBeep.collectAsStateWithLifecycle()
    val keepOn by s.keepScreenOn.collectAsStateWithLifecycle()
    val alarmUri by s.alarmUri.collectAsStateWithLifecycle()
    val stats by vm.dashboard.collectAsStateWithLifecycle()

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            s.setAlarmUri(uri?.toString() ?: "default")
        }
    }

    val openRingtonePicker: () -> Unit = {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Dinlenme alarmı sesi")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            if (alarmUri.isNotBlank() && alarmUri != "default" && alarmUri != "beep") {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(alarmUri))
            }
        }
        try {
            ringtoneLauncher.launch(intent)
        } catch (_: Throwable) {
        }
    }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerShape = RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            /* ------------------------------ Başlık ------------------------------ */
            Row(
                Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.fit.accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.fit.accent
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Bu hafta ${stats.thisWeekWorkouts}/$goal" +
                            if (stats.streakDays > 0) "  ·  ${stats.streakDays} gün seri" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.fit.muted,
                        maxLines = 1
                    )
                }
                RoundIconButton(Icons.Default.Close, MaterialTheme.fit.muted, 36.dp, Color.Transparent) { onClose() }
            }

            ThinProgress(if (goal <= 0) 0f else stats.thisWeekWorkouts / goal.toFloat())

            Spacer(Modifier.height(20.dp))

            /* ------------------------------ Görünüm ----------------------------- */
            DrawerSection("Görünüm")
            FitCard(contentPadding = PaddingValues(14.dp)) {
                PillTabs(
                    listOf("Koyu", "Açık", "Sistem"),
                    when (themeMode) {
                        "dark" -> 0
                        "light" -> 1
                        else -> 2
                    }
                ) {
                    s.setThemeMode(
                        when (it) {
                            0 -> "dark"
                            1 -> "light"
                            else -> "system"
                        }
                    )
                }

                Spacer(Modifier.height(14.dp))
                OverlineText("Vurgu rengi")
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Palette.accentPresets.forEach { preset ->
                        val hex = preset.second
                        val c = parseHex(hex)
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    3.dp,
                                    if (accent.equals(hex, true)) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { s.setAccent(hex) }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                LabeledSwitch("AMOLED siyah", "Tam siyah arka plan", amoled) { s.setAmoled(it) }
            }

            Spacer(Modifier.height(18.dp))

            /* ---------------------------- Dinlenme ------------------------------ */
            DrawerSection("Dinlenme sayacı")
            FitCard(contentPadding = PaddingValues(14.dp)) {
                LabeledSwitch("Otomatik başlat", "Set biter bitmez sayaç çalışsın", autoRest) { s.setAutoRest(it) }
                Spacer(Modifier.height(8.dp))
                OverlineText("Varsayılan süre")
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    listOf(45, 60, 90, 120, 180).forEach { v ->
                        ChoiceChip("$v sn", defaultRest == v, { s.setDefaultRest(v) })
                    }
                }
                Spacer(Modifier.height(2.dp))
                LabeledSwitch("Ekran açık kalsın", "Antrenman sırasında sönmesin", keepOn) { s.setKeepScreenOn(it) }
            }

            Spacer(Modifier.height(18.dp))

            /* ------------------------------- Ses -------------------------------- */
            DrawerSection("Ses ve titreşim")
            FitCard(contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(MaterialTheme.fit.accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.fit.accent, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { openRingtonePicker() }
                    ) {
                        Text("Alarm sesi", style = MaterialTheme.typography.titleSmall)
                        Text(
                            vm.alarmDisplayName(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.fit.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    RoundIconButton(Icons.Default.VolumeUp, MaterialTheme.fit.accent, 36.dp) { vm.previewAlarm() }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ChoiceChip("Telefondan seç", alarmUri != "beep", { openRingtonePicker() })
                    ChoiceChip("Bip", alarmUri == "beep", { s.setAlarmUri("beep") })
                }
                Spacer(Modifier.height(2.dp))
                LabeledSwitch("Sesli uyarı", null, sound) { s.setSound(it) }
                LabeledSwitch("Son 3 saniye bip", null, beep) { s.setCountdownBeep(it) }
                LabeledSwitch("Titreşim", null, vibrate) { s.setVibrate(it) }
            }

            Spacer(Modifier.height(18.dp))

            /* ----------------------------- Bölümler ----------------------------- */
            DrawerSection("Bölümler")
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                DrawerLink(
                    "Antrenman geçmişi",
                    "${stats.totalWorkouts} seans · ${formatTonnage(stats.totalVolume)}",
                    Icons.Default.History
                ) { onNavigate(Routes.HISTORY) }
                DrawerLink("Vücut ölçümleri", "Kilo ve çevre takibi", Icons.Default.MonitorWeight) { onNavigate(Routes.BODY) }
                DrawerLink("Notlar", "Günlük ve fikirler", Icons.Default.EditNote) { onNavigate(Routes.NOTES) }
                DrawerLink("Hesaplayıcılar", "1RM, plaka, ısınma, kalori", Icons.Default.Calculate) { onNavigate(Routes.TOOLS) }
                DrawerLink("Tüm ayarlar", "Yazı boyutu, ekipman, kilit", Icons.Default.Settings) { onNavigate(Routes.SETTINGS) }
            }

            Spacer(Modifier.height(18.dp))

            /* ----------------------------- Eylemler ----------------------------- */
            DrawerSection("Hızlı işlem")
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                DrawerLink(
                    "Serbest antrenman başlat",
                    "Programa bağlı olmadan kaydet",
                    Icons.Default.PlayArrow,
                    MaterialTheme.fit.success
                ) { onStartFreeWorkout() }
                DrawerLink("Verimi dışa aktar", "JSON yedeği paylaş", Icons.Default.Share) {
                    onClose()
                    val ok = Backup.shareBackup(context, vm.exportJson())
                    if (!ok) {
                        Backup.copyToClipboard(context, vm.exportJson())
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                "FitFlow Pro · 2.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.fit.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun DrawerSection(title: String) {
    OverlineText(title, MaterialTheme.fit.accent, Modifier.padding(bottom = 8.dp))
}

@Composable
private fun DrawerLink(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color = MaterialTheme.fit.accent,
    onClick: () -> Unit
) {
    FitCard(onClick = onClick, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp)) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.fit.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = MaterialTheme.fit.muted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/* ------------------------------- Alt gezinme ------------------------------- */

@Composable
private fun BottomBar(current: String, onSelect: (String) -> Unit) {
    NavigationBar(
        containerColor = if (MaterialTheme.fit.isDark) Color(0xFF10141A) else MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.border(
            width = 1.dp,
            color = MaterialTheme.fit.cardBorder,
            shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
        )
    ) {
        navItems.forEach { item ->
            val selected = current == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(item.route) },
                icon = { Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(22.dp)) },
                label = { Text(item.label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.fit.accent,
                    selectedTextColor = MaterialTheme.fit.accent,
                    indicatorColor = MaterialTheme.fit.accent.copy(alpha = 0.16f),
                    unselectedIconColor = MaterialTheme.fit.muted,
                    unselectedTextColor = MaterialTheme.fit.muted
                )
            )
        }
    }
}

/* ---------------------------- Dinlenme sayacı ------------------------------ */

@Composable
private fun RestTimerBar(
    state: RestTimerState,
    onAdjust: (Int) -> Unit,
    @Suppress("UNUSED_PARAMETER") onToggle: () -> Unit,
    onStop: () -> Unit,
    onClick: (() -> Unit)? = null
) {
    // Tasarım 1d: koyu yüzey, soldan dolan ince ilerleme zemini, büyük mono sayaç, −15 / +15 / Atla.
    val accent = if (state.finished) MaterialTheme.fit.success else MaterialTheme.fit.accent
    val shape = RoundedCornerShape(22.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.fit.elevated,
        shadowElevation = 16.dp,
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = if (MaterialTheme.fit.isDark) 0.08f else 0f), shape)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
    ) {
        Box {
            // Geçen süre kadar soldan dolan zemin
            val elapsed = if (state.finished) 1f else (1f - state.progress).coerceIn(0f, 1f)
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(elapsed)
                        .background(accent.copy(alpha = 0.10f))
                )
            }
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.finished) "DİNLENME BİTTİ" else "DİNLENME",
                        style = MaterialTheme.typography.labelSmall.overline(),
                        color = accent
                    )
                    Text(
                        if (state.finished) "Sıradaki set" else formatDuration(state.remaining),
                        style = if (state.finished) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.headlineMedium.mono().copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
                if (!state.finished) {
                    SmallPill("−15") { onAdjust(-15) }
                    SmallPill("+15") { onAdjust(15) }
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accent,
                    modifier = Modifier.clickable { onStop() }
                ) {
                    Text(
                        if (state.finished) "Kapat" else "Atla",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.fit.onAccent,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DurationTimerBar(
    state: DurationTimerState,
    onEarlyFinish: () -> Unit,
    onCancel: () -> Unit
) {
    val accent = Palette.warning
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 14.dp,
        modifier = Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProgressRing(
                progress = state.progress,
                size = 52.dp,
                stroke = 5.dp,
                color = accent
            ) {
                Text(
                    "${state.remaining}",
                    style = MaterialTheme.typography.titleSmall,
                    color = accent
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "⏱️ Hareket Süresi (${state.setNumber}. Set)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${formatDuration(state.remaining)} · ${state.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.fit.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.fit.success,
                    modifier = Modifier.clickable { onEarlyFinish() }
                ) {
                    Text(
                        "Tamamla",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
                RoundIconButton(Icons.Default.Close, MaterialTheme.fit.muted, 34.dp, MaterialTheme.fit.elevated) { onCancel() }
            }
        }
    }
}

@Composable
private fun SmallPill(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.mono(),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 13.dp)
        )
    }
}

/* ------------------------------ Rekor kutlama ------------------------------ */

@Composable
private fun PrDialog(c: PrCelebration, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.fit.gold.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.EmojiEvents, null, tint = MaterialTheme.fit.gold, modifier = Modifier.size(30.dp)) }
        },
        title = {
            Text(
                "${c.prs.size} yeni rekor!",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${c.workoutTitle} seansında kırdığın rekorlar:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.fit.muted
                )
                c.prs.take(8).forEach { pr ->
                    FitCard(contentPadding = PaddingValues(12.dp)) {
                        Text(pr.exerciseName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            when (pr.type) {
                                PrEntity.TYPE_WEIGHT -> "En ağır set: ${pr.weightKg.kg()} × ${pr.reps}"
                                PrEntity.TYPE_E1RM -> "Tahmini 1RM: ${pr.value.kg()}"
                                PrEntity.TYPE_VOLUME -> "Seans hacmi: ${pr.value.trimNum()} kg"
                                else -> pr.value.trimNum()
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.fit.gold
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Harika!", color = MaterialTheme.fit.accent, style = MaterialTheme.typography.titleMedium)
            }
        }
    )
}

/* -------------------------------- Kilit ekranı ----------------------------- */

@Composable
private fun LockScreen(correct: String, onUnlock: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    BackHandler(enabled = true) { }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(MaterialTheme.fit.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Lock, null, tint = MaterialTheme.fit.accent, modifier = Modifier.size(32.dp)) }
            Spacer(Modifier.height(18.dp))
            Text("FitFlow kilitli", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                if (error) "Şifre hatalı, tekrar dene" else "Devam etmek için şifreni gir",
                style = MaterialTheme.typography.bodyMedium,
                color = if (error) MaterialTheme.fit.danger else MaterialTheme.fit.muted
            )
            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { i ->
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (i < input.length) MaterialTheme.fit.accent
                                else MaterialTheme.fit.elevated
                            )
                    )
                }
            }
            Spacer(Modifier.height(30.dp))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                keys.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { key ->
                            Box(
                                Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(if (key.isBlank()) Color.Transparent else MaterialTheme.fit.elevated)
                                    .clickable(enabled = key.isNotBlank()) {
                                        error = false
                                        if (key == "⌫") {
                                            if (input.isNotEmpty()) input = input.dropLast(1)
                                        } else if (input.length < 8) {
                                            input += key
                                            if (input.length >= correct.length) {
                                                if (input == correct) onUnlock() else {
                                                    error = true
                                                    input = ""
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (key == "⌫") {
                                    Icon(Icons.Default.Backspace, null, tint = MaterialTheme.fit.muted)
                                } else {
                                    Text(key, style = MaterialTheme.typography.headlineSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
