package me.kitsu.hangy.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import me.kitsu.hangy.audio.SoundCue
import me.kitsu.hangy.audio.ToneGeneratorSoundCue
import me.kitsu.hangy.data.ble.BleScaleRepository
import me.kitsu.hangy.data.ble.ScaleRepository
import me.kitsu.hangy.data.db.HangyDatabase
import me.kitsu.hangy.data.repository.MeasurementRepository
import me.kitsu.hangy.data.repository.RoutineRepository
import me.kitsu.hangy.data.settings.SettingsRepository
import me.kitsu.hangy.domain.engine.RoutineEngine
import me.kitsu.hangy.session.AndroidServiceHost
import me.kitsu.hangy.session.ServiceHost
import me.kitsu.hangy.session.SessionController

/** Manual dependency container, wired once in [me.kitsu.hangy.HangyApplication]. */
class AppContainer(context: Context, appScope: CoroutineScope) {
    private val database = HangyDatabase.build(context)

    val routineRepository = RoutineRepository(database.routineDao())
    val measurementRepository = MeasurementRepository(database.sessionDao())
    val settingsRepository = SettingsRepository(context)
    val scaleRepository: ScaleRepository = BleScaleRepository(context, appScope)
    val routineEngine = RoutineEngine()
    val soundCue: SoundCue = ToneGeneratorSoundCue()

    /** Held here rather than in a ViewModel so an in-flight session outlives the Activity. */
    val sessionController = SessionController(
        scale = scaleRepository,
        measurementRepository = measurementRepository,
        settingsRepository = settingsRepository,
        engine = routineEngine,
        sound = soundCue,
        scope = appScope,
    )

    val serviceHost: ServiceHost = AndroidServiceHost(context)
}
