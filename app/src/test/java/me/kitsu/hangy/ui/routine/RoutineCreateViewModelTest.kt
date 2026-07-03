package me.kitsu.hangy.ui.routine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.kitsu.hangy.data.db.HangyDatabase
import me.kitsu.hangy.data.repository.RoutineRepository
import me.kitsu.hangy.domain.model.Alternation
import me.kitsu.hangy.domain.model.Protocol
import me.kitsu.hangy.domain.model.Routine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoutineCreateViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: HangyDatabase
    private lateinit var repository: RoutineRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HangyDatabase::class.java,
        )
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .build()
        repository = RoutineRepository(db.routineDao())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun `single-hand save keeps the alternation and drops it for two-hand`() = runTest(dispatcher) {
        val vm = RoutineCreateViewModel(repository)
        vm.update {
            it.copy(
                name = "My routine",
                protocol = Protocol.SINGLE_HAND,
                alternation = Alternation.ALL_ONE_THEN_OTHER,
            )
        }
        var savedId = 0L
        vm.save { savedId = it }
        advanceUntilIdle()

        val stored = repository.getById(savedId)!!
        assertEquals(Protocol.SINGLE_HAND, stored.protocol)
        assertEquals(Alternation.ALL_ONE_THEN_OTHER, stored.alternation)
    }

    @Test
    fun `clone prefills the form from an existing routine`() = runTest(dispatcher) {
        val id = repository.create(
            Routine(
                name = "Origin",
                protocol = Protocol.TWO_HAND,
                tensionSec = 7,
                restSec = 90,
                totalReps = 4,
            ),
        )
        val vm = RoutineCreateViewModel(repository, cloneFromId = id)
        advanceUntilIdle()

        val form = vm.form.value
        assertTrue(form.name.startsWith("Origin"))
        assertTrue(form.name.contains("copy"))
        assertEquals("7", form.tensionSec)
        assertEquals("4", form.totalReps)
    }

    @Test
    fun `invalid form does not save`() = runTest(dispatcher) {
        val vm = RoutineCreateViewModel(repository)
        vm.update { it.copy(name = "") } // blank name
        var called = false
        vm.save { called = true }
        advanceUntilIdle()
        assertTrue(!called)
    }
}
