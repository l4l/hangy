package me.kitsu.hangy.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.kitsu.hangy.data.db.RoutineDao
import me.kitsu.hangy.data.db.Seed
import me.kitsu.hangy.data.db.toDomain
import me.kitsu.hangy.data.db.toEntity
import me.kitsu.hangy.domain.model.Routine

class RoutineRepository(private val dao: RoutineDao) {

    fun observeAll(): Flow<List<Routine>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeById(id: Long): Flow<Routine?> = dao.observeById(id).map { it?.toDomain() }

    suspend fun getById(id: Long): Routine? = dao.getById(id)?.toDomain()

    /** Persists a new routine and returns its generated id. */
    suspend fun create(routine: Routine): Long = dao.insert(routine.toEntity().copy(id = 0))

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Inserts the built-in routines the first time the app runs (empty database). */
    suspend fun seedIfEmpty(now: Long) {
        if (dao.count() == 0) {
            Seed.defaultRoutines(now).forEach { dao.insert(it.toEntity()) }
        }
    }
}
