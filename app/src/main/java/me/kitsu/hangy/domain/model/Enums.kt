package me.kitsu.hangy.domain.model

/** Whether a routine works one hand at a time or both hands together on the scale. */
enum class Protocol { SINGLE_HAND, TWO_HAND }

/** For single-hand protocols, how the working hand alternates between reps. */
enum class Alternation {
    /** Do every rep on one hand, then every rep on the other. */
    ALL_ONE_THEN_OTHER,

    /** Swap hands on each successive rep. */
    ALTERNATE_EACH_REP,
}

/** Which hand a rep is performed with. BOTH is used for two-hand protocols. */
enum class Hand { LEFT, RIGHT, BOTH }

/** How the user expressed the target load. */
enum class TargetType { KG, PERCENT_BW }

/** Which series the per-routine progress graph plots. */
enum class HistoryMetric { MAX, AVERAGE, BOTH }
