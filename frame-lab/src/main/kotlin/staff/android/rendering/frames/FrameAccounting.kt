package staff.android.rendering.frames

/** A layer/surface and its frame token jointly identify one simplified observation. */
data class FrameKey(val surface: String, val token: Long)
data class ExpectedFrame(val key: FrameKey, val startNanos: Long, val deadlineNanos: Long)
enum class Outcome { Presented, Dropped }
data class ActualFrame(val key: FrameKey, val startNanos: Long, val endNanos: Long, val outcome: Outcome)
enum class FrameStatus { OnTime, Late, Dropped, Missing, Unmatched }
data class FrameObservation(
    val key: FrameKey,
    val status: FrameStatus,
    val startDelayNanos: Long?,
    val elapsedNanos: Long?,
    val overrunNanos: Long?,
)
data class FrameReport(val observations: List<FrameObservation>) {
    val matchedPresented: Int get() = observations.count { it.status == FrameStatus.OnTime || it.status == FrameStatus.Late }
    val late: Int get() = observations.count { it.status == FrameStatus.Late }
    val lateFraction: Double? get() = if (matchedPresented == 0) null else late.toDouble() / matchedPresented
}
interface FrameAccounting {
    fun account(expected: List<ExpectedFrame>, actual: List<ActualFrame>): FrameReport
}

/** Simplified supplied-data exercise, not a reimplementation of Perfetto's jank classifier. */
object ReferenceFrames : FrameAccounting {
    override fun account(expected: List<ExpectedFrame>, actual: List<ActualFrame>): FrameReport {
        fun validKey(key: FrameKey) = key.surface.isNotBlank() && key.token >= 0
        require(expected.all { validKey(it.key) && it.startNanos >= 0 && it.deadlineNanos > it.startNanos })
        require(actual.all { validKey(it.key) && it.startNanos >= 0 && it.endNanos > it.startNanos })
        require(expected.map { it.key }.toSet().size == expected.size)
        require(actual.map { it.key }.toSet().size == actual.size)
        val byKey = actual.associateBy { it.key }
        val expectedKeys = expected.map { it.key }.toSet()
        val matched = expected.map { plan ->
            val result = byKey[plan.key]
            if (result == null) {
                FrameObservation(plan.key, FrameStatus.Missing, null, null, null)
            } else {
                val startDelay = (result.startNanos - plan.startNanos).coerceAtLeast(0)
                val elapsed = result.endNanos - result.startNanos
                if (result.outcome == Outcome.Dropped) {
                    FrameObservation(plan.key, FrameStatus.Dropped, startDelay, elapsed, null)
                } else {
                    val overrun = result.endNanos - plan.deadlineNanos
                    FrameObservation(plan.key, if (overrun > 0) FrameStatus.Late else FrameStatus.OnTime,
                        startDelay, elapsed, overrun)
                }
            }
        }
        val unmatched = actual.filter { it.key !in expectedKeys }.map {
            FrameObservation(it.key, FrameStatus.Unmatched, null, it.endNanos - it.startNanos, null)
        }
        return FrameReport(matched + unmatched)
    }
}

/** Complete runnable starting checkpoint; intentionally assumes every expected frame was on time. */
object LearnerFrames : FrameAccounting {
    override fun account(expected: List<ExpectedFrame>, actual: List<ActualFrame>): FrameReport =
        FrameReport(expected.map { FrameObservation(it.key, FrameStatus.OnTime, 0, 0, 0) })
}
