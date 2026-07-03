package me.kitsu.hangy.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.kitsu.hangy.domain.model.HistoryMetric

/** User-configurable settings with sensible defaults. */
data class AppSettings(
    val bodyWeightKg: Double = DEFAULT_BODY_WEIGHT_KG,
    val avgWindowSec: Int = DEFAULT_AVG_WINDOW_SEC,
    val historyMetric: HistoryMetric = HistoryMetric.MAX,
    /** Load a rep must reach before its tension timer starts. Overridable per session. */
    val startThresholdKg: Double = DEFAULT_START_THRESHOLD_KG,
    val soundEnabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_BODY_WEIGHT_KG = 70.0
        const val DEFAULT_AVG_WINDOW_SEC = 15
        const val DEFAULT_START_THRESHOLD_KG = 4.0
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BODY_WEIGHT = doublePreferencesKey("body_weight_kg")
        val AVG_WINDOW = intPreferencesKey("avg_window_sec")
        val HISTORY_METRIC = stringPreferencesKey("history_metric")
        val START_THRESHOLD = doublePreferencesKey("start_threshold_kg")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            bodyWeightKg = prefs[Keys.BODY_WEIGHT] ?: AppSettings.DEFAULT_BODY_WEIGHT_KG,
            avgWindowSec = prefs[Keys.AVG_WINDOW] ?: AppSettings.DEFAULT_AVG_WINDOW_SEC,
            historyMetric = prefs[Keys.HISTORY_METRIC]
                ?.let { runCatching { HistoryMetric.valueOf(it) }.getOrNull() }
                ?: HistoryMetric.MAX,
            startThresholdKg = prefs[Keys.START_THRESHOLD] ?: AppSettings.DEFAULT_START_THRESHOLD_KG,
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
        )
    }

    suspend fun setBodyWeight(kg: Double) {
        context.dataStore.edit { it[Keys.BODY_WEIGHT] = kg }
    }

    suspend fun setAvgWindowSec(seconds: Int) {
        context.dataStore.edit { it[Keys.AVG_WINDOW] = seconds }
    }

    suspend fun setHistoryMetric(metric: HistoryMetric) {
        context.dataStore.edit { it[Keys.HISTORY_METRIC] = metric.name }
    }

    suspend fun setStartThreshold(kg: Double) {
        context.dataStore.edit { it[Keys.START_THRESHOLD] = kg }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }
}
