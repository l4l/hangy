package me.kitsu.hangy.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.kitsu.hangy.data.db.SessionDao
import me.kitsu.hangy.data.db.toDomain
import me.kitsu.hangy.data.db.toEntity
import me.kitsu.hangy.domain.model.RepResult
import me.kitsu.hangy.domain.model.Sample
import me.kitsu.hangy.domain.model.Session
import me.kitsu.hangy.domain.model.SessionDetail
import me.kitsu.hangy.domain.model.SessionSummary
import me.kitsu.hangy.domain.model.TargetType

/** A finished session ready to persist: the summary, per-rep stats and full raw stream. */
data class CompletedSession(val session: Session, val reps: List<RepResult>, val samples: List<Sample>)

/** A routine's previously used target range, stored in the unit the user entered it in. */
data class SessionTarget(val type: TargetType, val low: Double, val high: Double)

class MeasurementRepository(private val dao: SessionDao) {

    /** Persists a completed session atomically and returns its generated id. */
    suspend fun save(record: CompletedSession): Long = dao.saveCompleted(
        session = record.session.toEntity().copy(id = 0),
        reps = record.reps.map { it.toEntity() },
        samples = record.samples.map { it.toEntity() },
    )

    /** The target used in the routine's most recent session, or null if it was never measured. */
    suspend fun lastTarget(routineId: Long): SessionTarget? =
        dao.lastSession(routineId)?.toDomain()?.let { SessionTarget(it.targetType, it.targetLow, it.targetHigh) }

    /** How many sessions the routine has, so the paged list knows when it has loaded them all. */
    suspend fun countSessions(routineId: Long): Int = dao.countSessions(routineId)

    /** A page of the routine's sessions (newest first) for the session list. */
    suspend fun sessionsPage(routineId: Long, limit: Int, offset: Int): List<Session> =
        dao.getSessionsPaged(routineId, limit, offset).map { it.toDomain() }

    /** The full detail of one session — per-rep stats and raw stream — loaded when a card expands. */
    suspend fun sessionDetail(sessionId: Long): SessionDetail? {
        val session = dao.getSession(sessionId)?.toDomain() ?: return null
        val reps = dao.getRepResults(sessionId).map { it.toDomain() }
        val samples = dao.getSamples(sessionId).map { it.toDomain() }
        return SessionDetail(session, reps, samples)
    }

    fun observeSummaries(routineId: Long): Flow<List<SessionSummary>> = dao.observeSummaries(routineId).map { rows ->
        rows.map {
            SessionSummary(
                sessionId = it.id,
                startedAt = it.startedAt,
                bodyWeightKg = it.bodyWeightKg,
                maxLoadKg = it.maxLoadKg,
                avgLoadKg = it.avgLoadKg,
            )
        }
    }
}
