package me.kitsu.hangy.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SessionControllerTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: HangyDatabase
    private lateinit var scale: FakeControllableScale
    private lateinit var scope: CoroutineScope
    private lateinit var measurements: MeasurementRepository
    private var now = 0L

    private fun buildController(): SessionController {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, HangyDatabase::class.java)
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .build()
        scale = FakeControllableScale()
        scope = CoroutineScope(dispatcher)
        measurements = MeasurementRepository(db.sessionDao())
        return SessionController(
            scale = scale,
            measurementRepository = measurements,
            settingsRepository = SettingsRepository(context),
            engine = RoutineEngine(),
            scope = scope,
            elapsedClock = { now },
            wallClock = { 0L },
            processing = dispatcher,
        )
    }

    private fun routine() = Routine(
        id = 1,
        name = "r",
        protocol = Protocol.TWO_HAND,
        tensionSec = 5,
        restSec = 0,
        totalReps = 1,
    )

    /** Sessions carry a foreign key to their routine, so saving one requires a real routine row. */
    private suspend fun persistedRoutine(): Routine {
        val id = RoutineRepository(db.routineDao()).create(routine())
        return routine().copy(id = id)
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        if (::scope.isInitialized) scope.cancel()
        if (::db.isInitialized) db.close()
    }

    @Test
    fun `test mode aggregates current, average and max from readings`() = runTest(dispatcher) {
        val controller = buildController()
        controller.startTest()
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

        assertEquals(MeasureMode.TEST, controller.state.value.mode)
        val live = controller.live.value
        assertEquals(30.0, live.currentKg, 1e-9)
        assertEquals(30.0, live.maxKg, 1e-9)
        assertEquals(20.0, live.avgKg, 1e-9) // (10+20+30)/3 within the 15 s window
    }

    @Test
    fun `starting a routine enters RUNNING and drives the engine`() = runTest(dispatcher) {
        val controller = buildController()
        controller.selectRoutine(routine())
        controller.startRoutine()

        advanceTimeBy(300) // into the get-ready lead-in

        assertEquals(MeasureMode.RUNNING, controller.state.value.mode)
        val engineState = controller.live.value.engineState
        assertNotNull(engineState)
        assertEquals(PhaseType.GET_READY, engineState!!.phase)

        controller.stopAndDiscard() // cancel before completion to keep the test deterministic
        assertEquals(MeasureMode.CONFIG, controller.state.value.mode)
    }

    @Test
    fun `connect and disconnect drive isActive and the scan`() = runTest(dispatcher) {
        val controller = buildController()
        assertFalse(controller.isActive.value)

        controller.connect()
        assertTrue(controller.isActive.value)
        assertTrue(scale.started)

        controller.disconnect()
        assertFalse(controller.isActive.value)
        assertFalse(scale.started)
        assertEquals(MeasureMode.IDLE, controller.state.value.mode)
    }

    @Test
    fun `shutdown saves a running session that recorded samples`() = runTest(dispatcher) {
        val controller = buildController()
        controller.selectRoutine(persistedRoutine())
        controller.setSessionThreshold(1.0)
        controller.startRoutine()
        advanceUntilIdle() // the engine settles at WAITING (pull to start) and idles there

        now = 0
        scale.emit(20.0) // crosses the threshold, releasing the gate into TENSION
        // Bounded steps from here: advanceUntilIdle() would run the whole 5 s tension phase and
        // the routine would save itself as completed before the shutdown path ever ran.
        advanceTimeBy(200)
        now = 300
        scale.emit(25.0) // recorded, now that the phase is TENSION
        advanceTimeBy(200)

        controller.finishForShutdown()
        advanceUntilIdle()

        assertEquals(MeasureMode.FINISHED, controller.state.value.mode)
        val id = controller.state.value.savedSessionId
        assertNotNull(id)
        val saved = measurements.sessionDetail(id!!)
        assertNotNull(saved)
        assertFalse("stopped early, so the session is partial", saved!!.session.completed)
        assertTrue(saved.samples.isNotEmpty())
    }

    @Test
    fun `shutdown of a running session with no samples saves nothing`() = runTest(dispatcher) {
        val controller = buildController()
        controller.selectRoutine(routine())
        controller.startRoutine()
        advanceUntilIdle() // no readings ever arrive

        controller.finishForShutdown()
        advanceUntilIdle()

        assertEquals(MeasureMode.CONFIG, controller.state.value.mode)
        assertNull(controller.state.value.savedSessionId)
    }

    @Test
    fun `shutdown in test mode discards without saving`() = runTest(dispatcher) {
        val controller = buildController()
        controller.startTest()
        advanceUntilIdle()
        now = 0
        scale.emit(30.0)
        advanceUntilIdle()

        controller.finishForShutdown()
        advanceUntilIdle()

        assertEquals(MeasureMode.IDLE, controller.state.value.mode)
        assertNull(controller.state.value.savedSessionId)
    }
}
