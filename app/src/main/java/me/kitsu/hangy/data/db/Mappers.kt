package me.kitsu.hangy.data.db

import me.kitsu.hangy.domain.model.Alternation
import me.kitsu.hangy.domain.model.Hand
import me.kitsu.hangy.domain.model.Protocol
import me.kitsu.hangy.domain.model.RepResult
import me.kitsu.hangy.domain.model.Routine
import me.kitsu.hangy.domain.model.Sample
import me.kitsu.hangy.domain.model.Session
import me.kitsu.hangy.domain.model.TargetType

fun RoutineEntity.toDomain(): Routine = Routine(
    id = id,
    name = name,
    protocol = Protocol.valueOf(protocol),
    alternation = alternation?.let { Alternation.valueOf(it) },
    tensionSec = tensionSec,
    restSec = restSec,
    switchSec = switchSec,
    totalReps = totalReps,
    createdAt = createdAt,
)

fun Routine.toEntity(): RoutineEntity = RoutineEntity(
    id = id,
    name = name,
    protocol = protocol.name,
    alternation = alternation?.name,
    tensionSec = tensionSec,
    restSec = restSec,
    switchSec = switchSec,
    totalReps = totalReps,
    createdAt = createdAt,
)

fun SessionEntity.toDomain(): Session = Session(
    id = id,
    routineId = routineId,
    startedAt = startedAt,
    bodyWeightKg = bodyWeightKg,
    targetType = TargetType.valueOf(targetType),
    targetLow = targetLow,
    targetHigh = targetHigh,
    maxLoadKg = maxLoadKg,
    avgLoadKg = avgLoadKg,
    completed = completed,
)

fun Session.toEntity(): SessionEntity = SessionEntity(
    id = id,
    routineId = routineId,
    startedAt = startedAt,
    bodyWeightKg = bodyWeightKg,
    targetType = targetType.name,
    targetLow = targetLow,
    targetHigh = targetHigh,
    maxLoadKg = maxLoadKg,
    avgLoadKg = avgLoadKg,
    completed = completed,
)

fun RepResult.toEntity(): RepResultEntity = RepResultEntity(
    id = id,
    sessionId = sessionId,
    repIndex = repIndex,
    hand = hand.name,
    maxKg = maxKg,
    avgKg = avgKg,
    actualTutMs = actualTutMs,
    tStartMs = tStartMs,
    tEndMs = tEndMs,
)

fun RepResultEntity.toDomain(): RepResult = RepResult(
    id = id,
    sessionId = sessionId,
    repIndex = repIndex,
    hand = Hand.valueOf(hand),
    maxKg = maxKg,
    avgKg = avgKg,
    actualTutMs = actualTutMs,
    tStartMs = tStartMs,
    tEndMs = tEndMs,
)

fun Sample.toEntity(): SampleEntity = SampleEntity(
    id = id,
    sessionId = sessionId,
    tOffsetMs = tOffsetMs,
    weightKg = weightKg,
)

fun SampleEntity.toDomain(): Sample = Sample(
    id = id,
    sessionId = sessionId,
    tOffsetMs = tOffsetMs,
    weightKg = weightKg,
)
