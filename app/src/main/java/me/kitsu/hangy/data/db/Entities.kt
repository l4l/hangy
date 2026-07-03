package me.kitsu.hangy.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Enum values are stored as their `name` strings in flat columns (no TypeConverters), which
 * keeps the schema explicit and easy to query. Hold parameters are stored as nullable columns
 * per hand and reconstructed into `HandConfig?` by the mappers.
 */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: String,
    val alternation: String?,
    val tensionSec: Int,
    val restSec: Int,
    val switchSec: Int,
    val totalReps: Int,
    val createdAt: Long,
)

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routineId")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val startedAt: Long,
    val bodyWeightKg: Double,
    val targetType: String,
    val targetLow: Double,
    val targetHigh: Double,
    val maxLoadKg: Double,
    val avgLoadKg: Double,
    val completed: Boolean,
)

@Entity(
    tableName = "rep_results",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class RepResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val repIndex: Int,
    val hand: String,
    val maxKg: Double,
    val avgKg: Double,
    val actualTutMs: Long,
    val tStartMs: Long = 0,
    val tEndMs: Long = 0,
)

@Entity(
    tableName = "samples",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class SampleEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val sessionId: Long, val tOffsetMs: Long, val weightKg: Double)
