package me.kitsu.hangy.ui.measure

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.kitsu.hangy.data.db.HangyDatabase
import me.kitsu.hangy.data.repository.MeasurementRepository
import me.kitsu.hangy.data.repository.RoutineRepository
import me.kitsu.hangy.data.settings.SettingsRepository
import me.kitsu.hangy.domain.engine.PhaseType
import me.kitsu.hangy.domain.engine.RoutineEngine
import me.kitsu.hangy.domain.model.Protocol
import me.kitsu.hangy.domain.model.Routine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MeasureViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: HangyDatabase
    private lateinit var scale: FakeControllableScale
    private var now = 0L

    private fun buildViewModel(): MeasureViewModel {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, HangyDatabase::class.java)
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .build()
        scale = FakeControllableScale()
        return MeasureViewModel(
            scale = scale,
            routineRepository = RoutineRepository(db.routineDao()),
            measurementRepository = MeasurementRepository(db.sessionDao()),
            settingsRepository = SettingsRepository(context),
            engine = RoutineEngine(),
            elapsedClock = { now },
            wallClock = { 0L },
            processing = dispatcher,
        )
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        if (::db.isInitialized) db.close()
    }

    @Test
    fun `test mode aggregates current, average and max from readings`() = runTest(dispatcher) {
        val vm = buildViewModel()
        vm.startTest()
        advanceUntilIdle()

        now = 0
        scale.emit(10.0)
        advanceUntilIdle()
        now = 100
        scale.emit(20.0)
        advanceUntilIdle()
        now = 200
        scale.emit(30.0)
        advanceUntilIdle()

        assertEquals(MeasureMode.TEST, vm.uiState.value.mode)
        val live = vm.live.value
        assertEquals(30.0, live.currentKg, 1e-9)
        assertEquals(30.0, live.maxKg, 1e-9)
        assertEquals(20.0, live.avgKg, 1e-9) // (10+20+30)/3 within the 15 s window
    }

    @Test
    fun `starting a routine enters RUNNING and drives the engine`() = runTest(dispatcher) {
        val vm = buildViewModel()
        val routine = Routine(
            id = 1,
            name = "r",
            protocol = Protocol.TWO_HAND,
            tensionSec = 5,
            restSec = 0,
            totalReps = 1,
        )
        vm.selectRoutine(routine)
        vm.startRoutine()

        advanceTimeBy(300) // into the get-ready lead-in

        assertEquals(MeasureMode.RUNNING, vm.uiState.value.mode)
        val engineState = vm.live.value.engineState
        assertNotNull(engineState)
        assertEquals(PhaseType.GET_READY, engineState!!.phase)

        vm.stopAndDiscard() // cancel before completion to keep the test deterministic
        assertEquals(MeasureMode.CONFIG, vm.uiState.value.mode)
    }
}
