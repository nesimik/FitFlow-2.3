package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Basit, senkron ve gözlemlenebilir ayar deposu.
 * Her ayar hem SharedPreferences'a yazılır hem de StateFlow olarak yayınlanır.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("fitflow_settings", Context.MODE_PRIVATE)

    private fun <T> flow(value: T) = MutableStateFlow(value)

    /* --------------------------------- Profil -------------------------------- */
    private val _userName = flow(prefs.getString(K_NAME, "Sporcu") ?: "Sporcu")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _heightCm = flow(prefs.getFloat(K_HEIGHT, 175f))
    val heightCm: StateFlow<Float> = _heightCm.asStateFlow()

    private val _weightKg = flow(prefs.getFloat(K_WEIGHT, 75f))
    val weightKg: StateFlow<Float> = _weightKg.asStateFlow()

    private val _age = flow(prefs.getInt(K_AGE, 28))
    val age: StateFlow<Int> = _age.asStateFlow()

    private val _isMale = flow(prefs.getBoolean(K_MALE, true))
    val isMale: StateFlow<Boolean> = _isMale.asStateFlow()

    private val _weeklyGoal = flow(prefs.getInt(K_WEEKLY_GOAL, 3))
    val weeklyGoal: StateFlow<Int> = _weeklyGoal.asStateFlow()

    fun setProfile(name: String, heightCm: Float, weightKg: Float, age: Int, isMale: Boolean) {
        _userName.value = name; _heightCm.value = heightCm
        _weightKg.value = weightKg; _age.value = age; _isMale.value = isMale
        prefs.edit()
            .putString(K_NAME, name).putFloat(K_HEIGHT, heightCm)
            .putFloat(K_WEIGHT, weightKg).putInt(K_AGE, age)
            .putBoolean(K_MALE, isMale).apply()
    }

    fun setWeeklyGoal(v: Int) { _weeklyGoal.value = v; prefs.edit().putInt(K_WEEKLY_GOAL, v).apply() }

    fun setBodyWeight(v: Float) { _weightKg.value = v; prefs.edit().putFloat(K_WEIGHT, v).apply() }

    /* -------------------------------- Hedefler ------------------------------- */
    /** Hedef vücut ağırlığı (kg); 0 = hedef yok. */
    private val _targetWeightKg = flow(prefs.getFloat(K_TARGET_WEIGHT, 0f))
    val targetWeightKg: StateFlow<Float> = _targetWeightKg.asStateFlow()

    /** Güç hedefi: hareket adı + hedef 1RM (kg); kg 0 = hedef yok. */
    private val _goalLiftName = flow(prefs.getString(K_GOAL_LIFT, "Bench Press") ?: "Bench Press")
    val goalLiftName: StateFlow<String> = _goalLiftName.asStateFlow()

    private val _goalLiftKg = flow(prefs.getFloat(K_GOAL_LIFT_KG, 0f))
    val goalLiftKg: StateFlow<Float> = _goalLiftKg.asStateFlow()

    fun setGoals(targetWeightKg: Float, liftName: String, liftKg: Float) {
        _targetWeightKg.value = targetWeightKg; _goalLiftName.value = liftName; _goalLiftKg.value = liftKg
        prefs.edit()
            .putFloat(K_TARGET_WEIGHT, targetWeightKg)
            .putString(K_GOAL_LIFT, liftName)
            .putFloat(K_GOAL_LIFT_KG, liftKg)
            .apply()
    }

    /* --------------------------------- Görünüm ------------------------------- */
    private val _themeMode = flow(prefs.getString(K_THEME, "dark") ?: "dark") // dark | light | system
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()
    fun setThemeMode(v: String) { _themeMode.value = v; prefs.edit().putString(K_THEME, v).apply() }

    // 2.4: varsayılan vurgu tasarımdaki buz mavisine (#3CD3EB) geçti; eski varsayılanı kullananlar da otomatik geçer.
    private val _accent = flow(
        (prefs.getString(K_ACCENT, "#3CD3EB") ?: "#3CD3EB").let { if (it.equals("#22D3EE", ignoreCase = true)) "#3CD3EB" else it }
    )
    val accent: StateFlow<String> = _accent.asStateFlow()
    fun setAccent(v: String) { _accent.value = v; prefs.edit().putString(K_ACCENT, v).apply() }

    private val _amoled = flow(prefs.getBoolean(K_AMOLED, false))
    val amoled: StateFlow<Boolean> = _amoled.asStateFlow()
    fun setAmoled(v: Boolean) { _amoled.value = v; prefs.edit().putBoolean(K_AMOLED, v).apply() }

    private val _fontScale = flow(prefs.getFloat(K_FONT_SCALE, 1f))
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()
    fun setFontScale(v: Float) { _fontScale.value = v; prefs.edit().putFloat(K_FONT_SCALE, v).apply() }

    /* ------------------------------ Dinlenme/Ses ----------------------------- */
    private val _autoRest = flow(prefs.getBoolean(K_AUTO_REST, true))
    val autoRest: StateFlow<Boolean> = _autoRest.asStateFlow()
    fun setAutoRest(v: Boolean) { _autoRest.value = v; prefs.edit().putBoolean(K_AUTO_REST, v).apply() }

    private val _defaultRest = flow(prefs.getInt(K_DEFAULT_REST, 90))
    val defaultRest: StateFlow<Int> = _defaultRest.asStateFlow()
    fun setDefaultRest(v: Int) { _defaultRest.value = v; prefs.edit().putInt(K_DEFAULT_REST, v).apply() }

    private val _sound = flow(prefs.getBoolean(K_SOUND, true))
    val sound: StateFlow<Boolean> = _sound.asStateFlow()
    fun setSound(v: Boolean) { _sound.value = v; prefs.edit().putBoolean(K_SOUND, v).apply() }

    private val _vibrate = flow(prefs.getBoolean(K_VIBRATE, true))
    val vibrate: StateFlow<Boolean> = _vibrate.asStateFlow()
    fun setVibrate(v: Boolean) { _vibrate.value = v; prefs.edit().putBoolean(K_VIBRATE, v).apply() }

    private val _alarmUri = flow(prefs.getString(K_ALARM, "default") ?: "default")
    val alarmUri: StateFlow<String> = _alarmUri.asStateFlow()
    fun setAlarmUri(v: String) { _alarmUri.value = v; prefs.edit().putString(K_ALARM, v).apply() }

    private val _countdownBeep = flow(prefs.getBoolean(K_BEEP, true))
    val countdownBeep: StateFlow<Boolean> = _countdownBeep.asStateFlow()
    fun setCountdownBeep(v: Boolean) { _countdownBeep.value = v; prefs.edit().putBoolean(K_BEEP, v).apply() }

    private val _keepScreenOn = flow(prefs.getBoolean(K_SCREEN_ON, true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()
    fun setKeepScreenOn(v: Boolean) { _keepScreenOn.value = v; prefs.edit().putBoolean(K_SCREEN_ON, v).apply() }

    /* -------------------------------- Ekipman -------------------------------- */
    private val _barWeight = flow(prefs.getFloat(K_BAR, 20f))
    val barWeight: StateFlow<Float> = _barWeight.asStateFlow()
    fun setBarWeight(v: Float) { _barWeight.value = v; prefs.edit().putFloat(K_BAR, v).apply() }

    private val _increment = flow(prefs.getFloat(K_INCREMENT, 2.5f))
    val increment: StateFlow<Float> = _increment.asStateFlow()
    fun setIncrement(v: Float) { _increment.value = v; prefs.edit().putFloat(K_INCREMENT, v).apply() }

    /** Ayarlanabilir dambıl: bir dambıl için en küçük artış (ör. 2 × 0.5 kg plaka = 1 kg, 2 × 1 kg = 2 kg). */
    private val _dumbbellStep = flow(prefs.getFloat(K_DB_STEP, 2f))
    val dumbbellStep: StateFlow<Float> = _dumbbellStep.asStateFlow()
    fun setDumbbellStep(v: Float) { _dumbbellStep.value = v; prefs.edit().putFloat(K_DB_STEP, v).apply() }

    /** Makine / kablo ağırlık yığınındaki bir kademe. */
    private val _machineStep = flow(prefs.getFloat(K_MACHINE_STEP, 5f))
    val machineStep: StateFlow<Float> = _machineStep.asStateFlow()
    fun setMachineStep(v: Float) { _machineStep.value = v; prefs.edit().putFloat(K_MACHINE_STEP, v).apply() }

    /** Progresyon motorunun kullandığı güncel ekipman profili. */
    fun loadingProfile(): com.example.core.LoadingProfile = com.example.core.LoadingProfile(
        barKg = _barWeight.value,
        barbellStep = _increment.value,
        dumbbellStep = _dumbbellStep.value,
        machineStep = _machineStep.value
    )

    /* -------------------------------- Kilit ---------------------------------- */
    private val _lockEnabled = flow(prefs.getBoolean(K_LOCK, false))
    val lockEnabled: StateFlow<Boolean> = _lockEnabled.asStateFlow()
    fun setLockEnabled(v: Boolean) { _lockEnabled.value = v; prefs.edit().putBoolean(K_LOCK, v).apply() }

    private val _passcode = flow(prefs.getString(K_PASS, "") ?: "")
    val passcode: StateFlow<String> = _passcode.asStateFlow()
    fun setPasscode(v: String) { _passcode.value = v; prefs.edit().putString(K_PASS, v).apply() }

    /* -------------------------------- Deload --------------------------------- */
    private val _activeDeloadWeekStart = flow(prefs.getLong(K_DELOAD_WEEK_START, 0L))
    val activeDeloadWeekStart: StateFlow<Long> = _activeDeloadWeekStart.asStateFlow()

    fun setActiveDeloadWeek(weekStartMillis: Long) {
        _activeDeloadWeekStart.value = weekStartMillis
        prefs.edit().putLong(K_DELOAD_WEEK_START, weekStartMillis).apply()
    }

    fun clearActiveDeloadWeek() {
        _activeDeloadWeekStart.value = 0L
        prefs.edit().putLong(K_DELOAD_WEEK_START, 0L).apply()
    }

    fun setPreDeloadBackup(json: String) {
        prefs.edit().putString(K_PRE_DELOAD_BACKUP, json).apply()
    }

    fun getPreDeloadBackup(): String = prefs.getString(K_PRE_DELOAD_BACKUP, "") ?: ""

    /** Deload önerisinin yok sayıldığı haftanın başlangıcı (0 = yok). */
    private val _deloadDismissedWeek = flow(prefs.getLong(K_DELOAD_DISMISSED, 0L))
    val deloadDismissedWeek: StateFlow<Long> = _deloadDismissedWeek.asStateFlow()
    fun setDeloadDismissedWeek(weekStartMillis: Long) {
        _deloadDismissedWeek.value = weekStartMillis
        prefs.edit().putLong(K_DELOAD_DISMISSED, weekStartMillis).apply()
    }

    /* ------------------------ Otomatik yedek ve bildirimler ------------------------ */
    /** Otomatik yedeklerin yazılacağı klasör (SAF ağaç URI'si); boş = kapalı. */
    private val _backupFolder = flow(prefs.getString(K_BACKUP_FOLDER, "") ?: "")
    val backupFolder: StateFlow<String> = _backupFolder.asStateFlow()
    fun setBackupFolder(uri: String) { _backupFolder.value = uri; prefs.edit().putString(K_BACKUP_FOLDER, uri).apply() }

    private val _lastBackupAt = flow(prefs.getLong(K_LAST_BACKUP, 0L))
    val lastBackupAt: StateFlow<Long> = _lastBackupAt.asStateFlow()
    fun setLastBackupAt(v: Long) { _lastBackupAt.value = v; prefs.edit().putLong(K_LAST_BACKUP, v).apply() }

    /** Son otomatik yedek hatası (boş = sorun yok). */
    private val _backupError = flow(prefs.getString(K_BACKUP_ERROR, "") ?: "")
    val backupError: StateFlow<String> = _backupError.asStateFlow()
    fun setBackupError(v: String) { _backupError.value = v; prefs.edit().putString(K_BACKUP_ERROR, v).apply() }

    /** Arka plandaki yedek işi başka bir örnekten yazınca ekranın da güncellenmesi için. */
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        when (key) {
            K_LAST_BACKUP -> _lastBackupAt.value = p.getLong(K_LAST_BACKUP, 0L)
            K_BACKUP_ERROR -> _backupError.value = p.getString(K_BACKUP_ERROR, "") ?: ""
        }
    }
    init { prefs.registerOnSharedPreferenceChangeListener(prefListener) }

    /* ------------------------ Hareket başına merdiven ------------------------ */
    private val _ladders = flow(parseLadders(prefs.getString(K_LADDERS, "") ?: ""))
    /** Program hareketi (RoutineItem id) → merdiven ayarı. Varsayılan ayarlar saklanmaz. */
    val ladderConfigs: StateFlow<Map<Long, com.example.core.LadderConfig>> = _ladders.asStateFlow()

    fun setLadderConfig(itemId: Long, cfg: com.example.core.LadderConfig?) {
        val m = _ladders.value.toMutableMap()
        if (cfg == null || cfg.isDefault) m.remove(itemId) else m[itemId] = cfg
        _ladders.value = m
        prefs.edit().putString(K_LADDERS, ladderJson(m)).apply()
    }

    fun ladderFor(itemId: Long?): com.example.core.LadderConfig? = itemId?.let { _ladders.value[it] }

    /** Health Connect eşitlemesi açık mı. */
    private val _healthConnectOn = flow(prefs.getBoolean(K_HEALTH, false))
    val healthConnectOn: StateFlow<Boolean> = _healthConnectOn.asStateFlow()
    fun setHealthConnectOn(v: Boolean) { _healthConnectOn.value = v; prefs.edit().putBoolean(K_HEALTH, v).apply() }

    /** Antrenman günü hatırlatıcısı. */
    private val _reminderOn = flow(prefs.getBoolean(K_REMINDER_ON, false))
    val reminderOn: StateFlow<Boolean> = _reminderOn.asStateFlow()
    fun setReminderOn(v: Boolean) { _reminderOn.value = v; prefs.edit().putBoolean(K_REMINDER_ON, v).apply() }

    /** Hatırlatma saati, gün içindeki dakika (ör. 17:30 = 1050). */
    private val _reminderMinute = flow(prefs.getInt(K_REMINDER_MIN, 17 * 60))
    val reminderMinute: StateFlow<Int> = _reminderMinute.asStateFlow()
    fun setReminderMinute(v: Int) { _reminderMinute.value = v; prefs.edit().putInt(K_REMINDER_MIN, v).apply() }

    /** Pazartesi haftalık rapor bildirimi. */
    private val _weeklyReportOn = flow(prefs.getBoolean(K_WEEKLY_REPORT, true))
    val weeklyReportOn: StateFlow<Boolean> = _weeklyReportOn.asStateFlow()
    fun setWeeklyReportOn(v: Boolean) { _weeklyReportOn.value = v; prefs.edit().putBoolean(K_WEEKLY_REPORT, v).apply() }

    /** Haftalık raporun en son gönderildiği haftanın başlangıcı. */
    var lastReportWeek: Long
        get() = prefs.getLong(K_LAST_REPORT_WEEK, 0L)
        set(v) { prefs.edit().putLong(K_LAST_REPORT_WEEK, v).apply() }

    /** Hatırlatmanın en son gönderildiği günün başlangıcı. */
    var lastReminderDay: Long
        get() = prefs.getLong(K_LAST_REMINDER_DAY, 0L)
        set(v) { prefs.edit().putLong(K_LAST_REMINDER_DAY, v).apply() }

    /** İlerleme trend grafiği: true = çizgi, false = sütun. */
    private val _trendAsLine = flow(prefs.getBoolean(K_TREND_LINE, false))
    val trendAsLine: StateFlow<Boolean> = _trendAsLine.asStateFlow()
    fun setTrendAsLine(v: Boolean) { _trendAsLine.value = v; prefs.edit().putBoolean(K_TREND_LINE, v).apply() }

    /** Planlı blok: yüklenme haftası sayısı (ardından 1 hafta deload). 0 = otomatik. */
    private val _blockLoadWeeks = flow(prefs.getInt(K_BLOCK_WEEKS, 0))
    val blockLoadWeeks: StateFlow<Int> = _blockLoadWeeks.asStateFlow()
    fun setBlockLoadWeeks(v: Int) {
        _blockLoadWeeks.value = v
        prefs.edit().putInt(K_BLOCK_WEEKS, v).apply()
    }

    /* ------------------------------- Onboarding ------------------------------ */
    var onboardingDone: Boolean
        get() = prefs.getBoolean(K_ONBOARD, false)
        set(v) { prefs.edit().putBoolean(K_ONBOARD, v).apply() }

    private companion object {
        const val K_NAME = "name"; const val K_HEIGHT = "height"; const val K_WEIGHT = "weight"
        const val K_AGE = "age"; const val K_MALE = "male"; const val K_WEEKLY_GOAL = "weekly_goal"
        const val K_THEME = "theme"; const val K_ACCENT = "accent"; const val K_AMOLED = "amoled"
        const val K_FONT_SCALE = "font_scale"
        const val K_AUTO_REST = "auto_rest"; const val K_DEFAULT_REST = "default_rest"
        const val K_SOUND = "sound"; const val K_VIBRATE = "vibrate"; const val K_ALARM = "alarm"
        const val K_BEEP = "beep"; const val K_SCREEN_ON = "screen_on"
        const val K_BAR = "bar"; const val K_INCREMENT = "increment"
        const val K_DB_STEP = "db_step"; const val K_MACHINE_STEP = "machine_step"
        const val K_LOCK = "lock"; const val K_PASS = "pass"; const val K_ONBOARD = "onboard"
        const val K_DELOAD_WEEK_START = "deload_week_start"; const val K_PRE_DELOAD_BACKUP = "pre_deload_backup"
        const val K_DELOAD_DISMISSED = "deload_dismissed_week"; const val K_BLOCK_WEEKS = "block_load_weeks"
        const val K_TREND_LINE = "trend_as_line"
        const val K_BACKUP_FOLDER = "backup_folder"; const val K_LAST_BACKUP = "last_backup_at"; const val K_BACKUP_ERROR = "backup_error"
        const val K_REMINDER_ON = "reminder_on"; const val K_REMINDER_MIN = "reminder_minute"; const val K_WEEKLY_REPORT = "weekly_report"
        const val K_LAST_REPORT_WEEK = "last_report_week"; const val K_LAST_REMINDER_DAY = "last_reminder_day"
        const val K_HEALTH = "health_connect_on"
        const val K_LADDERS = "ladder_configs"

        fun parseLadders(json: String): Map<Long, com.example.core.LadderConfig> = runCatching {
            if (json.isBlank()) return emptyMap()
            val o = org.json.JSONObject(json)
            o.keys().asSequence().associate { k ->
                val c = o.getJSONObject(k)
                k.toLong() to com.example.core.LadderConfig(
                    mode = runCatching { com.example.core.LadderMode.valueOf(c.optString("mode", "AUTO")) }.getOrDefault(com.example.core.LadderMode.AUTO),
                    type = c.optString("type", "").takeIf { it.isNotBlank() }?.let { t -> runCatching { com.example.core.RepScheme.valueOf(t) }.getOrNull() },
                    base = c.optInt("base", 8), steps = c.optInt("steps", 4),
                    spread = c.optInt("spread", 2), reverse = c.optBoolean("reverse", false)
                )
            }
        }.getOrDefault(emptyMap())

        fun ladderJson(m: Map<Long, com.example.core.LadderConfig>): String {
            val o = org.json.JSONObject()
            m.forEach { (id, c) ->
                o.put(id.toString(), org.json.JSONObject().apply {
                    put("mode", c.mode.name); put("type", c.type?.name ?: "")
                    put("base", c.base); put("steps", c.steps); put("spread", c.spread); put("reverse", c.reverse)
                })
            }
            return o.toString()
        }
        const val K_TARGET_WEIGHT = "target_weight"; const val K_GOAL_LIFT = "goal_lift"; const val K_GOAL_LIFT_KG = "goal_lift_kg"
    }
}
