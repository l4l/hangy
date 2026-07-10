package me.kitsu.hangy.ui.measure

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.kitsu.hangy.data.db.HangyDatabase
import me.kitsu.hangy.data.repository.MeasurementRepository
import me.kitsu.hangy.data.repository.RoutineRepository
import me.kitsu.hangy.data.settings.SettingsRepository
import me.kitsu.hangy.domain.engine.RoutineEngine
import me.kitsu.hangy.session.FakeControllableScale
import me.kitsu.hangy.session.MeasureMode
import me.kitsu.hangy.session.ServiceHost
import me.kitsu.hangy.session.SessionController
import me.kitsu.hangy.ui.common.viewModelFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class RecordingServiceHost : ServiceHost {
    var startCount = 0
        private set

    override fun start() {
        startCount++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MeasureViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: HangyDatabase
    private lateinit var scale: FakeControllableScale
    private lateinit var scope: CoroutineScope
    private lateinit var controller: SessionController
    private lateinit var serviceHost: RecordingServiceHost

    private val store = ViewModelStore()

    private fun buildViewModel(): MeasureViewModel {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, HangyDatabase::class.java)
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .build()
        scale = FakeControllableScale()
        scope = CoroutineScope(dispatcher)
        serviceHost = RecordingServiceHost()
        controller = SessionController(
            scale = scale,
            measurementRepository = MeasurementRepository(db.sessionDao()),
            settingsRepository = SettingsRepository(context),
            engine = RoutineEngine(),
            scope = scope,
            elapsedClock = { 0L },
            wallClock = { 0L },
            processing = dispatcher,
        )
        // Built through a ViewModelStore so a test can clear() it and exercise onCleared().
        val factory = viewModelFactory {
            MeasureViewModel(
                controller = controller,
                serviceHost = serviceHost,
                routineRepository = RoutineRepository(db.routineDao()),
                settingsRepository = SettingsRepository(context),
            )
        }
        return ViewModelProvider(store, factory)[MeasureViewModel::class.java]
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
    fun `connect starts the service rather than the scan directly`() = runTest(dispatcher) {
        val vm = buildViewModel()

        vm.connect()

        assertEquals(1, serviceHost.startCount)
        // The service calls SessionController.connect() once it holds foreground importance.
        assertFalse(scale.started)
    }

    @Test
    fun `uiState mirrors the controller`() = runTest(dispatcher) {
        val vm = buildViewModel()
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()

        controller.connect()
        controller.startTest()
        advanceUntilIdle()

        assertEquals(MeasureMode.TEST, vm.uiState.value.mode)
        assertFalse(vm.uiState.value.connection.isReceiving) // Searching, not yet Connected
    }

    @Test
    fun `clearing the ViewModel leaves the scan running so the session survives the Activity`() = runTest(dispatcher) {
        buildViewModel()
        controller.connect()
        assertTrue(scale.started)

        store.clear() // what happens when the Activity is destroyed
        advanceUntilIdle()

        assertTrue("the foreground service, not the ViewModel, owns the scan", scale.started)
        assertTrue(controller.isActive.value)
    }
}
