package me.kitsu.hangy.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.kitsu.hangy.data.repository.CompletedSession
import me.kitsu.hangy.data.repository.MeasurementRepository
import me.kitsu.hangy.data.repository.RoutineRepository
import me.kitsu.hangy.domain.model.Protocol
import me.kitsu.hangy.domain.model.RepResult
import me.kitsu.hangy.domain.model.Sample
import me.kitsu.hangy.domain.model.Session
import me.kitsu.hangy.domain.model.TargetType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseTest {

    private lateinit var db: HangyDatabase
    private lateinit var routines: RoutineRepository
    private lateinit var measurements: MeasurementRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HangyDatabase::class.java,
        ).allowMainThreadQueries().build()
        routines = RoutineRepository(db.routineDao())
        measurements = MeasurementRepository(db.sessionDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `seedIfEmpty populates only once`() = runTest {
        routines.seedIfEmpty(now = 1_000)
        val firstCount = routines.observeAll().first().size
        assertTrue(firstCount >= 3)

        routines.seedIfEmpty(now = 2_000)
        assertEquals(firstCount, routines.observeAll().first().size)
    }

    @Test
    fun `create then delete a routine`() = runTest {
        val id = routines.create(sampleRoutine())
        assertEquals(1, routines.observeAll().first().size)

        routines.delete(id)
        assertEquals(0, routines.observeAll().first().size)
    }

    @Test
    fun `saving a completed session exposes it as a summary and cascades on delete`() = runTest {
        val routineId = routines.create(sampleRoutine())
        val session = Session(
            routineId = routineId,
            startedAt = 123,
            bodyWeightKg = 75.0,
            targetType = TargetType.KG,
            targetLow = 40.0,
            targetHigh = 50.0,
            maxLoadKg = 48.5,
            avgLoadKg = 42.0,
            completed = true,
        )
        val reps =
            listOf(
                RepResult(
                    sessionId = 0,
                    repIndex = 1,
                    hand = me.kitsu.hangy.domain.model.Hand.BOTH,
                    maxKg = 48.5,
                    avgKg = 42.0,
                    actualTutMs = 7_000,
                ),
            )
        val samples = listOf(
            Sample(sessionId = 0, tOffsetMs = 0, weightKg = 10.0),
            Sample(sessionId = 0, tOffsetMs = 100, weightKg = 48.5),
        )
        val sessionId = measurements.save(CompletedSession(session, reps, samples))

        val summaries = measurements.observeSummaries(routineId).first()
        assertEquals(1, summaries.size)
        assertEquals(48.5, summaries.first().maxLoadKg, 1e-9)

        // Full raw stream persisted.
        assertEquals(2, db.sessionDao().getSamples(sessionId).size)

        // Deleting the routine cascades to sessions/samples.
        routines.delete(routineId)
        assertEquals(0, measurements.observeSummaries(routineId).first().size)
        assertEquals(0, db.sessionDao().getSamples(sessionId).size)
    }

    private fun sampleRoutine() = me.kitsu.hangy.domain.model.Routine(
        name = "r",
        protocol = Protocol.TWO_HAND,
        tensionSec = 7,
        restSec = 60,
        totalReps = 3,
    )
}
