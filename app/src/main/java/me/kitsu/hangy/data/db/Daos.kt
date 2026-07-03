package me.kitsu.hangy.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE id = :id")
    fun observeById(id: Long): Flow<RoutineEntity?>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getById(id: Long): RoutineEntity?

    @Query("SELECT COUNT(*) FROM routines")
    suspend fun count(): Int

    @Insert
    suspend fun insert(routine: RoutineEntity): Long

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/** Row projection for the progress graph — avoids loading full sessions. */
data class SessionSummaryRow(val id: Long, val startedAt: Long, val bodyWeightKg: Double, val maxLoadKg: Double, val avgLoadKg: Double)

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Insert
    suspend fun insertRepResults(reps: List<RepResultEntity>)

    @Insert
    suspend fun insertSamples(samples: List<SampleEntity>)

    @Query(
        "SELECT id, startedAt, bodyWeightKg, maxLoadKg, avgLoadKg FROM sessions " +
            "WHERE routineId = :routineId ORDER BY startedAt ASC",
    )
    fun observeSummaries(routineId: Long): Flow<List<SessionSummaryRow>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: Long): SessionEntity?

    /** A page of the routine's sessions, newest first, for the session list (manual paging). */
    @Query(
        "SELECT * FROM sessions WHERE routineId = :routineId " +
            "ORDER BY startedAt DESC LIMIT :limit OFFSET :offset",
    )
    suspend fun getSessionsPaged(routineId: Long, limit: Int, offset: Int): List<SessionEntity>

    @Query("SELECT COUNT(*) FROM sessions WHERE routineId = :routineId")
    suspend fun countSessions(routineId: Long): Int

    /** The routine's most recent session, used to pre-fill its target range next time. */
    @Query("SELECT * FROM sessions WHERE routineId = :routineId ORDER BY startedAt DESC LIMIT 1")
    suspend fun lastSession(routineId: Long): SessionEntity?

    @Query("SELECT * FROM samples WHERE sessionId = :sessionId ORDER BY tOffsetMs ASC")
    suspend fun getSamples(sessionId: Long): List<SampleEntity>

    @Query("SELECT * FROM rep_results WHERE sessionId = :sessionId ORDER BY repIndex ASC")
    suspend fun getRepResults(sessionId: Long): List<RepResultEntity>

    /** Atomically persists a completed session together with its reps and full sample stream. */
    @Transaction
    suspend fun saveCompleted(session: SessionEntity, reps: List<RepResultEntity>, samples: List<SampleEntity>): Long {
        val sessionId = insertSession(session)
        insertRepResults(reps.map { it.copy(id = 0, sessionId = sessionId) })
        insertSamples(samples.map { it.copy(id = 0, sessionId = sessionId) })
        return sessionId
    }
}
